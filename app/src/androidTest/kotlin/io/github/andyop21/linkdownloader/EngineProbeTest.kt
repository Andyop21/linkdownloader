package io.github.andyop21.linkdownloader

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.andyop21.linkdownloader.download.Container
import io.github.andyop21.linkdownloader.download.DownloadEvent
import io.github.andyop21.linkdownloader.download.DownloadOutcome
import io.github.andyop21.linkdownloader.download.VideoDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Runs real extractions and downloads with youtubedl-android on a device and logs what each site
 * serves, under the Logcat tag [TAG]. It is a diagnostic, not a regression test: it is skipped
 * unless links are passed, and it writes only into the app's cache, which it deletes at the end.
 *
 * Instrumentation arguments:
 * - `links`: space-separated public links, one per site. Required; without it the test is skipped.
 * - `update`: `false` skips the yt-dlp update and the second pass. Default `true`.
 * - `allCandidates`: `true` fetches every candidate format choice instead of stopping at the first
 *   that works. Default `false`.
 * - `pipeline`: `true` runs the app's own `VideoDownloader` on every link instead (after the update,
 *   unless `update=false`), checks what it saved to `Downloads/`, and deletes it. Default `false`.
 * - `keep`: with `pipeline=true`, `true` leaves the saved videos in `Downloads/` so they can be
 *   played by hand. Default `false`.
 * - `cancel`: with `pipeline=true`, `true` also cancels two downloads of the first link, once
 *   during the fetch and once while saving, and checks that nothing is left behind. Default `false`.
 * - `select`: with `pipeline=true`, which videos of a post with several to download after the
 *   choice is offered: `all`, or 0-based positions such as `0,2`. Default `all`.
 *
 * The probe's own lines name each link by its number and host only. Titles and yt-dlp's messages
 * still carry video IDs and account names, so read the log before quoting it in public records.
 *
 * The format choice mirrors the picker rules in DEC-003. For diagnosis it tries its candidates in
 * order until one downloads, and extracts once more when every candidate gets HTTP 403; the
 * production rules live in `FormatPicker` (P03). Each downloaded file is opened with
 * MediaExtractor, and each track is offered to MediaMuxer, to show what P03 can join.
 */
@RunWith(AndroidJUnit4::class)
class EngineProbeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var allCandidates = false
    private var select = "all"

    @Test
    fun probeLinks() {
        val arguments = InstrumentationRegistry.getArguments()
        val links = arguments.getString(ARG_LINKS).orEmpty().split(' ').filter { it.isNotBlank() }
        assumeTrue("No '$ARG_LINKS' instrumentation argument; the engine probe is skipped", links.isNotEmpty())
        val update = arguments.getString(ARG_UPDATE)?.toBooleanStrictOrNull() ?: true
        allCandidates = arguments.getString(ARG_ALL_CANDIDATES)?.toBooleanStrictOrNull() ?: false
        if (arguments.getString(ARG_PIPELINE)?.toBooleanStrictOrNull() == true) {
            val keep = arguments.getString(ARG_KEEP)?.toBooleanStrictOrNull() ?: false
            val cancel = arguments.getString(ARG_CANCEL)?.toBooleanStrictOrNull() ?: false
            select = arguments.getString(ARG_SELECT) ?: "all"
            probePipeline(links, update, keep, cancel)
            return
        }

        val root = File(context.cacheDir, "engine-probe")
        root.deleteRecursively()
        val failures = mutableListOf<String>()
        try {
            val initStart = SystemClock.elapsedRealtime()
            YoutubeDL.getInstance().init(context)
            log("init: ${SystemClock.elapsedRealtime() - initStart} ms")
            logEnvironment()

            failures += runPass("bundled", links, File(root, "bundled"))

            if (update) {
                val updateStart = SystemClock.elapsedRealtime()
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                log("update: $status in ${SystemClock.elapsedRealtime() - updateStart} ms")
                log("yt-dlp after update: ${ytDlpVersion()} (library records ${YoutubeDL.getInstance().versionName(context)})")
                failures += runPass("updated", links, File(root, "updated"))
            }
        } finally {
            root.deleteRecursively()
            log("cache cleaned: ${!root.exists()}")
        }
        failures.forEach { log("FAILED $it") }
        assertTrue("Engine probe failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Runs the app's [VideoDownloader] on every link, as P04's worker will, and checks each saved
     * file: its container, its tracks, its rotation and its size. Fails if any link fails, if the
     * progress ever goes backwards, or if a cancel leaves anything behind.
     */
    private fun probePipeline(links: List<String>, update: Boolean, keep: Boolean, cancel: Boolean) {
        val failures = mutableListOf<String>()
        val saved = mutableListOf<Uri>()
        try {
            YoutubeDL.getInstance().init(context)
            logEnvironment()
            if (update) {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                log("update: $status, yt-dlp ${ytDlpVersion()}")
            }
            log("===== pipeline =====")
            val downloader = VideoDownloader(context)
            links.forEachIndexed { index, link ->
                val name = "#${index + 1} ${Uri.parse(link).host}"
                try {
                    saved += pipelineLink(downloader, name, link, "probe-${index + 1}")
                } catch (e: Throwable) {
                    log("[$name] ERROR ${e.javaClass.simpleName}: ${e.message}")
                    failures += "[$name] ${e.message}"
                }
            }
            if (cancel) failures += cancelChecks(downloader, links.first())
        } finally {
            if (keep) {
                log("keep=true: ${saved.size} video(s) left in Downloads/ for a manual check")
            } else {
                saved.forEach { context.contentResolver.delete(it, null, null) }
                log("deleted ${saved.size} saved video(s)")
            }
        }
        failures.forEach { log("FAILED $it") }
        assertTrue("Pipeline probe failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Downloads one link with [downloader]; returns the saved files' uris. A post with several
     * videos is offered first; the probe logs the choices and downloads the ones `select` names.
     */
    private fun pipelineLink(downloader: VideoDownloader, name: String, link: String, jobId: String): List<Uri> {
        val first = runPipeline(downloader, name, link, jobId, null)
        if (first !is DownloadOutcome.ChooseVideos) return checkSaved(name, first, jobId)
        log("[$name] a post with ${first.choices.size} videos (title ${first.title.length} chars):")
        first.choices.forEach {
            log("[$name]   choice ${it.index}: title ${it.title.length} chars, ${it.durationSeconds} s, thumbnail from ${it.thumbnail?.let { url -> Uri.parse(url).host }}")
        }
        check(first.choices.size > 1) { "a choice of fewer than two videos" }
        check(first.choices.all { it.thumbnail != null }) { "a choice without a thumbnail" }
        val selection = if (select == "all") first.choices.map { it.index } else select.split(',').map { it.trim().toInt() }
        log("[$name] downloading choices $selection")
        return checkSaved(name, runPipeline(downloader, name, link, "$jobId-chosen", selection), "$jobId-chosen")
    }

    private fun runPipeline(
        downloader: VideoDownloader,
        name: String,
        link: String,
        jobId: String,
        selection: List<Int>?,
    ): DownloadOutcome {
        val start = SystemClock.elapsedRealtime()
        val marks = mutableListOf<String>()
        var progressEvents = 0
        var last = -1.0
        var backwards = false
        val outcome = runBlocking {
            downloader.download(link, jobId, selection) { event ->
                val at = SystemClock.elapsedRealtime() - start
                when (event) {
                    is DownloadEvent.Progress -> {
                        progressEvents++
                        if (event.fraction < last) backwards = true
                        if (last < 0.5 && event.fraction >= 0.5) marks += "50% at $at ms"
                        last = event.fraction
                    }
                    is DownloadEvent.TitleKnown ->
                        marks += "title ${event.position}/${event.count} (${event.title.length} chars) at $at ms"
                    DownloadEvent.Preparing -> marks += "preparing at $at ms"
                    DownloadEvent.Saving -> marks += "saving at $at ms"
                }
            }
        }
        val total = SystemClock.elapsedRealtime() - start
        log("[$name] ${outcome::class.simpleName} after $total ms; events: ${marks.joinToString("; ")}")
        log("[$name] progress: $progressEvents events, last ${"%.3f".format(Locale.ROOT, last)}, went backwards: $backwards")
        check(!backwards) { "progress went backwards" }
        check(!File(context.cacheDir, "jobs/$jobId").exists()) { "job folder left behind" }
        if (outcome is DownloadOutcome.Saved) check(last == 1.0) { "progress ended at $last" }
        return outcome
    }

    private fun checkSaved(name: String, outcome: DownloadOutcome, jobId: String): List<Uri> = when (outcome) {
        is DownloadOutcome.Failed -> throw AssertionError("$jobId failed as ${outcome.reason}")
        is DownloadOutcome.ChooseVideos -> throw AssertionError("$jobId offered a choice again")
        is DownloadOutcome.Saved -> {
            check(outcome.failed == 0) { "${outcome.failed} chosen video(s) failed" }
            outcome.videos.map { video ->
                log("[$name] saved '${video.fileName}' (${video.uri})")
                log("[$name]   ${describeSaved(video.uri)}")
                video.uri
            }
        }
    }

    /** The saved file's size, real container, tracks and rotation, as MediaExtractor reads them. */
    private fun describeSaved(uri: Uri): String {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE)
        val size = resolver.query(uri, projection, null, null, null)
            ?.use { if (it.moveToFirst()) "${it.getLong(0)} bytes ${it.getString(1)}" else "not found" }
        val head = ByteArray(Container.HEAD_SIZE)
        val read = resolver.openInputStream(uri)?.use { it.read(head) } ?: 0
        val extractor = MediaExtractor()
        val tracks = try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).joinToString("; ") { index ->
                val format = extractor.getTrackFormat(index)
                buildString {
                    append(format.getString(MediaFormat.KEY_MIME))
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        append(" ${format.getInteger(MediaFormat.KEY_WIDTH)}x${format.getInteger(MediaFormat.KEY_HEIGHT)}")
                    }
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) append(" rotation ${format.getInteger(MediaFormat.KEY_ROTATION)}")
                    if (format.containsKey(MediaFormat.KEY_DURATION)) append(" ${format.getLong(MediaFormat.KEY_DURATION) / 1000} ms")
                }
            }
        } catch (e: Exception) {
            "MediaExtractor failed: ${e.javaClass.simpleName} ${e.message}"
        } finally {
            extractor.release()
        }
        return "$size, container ${Container.of(head, read)}, tracks: $tracks"
    }

    /**
     * Cancels a download of [link] twice: at the first progress past 20 % (fetching), and at the
     * first progress past the fetch share (writing into the pending Downloads entry). Each time the
     * job folder, the pending entry and the yt-dlp process must be gone.
     */
    private fun cancelChecks(downloader: VideoDownloader, link: String): List<String> {
        val points = listOf<Pair<String, (Double) -> Boolean>>(
            "during fetch" to { it >= 0.2 && it < 0.95 },
            "while saving" to { it > 0.95 },
        )
        val failures = mutableListOf<String>()
        points.forEachIndexed { index, (label, shouldCancel) ->
            val jobId = "probe-cancel-${index + 1}"
            val before = ownDownloads()
            var cancelledAt = -1.0
            var outcome: DownloadOutcome? = null
            val start = SystemClock.elapsedRealtime()
            runBlocking {
                lateinit var job: Job
                job = launch(Dispatchers.Default) {
                    try {
                        outcome = downloader.download(link, jobId) { event ->
                            if (event is DownloadEvent.Progress && cancelledAt < 0 && shouldCancel(event.fraction)) {
                                cancelledAt = event.fraction
                                job.cancel()
                            }
                        }
                    } catch (e: CancellationException) {
                        log("[cancel $label] download threw ${e.javaClass.simpleName}")
                        throw e
                    }
                }
                job.join()
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val after = ownDownloads()
            val jobDirLeft = File(context.cacheDir, "jobs/$jobId").exists()
            val processLeft = YoutubeDL.getInstance().destroyProcessById(jobId)
            log(
                "[cancel $label] cancelled at ${"%.3f".format(Locale.ROOT, cancelledAt)} after $elapsed ms; " +
                    "outcome $outcome; Downloads entries before ${before.size} after ${after.size} " +
                    "(new: ${(after - before).joinToString()}); job folder left: $jobDirLeft; process left: $processLeft",
            )
            when {
                cancelledAt < 0 -> failures += "[cancel $label] the download never reached the cancel point (outcome $outcome)"
                outcome != null -> failures += "[cancel $label] the download finished despite the cancel: $outcome"
                after != before -> failures += "[cancel $label] Downloads entries left behind: ${(after - before).joinToString()}"
                jobDirLeft -> failures += "[cancel $label] job folder left behind"
                processLeft -> failures += "[cancel $label] yt-dlp process still registered"
            }
        }
        return failures
    }

    /** This app's entries in the Downloads collection, pending ones included, as "id:name:pending". */
    private fun ownDownloads(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(context.packageName))
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.IS_PENDING)
        return context.contentResolver.query(collection, projection, args, null)?.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    add("${uri.lastPathSegment}:${cursor.getString(1)}:pending=${cursor.getInt(2)}")
                }
            }
        }.orEmpty()
    }

    private fun logEnvironment() {
        log("device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}, ABIs ${Build.SUPPORTED_ABIS.joinToString()}")
        log("page size: ${Os.sysconf(OsConstants._SC_PAGESIZE)} bytes")
        log("yt-dlp bundled: ${ytDlpVersion()} (library records ${YoutubeDL.getInstance().versionName(context)})")
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val libs = nativeDir.listFiles().orEmpty().sortedBy { it.name }
        log("nativeLibraryDir: ${libs.joinToString { "${it.name} ${it.length() / 1024} KiB" }}")
        log("libffmpeg.so present: ${File(nativeDir, "libffmpeg.so").exists()}")
    }

    /** Runs every link once; returns one line per failed link. */
    private fun runPass(pass: String, links: List<String>, passDir: File): List<String> {
        log("===== pass '$pass' =====")
        val failures = mutableListOf<String>()
        links.forEachIndexed { index, link ->
            val name = "#${index + 1} ${Uri.parse(link).host}"
            try {
                probeLink(pass, name, link, File(passDir, "link-${index + 1}"))
            } catch (e: Exception) {
                val lines = (e.message ?: e.javaClass.name).lines().filter { it.isNotBlank() }
                log("[$pass $name] ERROR ${e.javaClass.simpleName}:")
                lines.forEach { log("[$pass $name]   $it") }
                failures += "[$pass $name] " + lines.takeLast(1).joinToString()
            }
        }
        return failures
    }

    /**
     * Extracts and fetches one link. When every candidate fails with HTTP 403 (YouTube sometimes
     * refuses all the URLs of one extraction), extracts once more and tries again.
     */
    private fun probeLink(pass: String, name: String, link: String, dir: File) {
        val label = "[$pass $name]"
        check(dir.mkdirs()) { "cannot create $dir" }
        val first = extractAndFetch(label, link, File(dir, "attempt-1")) ?: return
        if (first.message.orEmpty().contains("HTTP Error 403")) {
            log("$label every candidate got HTTP 403; extracting again")
            val second = extractAndFetch("$label (re-extracted)", link, File(dir, "attempt-2")) ?: return
            throw second
        }
        throw first
    }

    /** Returns null when a candidate was fetched, else the last fetch error. */
    private fun extractAndFetch(label: String, link: String, dir: File): Exception? {
        check(dir.mkdirs()) { "cannot create $dir" }

        // Extract
        val extractStart = SystemClock.elapsedRealtime()
        val extract = YoutubeDL.getInstance().execute(
            YoutubeDLRequest(link).addOption("-J").addOption("--no-playlist"),
        )
        val extractMs = SystemClock.elapsedRealtime() - extractStart
        logStderr(label, "extract", extract.err)
        val info = JSONObject(extract.out)
        log("$label extractor=${info.str("extractor_key")} title='${info.str("title")}' duration=${info.num("duration")} s extract=$extractMs ms")
        log("$label yt-dlp default selection: format_id=${info.str("format_id")} ext=${info.str("ext")}")

        val formats = info.optJSONArray("formats")?.objects().orEmpty().map(::Format)
        log("$label ${formats.size} formats:")
        formats.forEach { log("$label   $it") }

        // Pick (the rules proposed in DEC-003), then fetch the first candidate that works
        val candidates = candidatesForProbe(formats)
        candidates.forEachIndexed { i, c -> log("$label candidate ${i + 1}: -f ${c.spec} (${c.why})") }
        val infoFile = File(dir, "info.json").apply { writeText(extract.out) }
        var lastError: Exception? = null
        var fetched = false
        for ((i, candidate) in candidates.withIndex()) {
            val outDir = File(dir, "out-${i + 1}").apply { mkdirs() }
            try {
                fetch(label, infoFile, candidate, outDir)
                log("$label fetched candidate ${i + 1}")
                fetched = true
                outDir.deleteRecursively()
                if (!allCandidates) return null
            } catch (e: YoutubeDLException) {
                val reason = e.message.orEmpty().lines().lastOrNull { it.isNotBlank() }
                log("$label candidate ${i + 1} failed: $reason")
                outDir.deleteRecursively()
                lastError = e
            }
        }
        return if (fetched) null else lastError ?: IllegalStateException("no candidates")
    }

    private fun fetch(label: String, infoFile: File, candidate: Selection, outDir: File) {
        val lines = mutableListOf<String>()
        var lastProgress = -1f
        var progressWentBack = false
        val fetchStart = SystemClock.elapsedRealtime()
        val fetch = YoutubeDL.getInstance().execute(
            YoutubeDLRequest(emptyList())
                .addOption("--load-info-json", infoFile.absolutePath)
                .addOption("-f", candidate.spec)
                // No ffmpeg is shipped: never run yt-dlp's ffmpeg fixups (DEC-003).
                .addOption("--fixup", "never")
                .addOption("-o", "${outDir.absolutePath}/%(format_id)s.%(ext)s"),
        ) { progress, _, line ->
            if (progress < lastProgress) progressWentBack = true
            lastProgress = progress
            lines += "${"%.1f".format(Locale.ROOT, progress)}% | $line"
        }
        val fetchMs = SystemClock.elapsedRealtime() - fetchStart
        logStderr(label, "fetch", fetch.err)

        log("$label fetch=$fetchMs ms, ${lines.size} callback lines, progress went backwards: $progressWentBack")
        val shown = if (lines.size <= 12) lines else lines.take(6) + "…" + lines.takeLast(6)
        shown.forEach { log("$label   cb $it") }

        val files = outDir.listFiles().orEmpty().sortedBy { it.name }
        check(files.isNotEmpty()) { "fetch produced no files" }
        files.forEach {
            log("$label file ${it.name} ${it.length()} bytes, container=${sniffContainer(it)}")
            log("$label   tracks: ${describeTracks(it, File(outDir.parentFile, "mux-check.mp4"))}")
        }
    }

    /**
     * Lists the file's tracks as MediaExtractor reads them, and whether MediaMuxer accepts each
     * track's format for an MP4 (addTrack only; no samples are written).
     */
    private fun describeTracks(file: File, scratch: File): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).joinToString("; ") { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                val size = if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                    " ${format.getInteger(MediaFormat.KEY_WIDTH)}x${format.getInteger(MediaFormat.KEY_HEIGHT)}"
                } else {
                    ""
                }
                val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    " ${format.getLong(MediaFormat.KEY_DURATION) / 1000} ms"
                } else {
                    ""
                }
                "$mime$size$duration muxer=${muxerAccepts(format, scratch)}"
            }.ifEmpty { "no tracks" }
        } catch (e: Exception) {
            "MediaExtractor failed: ${e.javaClass.simpleName} ${e.message}"
        } finally {
            extractor.release()
            scratch.delete()
        }
    }

    private fun muxerAccepts(format: MediaFormat, scratch: File): String {
        val muxer = MediaMuxer(scratch.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        return try {
            muxer.addTrack(format)
            "yes"
        } catch (e: Exception) {
            "no (${e.javaClass.simpleName})"
        } finally {
            muxer.release()
        }
    }

    private fun logStderr(label: String, step: String, err: String) {
        err.lines().filter { it.isNotBlank() }.forEach { log("$label $step stderr: $it") }
    }

    private fun ytDlpVersion(): String = try {
        YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList()).addOption("--version")).out.trim()
    } catch (e: Exception) {
        "unknown (${e.message?.lines()?.firstOrNull()})"
    }

    private data class Selection(val spec: String, val why: String)

    /**
     * The picker rules of DEC-003, as an ordered list for diagnosis (FormatPicker takes the first):
     * 1. the best pair of an H.264/HEVC video-only and an AAC audio-only format, with HTTPS video;
     * 2. the same with HLS video;
     * 3. the best single format with video and audio.
     * The 1080 cap applies to the frame's short side; the original audio language beats dubbed
     * tracks, and DRC audio is avoided; yt-dlp lists formats worst to best, so the later entry wins
     * a tie.
     */
    private fun candidatesForProbe(formats: List<Format>): List<Selection> {
        val indexed = formats.withIndex().toList()
        val videos = indexed.filter { it.value.videoOnly && it.value.vcodec.isMuxableVideo() && it.value.withinCap }
        val audio = indexed
            .filter { it.value.audioOnly && it.value.acodec.isAac() }
            .maxWithOrNull(
                compareBy(
                    { it.value.languagePreference ?: 0.0 },
                    { !it.value.isDrc },
                    { it.value.isHttps },
                    { it.value.tbr ?: 0.0 },
                    { it.index },
                ),
            )
        val candidates = mutableListOf<Selection>()
        if (audio != null) {
            val httpsVideo = videos.filter { it.value.isHttps }.maxWithOrNull(videoRank)
            val hlsVideo = videos.filter { it.value.protocol.orEmpty().startsWith("m3u8") }.maxWithOrNull(videoRank)
            httpsVideo?.let { candidates += Selection("${it.value.id},${audio.value.id}", "HTTPS H.264/HEVC video + AAC audio") }
            hlsVideo?.let { candidates += Selection("${it.value.id},${audio.value.id}", "HLS H.264/HEVC video + AAC audio") }
        }
        val single = indexed
            .filter { it.value.hasBoth && it.value.withinCap }
            .maxWithOrNull(
                compareBy(
                    { it.value.shortSide ?: 0.0 },
                    { it.value.ext == "mp4" },
                    { it.value.isHttps },
                    { it.value.tbr ?: 0.0 },
                    { it.index },
                ),
            )
        single?.let { candidates += Selection(it.value.id, "best single format with video and audio") }
        if (candidates.isEmpty()) candidates += Selection("best", "no pair and no single with both; yt-dlp's 'best'")
        return candidates
    }

    private val videoRank = compareBy<IndexedValue<Format>>(
        { it.value.shortSide ?: 0.0 },
        { it.value.tbr ?: 0.0 },
        { it.index },
    )

    /** One entry of the JSON's `formats` array, with the fields the probe logs. */
    private class Format(json: JSONObject) {
        val id: String = json.str("format_id") ?: "?"
        val ext = json.str("ext")
        val protocol = json.str("protocol")
        val vcodec = json.str("vcodec")
        val acodec = json.str("acodec")
        val width = json.num("width")
        val height = json.num("height")
        val note = json.str("format_note")
        val language = json.str("language")
        val languagePreference = json.num("language_preference")
        val tbr = json.num("tbr")
        val filesize = json.num("filesize")
        val filesizeApprox = json.num("filesize_approx")

        // yt-dlp writes "none" for an absent stream; null means the site did not say.
        val videoOnly get() = vcodec != null && vcodec != "none" && acodec == "none"
        val audioOnly get() = vcodec == "none" && acodec != null && acodec != "none"
        val hasBoth get() = vcodec != "none" && acodec != "none"
        val isHttps get() = protocol == "https"
        val isDrc get() = id.contains("drc")

        /** The "p" number: the short side when both sides are known, else the height. */
        val shortSide: Double? get() = if (width != null && height != null) minOf(width, height) else height
        val withinCap get() = (shortSide ?: 0.0) <= 1080

        override fun toString() =
            "id=$id ext=$ext proto=$protocol vcodec=$vcodec acodec=$acodec " +
                "size=${width?.toInt()}x${height?.toInt()} note=$note lang=$language/${languagePreference?.toInt()} tbr=$tbr filesize=${filesize?.toLong()} approx=${filesizeApprox?.toLong()}"
    }

    private companion object {
        const val TAG = "EngineProbe"
        const val ARG_LINKS = "links"
        const val ARG_UPDATE = "update"
        const val ARG_ALL_CANDIDATES = "allCandidates"
        const val ARG_PIPELINE = "pipeline"
        const val ARG_KEEP = "keep"
        const val ARG_CANCEL = "cancel"
        const val ARG_SELECT = "select"

        fun log(message: String) {
            Log.i(TAG, message)
        }

        fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name)

        fun JSONObject.num(name: String): Double? = if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

        fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

        fun String?.isMuxableVideo(): Boolean {
            val codec = this.orEmpty().lowercase()
            return listOf("avc1", "avc3", "h264", "hvc1", "hev1", "h265", "hevc").any { codec.startsWith(it) }
        }

        fun String?.isAac(): Boolean = this != null && (startsWith("mp4a") || this == "aac")

        /** Names the container from the file's first bytes, whatever its extension says. */
        fun sniffContainer(file: File): String {
            val head = ByteArray(376)
            val read = file.inputStream().use { it.read(head) }
            if (read >= 8 && String(head, 4, 4, Charsets.US_ASCII) in setOf("ftyp", "moov", "styp", "sidx")) {
                return "MP4/ISO-BMFF (${String(head, 4, 4, Charsets.US_ASCII)})"
            }
            if (read >= 189 && head[0] == 0x47.toByte() && head[188] == 0x47.toByte()) return "MPEG-TS"
            if (read >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()) {
                return "Matroska/WebM"
            }
            return "unknown (" + head.take(minOf(read, 8)).joinToString(" ") { "%02x".format(Locale.ROOT, it) } + ")"
        }
    }
}
