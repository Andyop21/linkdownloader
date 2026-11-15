package io.github.andyop21.linkdownloader.download

import androidx.annotation.StringRes
import io.github.andyop21.linkdownloader.R

/** The friendly message for this reason (docs/product.md § Failure reasons). No technical text, ever. */
@get:StringRes
val FailureReason.messageRes: Int
    get() = when (this) {
        FailureReason.NoInternet -> R.string.failure_no_internet
        FailureReason.NotSupported -> R.string.failure_not_supported
        FailureReason.Unavailable -> R.string.failure_unavailable
        FailureReason.NeedsLogin -> R.string.failure_needs_login
        FailureReason.Unknown -> R.string.failure_unknown
    }
