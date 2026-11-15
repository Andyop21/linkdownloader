package io.github.andyop21.linkdownloader.download

/** What to fetch for one video: one format with both streams, or a video-only and an audio-only format. */
sealed interface Plan {

    /** The formats to fetch, in the order yt-dlp downloads them. */
    val formats: List<FormatJson>

    data class Single(val format: FormatJson) : Plan {
        override val formats get() = listOf(format)
    }

    data class Pair(val video: FormatJson, val audio: FormatJson) : Plan {
        override val formats get() = listOf(video, audio)
    }
}

/**
 * Chooses the formats to fetch, by the rules in DEC-003. Pure Kotlin.
 *
 * It returns the first of:
 * 1. the best **pair** of a video-only H.264/HEVC format and an audio-only AAC format;
 * 2. the best **single** format with both streams, as MP4 (or WebM when kept as is);
 * and throws [DownloadException] with [FailureReason.NotSupported] when neither exists. It never
 * returns a format `MediaMuxer` could not write, and never yt-dlp's own `best`.
 *
 * The 1080p cap applies to the frame's short side, so vertical 1080×1920 videos stay in. yt-dlp
 * lists formats worst to best, so the later entry wins a tie.
 */
object FormatPicker {

    /** The cap on the frame's short side. */
    const val MAX_SHORT_SIDE = 1080.0

    private val muxableVideoCodecs = listOf("avc1", "avc3", "h264", "hvc1", "hev1", "h265", "hevc")

    /** Protocols yt-dlp downloads natively, without ffmpeg; `null` is accepted as yt-dlp's default. */
    private val nativeProtocols = setOf("https", "http", "m3u8_native", "m3u8", "http_dash_segments")

    fun pick(video: VideoJson): Plan =
        pickPair(video.formats) ?: pickSingle(video.formats) ?: throw DownloadException(
            FailureReason.NotSupported,
            "no format within ${MAX_SHORT_SIDE.toInt()}p that MediaMuxer can write (${video.formats.size} formats)",
        )

    private fun pickPair(formats: List<FormatJson>): Plan.Pair? {
        val usable = formats.usable()
        val video = usable
            .filter { it.value.isVideoOnly && it.value.hasMuxableVideo && it.value.withinCap }
            .maxWithOrNull(
                compareBy<IndexedValue<FormatJson>>(
                    { it.value.isHttps },
                    { it.value.shortSide ?: 0.0 },
                    { it.value.tbr ?: 0.0 },
                    { it.index },
                ),
            ) ?: return null
        val audio = usable
            .filter { it.value.isAudioOnly && it.value.hasAacAudio }
            .maxWithOrNull(
                compareBy<IndexedValue<FormatJson>>(
                    { it.value.languagePreference ?: 0.0 },
                    { !it.value.isDrc },
                    { it.value.isHttps },
                    { it.value.tbr ?: it.value.abr ?: 0.0 },
                    { it.index },
                ),
            ) ?: return null
        return Plan.Pair(video.value, audio.value)
    }

    private fun pickSingle(formats: List<FormatJson>): Plan.Single? =
        formats.usable()
            .filter { it.value.hasBoth && it.value.withinCap && it.value.isWritableSingle }
            .maxWithOrNull(
                compareBy<IndexedValue<FormatJson>>(
                    { it.value.shortSide ?: 0.0 },
                    { it.value.ext == "mp4" },
                    { it.value.isHttps },
                    { it.value.tbr ?: 0.0 },
                    { it.index },
                ),
            )
            ?.let { Plan.Single(it.value) }

    private fun List<FormatJson>.usable(): List<IndexedValue<FormatJson>> =
        withIndex().filter { it.value.formatId.isNotBlank() && (it.value.protocol == null || it.value.protocol in nativeProtocols) }

    private val FormatJson.isVideoOnly get() = vcodec.isPresent() && acodec == NONE
    private val FormatJson.isAudioOnly get() = vcodec == NONE && acodec.isPresent()

    /** Both streams, where `null` (the site did not say) counts as present. */
    private val FormatJson.hasBoth get() = vcodec != NONE && acodec != NONE

    private val FormatJson.hasMuxableVideo: Boolean
        get() = vcodec.orEmpty().lowercase().let { codec -> muxableVideoCodecs.any { codec.startsWith(it) } }

    private val FormatJson.hasAacAudio: Boolean
        get() = acodec.orEmpty().lowercase().let { it.startsWith("mp4a") || it == "aac" }

    /**
     * A single format is usable as an MP4 when its codecs are H.264/HEVC and AAC, or unknown (an
     * MPEG-TS single is remuxed, so it must suit `MediaMuxer`); a WebM is saved as is.
     */
    private val FormatJson.isWritableSingle: Boolean
        get() = when (ext) {
            "mp4" -> (vcodec == null || hasMuxableVideo) && (acodec == null || hasAacAudio)
            "webm" -> true
            else -> false
        }

    private val FormatJson.isHttps get() = protocol == "https"
    private val FormatJson.isDrc get() = formatId.contains("drc", ignoreCase = true)

    /** The "p" number: the short side when both sides are known, else the height. */
    private val FormatJson.shortSide: Double?
        get() {
            val w = width
            val h = height
            return if (w != null && h != null) minOf(w, h) else h
        }

    private val FormatJson.withinCap get() = (shortSide ?: 0.0) <= MAX_SHORT_SIDE

    private fun String?.isPresent() = this != null && this != NONE

    private const val NONE = "none"
}
