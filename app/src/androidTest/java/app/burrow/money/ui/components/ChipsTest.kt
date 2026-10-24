package app.burrow.money.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** New expense's FormChip and the sheets' Chip / ChipRow. */
class ChipsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun formChipHasA48dpTargetAroundA36dpPill() {
        // The ripple bug: the 48 dp target is invisible; the pill (and its ripple) stays 36 dp.
        var taps = 0
        rule.show { FormChipRow { FormChip("Travel", { taps++ }, FormChipStyle.SELECTED) } }
        val target = rule.onNodeWithText("Travel").assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        val label = rule.onNodeWithText("Travel", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("label inside the target", label.top >= target.top && label.bottom <= target.bottom)
        rule.onNodeWithText("Travel").performClick()
        assertEquals(1, taps)
    }

    @Test fun formChipKeepsLongNamesOnOneLineWithAnEllipsis() {
        rule.show(320.dp) { FormChipRow { FormChip("Subscriptions and memberships and more", {}) } }
        rule.onNodeWithText("Subscriptions and memberships and more", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun formChipRowScrollsInsteadOfSquashing() {
        rule.show(320.dp) {
            FormChipRow { listOf("Food & drinks", "Travel", "Misc", "Shopping", "Bills").forEach { FormChip(it, {}) } }
        }
        rule.onNodeWithText("Food & drinks", useUnmergedTree = true).assertTextFits()
        rule.onNodeWithText("Travel", useUnmergedTree = true).assertTextFits()
    }

    @Test fun formChipWithCircleAndTrailingAmount() {
        rule.show {
            FormChipRow { FormChip("Chai", {}, circle = { IconCircle(painterResource(R.drawable.ic_check_regular)) }, trailing = "₹20") }
        }
        assertNoOverlap(rule.onNodeWithText("Chai", useUnmergedTree = true), rule.onNodeWithText("₹20", useUnmergedTree = true))
    }

    @Test fun chipIsTappableAndMarksSelection() {
        var taps = 0
        rule.show { Chip("Food & drinks", selected = true, onClick = { taps++ }) }
        rule.onNodeWithText("Food & drinks").assertIsDisplayed().performClick()
        assertEquals(1, taps)
        assertTrue(rule.onNodeWithText("Food & drinks").isSelected())
    }

    @Test fun chipRowChipsAreEqualWidth() {
        rule.show(320.dp, 1.3f) { ChipRow(listOf("Yesterday", "Today", "Tomorrow"), { it }, { it == "Today" }, {}) }
        val w = listOf("Yesterday", "Today", "Tomorrow").map { rule.onNodeWithText(it).assertWidthIsAtLeast(48.dp).getUnclippedBoundsInRoot().let { r -> (r.right - r.left).value } }
        assertEquals(w.max(), w.min(), 1f)
    }
}
