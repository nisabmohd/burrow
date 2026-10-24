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

/** Plan's tabs, Expense / Income / Transfer, theme, week start: one connected group. */
@RunWith(Parameterized::class)
class ButtonGroupTest(private val width: Dp, private val fontScale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first, it.second) }
    }

    @get:Rule val rule = createComposeRule()
    private val options = listOf("Coming up", "Budgets", "Goals")

    @Test fun selectedIsMarkedAndOnlyOne() {
        rule.show(width, fontScale) { ButtonGroup(options, "Budgets", { it }, {}) }
        assertFalse(rule.onNodeWithText("Coming up").isSelected())
        assertTrue(rule.onNodeWithText("Budgets").isSelected())
        assertFalse(rule.onNodeWithText("Goals").isSelected())
    }

    @Test fun tappingAnotherSelectsItTappingTheSelectedDoesNothing() {
        val picks = mutableListOf<String>()
        rule.show(width, fontScale) {
            var sel by remember { mutableStateOf("Coming up") }
            ButtonGroup(options, sel, { it }, { picks += it; sel = it })
        }
        rule.onNodeWithText("Coming up").performClick()
        assertEquals("tapping the open tab fires nothing", emptyList<String>(), picks)
        rule.onNodeWithText("Goals").performClick()
        assertEquals(listOf("Goals"), picks)
        assertTrue(rule.onNodeWithText("Goals").isSelected())
        assertFalse(rule.onNodeWithText("Coming up").isSelected())
    }

    @Test fun labelSitsInTheSameSpotInEveryPillSelectedOrNot() {
        // 2026-10-24: a check beside the label put "Coming up" left and "Goals" centred.
        rule.show(width, fontScale) { ButtonGroup(options, "Goals", { it }, {}) }
        for (o in options) {
            val pill = rule.onNodeWithText(o).getUnclippedBoundsInRoot()
            val text = rule.onNodeWithText(o, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("$o centred", pill.centerX().value, text.centerX().value, 1.5f)
            assertEquals("$o centred", pill.centerY().value, text.centerY().value, 1.5f)
        }
    }

    @Test fun pillsAreEqualAndTallEnoughToTap() {
        rule.show(width, fontScale) { ButtonGroup(options, "Goals", { it }, {}) }
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
