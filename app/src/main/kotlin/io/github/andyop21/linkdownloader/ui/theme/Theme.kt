package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalMascotColors = staticCompositionLocalOf { LightMascot }

/**
 * The app theme: Material 3 with the app's own light and dark palettes, Nunito, soft shapes, the
 * mascot's colours and whether motion is off ([LocalReducedMotion]). Dynamic colour is off on
 * purpose, so the app keeps its own look on every phone.
 */
@Composable
fun LinkDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMascotColors provides if (darkTheme) DarkMascot else LightMascot,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** The app's extra tokens, next to [MaterialTheme]'s. */
object AppTheme {

    /** The mascot's colours for the current theme. */
    val mascot: MascotColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMascotColors.current
}
