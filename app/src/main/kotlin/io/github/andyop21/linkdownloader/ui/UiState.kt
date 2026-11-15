package io.github.andyop21.linkdownloader.ui

import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.FailureReason
import io.github.andyop21.linkdownloader.download.VideoChoice
import java.util.UUID

/**
 * Everything the screen shows, from [MainViewModel]: the bar's text, what the download is doing
 * and the hidden About sheet. The screen renders it and holds no logic of its own.
 */
data class UiState(
    val bar: String = "",
    val status: Status = Status.Idle,
    val about: AboutState = AboutState(),
)

/** What the screen says is happening (docs/product.md § Behaviour). */
sealed interface Status {

    /** Nothing is happening; the screen waits for a link. */
    data object Idle : Status

    /** Paste or Go found no link: on the clipboard ([fromClipboard]) or in the bar. Nothing started (B2, B3). */
    data class NoLink(val fromClipboard: Boolean) : Status

    /** The download is queued or checking the link with the site. */
    data object Preparing : Status

    /** The link is a post with several videos; the choice sheet is open (B11). */
    data class Choosing(val title: String, val choices: List<VideoChoice>) : Status

    /**
     * Fetching video [position] of [count]. [title] is null until known; [progress] (0 to 1, over
     * the whole job, never going backwards) is null until the first progress arrives.
     */
    data class Downloading(val title: String?, val position: Int, val count: Int, val progress: Float?) : Status

    /** Video [position] of [count] is being assembled and written to `Downloads/`. */
    data class Saving(val title: String?, val position: Int, val count: Int, val progress: Float?) : Status

    /**
     * The job [jobId] saved [saved] videos ([videos] lists them, first to last; it is shorter than
     * [saved] only in the rare case the list did not fit WorkManager's data) and could not save
     * [failed] of the chosen ones.
     */
    data class Done(val jobId: UUID, val videos: List<DownloadResult.Video>, val saved: Int, val failed: Int) : Status

    /**
     * Paste or Go found the link this screen saved last ([saved] videos, [videos] as in [Done]);
     * nothing started, so no duplicate is made (B12).
     */
    data class AlreadySaved(val videos: List<DownloadResult.Video>, val saved: Int) : Status

    /** The download failed for [reason]; the card shows the plain message. */
    data class Failed(val reason: FailureReason) : Status
}

/** True while a download is queued or running: Paste and Go are disabled and Cancel is shown (B4). */
val Status.isRunning: Boolean
    get() = this is Status.Preparing || this is Status.Downloading || this is Status.Saving

/** True when the bar can be cleared: neither a download nor a choice is in progress (B13). */
val Status.isSettled: Boolean
    get() = !isRunning && this !is Status.Choosing

/** True when trying the same link again may work: the phone was offline, or the cause is unknown (B7). */
val FailureReason.retryable: Boolean
    get() = this == FailureReason.NoInternet || this == FailureReason.Unknown

/** The About sheet (B10). [engineVersion] is null while [versionLoading] or when it cannot be read. */
data class AboutState(
    val open: Boolean = false,
    val engineVersion: String? = null,
    val versionLoading: Boolean = false,
    val check: EngineCheck = EngineCheck.None,
)

/** The About sheet's **Update yt-dlp** check. */
sealed interface EngineCheck {
    data object None : EngineCheck
    data object Checking : EngineCheck
    data object UpToDate : EngineCheck
    data class Updated(val version: String?) : EngineCheck

    /** Offline or failed; nothing changed. */
    data object NotChecked : EngineCheck
}
