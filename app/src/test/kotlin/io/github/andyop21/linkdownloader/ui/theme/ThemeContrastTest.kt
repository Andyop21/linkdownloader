package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Every text/background pair the screen draws, in both themes, against WCAG AA: 4.5:1 for text,
 * 3:1 for icons, control edges and the progress bar. A translucent colour is laid over what it
 * sits on first. Disabled controls are exempt, as in WCAG.
 */
class ThemeContrastTest {

    private data class Pair(val name: String, val foreground: Color, val background: Color, val minimum: Double)

    private fun text(name: String, fg: Color, bg: Color) = Pair(name, fg, bg, TEXT)
    private fun graphic(name: String, fg: Color, bg: Color) = Pair(name, fg, bg, GRAPHIC)

    private fun pairs(c: AppColors): List<Pair> {
        val orbs = Tone.entries.flatMap { tone ->
            val orb = c.orb(tone)
            listOf(
                graphic("$tone orb glyph, light side", Color.White, orb.light),
                graphic("$tone orb glyph, deep side", Color.White, orb.deep),
                graphic("$tone ring on the page", c.accent(tone), c.canvas),
            )
        }
        val track = c.track.compositeOver(c.canvas)
        return orbs + listOf(
            text("bar title on the canvas", c.ink, c.canvas),
            text("status line on the canvas", c.ink, c.canvas),
            text("status detail on the canvas", c.muted, c.canvas),
            text("percentage (large text) in the orb", Color.White, c.orb(Tone.Primary).light).copy(minimum = GRAPHIC),
            text("field text", c.ink, c.surface),
            text("field placeholder", c.faint, c.surface),
            text("field text while a download runs", c.muted, c.surface),
            graphic("field edge on the canvas", c.controlBorder, c.canvas),
            graphic("field edge on its surface", c.controlBorder, c.surface),
            graphic("field edge with focus", c.primary, c.canvas),
            graphic("field link icon", c.faint, c.surface),
            graphic("clear button icon", c.ink2, c.surfaceSubtle),
            text("main and primary button labels", c.onPrimary, c.primaryFill),
            text("primary button labels, pressed", c.onPrimary, c.primaryFillPressed),
            text("outlined button labels", c.ink, c.canvas),
            text("outlined button labels, pressed", c.ink, c.press.compositeOver(c.canvas)),
            graphic("progress on its track", c.primary, track),
            graphic("about live dot", c.primary, c.surface),
            text("sheet text", c.ink, c.surface),
            text("sheet secondary text", c.muted, c.surface),
            text("about list labels", c.ink2, c.canvas),
            text("about list values", c.ink, c.canvas),
            text("about result", c.ink2, c.surface),
            graphic("about result dot, success", c.success, c.surface),
            graphic("about result dot, warning", c.warning, c.surface),
            graphic("selected thumbnail edge", c.primary, c.surface),
            graphic("selected mark tick", c.onPrimary, c.primaryFill),
            text("snackbar", c.overlayInk, c.overlay),
            text("snackbar action", c.overlayAction, c.overlay),
        )
    }

    @Test
    fun `light theme passes WCAG AA`() = check("light", AppColors.Light)

    @Test
    fun `dark theme passes WCAG AA`() = check("dark", AppColors.Dark)

    private fun check(theme: String, colors: AppColors) {
        val failures = pairs(colors).mapNotNull { p ->
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
