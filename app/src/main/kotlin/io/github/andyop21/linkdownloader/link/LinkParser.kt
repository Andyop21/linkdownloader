package io.github.andyop21.linkdownloader.link

/**
 * Finds the link in a piece of text, such as what an app's Share or Copy link puts on the
 * clipboard ("Look at this! https://example.com/v/1?s=20"). Pure Kotlin (docs/architecture.md
 * § Code layout).
 */
object LinkParser {

    /** An `http`/`https` URL up to the first space, quote or angle bracket. */
    private val candidate = Regex("""(?i)(?<![\p{L}\p{N}])https?://[^\s\p{Z}"“”«»<>]+""")

    /** A host name made of letters, digits and hyphens between dots, or an IPv6 literal. */
    private val hostName = Regex("""[\p{L}\p{N}]([\p{L}\p{N}-]*[\p{L}\p{N}])?(\.[\p{L}\p{N}]([\p{L}\p{N}-]*[\p{L}\p{N}])?)*\.?""")
    private val ipv6 = Regex("""\[[0-9A-Fa-f:.]+]""")
    private val port = Regex("""\d{0,5}""")

    /** Characters that end a sentence rather than a URL when they come last. */
    private const val TRAILING_PUNCTUATION = ".,;:!?'’…。，、！？"

    private val closingToOpening = mapOf(')' to '(', ']' to '[', '}' to '{', '>' to '<')

    /**
     * The first `http`/`https` link in [text], or null when there is none. Trailing punctuation
     * and closing brackets that are not part of the link are dropped; a link without a host
     * (`https://`, `https://./a`) does not count. A link without a scheme (`www.example.com`) is
     * not a link here. The scheme is returned in lower case; the rest is kept as it is.
     */
    fun firstLink(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return candidate.findAll(text)
            .map { trimEnd(it.value) }
            .firstOrNull { hasHost(it) }
            ?.let { normaliseScheme(it) }
    }

    /** Drops trailing punctuation and closing brackets that have no opening partner in the link. */
    private fun trimEnd(url: String): String {
        var end = url.length
        while (end > 0) {
            val last = url[end - 1]
            val opening = closingToOpening[last]
            end = when {
                last in TRAILING_PUNCTUATION -> end - 1
                opening != null && url.count(end, opening) < url.count(end, last) -> end - 1
                else -> return url.substring(0, end)
            }
        }
        return ""
    }

    private fun String.count(end: Int, char: Char): Int = (0 until end).count { this[it] == char }

    private fun hasHost(url: String): Boolean {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        val hostAndPort = authority.substringAfterLast('@')
        if (hostAndPort.isEmpty()) return false
        if (hostAndPort.startsWith("[")) {
            val close = hostAndPort.indexOf(']')
            if (close < 0) return false
            val rest = hostAndPort.substring(close + 1)
            return ipv6.matches(hostAndPort.substring(0, close + 1)) &&
                (rest.isEmpty() || (rest.startsWith(":") && port.matches(rest.substring(1))))
        }
        val host = hostAndPort.substringBefore(':')
        val portText = hostAndPort.substringAfter(':', missingDelimiterValue = "")
        return hostName.matches(host) && port.matches(portText)
    }

    private fun normaliseScheme(url: String): String {
        val schemeEnd = url.indexOf("://")
        return url.substring(0, schemeEnd).lowercase() + url.substring(schemeEnd)
    }
}
