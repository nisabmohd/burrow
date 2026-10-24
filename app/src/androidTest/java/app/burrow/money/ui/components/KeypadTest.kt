package app.burrow.money.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The New expense keypad: every key sends the right Key, holding ⌫ clears. */
class KeypadTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyKeySendsItsKey() {
        val keys = mutableListOf<Key>()
        rule.show(320.dp) { Keypad({ keys += it }, doneEnabled = true) }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "00", ".").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithContentDescription(string(R.string.key_plus)).performClick()
        rule.onNodeWithContentDescription(string(R.string.key_minus)).performClick()
        rule.onNodeWithContentDescription(string(R.string.key_delete)).performClick()
        rule.onNodeWithContentDescription(string(R.string.save)).performClick()
        assertEquals(
            "1234567890".map { Key.Digit(it) } + listOf(Key.DoubleZero, Key.Dot, Key.Plus, Key.Minus, Key.Backspace, Key.Done),
            keys,
        )
    }

    @Test fun holdingBackspaceClears() {
        val keys = mutableListOf<Key>()
        rule.show { Keypad({ keys += it }, doneEnabled = false) }
        rule.onNodeWithContentDescription(string(R.string.key_delete)).performTouchInput { longClick() }
        assertEquals(listOf<Key>(Key.Clear), keys)
    }

    @Test fun doneStillTapsWhileItLooksOff() {
        val keys = mutableListOf<Key>()
        rule.show { Keypad({ keys += it }, doneEnabled = false) }
        rule.onNodeWithContentDescription(string(R.string.save)).performClick()
        assertEquals(listOf<Key>(Key.Done), keys)
    }

    @Test fun noDotForCurrenciesWithoutDecimals() {
        rule.show { Keypad({}, doneEnabled = true, showDot = false) }
        rule.onNodeWithText(".").assertDoesNotExist()
    }

    @Test fun keysAreBigEnoughEvenAtLargeText() {
        rule.show(320.dp, 2f) { Keypad({}, doneEnabled = true) }
        for (k in listOf("1", "5", "0", "00")) rule.onNodeWithText(k).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("00", useUnmergedTree = true).assertNotClipped()
    }
}
