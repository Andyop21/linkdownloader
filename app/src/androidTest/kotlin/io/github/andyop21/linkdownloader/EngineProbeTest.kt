package io.github.andyop21.linkdownloader

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
 *
 * Links are never logged in full: each one is named by its number and host only, so the log can be
 * quoted in public records.
 *
 * The format choice here is deliberately simple and only feeds the probe's own fetch. The
 * production rules live in `FormatPicker` (P03), derived from these results (DEC-003).
 */
@RunWith(AndroidJUnit4::class)
class EngineProbeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun probeLinks() {
        val arguments = InstrumentationRegistry.getArguments()
        val links = arguments.getString(ARG_LINKS).orEmpty().split(' ').filter { it.isNotBlank() }
        assumeTrue("No '$ARG_LINKS' instrumentation argument; the engine probe is skipped", links.isNotEmpty())
        val update = arguments.getString(ARG_UPDATE)?.toBooleanStrictOrNull() ?: true

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
                val reason = (e.message ?: e.javaClass.name).lines().filter { it.isNotBlank() }.takeLast(3).joinToString(" | ")
                log("[$pass $name] ERROR ${e.javaClass.simpleName}: $reason")
                failures += "[$pass $name] $reason"
            }
        }
        return failures
    }

    private fun probeLink(pass: String, name: String, link: String, dir: File) {
        val label = "[$pass $name]"
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

        // Pick (probe-only rules; see the class comment)
        val selection = pickForProbe(formats)
        log("$label probe picks -f ${selection.spec} (${selection.why})")

        // Fetch from the saved JSON
        val infoFile = File(dir, "info.json").apply { writeText(extract.out) }
        val outDir = File(dir, "out").apply { mkdirs() }
        val lines = mutableListOf<String>()
        var lastProgress = -1f
        var progressWentBack = false
        val fetchStart = SystemClock.elapsedRealtime()
        val fetch = YoutubeDL.getInstance().execute(
            YoutubeDLRequest(emptyList())
                .addOption("--load-info-json", infoFile.absolutePath)
                .addOption("-f", selection.spec)
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
        files.forEach { log("$label file ${it.name} ${it.length()} bytes, container=${sniffContainer(it)}") }
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

    private fun pickForProbe(formats: List<Format>): Selection {
        val rank = compareBy<Format>({ it.height ?: 0.0 }, { it.tbr ?: 0.0 })
        val video = formats
            .filter { it.videoOnly && it.vcodec.isMuxableVideo() && (it.height ?: 0.0) <= 1080 }
            .maxWithOrNull(rank)
        val audio = formats
            .filter { it.audioOnly && it.acodec.orEmpty().startsWith("mp4a") }
            .maxWithOrNull(compareBy { it.tbr ?: 0.0 })
        if (video != null && audio != null) {
            return Selection("${video.id},${audio.id}", "H.264/HEVC video-only + AAC audio-only pair")
        }
        val single = formats.filter { it.hasBoth }.maxWithOrNull(rank)
        if (single != null) return Selection(single.id, "best single format with video and audio")
        return Selection("best", "no pair and no single with both; yt-dlp's 'best'")
    }

    /** One entry of the JSON's `formats` array, with the fields the probe logs. */
    private class Format(json: JSONObject) {
        val id: String = json.str("format_id") ?: "?"
        val ext = json.str("ext")
        val protocol = json.str("protocol")
        val vcodec = json.str("vcodec")
        val acodec = json.str("acodec")
        val height = json.num("height")
        val tbr = json.num("tbr")
        val filesize = json.num("filesize")
        val filesizeApprox = json.num("filesize_approx")

        // yt-dlp writes "none" for an absent stream; null means the site did not say.
        val videoOnly get() = vcodec != null && vcodec != "none" && acodec == "none"
        val audioOnly get() = vcodec == "none" && acodec != null && acodec != "none"
        val hasBoth get() = vcodec != "none" && acodec != "none"

        override fun toString() =
            "id=$id ext=$ext proto=$protocol vcodec=$vcodec acodec=$acodec height=${height?.toInt()} " +
                "tbr=$tbr filesize=${filesize?.toLong()} approx=${filesizeApprox?.toLong()}"
    }

    private companion object {
        const val TAG = "EngineProbe"
        const val ARG_LINKS = "links"
        const val ARG_UPDATE = "update"

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
