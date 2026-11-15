package io.github.andyop21.linkdownloader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.VideoChoice
import io.github.andyop21.linkdownloader.ui.theme.PillShape
import io.github.andyop21.linkdownloader.ui.theme.SheetShape
import io.github.andyop21.linkdownloader.ui.theme.TileShape
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The sheet for a post with several videos (B11, DEC-005): the post's title and a grid of the
 * videos' thumbnails with their lengths, all selected at first. Tapping a thumbnail selects or
 * clears it; **Download (n)** queues the chosen ones; closing the sheet calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(
    title: String,
    choices: List<VideoChoice>,
    onDownload: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
    ) {
        ChoiceSheetContent(title, choices, onDownload)
    }
}

/** The choice sheet's content: title, hint, **Select all**, the grid and **Download (n)**. */
@Composable
fun ChoiceSheetContent(title: String, choices: List<VideoChoice>, onDownload: (List<Int>) -> Unit) {
    var selected by rememberSaveable(choices) { mutableStateOf(choices.map { it.index }) }
    val cache = remember(choices) { ThumbnailCache() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title.ifBlank { stringResource(R.string.choice_title_fallback) },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.choice_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { selected = choices.map { it.index } }, shape = PillShape) {
                Text(stringResource(R.string.choice_select_all))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(choices, key = { _, choice -> choice.index }) { i, choice ->
                val isSelected = choice.index in selected
                ThumbnailTile(
                    number = i + 1,
                    choice = choice,
                    selected = isSelected,
                    cache = cache,
                    onToggle = { selected = if (isSelected) selected - choice.index else selected + choice.index },
                )
            }
        }
        Button(
            onClick = { onDownload(selected) },
            enabled = selected.isNotEmpty(),
            shape = PillShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.choice_download, selected.size), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One video: its thumbnail (or a neutral placeholder), its length and a selected mark. */
@Composable
private fun ThumbnailTile(
    number: Int,
    choice: VideoChoice,
    selected: Boolean,
    cache: ThumbnailCache,
    onToggle: () -> Unit,
) {
    val length = durationText(choice.durationSeconds)
    val description = if (length == null) {
        stringResource(R.string.choice_video, number)
    } else {
        stringResource(R.string.choice_video_with_length, number, length)
    }
    val state = stringResource(if (selected) R.string.choice_selected else R.string.choice_not_selected)
    val bitmap = rememberThumbnail(choice.thumbnail, cache)
    val shape = TileShape
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(colors.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, colors.primary, shape) else Modifier)
            .semantics {
                contentDescription = description
                stateDescription = state
            }
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (selected) 1f else 0.55f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (length != null) {
            Text(
                text = length,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clearAndSetSemantics {},
            )
        }
        SelectedMark(
            selected = selected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}

/** A filled circle with a tick when selected, an empty ring when not. Decorative. */
@Composable
private fun SelectedMark(selected: Boolean, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(24.dp)) {
        val radius = size.minDimension / 2
        if (selected) {
            drawCircle(fill, radius)
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.36f)
            }
            drawPath(path, tick, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(Color.Black.copy(alpha = 0.25f), radius)
            drawCircle(Color.White, radius - 1.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx()))
        }
    }
}

/** A video's length as `0:08`, `12:05` or `1:02:03`, or null when unknown. */
fun durationText(seconds: Double?): String? {
    if (seconds == null || !seconds.isFinite() || seconds < 0) return null
    val total = seconds.roundToLong()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
