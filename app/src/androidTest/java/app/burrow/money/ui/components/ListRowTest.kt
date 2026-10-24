package app.burrow.money.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Every settings row, transaction row and pick list row. */
@RunWith(Parameterized::class)
class ListRowTest(private val width: Dp, private val fontScale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first, it.second) }
    }

    @get:Rule val rule = createComposeRule()

    @Test fun plainRowIsNotTappable() {
        rule.show(width, fontScale) { ListGroup { ListRow("Your data stays yours", 0, 1, supporting = "Burrow never sends your data anywhere") } }
        rule.onNodeWithText("Your data stays yours").assertHasNoClickAction()
    }

    @Test fun tapAndLongPressGoToTheirOwnCallbacks() {
        var taps = 0; var holds = 0
        rule.show(width, fontScale) { ListGroup { ListRow("Third Wave Coffee", 0, 1, onClick = { taps++ }, onLongClick = { holds++ }) } }
        rule.onNodeWithText("Third Wave Coffee").assertHasClickAction().performClick()
        rule.onNodeWithText("Third Wave Coffee").performTouchInput { longClick() }
        assertEquals(1, taps); assertEquals(1, holds)
    }

    @Test fun headlineSupportingAndTrailingNeverOverlapOrClip() {
        rule.show(width, fontScale) {
            ListGroup {
                ListRow(
                    "Payment apps", 0, 1, supporting = "GPay, PhonePe, Paytm and every other app that shows a payment",
                    lead = { IconCircle(painterResource(R.drawable.ic_check_regular)) },
                    trailing = { BurrowSwitch(true, {}) },
                )
            }
        }
        val h = rule.onNodeWithText("Payment apps", useUnmergedTree = true).assertNotClipped()
        val s = rule.onNodeWithText("GPay, PhonePe, Paytm and every other app that shows a payment", useUnmergedTree = true).assertNotClipped()
        assertNoOverlap(h, s)
    }

    @Test fun rowIsAtLeastATapTall() {
        rule.show(width, fontScale) { ListGroup { ListRow("How it works", 0, 1, onClick = {}) } }
        rule.onNodeWithText("How it works").assertHeightIsAtLeast(48.dp)
    }

    @Test fun pickListMarksOneAndTapPicks() {
        val picks = mutableListOf<String>()
        rule.show(width, fontScale) { PickList(listOf(Choice("B", "Budget left", "₹14.5k left"), Choice("S", "Spent", "₹15.5k spent")), "B", { picks += it }) }
        assertEquals(true, rule.onNodeWithText("Budget left").isSelected())
        assertEquals(false, rule.onNodeWithText("Spent").isSelected())
        rule.onNodeWithText("Spent").performClick()
        assertEquals(listOf("S"), picks)
    }
}
