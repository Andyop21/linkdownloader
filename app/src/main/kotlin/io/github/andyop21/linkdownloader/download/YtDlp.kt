package io.github.andyop21.linkdownloader.download

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The `-J` JSON of one link, parsed, and the file it was saved to for `--load-info-json`. For a
 * post with several videos, [entryFile] writes one entry out for its own `--load-info-json`.
 */
class Extraction(val video: VideoJson, val jsonFile: File, private val tree: JsonObject) {

    /** Writes entry [index] of [VideoJson.entries] to [file] and returns it. */
    fun entryFile(index: Int, file: File): File = file.apply { writeText(VideoJson.entryText(tree, index)) }
}

/**
 * Runs yt-dlp through youtubedl-android, off the main thread, with the options DEC-003 found
 * necessary. Errors come out as [DownloadException] with a [FailureReason]; the raw text goes to
 * Logcat only. Cancelling the calling coroutine destroys the yt-dlp process.
 */
class YtDlp(private val context: Context) {

    private val youtubeDl: YoutubeDL get() = YoutubeDL.getInstance()

    /** Initialises youtubedl-android if the app has not finished doing so yet. Safe to call repeatedly. */
    suspend fun ensureReady() = withContext(Dispatchers.IO) {
        try {
            youtubeDl.init(context.applicationContext)
        } catch (e: YoutubeDLException) {
            throw DownloadException(FailureReason.Unknown, "youtubedl-android failed to initialise", e)
        }
    }

    /**
     * `yt-dlp -J --no-playlist --flat-playlist <link>`; the JSON is saved in [jobDir] and parsed.
     * `--flat-playlist` keeps a real playlist or profile from being extracted video by video; a post
     * with several videos still comes back with every video's formats.
     */
    suspend fun extract(link: String, jobDir: File, processId: String): Extraction {
        val request = YoutubeDLRequest(link)
            .addOption("-J")
            .addOption("--no-playlist")
            .addOption("--flat-playlist")
        val response = run("extract", request, processId) { _ -> }
        return withContext(Dispatchers.IO) {
            val jsonFile = File(jobDir, INFO_JSON)
            jsonFile.writeText(response.out)
            val (video, tree) = try {
                VideoJson.parseTree(response.out)
            } catch (e: SerializationException) {
                throw DownloadException(FailureReason.Unknown, "yt-dlp's JSON could not be read", e)
            } catch (e: IllegalArgumentException) {
                throw DownloadException(FailureReason.Unknown, "yt-dlp's JSON could not be read", e)
            }
            Extraction(video, jsonFile, tree)
        }
    }

    /**
     * Downloads [formatIds] from the saved [jsonFile] into the empty folder [outDir], in that order,
     * and returns the files in the same order. [onLine] receives every line yt-dlp prints, on
     * yt-dlp's reader thread.
     */
    suspend fun fetch(
        jsonFile: File,
        formatIds: List<String>,
        outDir: File,
        processId: String,
        onLine: (String) -> Unit,
    ): List<File> {
        val request = YoutubeDLRequest(emptyList())
            .addOption("--load-info-json", jsonFile.absolutePath)
            .addOption("-f", formatIds.joinToString(","))
            // No ffmpeg is shipped: yt-dlp's ffmpeg fixups crash the bundled version (DEC-003).
            .addOption("--fixup", "never")
            .addOption("--no-mtime")
            .addOption("-o", "${outDir.absolutePath}/%(format_id)s.%(ext)s")
        run("fetch", request, processId, onLine)
        return withContext(Dispatchers.IO) { fetchedFiles(outDir, formatIds) }
    }

    private fun fetchedFiles(outDir: File, formatIds: List<String>): List<File> {
        val files = outDir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
        val byId = formatIds.map { id -> files.firstOrNull { it.nameWithoutExtension == id } }
        if (byId.all { it != null }) return byId.filterNotNull()
        // yt-dlp may have changed a character of the format ID in the file name; fall back to the
        // order the files were written in.
        if (files.size == formatIds.size) return files.sortedBy { it.lastModified() }
        throw DownloadException(
            FailureReason.Unknown,
            "expected ${formatIds.size} files from the fetch, found ${files.size}",
        )
    }

    private suspend fun run(
        step: String,
        request: YoutubeDLRequest,
        processId: String,
        onLine: (String) -> Unit,
    ): YoutubeDLResponse = withContext(Dispatchers.IO) {
        ensureActive()
        val finished = AtomicBoolean(false)
        // Destroys the process when this coroutine is cancelled. The process may not be registered
        // under its ID yet, so it retries until the call returns.
        val killer = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                while (!finished.get()) {
                    if (youtubeDl.destroyProcessById(processId)) break
                    Thread.sleep(KILL_RETRY_MS)
                }
            }
        }
        try {
            val response = youtubeDl.execute(request, processId, false) { _, _, line -> onLine(line) }
            response.err.lineSequence().filter { it.isNotBlank() }.forEach { Log.d(TAG, "$step: $it") }
            response
        } catch (e: YoutubeDL.CanceledException) {
            throw CancellationException("yt-dlp $step was cancelled").apply { initCause(e) }
        } catch (e: InterruptedException) {
            throw CancellationException("yt-dlp $step was interrupted").apply { initCause(e) }
        } catch (e: YoutubeDLException) {
            ensureActive()
            val text = e.message.orEmpty()
            text.lineSequence().filter { it.isNotBlank() }.forEach { Log.w(TAG, "$step failed: $it") }
            throw DownloadException(
                FailureClassifier.classify(text),
                "yt-dlp $step failed",
                e,
                forbidden = FailureClassifier.isForbidden(text),
            )
        } finally {
            finished.set(true)
            killer.cancel()
        }
    }

    private companion object {
        const val TAG = "YtDlp"
        const val INFO_JSON = "info.json"
        const val KILL_RETRY_MS = 50L
    }
}
