package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAppColors = staticCompositionLocalOf { AppColors.Light }

/**
 * The app theme: the app's own light and dark palettes ([AppColors]), Lato, the corner scale and
 * whether motion is off ([LocalReducedMotion]), with Material 3 told the same colours and type.
 * Dynamic colour is off on purpose, so the app keeps its own look on every phone.
 */
@Composable
fun LinkDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) AppColors.Dark else AppColors.Light
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** The app's tokens in force: `AppTheme.colors.surface`. */
object AppTheme {

    /** The semantic colours for the current theme. */
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
