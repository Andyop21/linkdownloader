package io.github.andyop21.linkdownloader.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * The part of yt-dlp's `-J` output the pipeline uses. Everything else in the JSON is ignored.
 *
 * A post with several videos (an X post, an Instagram carousel) comes back as a playlist whose
 * [entries] are full videos with their own formats. A real playlist or profile, extracted with
 * `--flat-playlist`, has entries that are only links, without formats.
 *
 * Numbers are read as [Double] because yt-dlp writes some of them as integers on one site and as
 * floats on another; a missing value and a JSON `null` both read as `null`.
 */
@Serializable
data class VideoJson(
    @SerialName("_type") val type: String? = null,
    val id: String? = null,
    val title: String? = null,
    val extractor: String? = null,
    val thumbnail: String? = null,
    val duration: Double? = null,
    val formats: List<FormatJson> = emptyList(),
    val entries: List<VideoJson?> = emptyList(),
) {
    /** True for a playlist-shaped result: a post with several videos, or a playlist or profile. */
    val isPlaylist: Boolean get() = type == "playlist" || type == "multi_video" || entries.isNotEmpty()

    /**
     * The entries yt-dlp extracted as videos, each with its position in [entries]. Link-only
     * entries (a flat playlist's) and missing ones are left out.
     */
    val videoEntries: List<IndexedValue<VideoJson>>
        get() = entries.withIndex().mapNotNull { (index, entry) ->
            if (entry != null && entry.formats.isNotEmpty()) IndexedValue(index, entry) else null
        }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
        }

        /** Parses yt-dlp's `-J` output. Throws [kotlinx.serialization.SerializationException] on malformed JSON. */
        fun parse(text: String): VideoJson = json.decodeFromString(serializer(), text)

        /** Parses yt-dlp's `-J` output and also returns it as a JSON tree, to write single entries back out. */
        fun parseTree(text: String): Pair<VideoJson, JsonObject> {
            val tree = json.parseToJsonElement(text).jsonObject
            return json.decodeFromJsonElement(serializer(), tree) to tree
        }

        /** The JSON text of entry [index] of a playlist [tree], for `--load-info-json`. */
        fun entryText(tree: JsonObject, index: Int): String = tree.getValue("entries").jsonArray[index].toString()
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
