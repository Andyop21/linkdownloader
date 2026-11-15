package io.github.andyop21.linkdownloader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.messageRes
import io.github.andyop21.linkdownloader.ui.theme.CardShape
import io.github.andyop21.linkdownloader.ui.theme.LocalReducedMotion
import io.github.andyop21.linkdownloader.ui.theme.PillShape
import io.github.andyop21.linkdownloader.ui.theme.StatusTone
import io.github.andyop21.linkdownloader.ui.theme.cardColors
import io.github.andyop21.linkdownloader.ui.theme.springSpec
import io.github.andyop21.linkdownloader.ui.theme.tweenSpec
import kotlin.math.roundToInt

/** The card's tint for this status: neutral, lavender while working, mint when done, soft red on failure. */
val Status.tone: StatusTone
    get() = when (this) {
        Status.Idle, is Status.NoLink, is Status.Choosing -> StatusTone.Neutral
        Status.Preparing, is Status.Downloading, is Status.Saving -> StatusTone.Working
        is Status.Done -> StatusTone.Done
        is Status.Failed -> StatusTone.Failed
    }

/**
 * The card under the Paste button: one short line about what is happening, a detail line, the
 * progress while a download runs, **Cancel** while it runs and **Open** when it is done
 * (docs/product.md § The screen). Its tint follows [Status.tone] and its size changes smoothly.
 * TalkBack announces the main line when it changes.
 */
@Composable
fun StatusCard(status: Status, onCancel: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val text = statusText(status)
    val target = cardColors(status.tone, MaterialTheme.colorScheme)
    val container by animateColorAsState(target.container, tweenSpec(), label = "card container")
    val content by animateColorAsState(target.content, tweenSpec(), label = "card content")
    val detail by animateColorAsState(target.detail, tweenSpec(), label = "card detail")
    val border by animateColorAsState(
        targetValue = if (status.tone == StatusTone.Neutral) MaterialTheme.colorScheme.outlineVariant else target.container,
        animationSpec = tweenSpec(),
        label = "card border",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(springSpec())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            text.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = detail,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusProgress(status, target.progress, target.track)
            StatusActions(status, target.action, onCancel, onOpen)
        }
    }
}

/**
 * A soft, rounded progress bar that glides between updates. Before the first progress arrives it
 * is indeterminate; with motion off it is a still, empty track instead. The track is one pill
 * drawn behind the indicator, so the moving bar never splits it into pieces.
 */
@Composable
private fun StatusProgress(status: Status, color: Color, track: Color) {
    val progress = when (status) {
        Status.Preparing -> null
        is Status.Downloading -> status.progress
        is Status.Saving -> status.progress
        else -> return
    }
    val modifier = Modifier
        .padding(top = 4.dp)
        .fillMaxWidth()
        .height(10.dp)
        .background(track, PillShape)
    when {
        progress != null -> {
            val shown by animateFloatAsState(progress, tweenSpec(), label = "progress")
            LinearProgressIndicator(
                progress = { shown },
                modifier = modifier,
                color = color,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        LocalReducedMotion.current -> Box(
            modifier.clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        )
        else -> LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }
}

@Composable
private fun StatusActions(status: Status, actionColor: Color, onCancel: () -> Unit, onOpen: () -> Unit) {
    val canOpen = status is Status.Done && status.videos.isNotEmpty()
    if (!status.isRunning && !canOpen) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (status.isRunning) {
            TextButton(
                onClick = onCancel,
                shape = PillShape,
                colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.status_cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (canOpen) {
            Button(
                onClick = onOpen,
                shape = PillShape,
                contentPadding = OpenPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.status_open), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private val OpenPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)

/** The card's main line and its optional detail. */
private data class StatusText(val title: String, val detail: String?)

@Composable
private fun statusText(status: Status): StatusText = when (status) {
    Status.Idle -> StatusText(
        stringResource(R.string.status_idle_title),
        stringResource(R.string.status_idle_detail),
    )
    is Status.NoLink -> if (status.fromClipboard) {
        StatusText(
            stringResource(R.string.status_no_link_clipboard_title),
            stringResource(R.string.status_no_link_clipboard_detail),
        )
    } else {
        StatusText(
            stringResource(R.string.status_no_link_bar_title),
            stringResource(R.string.status_no_link_bar_detail),
        )
    }
    Status.Preparing -> StatusText(
        stringResource(R.string.status_preparing_title),
        stringResource(R.string.status_preparing_detail),
    )
    is Status.Choosing -> StatusText(
        stringResource(R.string.status_choosing_title),
        stringResource(R.string.status_choosing_detail),
    )
    is Status.Downloading -> StatusText(
        status.title ?: stringResource(R.string.status_downloading_title),
        detailOf(
            position(status.position, status.count),
            stringResource(R.string.status_downloading),
            status.progress?.let { stringResource(R.string.status_percent, (it * 100).roundToInt()) },
        ),
    )
    is Status.Saving -> StatusText(
        status.title ?: stringResource(R.string.status_saving),
        detailOf(
            position(status.position, status.count),
            status.title?.let { stringResource(R.string.status_saving) },
        ),
    )
    is Status.Done -> {
        val notSaved = if (status.failed > 0) {
            pluralStringResource(R.plurals.status_done_not_saved, status.failed, status.failed)
        } else {
            null
        }
        if (status.saved == 1) {
            StatusText(
                stringResource(R.string.status_done_title),
                detailOf(status.videos.firstOrNull()?.fileName, notSaved),
            )
        } else {
            StatusText(
                pluralStringResource(R.plurals.status_done_videos_title, status.saved, status.saved),
                notSaved,
            )
        }
    }
    is Status.Failed -> StatusText(
        stringResource(R.string.status_failed_title),
        stringResource(status.reason.messageRes),
    )
}

@Composable
private fun position(position: Int, count: Int): String? =
    if (count > 1) stringResource(R.string.status_position, position, count) else null

private fun detailOf(vararg parts: String?): String? =
    parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }
