package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors: ColorScheme = lightColorScheme(
    primary = LavenderLight,
    primaryContainer = LavenderContainerLight,
    secondary = PeachLight,
    tertiary = MintLight,
    error = SoftRedLight,
    background = CreamBackground,
    onBackground = InkLight,
    surface = WhiteSurface,
    onSurface = InkLight,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = LavenderDark,
    primaryContainer = LavenderContainerDark,
    secondary = PeachDark,
    tertiary = MintDark,
    error = SoftRedDark,
    background = NightBackground,
    onBackground = InkDark,
    surface = NightSurface,
    onSurface = InkDark,
)

/**
 * The app theme: Material 3 with the app's own light and dark palettes. Dynamic colour is off on
 * purpose, so the app keeps its own look on every phone.
 */
@Composable
fun LinkDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
