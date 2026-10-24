package app.burrow.money.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The one sheet for every number (135–142): typing, chips, max, footer. */
class ValueSheetTest {
    @get:Rule val rule = createComposeRule()

    private fun tap(vararg labels: String) = labels.forEach { rule.onNodeWithText(it).performClick() }

    @Test fun typingReplacesTheStartAmountAndDoneReturnsIt() {
        var done: Long? = null
        rule.show { AmountSheet("Type an amount", 9_000_00, { done = it }, {}, line = "Monthly budget", digits = 2) }
        tap("2", "5", "0", "0", "0")
        rule.onNodeWithText(string(R.string.done)).performClick()
        rule.runOnIdle { assertEquals(25_000_00L, done) }
    }

    @Test fun aChipSetsTheValueAndShowsItsCheck() {
        var done: Long? = null
        rule.show { AmountSheet("Food & drinks", 9_000_00, { done = it }, {}, chips = listOf("₹2,000" to 2_000_00L, "₹4,000" to 4_000_00L), digits = 2, cancel = true, confirm = "Save") }
        rule.onNodeWithText("₹4,000").performClick()
        assertEquals(true, rule.onNodeWithText("₹4,000").isSelected())
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle { assertEquals(4_000_00L, done) }
    }

    @Test fun overTheMaxTurnsRedAndSaveDoesNothing() {
        var done: Long? = null
        rule.show { AmountSheet("Refund", 1_930_00, { done = it }, {}, max = 1_930_00, digits = 2, confirm = "Save refund", cancel = true) }
        tap("9", "9", "9", "9")
        rule.onNodeWithText(string(R.string.at_most, "₹1,930"), substring = true).assertIsDisplayed()
        rule.onNodeWithText("Save refund").performClick()
        rule.runOnIdle { assertNull(done) }
    }

    @Test fun cancelAndSecondaryButtons() {
        var dismissed = 0; var removed = 0
        rule.show { AmountSheet("Groceries", 840_00, {}, { dismissed++ }, digits = 2, secondary = "Remove part" to { removed++ }) }
        rule.onNodeWithText("Remove part").performClick()
        rule.runOnIdle { assertEquals(1, removed); assertEquals(0, dismissed) }
    }

    @Test fun keysAreBigEnoughAndThereIsNoDotForDays() {
        rule.show(320.dp, 2f) { DaySheet("Month starts on", 1, {}, {}, ordinal = { "$it." }, quick = listOf(1, 5, 7, 25)) }
        rule.onNodeWithText(".").assertDoesNotExist()
        rule.onNodeWithContentDescription(string(R.string.key_delete)).assertHeightIsAtLeast(48.dp)
    }

    @Test fun dayTypingAndQuickChips() {
        var day: Int? = null
        rule.show { DaySheet("Due every month on", 1, { day = it }, {}, ordinal = { "$it." }, quick = listOf(1, 5, 25), max = 31) }
        tap("3", "1")
        rule.onNodeWithText("31.").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.done)).performClick()
        rule.runOnIdle { assertEquals(31, day) }
    }

    @Test fun timeSheetTypesAndPicksPm() {
        var picked: java.time.LocalTime? = null
        rule.show { TimeValueSheet(java.time.LocalTime.of(9, 30), { picked = it }, {}) }
        val h24 = android.text.format.DateFormat.is24HourFormat(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        if (h24) { tap("2", "2", "1", "5") } else { tap("1", "0", "1", "5"); rule.onNodeWithText(string(R.string.pm)).performClick() }
        rule.onNodeWithText(string(R.string.done)).performClick()
        rule.runOnIdle { assertEquals(java.time.LocalTime.of(22, 15), picked) }
    }
}
