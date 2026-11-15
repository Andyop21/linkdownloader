package io.github.andyop21.linkdownloader.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The part of yt-dlp's `-J` output the pipeline uses. Everything else in the JSON is ignored.
 *
 * Numbers are read as [Double] because yt-dlp writes some of them as integers on one site and as
 * floats on another; a missing value and a JSON `null` both read as `null`.
 */
@Serializable
data class VideoJson(
    val id: String? = null,
    val title: String? = null,
    val extractor: String? = null,
    val formats: List<FormatJson> = emptyList(),
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
        }

        /** Parses yt-dlp's `-J` output. Throws [kotlinx.serialization.SerializationException] on malformed JSON. */
        fun parse(text: String): VideoJson = json.decodeFromString(serializer(), text)
    }
}

/** One entry of the JSON's `formats` array. yt-dlp writes `"none"` for an absent stream; `null` means unknown. */
@Serializable
data class FormatJson(
    @SerialName("format_id") val formatId: String = "",
    val ext: String? = null,
    val protocol: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val tbr: Double? = null,
    val abr: Double? = null,
    val filesize: Double? = null,
    @SerialName("filesize_approx") val filesizeApprox: Double? = null,
    @SerialName("language_preference") val languagePreference: Double? = null,
) {
    /** The size used to weigh this format's share of the progress: `filesize`, else `filesize_approx`. */
    val knownSize: Double? get() = (filesize ?: filesizeApprox)?.takeIf { it > 0 }
}
