package io.github.andyop21.linkdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.FailureReason
import io.github.andyop21.linkdownloader.download.messageRes
import io.github.andyop21.linkdownloader.download.titleRes
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.AppType
import io.github.andyop21.linkdownloader.ui.theme.CardShape
import io.github.andyop21.linkdownloader.ui.theme.LocalReducedMotion
import io.github.andyop21.linkdownloader.ui.theme.Motion
import io.github.andyop21.linkdownloader.ui.theme.Tone

import kotlin.math.roundToInt

/** The icon tile's tone for this status: neutral, blue while working, green when saved, amber or red when not. */
val Status.tone: Tone
    get() = when (this) {
        Status.Idle, is Status.Choosing -> Tone.Neutral
        is Status.NoLink -> Tone.Warning
        Status.Preparing, is Status.Downloading, is Status.Saving -> Tone.Primary
        is Status.Done, is Status.AlreadySaved -> Tone.Success
        is Status.Failed -> Tone.Danger
    }

/**
 * What the card shows as one piece. A new kind cross-fades in; a change within a kind (progress,
 * a title arriving) updates in place.
 */
private val Status.kind: Any
    get() = when (this) {
        Status.Idle -> "idle"
        is Status.NoLink -> this
        Status.Preparing, is Status.Downloading, is Status.Saving -> "running"
        is Status.Choosing -> "choosing"
        is Status.Done -> jobId
        is Status.AlreadySaved -> "already"
        is Status.Failed -> reason
    }

/** What the card's actions do. [canRetry] shows **Try again** on a failure that may pass. */
class StatusActions(
    val onCancel: () -> Unit = {},
    val onOpen: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDownloadAgain: () -> Unit = {},
    val canRetry: Boolean = false,
)

/**
 * The card under the link field (docs/product.md § The screen): an icon tile in the status's
 * tone, a small uppercase label, one line about what is happening and a detail line; the progress
 * while a download runs; and the action that fits (**Cancel**, **Open**, **Try again**,
 * **Download again**). A new kind of status fades and rises in while the card eases to its new
 * height. TalkBack announces the main line when it changes.
 */
@Composable
fun StatusCard(status: Status, actions: StatusActions, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedContent(
        targetState = status,
        contentKey = { it.kind },
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (
                    fadeIn(tween(Motion.SWAP, delayMillis = 60, easing = Motion.EaseOut)) +
                        slideInVertically(tween(Motion.SWAP, delayMillis = 60, easing = Motion.EaseOut)) { rise }
                    ) togetherWith fadeOut(tween(Motion.FAST, easing = Motion.EaseIn)) using
                    SizeTransform(clip = false) { _, _ -> tween(Motion.SLOW, easing = Motion.Standard) }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, CardShape)
            .border(1.dp, colors.border, CardShape),
        label = "status",
    ) { shown ->
        StatusBody(shown, actions)
    }
}

@Composable
private fun StatusBody(status: Status, actions: StatusActions) {
    val colors = AppTheme.colors
    val text = statusText(status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            StatusTile(status)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.isRunning) {
                        PulseDot(colors.primary)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = text.eyebrow.uppercase(LocalLocale.current.platformLocale),
                        style = AppType.eyebrow,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    percentOf(status)?.let {
                        Text(
                            text = stringResource(R.string.status_percent, it),
                            style = AppType.figure,
                            color = colors.ink2,
                        )
                    }
                }
                Text(
                    text = text.title,
                    style = AppType.cardTitle,
                    color = colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                text.detail?.let {
                    Text(
                        text = it,
                        style = AppType.body,
                        color = colors.muted,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (status.isRunning) {
            ProgressLine(progress = progressOf(status), color = colors.primary, track = colors.track)
        }
        StatusButtons(status, actions)
    }
}

/** The tile; a saved check pops in on the spring. */
@Composable
private fun StatusTile(status: Status) {
    val icon = when (status) {
        Status.Idle -> AppIcons.Link
        is Status.NoLink -> AppIcons.Empty
        Status.Preparing, is Status.Downloading, is Status.Saving -> AppIcons.Download
        is Status.Choosing -> AppIcons.Grid
        is Status.Done, is Status.AlreadySaved -> AppIcons.Check
        is Status.Failed -> AppIcons.Alert
    }
    val pops = status is Status.Done && !LocalReducedMotion.current
    val scale = remember { Animatable(if (pops) 0.6f else 1f) }
    LaunchedEffect(pops) {
        if (pops) scale.animateTo(1f, tween(Motion.POP, delayMillis = 80, easing = Motion.Spring))
    }
    IconTile(
        icon = icon,
        tone = status.tone,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

@Composable
private fun StatusButtons(status: Status, actions: StatusActions) {
    val canOpen = (status is Status.Done && status.videos.isNotEmpty()) ||
        (status is Status.AlreadySaved && status.videos.isNotEmpty())
    val canRetry = status is Status.Failed && actions.canRetry
    val again = status is Status.AlreadySaved
    if (!status.isRunning && !canOpen && !canRetry && !again) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status.isRunning) ActionButton(stringResource(R.string.status_cancel), actions.onCancel)
        if (again) ActionButton(stringResource(R.string.status_download_again), actions.onDownloadAgain)
        if (canRetry) ActionButton(stringResource(R.string.status_retry), actions.onRetry, primary = true)
        if (canOpen) ActionButton(stringResource(R.string.status_open), actions.onOpen, primary = true)
    }
}

private fun progressOf(status: Status): Float? = when (status) {
    is Status.Downloading -> status.progress
    is Status.Saving -> status.progress
    else -> null
}

private fun percentOf(status: Status): Int? = progressOf(status)?.let { (it * 100).roundToInt() }

/** The card's label, main line and optional detail. */
private data class StatusText(val eyebrow: String, val title: String, val detail: String?)

@Composable
private fun statusText(status: Status): StatusText = when (status) {
    Status.Idle -> StatusText(
        stringResource(R.string.status_idle_eyebrow),
        stringResource(R.string.status_idle_title),
        stringResource(R.string.status_idle_detail),
    )
    is Status.NoLink -> if (status.fromClipboard) {
        StatusText(
            stringResource(R.string.status_no_link_eyebrow),
            stringResource(R.string.status_no_link_clipboard_title),
            stringResource(R.string.status_no_link_clipboard_detail),
        )
    } else {
        StatusText(
            stringResource(R.string.status_no_link_eyebrow),
            stringResource(R.string.status_no_link_bar_title),
            stringResource(R.string.status_no_link_bar_detail),
        )
    }
    Status.Preparing -> StatusText(
        stringResource(R.string.status_preparing_eyebrow),
        stringResource(R.string.status_preparing_title),
        null,
    )
    is Status.Choosing -> StatusText(
        stringResource(R.string.status_choosing_eyebrow),
        stringResource(R.string.status_choosing_title),
        stringResource(R.string.status_choosing_detail),
    )
    is Status.Downloading -> StatusText(
        eyebrow(stringResource(R.string.status_downloading_eyebrow), status.position, status.count),
        status.title ?: stringResource(R.string.status_downloading_title),
        null,
    )
    is Status.Saving -> StatusText(
        eyebrow(stringResource(R.string.status_saving_eyebrow), status.position, status.count),
        status.title ?: stringResource(R.string.status_saving_title),
        null,
    )
    is Status.Done -> {
        val notSaved = if (status.failed > 0) {
            pluralStringResource(R.plurals.status_done_not_saved, status.failed, status.failed)
        } else {
            null
        }
        val (title, detail) = savedLines(status.videos, status.saved)
        StatusText(stringResource(R.string.status_done_eyebrow), title, detailOf(detail, notSaved))
    }
    is Status.AlreadySaved -> StatusText(
        stringResource(R.string.status_already_eyebrow),
        savedLines(status.videos, status.saved).first,
        stringResource(R.string.status_already_detail),
    )
    is Status.Failed -> failedText(status.reason)
}

/** For one video its title and file name; for several, how many were saved. */
@Composable
private fun savedLines(videos: List<DownloadResult.Video>, saved: Int): Pair<String, String?> {
    val video = videos.firstOrNull()
    return if (saved == 1 && video != null) {
        val title = video.title.ifBlank { video.fileName }
        title to video.fileName.takeIf { it != title }
    } else {
        pluralStringResource(R.plurals.status_done_videos_title, saved, saved) to null
    }
}

@Composable
private fun failedText(reason: FailureReason) = StatusText(
    stringResource(R.string.status_failed_eyebrow),
    stringResource(reason.titleRes),
    stringResource(reason.messageRes),
)

@Composable
private fun eyebrow(stage: String, position: Int, count: Int): String =
    if (count > 1) stringResource(R.string.status_position_eyebrow, stage, position, count) else stage

private fun detailOf(vararg parts: String?): String? =
    parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }
