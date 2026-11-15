package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Every text/background pair the screen draws, in both themes, against WCAG AA: 4.5:1 for text,
 * 3:1 for large text, icons and the progress bar. Disabled controls are exempt, as in WCAG.
 */
class ThemeContrastTest {

    private class Pair(val name: String, val foreground: Color, val background: Color, val minimum: Double)

    private fun text(name: String, fg: Color, bg: Color) = Pair(name, fg, bg, TEXT)
    private fun graphic(name: String, fg: Color, bg: Color) = Pair(name, fg, bg, GRAPHIC)

    private fun pairs(s: ColorScheme, mascot: MascotColors): List<Pair> {
        val cards = StatusTone.entries.flatMap { tone ->
            val c = cardColors(tone, s)
            listOf(
                text("$tone card title", c.content, c.container),
                text("$tone card detail", c.detail, c.container),
                text("$tone card text button", c.action, c.container),
            )
        }
        val working = cardColors(StatusTone.Working, s)
        return cards + listOf(
            text("app name and screen text", s.onBackground, s.background),
            text("Paste and Open labels", s.onPrimary, s.primary),
            text("bar text", s.onSurface, s.surfaceContainerHighest),
            text("bar label and placeholder", s.onSurfaceVariant, s.surfaceContainerHighest),
            text("bar label, focused", s.primary, s.surfaceContainerHighest),
            graphic("bar focus ring", s.primary, s.background),
            graphic("bar link icon", s.onSurfaceVariant, s.surfaceContainerHighest),
            text("sheet text", s.onSurface, s.surfaceContainerLow),
            text("sheet secondary text", s.onSurfaceVariant, s.surfaceContainerLow),
            text("sheet text button", s.primary, s.surfaceContainerLow),
            text("snackbar", s.inverseOnSurface, s.inverseSurface),
            graphic("progress on its track", working.progress, working.track),
            graphic("progress on the card", working.progress, working.container),
            graphic("selected thumbnail border", s.primary, s.surfaceContainerLow),
            graphic("selected mark tick", s.onPrimary, s.primary),
            graphic("mascot face", mascot.ink, mascot.body),
            graphic("mascot face on its progress fill", mascot.ink, mascot.fill),
            graphic("mascot arrow", mascot.arrow, s.background),
        )
    }

    @Test
    fun `light theme passes WCAG AA`() = check("light", LightColors, LightMascot)

    @Test
    fun `dark theme passes WCAG AA`() = check("dark", DarkColors, DarkMascot)

    private fun check(theme: String, scheme: ColorScheme, mascot: MascotColors) {
        val failures = pairs(scheme, mascot).mapNotNull { p ->
            val ratio = contrast(p.foreground, p.background)
            if (ratio < p.minimum) "${p.name}: %.2f < %.1f".format(ratio, p.minimum) else null
        }
        assertTrue("$theme theme:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
    }
}
