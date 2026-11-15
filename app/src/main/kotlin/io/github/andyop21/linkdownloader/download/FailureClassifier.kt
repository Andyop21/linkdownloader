package io.github.andyop21.linkdownloader.download

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps yt-dlp's error text, and exceptions, to a [FailureReason]. Pure Kotlin.
 *
 * yt-dlp prints warnings and errors to the same stream. When the text holds `ERROR:` lines, only
 * those are read, so a warning (an outdated version, a missing ffmpeg) never decides the reason.
 * The patterns are checked in order: network trouble first, then the strong "unavailable" signs
 * (a private, removed, age- or region-locked video, whose messages also mention signing in), then
 * login and bot checks, then unsupported links, then the weaker "not available" wordings.
 */
object FailureClassifier {

    private val noInternet = listOf(
        "unable to resolve host",
        "failed to resolve",
        "name resolution",
        "name or service not known",
        "no address associated with hostname",
        "nodename nor servname",
        "getaddrinfo failed",
        "network is unreachable",
        "no route to host",
        "connection refused",
        "connection reset",
        "connection aborted",
        "timed out",
        "remote end closed connection",
        "software caused connection abort",
        "errno 7]",
        "errno 101]",
        "errno 110]",
        "errno 111]",
        "errno 113]",
        "errno -2]",
        "errno -3]",
    )

    private val unavailableStrong = listOf(
        "private video",
        "this video is private",
        "video unavailable",
        "this video is unavailable",
        "has been removed",
        "removed by the uploader",
        "account associated with this video has been terminated",
        "confirm your age",
        "age-restricted",
        "age restricted",
        "inappropriate for some users",
        "not available in your country",
        "not available from your location",
        "geo restriction",
        "geo-restricted",
        "geo restricted",
        "blocked it in your country",
        "members-only",
        "join this channel",
        "premieres in",
        "live event will begin",
        "this live event has ended",
        "copyright",
    )

    private val needsLogin = listOf(
        "sign in to confirm",
        "login required",
        "log in",
        "login page",
        "logged-in",
        "logged in",
        "use --cookies",
        "--cookies-from-browser",
        "cookies for the authentication",
        "account authentication",
        "authentication is required",
        "rate-limit reached",
        "not a bot",
        "empty media response",
    )

    private val unavailableWeak = listOf(
        "is not available",
        "content is not available",
        "no longer available",
        "does not exist",
        "not found",
        "http error 404",
        "http error 410",
        "post is unavailable",
        "deleted",
    )

    private val notSupported = listOf(
        "unsupported url",
        "is not a valid url",
        "no video formats found",
        "no video could be found",
        "there's no video",
        "there is no video",
        "no media found",
        "requested format is not available",
        "does not contain a video",
        "not a video",
    )

    /** The reason for yt-dlp's error [text], or [FailureReason.Unknown]. */
    fun classify(text: String?): FailureReason {
        val relevant = relevantText(text)
        if (relevant.isBlank()) return FailureReason.Unknown
        return when {
            noInternet.any(relevant::contains) -> FailureReason.NoInternet
            unavailableStrong.any(relevant::contains) -> FailureReason.Unavailable
            needsLogin.any(relevant::contains) -> FailureReason.NeedsLogin
            notSupported.any(relevant::contains) -> FailureReason.NotSupported
            unavailableWeak.any(relevant::contains) -> FailureReason.Unavailable
            else -> FailureReason.Unknown
        }
    }

    /**
     * The reason for [error]: a [DownloadException] keeps its own; a network exception anywhere in
     * the cause chain is [FailureReason.NoInternet]; otherwise the messages of the chain are read.
     */
    fun classify(error: Throwable): FailureReason {
        if (error is DownloadException) return error.reason
        val chain = generateSequence(error) { it.cause }.take(MAX_CAUSES).toList()
        if (chain.any { it.isNetworkException() }) return FailureReason.NoInternet
        return chain.asSequence()
            .map { classify(it.message) }
            .firstOrNull { it != FailureReason.Unknown }
            ?: FailureReason.Unknown
    }

    /** True when yt-dlp's error [text] is an HTTP 403, which a fresh extraction may cure. */
    fun isForbidden(text: String?): Boolean = relevantText(text).contains("http error 403")

    private fun relevantText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val errorLines = text.lineSequence().filter { it.trimStart().startsWith("ERROR:") }.toList()
        val relevant = if (errorLines.isEmpty()) text else errorLines.joinToString("\n")
        // yt-dlp writes some apostrophes as U+2019 ("you’re not a bot").
        return relevant.lowercase().replace('’', '\'')
    }

    private fun Throwable.isNetworkException(): Boolean =
        this is UnknownHostException ||
            this is ConnectException ||
            this is NoRouteToHostException ||
            this is SocketTimeoutException

    private const val MAX_CAUSES = 8
}
