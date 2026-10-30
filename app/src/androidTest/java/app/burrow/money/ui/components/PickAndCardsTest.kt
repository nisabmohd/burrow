package app.burrow.money.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** PickRow (C1: round check, no tint) and MessageCard (To review / Not understood: a button, no swipe). */
@RunWith(Parameterized::class)
class PickAndCardsTest(private val widthDp: Float, private val fontScale: Float) {
    // JUnit builds the test by reflection, which can't pass a Dp (a value class): the width comes in as a Float.
    private val width: Dp get() = widthDp.dp

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first.value, it.second) }
    }

    @get:Rule val rule = createComposeRule()

    @Test fun pickRowMarksTheChosenOneAndPicks() {
        val picks = mutableListOf<String>()
        rule.show(width, fontScale) {
            ListGroup {
                PickRow("Expense", 0, 3, true, { picks += "E" }, supporting = "Money out")
                PickRow("Income", 1, 3, false, { picks += "I" }, supporting = "Money in")
                PickRow("Transfer", 2, 3, false, { picks += "T" }, supporting = "Between your accounts · not spending")
            }
        }
        assertTrue(rule.onNodeWithText("Expense").isSelected())
        assertFalse(rule.onNodeWithText("Income").isSelected())
        rule.onNodeWithText("Transfer").performClick()
        assertEquals(listOf("T"), picks)
        rule.onNodeWithText("Between your accounts · not spending", useUnmergedTree = true).assertNotClipped()
    }

    @Test fun messageCardButtonIgnoreAndNoSwipe() {
        var saves = 0; var ignores = 0
        rule.show(width, fontScale) {
            MessageCard(
                lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
                title = "Chaayos", line = "HDFC Savings · 8:05 AM", amount = "−₹180",
                ignoreLabel = "Ignore", onIgnore = { ignores++ },
                button = "Save", buttonIcon = painterResource(R.drawable.ic_check_regular), onButton = { saves++ },
            ) { MessageBox("HDFC Bank", "Rs.180.00 debited from A/c XX1234 to VPA chaayos.paytm@hdfc.", emptyList()) }
        }
        rule.onNodeWithText("Chaayos").performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals("a swipe does nothing", 0, saves + ignores) }
        rule.onNodeWithText("Save").assertWidthIsAtLeast(200.dp).performClick()
        rule.onNodeWithContentDescription("Ignore").performClick()
        rule.runOnIdle { assertEquals(1, saves); assertEquals(1, ignores) }
        assertNoOverlap(rule.onNodeWithText("Chaayos", useUnmergedTree = true), rule.onNodeWithText("−₹180", useUnmergedTree = true))
    }

    @Test fun messageBoxWithoutHeader() {
        rule.show(width, fontScale) { MessageBox(null, "Txn of Rs 899.00 on SBI Card ending 3321", emptyList()) }
        rule.onNodeWithText("Txn of Rs 899.00 on SBI Card ending 3321", substring = true).assertIsDisplayed()
    }
}

