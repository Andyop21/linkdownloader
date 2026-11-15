package io.github.andyop21.linkdownloader.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's motion: a few curves and durations (DEC-008).
 * Arrivals decelerate ([EaseOut]), departures accelerate ([EaseIn]), colours and sizes change on
 * [Standard], and only a small confirmation (the saved check) overshoots ([Spring]).
 */
object Motion {

    /** `cubic-bezier(0.16, 1, 0.3, 1)`: anything arriving. */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** `cubic-bezier(0.4, 0, 1, 1)`: anything leaving. */
    val EaseIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** `cubic-bezier(0.4, 0, 0.2, 1)`: colour changes, cross-fades and a card changing height. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** `cubic-bezier(0.34, 1.56, 0.64, 1)`: overshoots a touch and settles; a check popping in. */
    val Spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** A press, a hover-sized change. */
    const val FAST = 120

    /** A colour change. */
    const val BASE = 160

    /** An entrance or a size change. */
    const val SLOW = 240

    /** Content fading in where it stands. */
    const val SWAP = 320

    /** A pop on [Spring]. */
    const val POP = 380

    /** Progress gliding to its new value. */
    const val GLIDE = 480

    /** The screen's blocks rising in when it first appears. */
    const val RISE = 640

    /** The delay between one rising block and the next. */
    const val STAGGER = 70
}

/** True when the system's animator duration scale is 0: all motion is off. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** A tween of [durationMillis] on [easing], or an instant change when motion is off. */
@Composable
fun <T> motion(
    durationMillis: Int = Motion.BASE,
    easing: Easing = Motion.Standard,
    delayMillis: Int = 0,
): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) {
    snap()
} else {
    tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
}

/**
 * The block fades in from 14 dp below over [Motion.RISE] ms on [Motion.EaseOut], [index] steps of
 * [Motion.STAGGER] ms after the screen appears. Once per composition; still when motion is off.
 */
@Composable
fun Modifier.rise(index: Int = 0): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(Motion.RISE, delayMillis = 40 + index * Motion.STAGGER, easing = Motion.EaseOut),
            )
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 14.dp.toPx()
    }
}

/**
 * A tactile press: the control shrinks to [scale] and dips [dip] while held, over [Motion.FAST]
 * ms on [Motion.EaseOut]. Still when motion is off.
 */
@Composable
fun Modifier.tactile(interaction: InteractionSource, scale: Float = 0.97f, dip: Dp = 1.dp): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed, reduced) {
        val target = if (pressed && !reduced) 1f else 0f
        if (reduced) press.snapTo(target) else press.animateTo(target, tween(Motion.FAST, easing = Motion.EaseOut))
    }
    return graphicsLayer {
        val s = 1f - (1f - scale) * press.value
        scaleX = s
        scaleY = s
        translationY = dip.toPx() * press.value
    }
}

/**
 * Whether the system's animator duration scale (Developer options, or Accessibility → Remove
 * animations) is 0. Follows changes while the screen is shown.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    var reduced by remember { mutableStateOf(animatorScale(resolver) == 0f) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animatorScale(resolver) == 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        reduced = animatorScale(resolver) == 0f
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animatorScale(resolver: android.content.ContentResolver): Float =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
