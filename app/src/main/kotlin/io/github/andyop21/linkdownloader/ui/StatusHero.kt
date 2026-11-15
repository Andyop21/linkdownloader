package io.github.andyop21.linkdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.download.DownloadResult
import io.github.andyop21.linkdownloader.download.FailureReason
import io.github.andyop21.linkdownloader.download.messageRes
import io.github.andyop21.linkdownloader.download.titleRes
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.AppType
import io.github.andyop21.linkdownloader.ui.theme.LocalReducedMotion
import io.github.andyop21.linkdownloader.ui.theme.Motion
import io.github.andyop21.linkdownloader.ui.theme.Space
import io.github.andyop21.linkdownloader.ui.theme.Tone
import io.github.andyop21.linkdownloader.ui.theme.accent
import io.github.andyop21.linkdownloader.ui.theme.motion
import io.github.andyop21.linkdownloader.ui.theme.orb
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The status's colour: blue while ready or working, green when saved, amber or red when not. */
val Status.tone: Tone
    get() = when (this) {
        Status.Idle, is Status.Choosing -> Tone.Neutral
        is Status.NoLink -> Tone.Warning
        Status.Preparing, is Status.Downloading, is Status.Saving -> Tone.Primary
        is Status.Done, is Status.AlreadySaved -> Tone.Success
        is Status.Failed -> Tone.Danger
    }

/**
 * What the caption shows as one piece. A new kind fades in; a change within a kind (progress, a
 * title arriving) updates in place.
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

/** The mark inside the orb. */
private enum class Glyph { Ready, Fetching, Percent, Check, Alert, Empty, Grid }

private val Status.glyph: Glyph
    get() = when (this) {
        Status.Idle -> Glyph.Ready
        is Status.NoLink -> Glyph.Empty
        Status.Preparing -> Glyph.Fetching
        is Status.Downloading -> if (shownPercent(progress) == null) Glyph.Fetching else Glyph.Percent
        is Status.Saving -> if (shownPercent(progress) == null) Glyph.Fetching else Glyph.Percent
        is Status.Choosing -> Glyph.Grid
        is Status.Done, is Status.AlreadySaved -> Glyph.Check
        is Status.Failed -> Glyph.Alert
    }

/** What the caption's buttons do. [canRetry] shows **Try again** on a failure that may pass. */
class StatusActions(
    val onCancel: () -> Unit = {},
    val onOpen: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDownloadAgain: () -> Unit = {},
    val canRetry: Boolean = false,
)

/**
 * The centre of the screen (docs/product.md § The screen): the orb, which shows the state at a
 * glance, and under it a one-line status, a detail line and the button that fits (**Cancel**,
 * **Open**, **Try again**, **Download again**). [compact] while the keyboard is open: the orb
 * shrinks and the detail folds away, so the field and **Paste link** stay clear above the keyboard.
 */
@Composable
fun StatusHero(status: Status, actions: StatusActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Orb(status, compact)
        Spacer(Modifier.height(Space.s))
        Caption(status, actions, compact)
    }
}

/** The orb's box; the glow and the ripple reach past it. */
private val OrbBox = 200.dp

/** The disc's radius. */
private val DiscRadius = 60.dp

/** The progress ring's radius, 16 dp clear of the disc. */
private val RingRadius = 76.dp

/** The glyph's size inside the disc. */
private val GlyphSize = 46.dp

/** The orb's size while the keyboard is open, as a share of its full size. */
private const val CompactScale = 0.56f

/**
 * A disc in the status's colour, lit from the top left, on a soft glow of the same colour. Ready,
 * a halo breathes out from it. Working, a ring fills around it with the progress, and the
 * percentage shows inside (before the first progress an arc turns and the arrow drops into the
 * tray). Saved, it turns green, the check pops in and one ripple spreads. Failed or nothing to
 * download, it turns red or amber and shakes once. Still when motion is off.
 */
@Composable
private fun Orb(status: Status, compact: Boolean) {
    val colors = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val tone = status.tone
    val target = colors.orb(tone)
    val light by animateColorAsState(target.light, motion(Motion.SWAP), label = "orb light")
    val deep by animateColorAsState(target.deep, motion(Motion.SWAP), label = "orb deep")
    val accent by animateColorAsState(colors.accent(tone), motion(Motion.SWAP), label = "orb accent")
    val running = status.isRunning
    val ready = status == Status.Idle
    val progress = progressOf(status)
    val determinate = progress != null
    val ring by animateFloatAsState(if (running) 1f else 0f, motion(Motion.SLOW), label = "ring")
    val filled by animateFloatAsState(progress ?: 0f, motion(Motion.GLIDE, Motion.EaseOut), label = "ring fill")
    val track = colors.track
    val glowAlpha = if (colors.dark) 0.22f else 0.14f
    val k by animateFloatAsState(
        targetValue = if (compact) CompactScale else 1f,
        animationSpec = motion(Motion.SWAP, Motion.Standard),
        label = "orb size",
    )

    val loop = rememberInfiniteTransition(label = "orb")
    val halo by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = Motion.EaseOut)),
        label = "halo",
    )
    val spin by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )

    val shake = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    val jolts = status is Status.Failed || status is Status.NoLink
    val cheers = status is Status.Done
    LaunchedEffect(status.kind, reduced) {
        if (reduced) return@LaunchedEffect
        if (jolts) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(520, easing = LinearEasing))
        }
        if (cheers) {
            pop.snapTo(0.86f)
            ripple.snapTo(0f)
            launch { ripple.animateTo(1f, tween(900, easing = Motion.EaseOut)) }
            pop.animateTo(1f, tween(Motion.POP, easing = Motion.Spring))
        }
    }

    val shadow = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val progressInfo = if (determinate) ProgressBarRangeInfo(progress, 0f..1f) else ProgressBarRangeInfo.Indeterminate
    Box(
        modifier = Modifier
            .size(OrbBox * k)
            .clearAndSetSemantics { if (running) progressBarRangeInfo = progressInfo }
            .graphicsLayer {
                val p = shake.value
                translationX = if (p < 1f) sin(p * 6f * PI.toFloat()) * 8.dp.toPx() * (1f - p) else 0f
            }
            .drawBehind {
                val disc = DiscRadius.toPx() * k
                val glow = 150.dp.toPx() * k
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent.copy(alpha = glowAlpha), Color.Transparent), center, glow),
                    radius = glow,
                )
                if (ready && !reduced) {
                    drawCircle(
                        color = accent.copy(alpha = 0.3f * (1f - halo)),
                        radius = disc + 30.dp.toPx() * k * halo,
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
                if (ripple.value < 1f) {
                    val t = ripple.value
                    drawCircle(
                        color = accent.copy(alpha = 0.45f * (1f - t)),
                        radius = disc + 40.dp.toPx() * k * t,
                        style = Stroke(2.dp.toPx()),
                    )
                }
                if (ring > 0f) {
                    val r = RingRadius.toPx() * k
                    val width = 4.dp.toPx() * k.coerceAtLeast(0.75f)
                    val topLeft = Offset(center.x - r, center.y - r)
                    val box = Size(r * 2, r * 2)
                    drawCircle(track.copy(alpha = track.alpha * ring), r, style = Stroke(width))
                    val (start, sweep) = if (determinate) -90f to 360f * filled else (spin - 90f) to 80f
                    drawArc(
                        color = accent.copy(alpha = ring),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = box,
                        style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
                scale(pop.value) {
                    drawIntoCanvas { canvas ->
                        shadow.color = deep.toArgb()
                        shadow.setShadowLayer(22.dp.toPx() * k, 0f, 10.dp.toPx() * k, deep.copy(alpha = 0.35f).toArgb())
                        canvas.nativeCanvas.drawCircle(center.x, center.y, disc, shadow)
                    }
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(light, deep),
                            start = Offset(center.x - disc, center.y - disc),
                            end = Offset(center.x + disc, center.y + disc),
                        ),
                        radius = disc,
                    )
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                            startY = center.y - disc,
                            endY = center.y + disc * 0.1f,
                        ),
                        radius = disc,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        OrbGlyph(
            status = status,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value * k
                scaleY = pop.value * k
            },
        )
    }
}

/** The white mark inside the disc; a new mark scales and fades in, the check on the spring. */
@Composable
private fun OrbGlyph(status: Status, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = status.glyph,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val easing = if (targetState == Glyph.Check) Motion.Spring else Motion.EaseOut
                (fadeIn(tween(Motion.SLOW, delayMillis = 60, easing = Motion.EaseOut)) + scaleIn(tween(Motion.POP, easing = easing), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(Motion.FAST, easing = Motion.EaseIn)) + scaleOut(tween(Motion.FAST, easing = Motion.EaseIn), targetScale = 0.8f))
            }
        },
        modifier = modifier,
        contentAlignment = Alignment.Center,
        label = "glyph",
    ) { glyph ->
        when (glyph) {
            Glyph.Percent -> PercentText(percentOf(status) ?: 0)
            Glyph.Fetching -> FallingArrow()
            Glyph.Ready -> Box(Modifier.size(GlyphSize)) {
                WhiteIcon(AppIcons.Tray, Modifier.fillMaxSize())
                WhiteIcon(AppIcons.Arrow, Modifier.fillMaxSize())
            }
            Glyph.Check -> WhiteIcon(AppIcons.Check, Modifier.size(GlyphSize))
            Glyph.Alert -> WhiteIcon(AppIcons.Alert, Modifier.size(GlyphSize))
            Glyph.Empty -> WhiteIcon(AppIcons.Empty, Modifier.size(GlyphSize))
            Glyph.Grid -> WhiteIcon(AppIcons.Grid, Modifier.size(GlyphSize))
        }
    }
}

@Composable
private fun WhiteIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = modifier)
}

/** "42%", the sign set smaller than the number. */
@Composable
private fun PercentText(percent: Int) {
    val text = stringResource(R.string.status_percent, percent)
    val sign = text.lastIndexOf('%')
    Text(
        text = buildAnnotatedString {
            if (sign < 0) {
                append(text)
            } else {
                append(text.substring(0, sign))
                withStyle(SpanStyle(fontSize = 15.sp)) { append(text.substring(sign)) }
                append(text.substring(sign + 1))
            }
        },
        style = AppType.percent,
        color = Color.White,
    )
}

/** The arrow drops into the tray over and over while the link is read. Still when motion is off. */
@Composable
private fun FallingArrow() {
    val reduced = LocalReducedMotion.current
    val fall = rememberInfiniteTransition(label = "fall")
    val t by fall.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "fall progress",
    )
    Box(Modifier.size(GlyphSize)) {
        WhiteIcon(AppIcons.Tray, Modifier.fillMaxSize())
        WhiteIcon(
            icon = AppIcons.Arrow,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reduced) {
                        translationY = (Motion.Standard.transform(t) - 0.55f) * 22.dp.toPx()
                        alpha = sin(t * PI.toFloat())
                    }
                },
        )
    }
}

/**
 * The status line, its detail and the button that fits, centred under the orb. A new kind of
 * status fades and rises in while the block eases to its new height; TalkBack announces the line.
 * The block keeps a minimum height, so the orb above it stays where it is.
 */
@Composable
private fun Caption(status: Status, actions: StatusActions, compact: Boolean) {
    val colors = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
    val minHeight by animateDpAsState(
        targetValue = if (compact) 0.dp else CaptionHeight,
        animationSpec = motion(Motion.SWAP, Motion.Standard),
        label = "caption room",
    )
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
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        label = "caption",
    ) { shown ->
        val text = statusText(shown)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text.title,
                style = AppType.heroTitle,
                color = colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            text.detail?.let {
                AnimatedVisibility(
                    visible = !compact,
                    enter = if (reduced) EnterTransition.None else fadeIn(tween(Motion.SLOW, delayMillis = 80)) + expandVertically(tween(Motion.SWAP, easing = Motion.Standard)),
                    exit = if (reduced) ExitTransition.None else fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.SWAP, easing = Motion.Standard)),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = it,
                            style = AppType.lead,
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 320.dp),
                        )
                    }
                }
            }
            CaptionButtons(shown, actions)
        }
    }
}

/** Room for a two-line status, a two-line detail and a row of buttons. */
private val CaptionHeight = 176.dp

@Composable
private fun CaptionButtons(status: Status, actions: StatusActions) {
    val canOpen = (status is Status.Done && status.videos.isNotEmpty()) ||
        (status is Status.AlreadySaved && status.videos.isNotEmpty())
    val canRetry = status is Status.Failed && actions.canRetry
    val again = status is Status.AlreadySaved
    if (!status.isRunning && !canOpen && !canRetry && !again) return
    Spacer(Modifier.height(Space.xl))
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
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

private fun percentOf(status: Status): Int? = shownPercent(progressOf(status))

/** The whole percentage once there is one to show: a bare "0%" says nothing, so the arrow stays until 1 %. */
private fun shownPercent(progress: Float?): Int? = progress?.let { (it * 100).roundToInt() }?.takeIf { it >= 1 }

/** The status line and its optional detail. */
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
        stage(stringResource(R.string.status_downloading), status.position, status.count),
    )
    is Status.Saving -> StatusText(
        status.title ?: stringResource(R.string.status_saving_title),
        stage(stringResource(R.string.status_saving), status.position, status.count),
    )
    is Status.Done -> {
        val notSaved = if (status.failed > 0) {
            pluralStringResource(R.plurals.status_done_not_saved, status.failed, status.failed)
        } else {
            null
        }
        StatusText(
            savedTitle(status.videos, status.saved),
            detailOf(stringResource(R.string.status_done_detail), notSaved),
        )
    }
    is Status.AlreadySaved -> StatusText(
        savedTitle(status.videos, status.saved),
        stringResource(R.string.status_already_detail),
    )
    is Status.Failed -> failedText(status.reason)
}

/** For one video its title (or file name); for several, how many were saved. */
@Composable
private fun savedTitle(videos: List<DownloadResult.Video>, saved: Int): String {
    val video = videos.firstOrNull()
    return if (saved == 1 && video != null) {
        video.title.ifBlank { video.fileName }
    } else {
        pluralStringResource(R.plurals.status_done_videos_title, saved, saved)
    }
}

@Composable
private fun failedText(reason: FailureReason) = StatusText(
    stringResource(reason.titleRes),
    stringResource(reason.messageRes),
)

@Composable
private fun stage(stage: String, position: Int, count: Int): String =
    if (count > 1) stringResource(R.string.status_position, stage, position, count) else stage

private fun detailOf(vararg parts: String?): String? =
    parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }
