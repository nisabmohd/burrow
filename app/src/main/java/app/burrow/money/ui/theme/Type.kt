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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.burrow.money.R

/*
 * Six text styles, two weights (400, 500). Nothing else, anywhere (docs/TOKENS.md § Type).
 * Fonts are variable TTFs in res/font (OFL, see FONTS-LICENSE.txt), one file per family.
 *
 * The designs are drawn in a browser, which renders strokes heavier than Android does. So each
 * family is drawn a step heavier than its nominal weight to match the PNGs: the weights in code stay
 * 400 and 500 (checked side by side against design/screens at 4x, 2026-10-22).
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(@FontRes res: Int, regular: Int, medium: Int) = FontFamily(
    Font(res, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(regular))),
    Font(res, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(medium))),
)

private val LexendFamily = variable(R.font.lexend, 440, 540)
private val OutfitFamily = variable(R.font.outfit, 450, 550)

/** Atkinson Hyperlegible has no true 500; its "Medium" is drawn near Bold, as before. */
private val AtkinsonFamily = variable(R.font.atkinson, 440, 680)

/** Space Grotesk's strokes are about 17% thinner than Lexend's at the same weight (as in Sprout). */
private val SpaceGroteskFamily = variable(R.font.space_grotesk, 490, 620)

fun BodyFont.family(): FontFamily = when (this) {
    BodyFont.LEXEND -> LexendFamily
    BodyFont.OUTFIT -> OutfitFamily
    BodyFont.ATKINSON -> AtkinsonFamily
    BodyFont.SPACE_GROTESK -> SpaceGroteskFamily
    BodyFont.SYSTEM -> FontFamily.Default
}

fun burrowTypography(font: BodyFont, scale: Float): Typography {
    val f = font.family()
    // Line height as a ratio (em), not sp: Android 14+ scales big sp values less than small ones
    // (non-linear font scaling), so a 24 sp line under 16 sp text crowded wrapped lines into each
    // other at large font sizes (the paused note's title over its line, 2026-10-24).
    fun s(size: Int, line: Int, w: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = f, fontWeight = w, fontSize = (size * scale).sp, lineHeight = (line.toFloat() / size).em,
        letterSpacing = tracking.sp, fontFeatureSettings = "tnum",
    )
    val display = s(44, 52, FontWeight.Medium, -0.5f)
    val headline = s(28, 36, FontWeight.Medium)
    val title = s(22, 28, FontWeight.Medium)
    // 22, not 24: a wrapped title ("These are sample / transactions") had more space between its
    // own lines than above its subtitle (user, 2026-10-24). 22 matches the gap to a 14 / 20 line.
    val titleSmall = s(16, 22, FontWeight.Medium)
    val bodyLarge = s(16, 24, FontWeight.Normal)
    val body = s(14, 20, FontWeight.Normal)
    val label = s(12, 16, FontWeight.Medium)
    val keypad = s(24, 32, FontWeight.Normal)
    val intro = s(36, 44, FontWeight.Medium)
    val tiny = s(10, 12, FontWeight.Medium)
    return Typography(
        displayLarge = display, displayMedium = intro, displaySmall = keypad,
        headlineLarge = headline, headlineMedium = headline, headlineSmall = title,
        titleLarge = title, titleMedium = titleSmall, titleSmall = s(14, 20, FontWeight.Medium),
        bodyLarge = bodyLarge, bodyMedium = body, bodySmall = s(12, 16, FontWeight.Normal),
        labelLarge = s(14, 20, FontWeight.Medium), labelMedium = label, labelSmall = tiny,
    )
}

/** Nav labels shrink to fit long translations, never below this (docs/TOKENS.md § Type). */
val NavLabelMinSize = 10.sp

/** A widget's big value (a long category name, a large amount) shrinks to stay on one line, never below this. */
val WidgetValueMinSize = 16.sp

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

    /** 10/12 Medium: the percent inside a 40 dp ring only. */
    val tiny: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelSmall

    /** 36/44 Medium: the welcome and intro headlines only. */
    val intro: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displayMedium

    /** 24/32 Regular: keypad digits only (the prototype's keys). */
    val keypad: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displaySmall
}
