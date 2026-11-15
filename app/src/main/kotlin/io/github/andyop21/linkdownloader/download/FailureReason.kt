package io.github.andyop21.linkdownloader.download

/** Why a download failed, as the product names it (docs/product.md § Failure reasons). */
enum class FailureReason {
    NoInternet,
    NotSupported,
    Unavailable,
    NeedsLogin,
    Unknown,
}

/**
 * A pipeline step failed for [reason]. [forbidden] is true when yt-dlp's error was HTTP 403, which
 * the pipeline answers with one fresh extraction (DEC-003).
 */
class DownloadException(
    val reason: FailureReason,
    message: String,
    cause: Throwable? = null,
    val forbidden: Boolean = false,
) : Exception(message, cause)
