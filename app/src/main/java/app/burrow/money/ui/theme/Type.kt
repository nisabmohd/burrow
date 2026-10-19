package app.burrow.money.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.burrow.money.R

/*
 * Six text styles, two weights (400, 500). Nothing else, anywhere (docs/TOKENS.md § Type).
 * Fonts are static TTFs in res/font (OFL, see res/font/OFL.txt): two weights each, ~55–80 KB per file.
 * Atkinson Hyperlegible has no 500, so its Bold stands in for Medium.
 * Space Grotesk is one variable TTF (from Sprout); see [SpaceGroteskFamily].
 */
private fun family(@FontRes regular: Int, @FontRes medium: Int) =
    FontFamily(Font(regular, FontWeight.Normal), Font(medium, FontWeight.Medium))

private val LexendFamily = family(R.font.lexend_regular, R.font.lexend_medium)
private val OutfitFamily = family(R.font.outfit_regular, R.font.outfit_medium)
private val AtkinsonFamily = family(R.font.atkinson_regular, R.font.atkinson_bold)

/**
 * Space Grotesk's strokes are about 17% thinner than Lexend's at the same weight, so it is drawn a
 * step heavier (as in Sprout): Regular at 470, Medium at 600. The weights in code stay 400 and 500.
 */
@OptIn(ExperimentalTextApi::class)
private val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(470))),
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

fun BodyFont.family(): FontFamily = when (this) {
    BodyFont.LEXEND -> LexendFamily
    BodyFont.OUTFIT -> OutfitFamily
    BodyFont.ATKINSON -> AtkinsonFamily
    BodyFont.SPACE_GROTESK -> SpaceGroteskFamily
    BodyFont.SYSTEM -> FontFamily.Default
}

fun burrowTypography(font: BodyFont, scale: Float): Typography {
    val f = font.family()
    fun s(size: Int, line: Int, w: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = f, fontWeight = w, fontSize = (size * scale).sp, lineHeight = (line * scale).sp,
        letterSpacing = tracking.sp, fontFeatureSettings = "tnum",
    )
    val display = s(44, 52, FontWeight.Medium, -0.5f)
    val headline = s(28, 36, FontWeight.Medium)
    val title = s(22, 28, FontWeight.Medium)
    val titleSmall = s(16, 24, FontWeight.Medium)
    val bodyLarge = s(16, 24, FontWeight.Normal)
    val body = s(14, 20, FontWeight.Normal)
    val label = s(12, 16, FontWeight.Medium)
    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = headline, headlineMedium = headline, headlineSmall = title,
        titleLarge = title, titleMedium = titleSmall, titleSmall = s(14, 20, FontWeight.Medium),
        bodyLarge = bodyLarge, bodyMedium = body, bodySmall = s(12, 16, FontWeight.Normal),
        labelLarge = s(14, 20, FontWeight.Medium), labelMedium = label, labelSmall = label,
    )
}

/** Nav labels shrink to fit long translations, never below this (docs/TOKENS.md § Type). */
val NavLabelMinSize = 10.sp

/** The only text styles screens use. Colour is never part of a style. */
object BurrowType {
    /** 44/52 Medium: Today's hero amount, detail-header amounts, keypad amount. */
    val display: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displayLarge

    /** 28/36 Medium: tab titles (Today, Activity…), welcome/intro headline (36/44 there). */
    val headline: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineMedium

    /** 22/28 Medium: widget values, sheet titles, top-bar titles, stats. */
    val title: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge

    /** 16/24 Medium: row amounts, centred-header title, card titles, note titles. */
    val titleSmall: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium

    /** 16/24 Regular: row headline, text fields. */
    val bodyLarge: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge

    /** 14/20 Regular: the line under a row headline, one-line descriptions. */
    val body: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyMedium

    /** 14/20 Medium: buttons, chips, section headers, text buttons. */
    val button: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge

    /** 12/16 Medium: widget labels, nav labels, chart axis, legend. */
    val label: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium
}
