package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The palette from docs/architecture.md § Look and feel. Every text/background pair the screen
// uses is checked against WCAG AA by ThemeContrastTest; change the table there too.

/** The light scheme: cream background, white cards, lavender actions. */
internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF6352D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E5FF),
    onPrimaryContainer = Color(0xFF2A1F6B),
    inversePrimary = Color(0xFFC9C0FF),
    secondary = Color(0xFFFF9E8A),
    onSecondary = Color(0xFF4A1A10),
    secondaryContainer = Color(0xFFFFE3DB),
    onSecondaryContainer = Color(0xFF5A2418),
    tertiary = Color(0xFF3FAE93),
    onTertiary = Color(0xFF00382C),
    tertiaryContainer = Color(0xFFD5F3EA),
    onTertiaryContainer = Color(0xFF0F4A3C),
    error = Color(0xFFC4433B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF6B1712),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF2E2A3B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2E2A3B),
    surfaceVariant = Color(0xFFF1E9F0),
    onSurfaceVariant = Color(0xFF5E586B),
    surfaceTint = Color(0xFF6352D9),
    inverseSurface = Color(0xFF322E3E),
    inverseOnSurface = Color(0xFFF6F1FA),
    outline = Color(0xFF8C8599),
    outlineVariant = Color(0xFFE3DCE6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEDE4DE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFFBF3EE),
    surfaceContainerHigh = Color(0xFFF6EDE7),
    surfaceContainerHighest = Color(0xFFF1E7E1),
)

/** The dark scheme: deep plum background, soft lavender actions with dark text. */
internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9AEFF),
    onPrimary = Color(0xFF2A1F6B),
    primaryContainer = Color(0xFF3D3370),
    onPrimaryContainer = Color(0xFFE6E0FF),
    inversePrimary = Color(0xFF6352D9),
    secondary = Color(0xFFFFB8A8),
    onSecondary = Color(0xFF5A2418),
    secondaryContainer = Color(0xFF5C3329),
    onSecondaryContainer = Color(0xFFFFDAD1),
    tertiary = Color(0xFF8EE3CB),
    onTertiary = Color(0xFF00382C),
    tertiaryContainer = Color(0xFF1E4A3F),
    onTertiaryContainer = Color(0xFFBFF5E4),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0F0A),
    errorContainer = Color(0xFF5A2425),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1825),
    onBackground = Color(0xFFEDE9F7),
    surface = Color(0xFF252134),
    onSurface = Color(0xFFEDE9F7),
    surfaceVariant = Color(0xFF3A344C),
    onSurfaceVariant = Color(0xFFC9C2D8),
    surfaceTint = Color(0xFFB9AEFF),
    inverseSurface = Color(0xFFEDE9F7),
    inverseOnSurface = Color(0xFF322E3E),
    outline = Color(0xFF948DA6),
    outlineVariant = Color(0xFF48425A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A344C),
    surfaceDim = Color(0xFF1B1825),
    surfaceContainerLowest = Color(0xFF16131F),
    surfaceContainerLow = Color(0xFF211D2E),
    surfaceContainer = Color(0xFF252134),
    surfaceContainerHigh = Color(0xFF2F2A40),
    surfaceContainerHighest = Color(0xFF3A344C),
)

/** The status card's tone, which follows what the download is doing. */
enum class StatusTone { Neutral, Working, Done, Failed }

/**
 * The status card's colours for one tone: its [container], the main line ([content]), the detail
 * line ([detail]), the progress bar ([progress] on [track]) and the text button ([action]).
 */
data class CardColors(
    val container: Color,
    val content: Color,
    val detail: Color,
    val progress: Color,
    val track: Color,
    val action: Color,
)

/** The card colours for [tone] in [scheme]: neutral, lavender, mint or soft red. */
fun cardColors(tone: StatusTone, scheme: ColorScheme): CardColors = when (tone) {
    StatusTone.Neutral -> CardColors(
        container = scheme.surface,
        content = scheme.onSurface,
        detail = scheme.onSurfaceVariant,
        progress = scheme.primary,
        track = scheme.primaryContainer,
        action = scheme.primary,
    )
    StatusTone.Working -> CardColors(
        container = scheme.primaryContainer,
        content = scheme.onPrimaryContainer,
        detail = scheme.onPrimaryContainer,
        progress = scheme.primary,
        track = scheme.surface,
        action = scheme.onPrimaryContainer,
    )
    StatusTone.Done -> CardColors(
        container = scheme.tertiaryContainer,
        content = scheme.onTertiaryContainer,
        detail = scheme.onTertiaryContainer,
        progress = scheme.tertiary,
        track = scheme.surface,
        action = scheme.onTertiaryContainer,
    )
    StatusTone.Failed -> CardColors(
        container = scheme.errorContainer,
        content = scheme.onErrorContainer,
        detail = scheme.onErrorContainer,
        progress = scheme.error,
        track = scheme.surface,
        action = scheme.onErrorContainer,
    )
}

/** The mascot's own colours; its face stays dark on its light body in both themes. */
data class MascotColors(
    val body: Color,
    val fill: Color,
    val ink: Color,
    val shine: Color,
    val cheek: Color,
    val arrow: Color,
    val sparkle: Color,
    val sparkleAlt: Color,
    val drop: Color,
)

internal val LightMascot = MascotColors(
    body = Color(0xFFC9C0FF),
    fill = Color(0xFFA699FA),
    ink = Color(0xFF2E2A3B),
    shine = Color(0xFFFFFFFF),
    cheek = Color(0xFFFF9E8A),
    arrow = Color(0xFF6352D9),
    sparkle = Color(0xFFFF9E8A),
    sparkleAlt = Color(0xFF3FAE93),
    drop = Color(0xFF7CB8F0),
)

internal val DarkMascot = MascotColors(
    body = Color(0xFFB9AEFF),
    fill = Color(0xFF8F80F0),
    ink = Color(0xFF1B1825),
    shine = Color(0xFFFFFFFF),
    cheek = Color(0xFFFFB8A8),
    arrow = Color(0xFFE6E0FF),
    sparkle = Color(0xFFFFB8A8),
    sparkleAlt = Color(0xFF8EE3CB),
    drop = Color(0xFF9CCBF5),
)
