package io.github.andyop21.linkdownloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.andyop21.linkdownloader.R

/**
 * Nunito, from Google Fonts' OFL release (`res/font/nunito.ttf`, the variable font; licence in
 * `assets/licenses/OFL-Nunito.txt`), used at three weights: regular, semibold and extrabold.
 */
val Nunito = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.ExtraBold),
)

private fun nunito(weight: FontWeight) = Font(
    resId = R.font.nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Base = Typography()

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

/** Material 3's type scale in Nunito: headings and titles extrabold, labels semibold, body regular. */
internal val AppTypography = Typography(
    displayLarge = Base.displayLarge.nunito(FontWeight.ExtraBold),
    displayMedium = Base.displayMedium.nunito(FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.nunito(FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.nunito(FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.nunito(FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.nunito(FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.nunito(FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.nunito(FontWeight.ExtraBold),
    titleSmall = Base.titleSmall.nunito(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.nunito(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.nunito(FontWeight.Normal),
    bodySmall = Base.bodySmall.nunito(FontWeight.Normal),
    labelLarge = Base.labelLarge.nunito(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.nunito(FontWeight.SemiBold),
    labelSmall = Base.labelSmall.nunito(FontWeight.SemiBold),
)
