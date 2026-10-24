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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tabs (COMPONENTS § Pill tabs): Plan fixed, Customise scrolling. No check; label centred. */
@RunWith(Parameterized::class)
class PillTabsTest(private val width: Dp, private val fontScale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first, it.second) }
    }

    @get:Rule val rule = createComposeRule()
    private val plan = listOf("Coming up", "Budgets", "Goals")
    private val customise = listOf("Today", "Expense", "Transactions", "Region", "Currency")

    @Test fun labelIsCentredInEveryTabSelectedOrNot() {
        // 2026-10-24: a check put "Coming up" left and "Goals" centred.
        rule.show(width, fontScale) { PillTabs(plan, "Goals", { it }, {}) }
        for (o in plan) {
            val pill = rule.onNodeWithText(o).getUnclippedBoundsInRoot()
            val text = rule.onNodeWithText(o, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("$o centred", pill.centerX().value, text.centerX().value, 1.5f)
            assertEquals("$o centred", pill.centerY().value, text.centerY().value, 1.5f)
        }
    }

    @Test fun fixedTabsAreEqualWidthAnd44dp() {
        rule.show(width, fontScale) { PillTabs(plan, "Budgets", { it }, {}) }
        val widths = plan.map { rule.onNodeWithText(it).assertHeightIsAtLeast(44.dp).getUnclippedBoundsInRoot().let { r -> (r.right - r.left).value } }
        assertEquals(widths.max(), widths.min(), 1f)
    }

    @Test fun onlyOneSelectedAndTappingSwitches() {
        rule.show(width, fontScale) {
            var sel by remember { mutableStateOf("Coming up") }
            PillTabs(plan, sel, { it }, { sel = it })
        }
        rule.onNodeWithText("Goals").performClick()
        assertTrue(rule.onNodeWithText("Goals").isSelected())
        assertFalse(rule.onNodeWithText("Coming up").isSelected())
    }

    @Test fun labelsFitAtLargeText() {
        rule.show(width, fontScale) { PillTabs(plan, "Coming up", { it }, {}) }
        for (o in plan) rule.onNodeWithText(o, useUnmergedTree = true).assertTextFits()
    }

    @Test fun scrollingTabsBringTheOpenOneIntoView() {
        rule.show(width, fontScale) { PillTabs(customise, "Currency", { it }, {}, scrolling = true) }
        rule.onNodeWithText("Currency").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("Currency").isSelected())
    }

    @Test fun scrollingTabsPick() {
        rule.show(width, fontScale) {
            var sel by remember { mutableStateOf("Today") }
            PillTabs(customise, sel, { it }, { sel = it }, scrolling = true)
        }
        rule.onNodeWithText("Region").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(rule.onNodeWithText("Region").isSelected())
    }
}
