package io.github.andyop21.linkdownloader.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.R
import io.github.andyop21.linkdownloader.ui.theme.AppTheme
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme
import io.github.andyop21.linkdownloader.ui.theme.LocalReducedMotion
import io.github.andyop21.linkdownloader.ui.theme.MascotColors
import io.github.andyop21.linkdownloader.ui.theme.Motion
import io.github.andyop21.linkdownloader.ui.theme.tweenSpec

/** How the mascot feels about what is happening (docs/architecture.md § Look and feel). */
enum class Mood(@param:StringRes val description: Int) {
    Idle(R.string.mascot_idle),
    Working(R.string.mascot_working),
    Happy(R.string.mascot_happy),
    Worried(R.string.mascot_worried),
}

/** The mascot's mood for this status. */
val Status.mood: Mood
    get() = when (this) {
        Status.Preparing, is Status.Downloading, is Status.Saving -> Mood.Working
        is Status.Done -> Mood.Happy
        is Status.Failed -> Mood.Worried
        Status.Idle, is Status.NoLink, is Status.Choosing -> Mood.Idle
    }

/** The download's progress for the mascot to show, or null when there is none yet. */
val Status.mascotProgress: Float?
    get() = when (this) {
        is Status.Downloading -> progress
        is Status.Saving -> progress
        else -> null
    }

/**
 * The mascot: a round lavender blob with a little download-arrow on its head, drawn in Compose.
 * It breathes slowly when [Mood.Idle], bobs and fills up with [progress] when [Mood.Working], hops
 * with sparkles when [Mood.Happy] and wobbles when [Mood.Worried]. With the system's animations
 * off it stays still. Its content description names the mood.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, progress: Float? = null) {
    val colors = AppTheme.mascot
    val pose = if (LocalReducedMotion.current) {
        Pose.still(mood)
    } else {
        when (mood) {
            Mood.Idle -> idlePose()
            Mood.Working -> workingPose()
            Mood.Happy -> happyPose()
            Mood.Worried -> worriedPose()
        }
    }
    val level by animateFloatAsState(
        targetValue = if (mood == Mood.Working) (progress ?: 0f).coerceIn(0f, 1f) else 0f,
        animationSpec = tweenSpec(),
        label = "mascot fill",
    )
    val description = stringResource(mood.description)
    Canvas(modifier.semantics { contentDescription = description }) {
        val unit = size.minDimension / UNITS
        translate(left = (size.width - UNITS * unit) / 2, top = (size.height - UNITS * unit) / 2) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawMascot(mood, pose, level, colors)
            }
        }
    }
}

/** Where the mascot is in its motion, in the 100-unit drawing space. */
private data class Pose(
    val lift: Float = 0f,
    val stretchX: Float = 1f,
    val stretchY: Float = 1f,
    val tilt: Float = 0f,
    val arrowDrop: Float = 0f,
    val sparkle: Float = 0f,
) {
    companion object {
        /** The pose with motion off: happy keeps its sparkles, the rest stand still. */
        fun still(mood: Mood) = Pose(sparkle = if (mood == Mood.Happy) 1f else 0f)
    }
}

/** A slow breath: a little taller and higher, then back. */
@Composable
private fun idlePose(): Pose {
    val breath by rememberInfiniteTransition(label = "idle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = Motion.Easing), RepeatMode.Reverse),
        label = "breath",
    )
    return Pose(lift = -2f * breath, stretchX = 1f - 0.015f * breath, stretchY = 1f + 0.03f * breath)
}

/** A busy bob, with the arrow pushing down into the head. */
@Composable
private fun workingPose(): Pose {
    val bob by rememberInfiniteTransition(label = "working").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_MS, easing = Motion.Easing), RepeatMode.Reverse),
        label = "bob",
    )
    return Pose(lift = -4f * bob, stretchY = 1f - 0.02f * bob, stretchX = 1f + 0.01f * bob, arrowDrop = 3f * bob)
}

/** One hop on arrival, then sparkles that stay. */
@Composable
private fun happyPose(): Pose {
    val lift = remember { Animatable(0f) }
    val sparkle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        lift.animateTo(-12f, Motion.tween())
        lift.animateTo(0f, Motion.spring())
    }
    LaunchedEffect(Unit) {
        sparkle.animateTo(1f, Motion.spring())
    }
    return Pose(lift = lift.value, sparkle = sparkle.value)
}

/** A small wobble on arrival, then still. */
@Composable
private fun worriedPose(): Pose {
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        for (angle in floatArrayOf(-8f, 6f, -3f, 0f)) tilt.animateTo(angle, Motion.tween())
    }
    return Pose(tilt = tilt.value)
}

private fun DrawScope.drawMascot(mood: Mood, pose: Pose, level: Float, colors: MascotColors) {
    val feet = Offset(50f, BODY_BOTTOM)
    translate(top = pose.lift) {
        rotate(pose.tilt, pivot = feet) {
            scale(pose.stretchX, pose.stretchY, pivot = feet) {
                drawArrow(pose.arrowDrop, colors)
                val body = bodyPath()
                drawPath(body, colors.body)
                if (level > 0f) {
                    clipPath(body) {
                        val top = BODY_BOTTOM - (BODY_BOTTOM - BODY_TOP) * level
                        drawRect(colors.fill, topLeft = Offset(0f, top), size = Size(UNITS, BODY_BOTTOM - top))
                    }
                }
                drawOval(colors.shine.copy(alpha = 0.45f), topLeft = Offset(24f, 33f), size = Size(12f, 7f))
                drawOval(colors.cheek.copy(alpha = 0.75f), topLeft = Offset(21.5f, 62f), size = Size(11f, 6f))
                drawOval(colors.cheek.copy(alpha = 0.75f), topLeft = Offset(67.5f, 62f), size = Size(11f, 6f))
                drawFace(mood, colors)
            }
        }
        if (mood == Mood.Worried) drawPath(dropPath(), colors.drop)
    }
    if (pose.sparkle > 0f) {
        drawSparkle(Offset(13f, 30f), 7f * pose.sparkle, colors.sparkle.copy(alpha = pose.sparkle))
        drawSparkle(Offset(87f, 20f), 6f * pose.sparkle, colors.sparkleAlt.copy(alpha = pose.sparkle))
        drawSparkle(Offset(91f, 50f), 4f * pose.sparkle, colors.sparkle.copy(alpha = pose.sparkle))
    }
}

private fun DrawScope.drawArrow(drop: Float, colors: MascotColors) {
    val stroke = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val arrow = Path().apply {
        moveTo(50f, 5f + drop)
        lineTo(50f, 18.5f + drop)
        moveTo(43.5f, 12f + drop)
        lineTo(50f, 18.5f + drop)
        lineTo(56.5f, 12f + drop)
    }
    drawPath(arrow, colors.arrow, style = stroke)
}

private fun DrawScope.drawFace(mood: Mood, colors: MascotColors) {
    val ink = colors.ink
    val line = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mood) {
        Mood.Idle, Mood.Working -> {
            drawEyes(ink, colors, radius = 4.5f, y = 56f)
            if (mood == Mood.Idle) {
                drawPath(Path().apply { moveTo(45f, 66f); quadraticTo(50f, 70.5f, 55f, 66f) }, ink, style = line)
            } else {
                drawCircle(ink, radius = 2.6f, center = Offset(50f, 68f), style = Stroke(width = 2.4f))
            }
        }
        Mood.Happy -> {
            val eyes = Path().apply {
                moveTo(33f, 58f); lineTo(38f, 53f); lineTo(43f, 58f)
                moveTo(57f, 58f); lineTo(62f, 53f); lineTo(67f, 58f)
            }
            drawPath(eyes, ink, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(Path().apply { moveTo(43f, 64f); quadraticTo(50f, 75f, 57f, 64f); close() }, ink)
        }
        Mood.Worried -> {
            drawEyes(ink, colors, radius = 3.8f, y = 57f)
            val brows = Path().apply {
                moveTo(32f, 50f); lineTo(43f, 46f)
                moveTo(68f, 50f); lineTo(57f, 46f)
            }
            drawPath(brows, ink, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
            drawPath(Path().apply { moveTo(44f, 70f); quadraticTo(50f, 65.5f, 56f, 70f) }, ink, style = line)
        }
    }
}

private fun DrawScope.drawEyes(ink: Color, colors: MascotColors, radius: Float, y: Float) {
    for (x in floatArrayOf(38f, 62f)) {
        drawCircle(ink, radius, Offset(x, y))
        drawCircle(colors.shine, radius * 0.34f, Offset(x + radius * 0.35f, y - radius * 0.35f))
    }
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val waist = radius * 0.28f
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + waist, center.y - waist)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + waist, center.y + waist)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - waist, center.y + waist)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - waist, center.y - waist)
        close()
    }
    drawPath(star, color)
}

/** The body: a soft mochi shape, a little flatter at the bottom. Same outline as the app icon. */
private fun bodyPath() = Path().apply {
    moveTo(50f, BODY_TOP)
    cubicTo(74f, BODY_TOP, 90f, 40f, 90f, 62f)
    cubicTo(90f, 82f, 74f, BODY_BOTTOM, 50f, BODY_BOTTOM)
    cubicTo(26f, BODY_BOTTOM, 10f, 82f, 10f, 62f)
    cubicTo(10f, 40f, 26f, BODY_TOP, 50f, BODY_TOP)
    close()
}

/** A little sweat drop beside the head. */
private fun dropPath() = Path().apply {
    moveTo(84f, 30f)
    cubicTo(86f, 34f, 89f, 37f, 89f, 40.5f)
    cubicTo(89f, 43.5f, 86.8f, 45.5f, 84f, 45.5f)
    cubicTo(81.2f, 45.5f, 79f, 43.5f, 79f, 40.5f)
    cubicTo(79f, 37f, 82f, 34f, 84f, 30f)
    close()
}

private const val UNITS = 100f
private const val BODY_TOP = 24f
private const val BODY_BOTTOM = 92f
private const val BREATH_MS = 2400
private const val BOB_MS = 450

@Preview(name = "Moods, light", showBackground = true)
@Preview(name = "Moods, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MascotPreview() {
    LinkDownloaderTheme(reducedMotion = true) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Mood.entries.forEach { Mascot(it, Modifier.size(72.dp), progress = 0.5f) }
        }
    }
}
