package app.burrow.money.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Buttons, icon buttons, switch, text field. */
class ControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun offButtonStillTapsSoTheFormCanSayWhatIsMissing() {
        var taps = 0
        rule.show { BurrowButton("Save", { taps++ }, kind = ButtonKind.OFF) }
        rule.onNodeWithText("Save").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, taps)
    }

    @Test fun buttonLabelFitsAtLargeText() {
        rule.show(320.dp, 2f) { BurrowButton("Open release page", {}) }
        rule.onNodeWithText("Open release page", useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
    }

    @Test fun iconButtonIs48dpWithItsLabelAndBadgeOnlyAboveZero() {
        rule.show {
            androidx.compose.foundation.layout.Row {
                TonalIconButton(painterResource(R.drawable.ic_check_regular), "Filter", {}, badge = 2)
                TonalIconButton(painterResource(R.drawable.ic_check_regular), "Dates", {}, badge = 0)
            }
        }
        rule.onNodeWithContentDescription("Filter").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("0").assertDoesNotExist()
    }

    @Test fun switchFlips() {
        rule.show {
            var on by remember { mutableStateOf(false) }
            BurrowSwitch(on, { on = it })
        }
        val sw = rule.onNode(isToggleable())
        sw.assertIsOff().performClick()
        sw.assertIsOn()
    }

    @Test fun tallTextFieldWrapsLongText() {
        val long = "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184"
        rule.show(320.dp) { PillTextField(long, {}, "Paste the bank message here", lines = 3) }
        rule.onNodeWithText(long).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
    }

    @Test fun textFieldTypesShowsPlaceholderAndClears() {
        rule.show {
            var v by remember { mutableStateOf("") }
            PillTextField(v, { v = it }, "Paid to", clearLabel = "Clear", keyboardType = KeyboardType.Text)
        }
        rule.onNodeWithText("Paid to").assertIsDisplayed()
        rule.onNodeWithText("Paid to").performTextInput("Chai")
        rule.onNodeWithText("Chai").assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.onNodeWithText("Chai").assertDoesNotExist()
        rule.onNodeWithText("Paid to").assertIsDisplayed()
    }
}
