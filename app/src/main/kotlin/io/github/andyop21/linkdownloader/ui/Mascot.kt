package io.github.andyop21.linkdownloader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.andyop21.linkdownloader.ui.theme.LinkDownloaderTheme

/** How the mascot feels about what is happening (docs/architecture.md § Look and feel). */
enum class Mood { Idle, Working, Happy, Worried }

/** The mascot's mood for this status. */
val Status.mood: Mood
    get() = when (this) {
        Status.Preparing, is Status.Downloading, is Status.Saving -> Mood.Working
        is Status.Done -> Mood.Happy
        is Status.Failed -> Mood.Worried
        Status.Idle, is Status.NoLink, is Status.Choosing -> Mood.Idle
    }

/**
 * A placeholder mascot: a round face whose mouth follows the [mood]. Decorative, so it has no
 * semantics. P06 replaces it with the real artwork and its motion.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier) {
    val body = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(body, radius, center)

        val eyeY = center.y - radius * 0.15f
        val eyeDx = radius * 0.32f
        val eyeRadius = radius * 0.09f
        drawCircle(ink, eyeRadius, Offset(center.x - eyeDx, eyeY))
        drawCircle(ink, eyeRadius, Offset(center.x + eyeDx, eyeY))

        val stroke = Stroke(width = radius * 0.08f, cap = StrokeCap.Round)
        val mouthY = center.y + radius * 0.3f
        val mouthWidth = radius * 0.6f
        when (mood) {
            Mood.Idle -> drawLine(
                ink,
                Offset(center.x - mouthWidth / 4, mouthY),
                Offset(center.x + mouthWidth / 4, mouthY),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            Mood.Working -> drawCircle(ink, radius * 0.1f, Offset(center.x, mouthY), style = stroke)
            Mood.Happy -> drawArc(
                ink,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - mouthWidth / 2, mouthY - mouthWidth / 3),
                size = Size(mouthWidth, mouthWidth / 1.5f),
                style = stroke,
            )
            Mood.Worried -> drawArc(
                ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - mouthWidth / 3, mouthY),
                size = Size(mouthWidth / 1.5f, mouthWidth / 2.5f),
                style = stroke,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MascotPreview() {
    LinkDownloaderTheme {
        Row {
            Mood.entries.forEach { Mascot(it, Modifier.size(56.dp)) }
        }
    }
}
