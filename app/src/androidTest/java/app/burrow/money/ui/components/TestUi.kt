package app.burrow.money.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.burrow.money.ui.theme.BurrowTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/*
 * Shared helpers for component tests: render in Burrow's theme at a phone width and font size, and
 * the checks the bugs of 2026-10-24 needed: text not clipped, not truncated, two parts not on top of
 * each other.
 */

/** The phones every layout test runs on: a small phone at normal text, and at the largest text. */
val SIZES: List<Pair<Dp, Float>> = listOf(360.dp to 1f, 320.dp to 1.3f, 320.dp to 2f)

fun ComposeContentTestRule.show(width: Dp = 360.dp, fontScale: Float = 1f, content: @Composable () -> Unit) = setContent {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
        BurrowTheme { Box(Modifier.requiredWidth(width)) { content() } }
    }
}

fun string(id: Int, vararg args: Any): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** Not cut off by a parent (the "All days" bug: a 24 dp row clipped a 40 dp button). */
fun SemanticsNodeInteraction.assertNotClipped(tolerance: Dp = 0.5.dp): SemanticsNodeInteraction {
    val clipped = getBoundsInRoot()
    val full = getUnclippedBoundsInRoot()
    assertTrue(
        "clipped: shows $clipped of $full",
        (full.top - clipped.top).value <= tolerance.value && (clipped.bottom - full.bottom).value >= -tolerance.value &&
            (full.left - clipped.left).value <= tolerance.value && (clipped.right - full.right).value >= -tolerance.value,
    )
    return this
}

/** The text fits: no ellipsis, no overflow (the "1,240...." bug). */
fun SemanticsNodeInteraction.assertTextFits(): SemanticsNodeInteraction {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    val r = results.firstOrNull() ?: return this
    assertFalse("text overflows: ${r.layoutInput.text}", r.hasVisualOverflow)
    for (line in 0 until r.lineCount) assertFalse("line $line is ellipsized: ${r.layoutInput.text}", r.isLineEllipsized(line))
    return this
}

private fun DpRect.overlaps(o: DpRect, tol: Dp) =
    left < o.right - tol && o.left < right - tol && top < o.bottom - tol && o.top < bottom - tol

/** [a] and [b] don't sit on top of each other (the paused note's title over its line). */
fun assertNoOverlap(a: SemanticsNodeInteraction, b: SemanticsNodeInteraction, tolerance: Dp = 0.5.dp) {
    val ra = a.getUnclippedBoundsInRoot()
    val rb = b.getUnclippedBoundsInRoot()
    assertFalse("overlap: $ra and $rb", ra.overlaps(rb, tolerance))
}

/** [inner] lies inside [outer]. */
fun assertInside(inner: SemanticsNodeInteraction, outer: SemanticsNodeInteraction, tolerance: Dp = 0.5.dp) {
    val i = inner.getUnclippedBoundsInRoot()
    val o = outer.getUnclippedBoundsInRoot()
    assertTrue("$i not inside $o", i.left >= o.left - tolerance && i.top >= o.top - tolerance && i.right <= o.right + tolerance && i.bottom <= o.bottom + tolerance)
}

fun DpRect.centerX(): Dp = (left + right) / 2
fun DpRect.centerY(): Dp = (top + bottom) / 2

/** Whether the node reports itself selected (radio, tab, tile). */
fun SemanticsNodeInteraction.isSelected(): Boolean = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

