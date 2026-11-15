package io.github.andyop21.linkdownloader.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream

/** What a running download reports, in this order. */
sealed interface DownloadEvent {
    /** Checking the link with the site (extract and pick). */
    data object Preparing : DownloadEvent

    data class TitleKnown(val title: String) : DownloadEvent

    /** The overall fraction from 0 to 1. It never goes backwards. */
    data class Progress(val fraction: Double) : DownloadEvent

    /** The files are fetched; they are being assembled and written to `Downloads/`. */
    data object Saving : DownloadEvent
}

/** How a download ended. A cancelled download ends in a [CancellationException] instead. */
sealed interface DownloadOutcome {
    data class Saved(val uri: Uri, val fileName: String, val title: String) : DownloadOutcome

    data class Failed(val reason: FailureReason) : DownloadOutcome
}

/**
 * The download pipeline in one call (docs/architecture.md § Download pipeline):
 * extract → pick → fetch → assemble → save.
 *
 * Each job works in its own folder under the cache directory, deleted when the job ends whatever
 * the outcome. Cancelling the calling coroutine destroys the yt-dlp process, deletes the job's
 * files and any pending `Downloads/` entry, and rethrows the [CancellationException].
 */
class VideoDownloader(
    context: Context,
    private val ytDlp: YtDlp = YtDlp(context),
    private val saver: DownloadsSaver = DownloadsSaver(context),
) {

    private val appContext = context.applicationContext

    /**
     * Downloads the video at [link] into `Downloads/`. [jobId] names the job's folder and its yt-dlp
     * process; it must be unique among running jobs. [onEvent] may be called from any thread.
     */
    suspend fun download(link: String, jobId: String, onEvent: (DownloadEvent) -> Unit): DownloadOutcome {
        val jobDir = File(appContext.cacheDir, "$JOBS_DIR/$jobId")
        val start = SystemClock.elapsedRealtime()
        val emit = forwardOnlyProgress(onEvent)
        return try {
            if (!isOnline()) return DownloadOutcome.Failed(FailureReason.NoInternet)
            emit(DownloadEvent.Preparing)
            withContext(Dispatchers.IO) {
                jobDir.deleteRecursively()
                check(jobDir.mkdirs()) { "cannot create the job folder" }
            }
            ytDlp.ensureReady()
            run(link, jobId, jobDir, emit).also {
                Log.i(TAG, "job $jobId saved in ${SystemClock.elapsedRealtime() - start} ms")
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "job $jobId cancelled after ${SystemClock.elapsedRealtime() - start} ms")
            throw e
        } catch (e: Exception) {
            var reason = FailureClassifier.classify(e)
            if (reason == FailureReason.Unknown && !isOnline()) reason = FailureReason.NoInternet
            Log.w(TAG, "job $jobId failed as $reason after ${SystemClock.elapsedRealtime() - start} ms", e)
            DownloadOutcome.Failed(reason)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { jobDir.deleteRecursively() }
        }
    }

    private suspend fun run(
        link: String,
        jobId: String,
        jobDir: File,
        onEvent: (DownloadEvent) -> Unit,
    ): DownloadOutcome {
        var extraction = extract(link, jobDir, jobId)
        val video = extraction.video
        val title = video.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: FileNamer.name(null, video.extractor, video.id, "").removeSuffix(".")
        onEvent(DownloadEvent.TitleKnown(title))

        for (attempt in 1..MAX_EXTRACTIONS) {
            val plan = FormatPicker.pick(extraction.video)
            val tracker = ProgressTracker(plan.formats.map { it.knownSize })
            val files = try {
                fetch(extraction, plan, jobDir, jobId, tracker, onEvent)
            } catch (e: DownloadException) {
                if (!e.forbidden) throw e
                if (attempt == MAX_EXTRACTIONS) {
                    throw DownloadException(FailureReason.Unknown, "HTTP 403 again after a fresh extraction", e)
                }
                // YouTube sometimes refuses every URL of one extraction; a fresh one may work (DEC-003).
                Log.i(TAG, "job $jobId got HTTP 403; extracting again")
                withContext(Dispatchers.IO) { jobDir.listFiles().orEmpty().forEach { it.deleteRecursively() } }
                extraction = extract(link, jobDir, jobId)
                continue
            }
            return save(extraction.video, title, plan, files, tracker, onEvent)
        }
        error("unreachable: every attempt either returns or throws")
    }

    private suspend fun extract(link: String, jobDir: File, jobId: String): Extraction {
        val start = SystemClock.elapsedRealtime()
        return ytDlp.extract(link, jobDir, jobId).also {
            Log.i(TAG, "job $jobId extracted ${it.video.extractor}: ${it.video.formats.size} formats in ${SystemClock.elapsedRealtime() - start} ms")
        }
    }

    private suspend fun fetch(
        extraction: Extraction,
        plan: Plan,
        jobDir: File,
        jobId: String,
        tracker: ProgressTracker,
        onEvent: (DownloadEvent) -> Unit,
    ): List<File> {
        val ids = plan.formats.map { it.formatId }
        Log.i(TAG, "job $jobId plan ${plan::class.simpleName} ${ids.joinToString(",")}")
        val start = SystemClock.elapsedRealtime()
        val files = ytDlp.fetch(extraction.jsonFile, ids, jobDir, jobId) { line ->
            tracker.onFetchLine(line)?.let { onEvent(DownloadEvent.Progress(it)) }
        }
        Log.i(TAG, "job $jobId fetched ${files.sumOf { it.length() }} bytes in ${SystemClock.elapsedRealtime() - start} ms")
        return files
    }

    private suspend fun save(
        video: VideoJson,
        title: String,
        plan: Plan,
        files: List<File>,
        tracker: ProgressTracker,
        onEvent: (DownloadEvent) -> Unit,
    ): DownloadOutcome {
        onEvent(DownloadEvent.Saving)
        val output = withContext(Dispatchers.IO) { outputFor(plan, files) }
        Log.i(TAG, "assembling as $output")
        val fileName = FileNamer.name(video.title, video.extractor, video.id, output.extension)
        val onProgress: (Double) -> Unit = { progress ->
            tracker.onAssembleProgress(progress)?.let { onEvent(DownloadEvent.Progress(it)) }
        }
        val start = SystemClock.elapsedRealtime()
        val saved = saver.save(fileName, output.mimeType) { descriptor ->
            when (output) {
                is Output.Join -> MediaAssembler.join(output.video, output.audio, descriptor.fileDescriptor, onProgress)
                is Output.Remux -> MediaAssembler.remux(output.file, descriptor.fileDescriptor, onProgress)
                is Output.Copy -> copy(output.file, descriptor.fileDescriptor, onProgress)
            }
        }
        Log.i(TAG, "saved in ${SystemClock.elapsedRealtime() - start} ms")
        tracker.onSaved()?.let { onEvent(DownloadEvent.Progress(it)) }
        return DownloadOutcome.Saved(saved.uri, saved.fileName, title)
    }

    /** How the fetched files become the saved file, decided by their real container. */
    private sealed interface Output {
        val extension: String
        val mimeType: String

        data class Join(val video: File, val audio: File) : Output {
            override val extension get() = "mp4"
            override val mimeType get() = MIME_MP4
        }

        data class Remux(val file: File) : Output {
            override val extension get() = "mp4"
            override val mimeType get() = MIME_MP4
        }

        data class Copy(val file: File, override val extension: String, override val mimeType: String) : Output
    }

    private fun outputFor(plan: Plan, files: List<File>): Output = when (plan) {
        is Plan.Pair -> Output.Join(files[0], files[1])
        is Plan.Single -> when (Container.of(files[0])) {
            Container.Mp4 -> Output.Copy(files[0], "mp4", MIME_MP4)
            Container.WebM -> Output.Copy(files[0], "webm", MIME_WEBM)
            // MPEG-TS from HLS, or anything else MediaExtractor may read: rewrite it as MP4.
            Container.MpegTs, Container.Unknown -> Output.Remux(files[0])
        }
    }

    private suspend fun copy(file: File, target: FileDescriptor, onProgress: (Double) -> Unit) =
        withContext(Dispatchers.IO) {
            val total = file.length().coerceAtLeast(1)
            var written = 0L
            val buffer = ByteArray(COPY_BUFFER)
            // The descriptor belongs to DownloadsSaver, which closes it; the stream is not closed here.
            val output = FileOutputStream(target)
            file.inputStream().use { input ->
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    onProgress(written.toDouble() / total)
                }
            }
            output.flush()
        }

    /**
     * Passes events on, dropping any progress that is not ahead of the last one sent: a fresh
     * extraction after HTTP 403 starts its fetch again from zero.
     */
    private fun forwardOnlyProgress(onEvent: (DownloadEvent) -> Unit): (DownloadEvent) -> Unit {
        val lock = Any()
        var last = 0.0
        return { event ->
            if (event !is DownloadEvent.Progress) {
                onEvent(event)
            } else {
                val ahead = synchronized(lock) {
                    (event.fraction > last).also { if (it) last = event.fraction }
                }
                if (ahead) onEvent(event)
            }
        }
    }

    private fun isOnline(): Boolean {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "VideoDownloader"
        const val JOBS_DIR = "jobs"
        const val MIME_MP4 = "video/mp4"
        const val MIME_WEBM = "video/webm"
        const val COPY_BUFFER = 256 * 1024
        const val MAX_EXTRACTIONS = 2
    }
}
