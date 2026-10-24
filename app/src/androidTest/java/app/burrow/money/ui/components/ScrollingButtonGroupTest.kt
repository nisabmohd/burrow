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
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Customise's five tabs: the same pills as Plan, scrolling. */
class ScrollingButtonGroupTest {
    @get:Rule val rule = createComposeRule()
    private val tabs = listOf("Today", "Expense", "Transactions", "Region", "Currency")

    @Test fun openingOnTheLastTabBringsItIntoView() {
        rule.show(320.dp) { ScrollingButtonGroup(tabs, "Currency", { it }, {}) }
        rule.onNodeWithText("Currency").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("Currency").isSelected())
    }

    @Test fun tapPicksAndTheNewTabIsSelected() {
        rule.show(320.dp) {
            var sel by remember { mutableStateOf("Today") }
            ScrollingButtonGroup(tabs, sel, { it }, { sel = it })
        }
        rule.onNodeWithText("Region").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(rule.onNodeWithText("Region").isSelected())
        rule.onNodeWithText("Region").assertIsDisplayed()
    }

    @Test fun samePillHeightAsButtonGroupAndLabelCentred() {
        rule.show(360.dp, 1.3f) { ScrollingButtonGroup(tabs, "Today", { it }, {}) }
        for (t in listOf("Today", "Expense")) {
            val pill = rule.onNodeWithText(t).assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
            val text = rule.onNodeWithText(t, useUnmergedTree = true).assertTextFits().getUnclippedBoundsInRoot()
            assertEquals(pill.centerX().value, text.centerX().value, 1.5f)
        }
    }
}
