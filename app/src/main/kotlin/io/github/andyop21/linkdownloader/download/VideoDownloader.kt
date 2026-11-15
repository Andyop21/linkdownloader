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

/** What a running download reports, in this order (per video, for a post with several). */
sealed interface DownloadEvent {
    /** Checking the link with the site (extract and pick). */
    data object Preparing : DownloadEvent

    /** The video now being downloaded: video [position] of [count], counting from 1. */
    data class TitleKnown(val title: String, val position: Int = 1, val count: Int = 1) : DownloadEvent

    /** The overall fraction from 0 to 1, across every video of the job. It never goes backwards. */
    data class Progress(val fraction: Double) : DownloadEvent

    /** A video is fetched; it is being assembled and written to `Downloads/`. */
    data object Saving : DownloadEvent
}

/** One video saved to `Downloads/`. */
data class SavedVideo(val uri: Uri, val fileName: String, val title: String)

/** One video of a post with several, offered for the user to choose. [index] is what [VideoDownloader.download] takes back. */
data class VideoChoice(val index: Int, val title: String, val thumbnail: String?, val durationSeconds: Double?)

/** How a download ended. A cancelled download ends in a [CancellationException] instead. */
sealed interface DownloadOutcome {
    /**
     * The [videos] saved, in the order they were downloaded. When several were chosen and some
     * failed, [failed] counts them; when all of them fail the outcome is [Failed].
     */
    data class Saved(val videos: List<SavedVideo>, val failed: Int = 0) : DownloadOutcome

    /**
     * The link is a post with several videos and no choice was passed: nothing was downloaded.
     * Download again with the chosen [VideoChoice.index] values.
     */
    data class ChooseVideos(val title: String, val choices: List<VideoChoice>) : DownloadOutcome

    data class Failed(val reason: FailureReason) : DownloadOutcome
}

/**
 * The download pipeline in one call (docs/architecture.md § Download pipeline):
 * extract → pick → fetch → assemble → save, once per video.
 *
 * Each job works in its own folder under the cache directory, deleted when the job ends whatever
 * the outcome. Cancelling the calling coroutine destroys the yt-dlp process, deletes the job's
 * files and any pending `Downloads/` entry, and rethrows the [CancellationException]. Videos of the
 * same job that were already saved stay saved.
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
     *
     * When [link] is a post with several videos, the first call (with no [selection]) returns
     * [DownloadOutcome.ChooseVideos]; a second call with the chosen indices downloads those, one
     * after another. A post with a single video is downloaded at once.
     */
    suspend fun download(
        link: String,
        jobId: String,
        selection: List<Int>? = null,
        onEvent: (DownloadEvent) -> Unit,
    ): DownloadOutcome {
        val jobDir = File(appContext.cacheDir, "$JOBS_DIR/$jobId")
        val start = SystemClock.elapsedRealtime()
        return try {
            if (!isOnline()) return DownloadOutcome.Failed(FailureReason.NoInternet)
            val emit = forwardOnlyProgress(onEvent)
            emit(DownloadEvent.Preparing)
            withContext(Dispatchers.IO) {
                jobDir.deleteRecursively()
                check(jobDir.mkdirs()) { "cannot create the job folder" }
            }
            ytDlp.ensureReady()
            JobRun(link, jobId, jobDir, emit).run(selection).also {
                Log.i(TAG, "job $jobId ended as ${it::class.simpleName} after ${SystemClock.elapsedRealtime() - start} ms")
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "job $jobId cancelled after ${SystemClock.elapsedRealtime() - start} ms")
            throw e
        } catch (e: Exception) {
            val reason = reasonFor(e)
            Log.w(TAG, "job $jobId failed as $reason after ${SystemClock.elapsedRealtime() - start} ms", e)
            DownloadOutcome.Failed(reason)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { jobDir.deleteRecursively() }
        }
    }

    /** One job: its extraction, which a fresh extraction after HTTP 403 replaces, and its events. */
    private inner class JobRun(
        private val link: String,
        private val jobId: String,
        private val jobDir: File,
        private val emit: (DownloadEvent) -> Unit,
    ) {
        private var extraction: Extraction? = null

        suspend fun run(selection: List<Int>?): DownloadOutcome {
            val root = extract().video
            if (!root.isPlaylist) return DownloadOutcome.Saved(listOf(downloadOne(null, 0, 1)))

            val entries = root.videoEntries
            Log.i(TAG, "job $jobId: a playlist with ${root.entries.size} entries, ${entries.size} of them videos")
            if (entries.isEmpty()) {
                throw DownloadException(FailureReason.NotSupported, "a playlist or profile, not a post with videos")
            }
            val chosen = when {
                entries.size == 1 -> listOf(0)
                selection.isNullOrEmpty() -> return chooseVideos(root, entries)
                else -> selection.distinct().filter { it in entries.indices }.sorted()
            }
            if (chosen.isEmpty()) throw DownloadException(FailureReason.Unknown, "the choice matches none of the post's videos")

            val saved = mutableListOf<SavedVideo>()
            val failures = mutableListOf<Exception>()
            chosen.forEachIndexed { position, choice ->
                try {
                    saved += downloadOne(choice, position, chosen.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (chosen.size == 1) throw e
                    Log.w(TAG, "job $jobId: video ${choice + 1} failed; going on with the rest", e)
                    failures += e
                }
            }
            if (saved.isEmpty()) throw failures.first()
            return DownloadOutcome.Saved(saved, failures.size)
        }

        private fun chooseVideos(root: VideoJson, entries: List<IndexedValue<VideoJson>>): DownloadOutcome {
            val choices = entries.mapIndexed { index, (_, entry) ->
                VideoChoice(
                    index = index,
                    title = entry.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Video ${index + 1}",
                    thumbnail = entry.thumbnail,
                    durationSeconds = entry.duration,
                )
            }
            return DownloadOutcome.ChooseVideos(titleOf(root), choices)
        }

        /**
         * Downloads one video: the link's own video when [choice] is null, else video [choice] of
         * the post. [position] and [count] place it in the job's progress.
         */
        private suspend fun downloadOne(choice: Int?, position: Int, count: Int): SavedVideo {
            var titleSent = false
            for (attempt in 1..MAX_EXTRACTIONS) {
                val current = extraction ?: extract()
                val (video, jsonFile) = videoAndJson(current, choice)
                val title = titleOf(video)
                if (!titleSent) {
                    emit(DownloadEvent.TitleKnown(title, position + 1, count))
                    titleSent = true
                }
                val plan = FormatPicker.pick(video)
                val tracker = ProgressTracker(plan.formats.map { it.knownSize })
                val toOverall: (Double) -> Unit = { emit(DownloadEvent.Progress((position + it) / count)) }
                val outDir = File(jobDir, "media").also { withContext(Dispatchers.IO) { it.deleteRecursively(); it.mkdirs() } }
                try {
                    val files = try {
                        fetch(jsonFile, plan, outDir) { line -> tracker.onFetchLine(line)?.let(toOverall) }
                    } catch (e: DownloadException) {
                        if (!e.forbidden) throw e
                        if (attempt == MAX_EXTRACTIONS) {
                            throw DownloadException(FailureReason.Unknown, "HTTP 403 again after a fresh extraction", e)
                        }
                        // YouTube sometimes refuses every URL of one extraction; a fresh one may work (DEC-003).
                        Log.i(TAG, "job $jobId got HTTP 403; extracting again")
                        extraction = null
                        continue
                    }
                    emit(DownloadEvent.Saving)
                    val saved = save(video, plan, files, tracker, toOverall)
                    return SavedVideo(saved.uri, saved.fileName, title)
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { outDir.deleteRecursively() }
                }
            }
            error("unreachable: every attempt either returns or throws")
        }

        private fun videoAndJson(current: Extraction, choice: Int?): Pair<VideoJson, File> {
            if (choice == null) return current.video to current.jsonFile
            val (rawIndex, entry) = current.video.videoEntries.getOrNull(choice)
                ?: throw DownloadException(FailureReason.Unknown, "video ${choice + 1} is gone from the post")
            return entry to current.entryFile(rawIndex, File(jobDir, "entry-$choice.json"))
        }

        private suspend fun extract(): Extraction {
            val start = SystemClock.elapsedRealtime()
            return ytDlp.extract(link, jobDir, jobId).also {
                extraction = it
                Log.i(TAG, "job $jobId extracted ${it.video.extractor}: ${it.video.formats.size} formats, ${it.video.entries.size} entries in ${SystemClock.elapsedRealtime() - start} ms")
            }
        }

        private suspend fun fetch(jsonFile: File, plan: Plan, outDir: File, onLine: (String) -> Unit): List<File> {
            val ids = plan.formats.map { it.formatId }
            Log.i(TAG, "job $jobId plan ${plan::class.simpleName} ${ids.joinToString(",")}")
            val start = SystemClock.elapsedRealtime()
            val files = ytDlp.fetch(jsonFile, ids, outDir, jobId, onLine)
            Log.i(TAG, "job $jobId fetched ${files.sumOf { it.length() }} bytes in ${SystemClock.elapsedRealtime() - start} ms")
            return files
        }
    }

    private suspend fun save(
        video: VideoJson,
        plan: Plan,
        files: List<File>,
        tracker: ProgressTracker,
        onProgress: (Double) -> Unit,
    ): SavedFile {
        val output = withContext(Dispatchers.IO) { outputFor(plan, files) }
        Log.i(TAG, "assembling as ${output::class.simpleName}")
        val fileName = FileNamer.name(video.title, video.extractor, video.id, output.extension)
        val onAssemble: (Double) -> Unit = { progress -> tracker.onAssembleProgress(progress)?.let(onProgress) }
        val start = SystemClock.elapsedRealtime()
        val saved = saver.save(fileName, output.mimeType) { descriptor ->
            when (output) {
                is Output.Join -> MediaAssembler.join(output.video, output.audio, descriptor.fileDescriptor, onAssemble)
                is Output.Remux -> MediaAssembler.remux(output.file, descriptor.fileDescriptor, onAssemble)
                is Output.Copy -> copy(output.file, descriptor.fileDescriptor, onAssemble)
            }
        }
        Log.i(TAG, "saved in ${SystemClock.elapsedRealtime() - start} ms")
        tracker.onSaved()?.let(onProgress)
        return saved
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

    private fun titleOf(video: VideoJson): String =
        video.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: FileNamer.name(null, video.extractor, video.id, "").removeSuffix(".")

    private fun reasonFor(error: Exception): FailureReason {
        val reason = FailureClassifier.classify(error)
        return if (reason == FailureReason.Unknown && !isOnline()) FailureReason.NoInternet else reason
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
