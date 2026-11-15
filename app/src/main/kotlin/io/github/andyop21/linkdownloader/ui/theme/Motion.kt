package io.github.andyop21.linkdownloader.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion: one soft spring and one 250 ms ease, used for every transition on screen.
 * The mascot's looping moods use the same [Easing] over their own, longer periods.
 */
object Motion {

    /** Length of every eased transition. */
    const val DURATION_MS = 250

    /** A gentle ease out: quick start, soft landing. */
    val Easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** The one spring: a little bounce, settles quickly. */
    fun <T> spring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)

    /** The one eased transition. */
    fun <T> tween(): FiniteAnimationSpec<T> = tween(durationMillis = DURATION_MS, easing = Easing)
}

/** True when the system's animator duration scale is 0: all motion is off. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** [Motion.spring], or an instant change when motion is off. */
@Composable
fun <T> springSpec(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else Motion.spring()

/** [Motion.tween], or an instant change when motion is off. */
@Composable
fun <T> tweenSpec(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else Motion.tween()

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
