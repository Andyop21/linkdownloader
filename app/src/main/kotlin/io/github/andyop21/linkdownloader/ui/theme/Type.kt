package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.andyop21.linkdownloader.R

/**
 * Lato, from Google Fonts' OFL release (`res/font/lato_*.ttf`, unmodified; licence in
 * `assets/licenses/OFL-Lato.txt`), at three weights: regular, bold and black.
 */
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
)

/** Line boxes as CSS draws them: the leading split evenly above and below the glyphs. */
private val EvenLeading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** A run of type: [size] sp at [weight], line height [lineHeight] times the size, tracking [tracking] em. */
private fun style(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 1.45f,
    tracking: Float = 0f,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Lato,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
    lineHeightStyle = EvenLeading,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** The named runs the screen uses. Colours are set where the text is drawn. */
object AppType {
    /** The app's name in the bar at the top: 17 sp, bold. */
    val barTitle = style(17f, FontWeight.Bold, lineHeight = 1.2f, tracking = -0.01f)

    /** The status line under the orb: 22 sp, bold, tight. */
    val heroTitle = style(22f, FontWeight.Bold, lineHeight = 1.25f, tracking = -0.015f)

    /** The detail under the status line: 15 sp. */
    val lead = style(15f)

    /** The percentage inside the orb: 26 sp, black, tabular figures. */
    val percent = style(26f, FontWeight.Black, lineHeight = 1f, tracking = -0.02f, tabular = true)

    /** A sheet's title: 19 sp, bold. */
    val heading = style(19f, FontWeight.Bold, lineHeight = 1.25f, tracking = -0.015f)

    /** Body text: 14 sp. */
    val body = style(14f)

    /** A secondary line: 13 sp. */
    val sub = style(13f, lineHeight = 1.4f)

    /** The main button's label: 16 sp, bold. */
    val button = style(16f, FontWeight.Bold, lineHeight = 1.2f)

    /** A small button's label: 14 sp, bold. */
    val buttonSmall = style(14f, FontWeight.Bold, lineHeight = 1.2f)

    /** The link field: 16 sp. */
    val field = style(16f, lineHeight = 1.3f)

    /** A number that changes in place (a percentage, a length): 13 sp, bold, tabular figures. */
    val figure = style(13f, FontWeight.Bold, lineHeight = 1.3f, tabular = true)

    /** A version or other value in a list: 14 sp, bold, tabular figures. */
    val value = style(14f, FontWeight.Bold, tabular = true)
}

private val Base = Typography()

private fun TextStyle.lato(weight: FontWeight) = copy(fontFamily = Lato, fontWeight = weight)

/** Material 3's type scale in Lato, for the Material parts that draw words of their own. */
internal val AppTypography = Typography(
    displayLarge = Base.displayLarge.lato(FontWeight.Black),
    displayMedium = Base.displayMedium.lato(FontWeight.Black),
    displaySmall = Base.displaySmall.lato(FontWeight.Black),
    headlineLarge = Base.headlineLarge.lato(FontWeight.Black),
    headlineMedium = Base.headlineMedium.lato(FontWeight.Black),
    headlineSmall = Base.headlineSmall.lato(FontWeight.Bold),
    titleLarge = Base.titleLarge.lato(FontWeight.Bold),
    titleMedium = Base.titleMedium.lato(FontWeight.Bold),
    titleSmall = Base.titleSmall.lato(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.lato(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.lato(FontWeight.Normal),
    bodySmall = Base.bodySmall.lato(FontWeight.Normal),
    labelLarge = Base.labelLarge.lato(FontWeight.Bold),
    labelMedium = Base.labelMedium.lato(FontWeight.Bold),
    labelSmall = Base.labelSmall.lato(FontWeight.Bold),
)
