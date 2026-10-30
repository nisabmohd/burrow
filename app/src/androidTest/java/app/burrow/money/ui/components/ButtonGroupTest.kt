package app.burrow.money.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Settings choices (theme, text size, week start, Recent 3 / 5 / 10): the connected group with a check. */
@RunWith(Parameterized::class)
class ButtonGroupTest(private val widthDp: Float, private val fontScale: Float) {
    // JUnit builds the test by reflection, which can't pass a Dp (a value class): the width comes in as a Float.
    private val width: Dp get() = widthDp.dp

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first.value, it.second) }
    }

    @get:Rule val rule = createComposeRule()
    private val options = listOf("System", "Light", "Dark")

    @Test fun selectedIsMarkedAndOnlyOne() {
        rule.show(width, fontScale) { ButtonGroup(options, "Light", { it }, {}) }
        assertFalse(rule.onNodeWithText("System").isSelected())
        assertTrue(rule.onNodeWithText("Light").isSelected())
        assertFalse(rule.onNodeWithText("Dark").isSelected())
    }

    @Test fun tappingAnotherSelectsItTappingTheSelectedDoesNothing() {
        val picks = mutableListOf<String>()
        rule.show(width, fontScale) {
            var sel by remember { mutableStateOf("System") }
            ButtonGroup(options, sel, { it }, { picks += it; sel = it })
        }
        rule.onNodeWithText("System").performClick()
        assertEquals("tapping the open tab fires nothing", emptyList<String>(), picks)
        rule.onNodeWithText("Dark").performClick()
        assertEquals(listOf("Dark"), picks)
        assertTrue(rule.onNodeWithText("Dark").isSelected())
        assertFalse(rule.onNodeWithText("System").isSelected())
    }

    @Test fun checkAndLabelAreCentredTogetherInsideThePill() {
        rule.show(width, fontScale) { ButtonGroup(options, "Dark", { it }, {}) }
        for (o in options) {
            val pill = rule.onNodeWithText(o).getUnclippedBoundsInRoot()
            val text = rule.onNodeWithText(o, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$o label inside its pill", text.left >= pill.left && text.right <= pill.right)
            assertEquals("$o vertically centred", pill.centerY().value, text.centerY().value, 1.5f)
        }
    }

    @Test fun pillsAreEqualAndTallEnoughToTap() {
        rule.show(width, fontScale) { ButtonGroup(options, "Dark", { it }, {}) }
        val widths = options.map { rule.onNodeWithText(it).assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot().let { r -> (r.right - r.left).value } }
        assertEquals(widths.first(), widths.max(), 1f)
        assertEquals(widths.first(), widths.min(), 1f)
    }

    @Test fun longLabelsShrinkToFitNeverClip() {
        val long = listOf("Überweisungen", "Einnahmen", "Ausgaben")
        rule.show(width, fontScale) { ButtonGroup(long, long[0], { it }, {}) }
        for (o in long) rule.onNodeWithText(o, useUnmergedTree = true).assertIsDisplayed().assertTextFits()
    }
}
