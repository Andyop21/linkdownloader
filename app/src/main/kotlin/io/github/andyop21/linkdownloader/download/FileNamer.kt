package io.github.andyop21.linkdownloader.download

/**
 * Turns a video title into a safe display name for the file in `Downloads/`. Pure Kotlin.
 *
 * - Characters that are illegal in file names on Android's shared storage or on a PC the file may
 *   be copied to (`/ \ : * ? " < > |`) and control characters are removed.
 * - Emoji and other letters are kept; whitespace runs become one space.
 * - Leading and trailing spaces and dots are trimmed, so the name is never hidden or odd on a PC.
 * - The name is cut to [MAX_NAME_CODE_POINTS] code points and [MAX_NAME_BYTES] UTF-8 bytes (file
 *   systems count bytes, and an emoji takes four), never inside a surrogate pair.
 * - A title that leaves nothing falls back to `<site>-<video id>`, and then to [FALLBACK_NAME].
 */
object FileNamer {

    const val MAX_NAME_CODE_POINTS = 100
    const val MAX_NAME_BYTES = 200
    const val FALLBACK_NAME = "video"

    private val illegal = Regex("""[/\\:*?"<>|\p{Cntrl}\u007F]""")
    private val whitespace = Regex("""[\s  -     　]+""")

    /** The display name for [title], ending in `.extension` (without a leading dot in [extension]). */
    fun name(title: String?, site: String?, videoId: String?, extension: String): String {
        val base = clean(title)
            ?: clean(listOfNotNull(site?.takeIf { it.isNotBlank() }, videoId?.takeIf { it.isNotBlank() }).joinToString("-"))
            ?: FALLBACK_NAME
        return "$base.${extension.trimStart('.')}"
    }

    private fun clean(text: String?): String? {
        if (text == null) return null
        val cleaned = text
            .replace(illegal, " ")
            .replace(whitespace, " ")
            .trim(' ', '.')
        val cut = cut(cleaned).trim(' ', '.')
        return cut.ifEmpty { null }
    }

    private fun cut(text: String): String {
        var end = 0
        var codePoints = 0
        var bytes = 0
        while (end < text.length && codePoints < MAX_NAME_CODE_POINTS) {
            val codePoint = text.codePointAt(end)
            val size = utf8Size(codePoint)
            if (bytes + size > MAX_NAME_BYTES) break
            bytes += size
            codePoints++
            end += Character.charCount(codePoint)
        }
        return dropDanglingJoiners(text.substring(0, end))
    }

    /** A cut inside an emoji sequence must not leave a zero-width joiner or a variation selector at the end. */
    private fun dropDanglingJoiners(text: String): String {
        var result = text
        while (result.isNotEmpty() && result.last() in danglingMarks) result = result.dropLast(1)
        return result
    }

    private val danglingMarks = setOf('‍', '︎', '️')

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }
}
