package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// The palette from docs/architecture.md § Look and feel: a cool-gray neutral ramp, one blue for
// actions and soft tints for status, after the Enamel design tokens (DEC-008). Every
// text/background pair the screen draws is checked against WCAG AA by ThemeContrastTest; change
// the list there too.

/** The app's semantic colours. Screens take every colour from here by name. */
@Immutable
data class AppColors(
    val dark: Boolean,
    /** The page behind everything. */
    val canvas: Color,
    /** Cards, the link field and sheets. */
    val surface: Color,
    /** A neutral well: an idle icon tile, a grouped list, an empty thumbnail. */
    val surfaceSubtle: Color,
    /** Main text. */
    val ink: Color,
    /** Secondary text that still reads as content. */
    val ink2: Color,
    /** Supporting text: detail lines. */
    val muted: Color,
    /** The quietest text: placeholders, file names. */
    val faint: Color,
    /** A card's edge and dividers. */
    val border: Color,
    /** A secondary button's edge. */
    val borderStrong: Color,
    /** The edge of a form control, which must hold 3:1 against what is around it. */
    val controlBorder: Color,
    /** Blue for text, focus and progress on a surface. */
    val primary: Color,
    /** A filled blue control. */
    val primaryFill: Color,
    /** A filled blue control while pressed. */
    val primaryFillPressed: Color,
    /** Text and icons on [primaryFill]. */
    val onPrimary: Color,
    /** The blue tint behind a working icon. */
    val primarySoft: Color,
    /** Blue icons and text on [primarySoft]. */
    val primaryInk: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** The snackbar, which stays dark in both themes. */
    val overlay: Color,
    val overlayInk: Color,
    val overlayAction: Color,
    /** What lies under a sheet. */
    val scrim: Color,
) {
    /** The progress bar's empty track: ink at 8 %, over the card's surface. */
    val track: Color get() = ink.copy(alpha = 0.08f)

    /** A press or selection wash on a quiet control: ink at 6 %. */
    val press: Color get() = ink.copy(alpha = 0.06f)

    companion object {
        val Light = AppColors(
            dark = false,
            canvas = Color(0xFFF5F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceSubtle = Color(0xFFEEF0F3),
            ink = Color(0xFF1B1D21),
            ink2 = Color(0xFF464B52),
            muted = Color(0xFF5F656C),
            faint = Color(0xFF676E78),
            border = Color(0xFFE4E7EB),
            borderStrong = Color(0xFFD5D9DE),
            controlBorder = Color(0xFF80878F),
            primary = Color(0xFF0063F3),
            primaryFill = Color(0xFF0063F3),
            primaryFillPressed = Color(0xFF0059D1),
            onPrimary = Color(0xFFFFFFFF),
            primarySoft = Color(0xFFEAF2FF),
            primaryInk = Color(0xFF0059D1),
            success = Color(0xFF007E3A),
            successSoft = Color(0xFFE3F5EB),
            warning = Color(0xFF956300),
            warningSoft = Color(0xFFFBF1D6),
            danger = Color(0xFFCF1F14),
            dangerSoft = Color(0xFFFEE4E2),
            overlay = Color(0xFF1D2126),
            overlayInk = Color(0xFFFFFFFF),
            overlayAction = Color(0xFF8FB8FF),
            scrim = Color(0x8C080A0C),
        )

        val Dark = AppColors(
            dark = true,
            canvas = Color(0xFF0F1215),
            surface = Color(0xFF14171A),
            surfaceSubtle = Color(0xFF1D2126),
            ink = Color(0xFFE8EAED),
            ink2 = Color(0xFFC7CCD2),
            muted = Color(0xFF939BA4),
            faint = Color(0xFF81888F),
            border = Color(0xFF2C3138),
            borderStrong = Color(0xFF3A4048),
            controlBorder = Color(0xFF6B727C),
            primary = Color(0xFF3D8BFF),
            primaryFill = Color(0xFF2473E5),
            primaryFillPressed = Color(0xFF1466D7),
            onPrimary = Color(0xFFFFFFFF),
            primarySoft = Color(0xFF16233A),
            primaryInk = Color(0xFF6AA6FF),
            success = Color(0xFF34B26C),
            successSoft = Color(0xFF10291D),
            warning = Color(0xFFD5A12A),
            warningSoft = Color(0xFF2B2312),
            danger = Color(0xFFF0655A),
            dangerSoft = Color(0xFF33191A),
            overlay = Color(0xFF262A30),
            overlayInk = Color(0xFFFFFFFF),
            overlayAction = Color(0xFF8FB8FF),
            scrim = Color(0xA6050709),
        )
    }
}

/** A state's colour, as the orb shows it before the words under it (which are always there). */
enum class Tone { Neutral, Primary, Success, Warning, Danger }

/** The tone's solid colour on the page: the progress ring, the orb's glow and its halo. */
fun AppColors.accent(tone: Tone): Color = when (tone) {
    Tone.Neutral, Tone.Primary -> primary
    Tone.Success -> success
    Tone.Warning -> warning
    Tone.Danger -> danger
}

/** The orb's gradient for a tone, from its top-left [light] to its bottom-right [deep]; white glyphs sit on it. */
data class OrbColors(val light: Color, val deep: Color)

/** The orb's gradient for [tone]. The same in both themes: a saturated disc reads on either page. */
fun AppColors.orb(tone: Tone): OrbColors = when (tone) {
    Tone.Neutral, Tone.Primary -> OrbColors(Color(0xFF3D8BFF), if (dark) Color(0xFF1466D7) else Color(0xFF0059D1))
    Tone.Success -> OrbColors(Color(0xFF1F9D57), Color(0xFF007E3A))
    Tone.Warning -> OrbColors(Color(0xFFE8730C), Color(0xFFBF5600))
    Tone.Danger -> OrbColors(Color(0xFFE5483D), Color(0xFFB42318))
}

/** Material 3 told the same colours, so the few Material parts the screen uses draw in this palette. */
internal fun AppColors.toMaterial(): ColorScheme = if (dark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = primaryInk,
        inversePrimary = primaryFill,
        secondary = ink2,
        onSecondary = surface,
        secondaryContainer = surfaceSubtle,
        onSecondaryContainer = ink,
        tertiary = success,
        onTertiary = surface,
        tertiaryContainer = successSoft,
        onTertiaryContainer = success,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceSubtle,
        onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = overlay,
        inverseOnSurface = overlayInk,
        error = danger,
        onError = surface,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
        outline = controlBorder,
        outlineVariant = border,
        scrim = scrim,
        surfaceBright = surfaceSubtle,
        surfaceDim = canvas,
        surfaceContainerLowest = canvas,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceSubtle,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = primaryInk,
        inversePrimary = primaryFill,
        secondary = ink2,
        onSecondary = surface,
        secondaryContainer = surfaceSubtle,
        onSecondaryContainer = ink,
        tertiary = success,
        onTertiary = surface,
        tertiaryContainer = successSoft,
        onTertiaryContainer = success,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceSubtle,
        onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = overlay,
        inverseOnSurface = overlayInk,
        error = danger,
        onError = surface,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
        outline = controlBorder,
        outlineVariant = border,
        scrim = scrim,
        surfaceBright = surface,
        surfaceDim = canvas,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceSubtle,
    )
}
