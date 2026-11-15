package io.github.andyop21.linkdownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.messageRes
import kotlin.math.roundToInt

/**
 * The card under the Paste button: one short line about what is happening, a detail line, the
 * progress while a download runs, **Cancel** while it runs and **Open** when it is done
 * (docs/product.md § The screen). TalkBack announces the main line when it changes.
 */
@Composable
fun StatusCard(status: Status, onCancel: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val text = statusText(status)
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            text.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusProgress(status)
            StatusActions(status, onCancel, onOpen)
        }
    }
}

@Composable
private fun StatusProgress(status: Status) {
    val progress = when (status) {
        Status.Preparing -> null
        is Status.Downloading -> status.progress
        is Status.Saving -> status.progress
        else -> return
    }
    val modifier = Modifier.fillMaxWidth()
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier)
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = modifier)
    }
}

@Composable
private fun StatusActions(status: Status, onCancel: () -> Unit, onOpen: () -> Unit) {
    val canOpen = status is Status.Done && status.videos.isNotEmpty()
    if (!status.isRunning && !canOpen) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (status.isRunning) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.status_cancel)) }
        }
        if (canOpen) {
            Button(onClick = onOpen) { Text(stringResource(R.string.status_open)) }
        }
    }
}

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
