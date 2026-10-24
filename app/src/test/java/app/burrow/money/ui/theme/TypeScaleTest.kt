package app.burrow.money.ui.theme

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Line heights are a ratio of the text size (em). In sp, Android 14+ non-linear font scaling grows
 * a 24 sp line less than 16 sp text, and wrapped lines ran into the next text at large font sizes
 * (the paused note, 2026-10-24).
 */
class TypeScaleTest {
    private fun styles(t: androidx.compose.material3.Typography): Map<String, TextStyle> = mapOf(
        "displayLarge" to t.displayLarge, "displayMedium" to t.displayMedium, "displaySmall" to t.displaySmall,
        "headlineLarge" to t.headlineLarge, "headlineMedium" to t.headlineMedium, "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge, "titleMedium" to t.titleMedium, "titleSmall" to t.titleSmall,
        "bodyLarge" to t.bodyLarge, "bodyMedium" to t.bodyMedium, "bodySmall" to t.bodySmall,
        "labelLarge" to t.labelLarge, "labelMedium" to t.labelMedium, "labelSmall" to t.labelSmall,
    )

    @Test fun everyStyleInEveryFontAndSizeHasARatioLineHeight() {
        for (font in BodyFont.entries) for (size in TextSize.entries) {
            for ((name, style) in styles(burrowTypography(font, size.scale))) {
                val where = "$font/$size/$name"
                assertTrue("$where: line height must be em, was ${style.lineHeight}", style.lineHeight.isEm)
                assertTrue("$where: font size must be sp", style.fontSize.isSp)
                // Room for accents and the heavier variable weights: at least 1.15× the text.
                assertTrue("$where: line height ${style.lineHeight.value} too tight", style.lineHeight.value >= 1.15f)
            }
        }
    }

    @Test fun textSizeSettingScalesFontsNotRatios() {
        val small = burrowTypography(BodyFont.LEXEND, TextSize.SMALL.scale).bodyMedium
        val large = burrowTypography(BodyFont.LEXEND, TextSize.LARGE.scale).bodyMedium
        assertTrue(large.fontSize.value > small.fontSize.value)
        assertTrue(large.lineHeight == small.lineHeight)
    }

    @Test fun wrappedTitleLinesAreNoFurtherApartThanTheTitleIsFromItsLine() {
        // The note: titleSmall lines over a body line. The gap between two title lines (its leading)
        // must not be bigger than the gap from the title to the body line (half of each leading).
        val t = burrowTypography(BodyFont.LEXEND, 1f)
        val title = t.titleMedium // BurrowType.titleSmall
        val body = t.bodyMedium
        val titleLeading = title.lineHeight.value * title.fontSize.value - title.fontSize.value
        val bodyLeading = body.lineHeight.value * body.fontSize.value - body.fontSize.value
        assertTrue("title lines $titleLeading sp apart vs ${(titleLeading + bodyLeading) / 2} sp to the line", titleLeading <= (titleLeading + bodyLeading) / 2 + 0.01f)
    }
}
