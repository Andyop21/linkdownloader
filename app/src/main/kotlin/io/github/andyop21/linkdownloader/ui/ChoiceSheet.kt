package io.github.andyop21.linkdownloader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
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
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.AppType
import io.github.andyop21.linkdownloader.ui.theme.BadgeShape
import io.github.andyop21.linkdownloader.ui.theme.Motion
import io.github.andyop21.linkdownloader.ui.theme.SheetShape
import io.github.andyop21.linkdownloader.ui.theme.TileShape
import io.github.andyop21.linkdownloader.ui.theme.motion
import io.github.andyop21.linkdownloader.ui.theme.tactile
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The sheet for a post with several videos (B11, DEC-005): the post's title and a grid of the
 * videos' thumbnails with their lengths, all selected at first. Tapping a thumbnail selects or
 * clears it; **Download n videos** queues the chosen ones; closing the sheet calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(
    title: String,
    choices: List<VideoChoice>,
    onDownload: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderStrong) },
    ) {
        ChoiceSheetContent(title, choices, onDownload)
    }
}

/** The choice sheet's content: title, count, **Select all** or **Select none**, the grid and the download button. */
@Composable
fun ChoiceSheetContent(title: String, choices: List<VideoChoice>, onDownload: (List<Int>) -> Unit) {
    val colors = AppTheme.colors
    var selected by rememberSaveable(choices) { mutableStateOf(choices.map { it.index }) }
    val cache = remember(choices) { ThumbnailCache() }
    val all = selected.size == choices.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title.ifBlank { stringResource(R.string.choice_title_fallback) },
                    style = AppType.heading,
                    color = colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = pluralStringResource(R.plurals.choice_hint, choices.size, choices.size),
                    style = AppType.sub,
                    color = colors.muted,
                )
            }
            ActionButton(
                text = stringResource(if (all) R.string.choice_select_none else R.string.choice_select_all),
                onClick = { selected = if (all) emptyList() else choices.map { it.index } },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        val label = if (selected.isEmpty()) {
            stringResource(R.string.choice_download_none)
        } else {
            pluralStringResource(R.plurals.choice_download, selected.size, selected.size)
        }
        ActionButton(
            text = label,
            onClick = { onDownload(selected) },
            enabled = selected.isNotEmpty(),
            primary = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val edge by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = motion(Motion.BASE),
        label = "tile edge",
    )
    val dim by animateFloatAsState(
        targetValue = if (selected) 1f else 0.45f,
        animationSpec = motion(Motion.SLOW),
        label = "tile dim",
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .tactile(interaction, scale = 0.96f, dip = 0.dp)
            .clip(TileShape)
            .background(colors.surfaceSubtle)
            .border(2.dp, edge, TileShape)
            .semantics {
                contentDescription = description
                stateDescription = state
            }
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(TileShape)
                    .graphicsLayer { alpha = dim },
            )
        }
        if (length != null) {
            Text(
                text = length,
                style = AppType.figure,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), BadgeShape)
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

/** A blue disc with a tick when selected, an empty ring when not. It pops a little on change. Decorative. */
@Composable
private fun SelectedMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val fill = colors.primaryFill
    val tick = colors.onPrimary
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = motion(Motion.POP, Motion.Spring),
        label = "mark scale",
    )
    Canvas(
        modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val radius = size.minDimension / 2
        if (selected) {
            drawCircle(fill, radius)
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.36f)
            }
            drawPath(path, tick, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(Color.Black.copy(alpha = 0.3f), radius)
            drawCircle(Color.White, radius - 1.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(1.5.dp.toPx()))
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
