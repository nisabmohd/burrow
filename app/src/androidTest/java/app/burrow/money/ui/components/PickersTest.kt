package app.burrow.money.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Swatches (goal / account / category icons, colours) and toggle tiles. */
class PickersTest {
    @get:Rule val rule = createComposeRule()
    private val icons = listOf("flight", "shield", "laptop_mac", "home", "school", "redeem", "directions_car", "flag")

    @Test fun eightGoalIconsAllShowOnASmallPhone() {
        // 2026-10-24: eight 48 dp circles in one row on a 360 dp phone: the last one was cut off.
        rule.show(320.dp) {
            SwatchGrid(icons, columns = 4) { i -> Swatch(i == "flag", i.replace('_', ' '), {}) { Box(androidx.compose.ui.Modifier) } }
        }
        for (i in icons) rule.onNodeWithContentDescription(i.replace('_', ' ')).assertIsDisplayed().assertNotClipped()
    }

    @Test fun swatchesDontTouch() {
        rule.show(320.dp) { SwatchGrid(icons, columns = 4) { i -> Swatch(false, i, {}) { } } }
        for (a in 0 until 3) assertNoOverlap(rule.onNodeWithContentDescription(icons[a]), rule.onNodeWithContentDescription(icons[a + 1]))
    }

    @Test fun tappingASwatchPicksItAndOnlyOneIsSelected() {
        rule.show {
            var sel by remember { mutableStateOf("flag") }
            SwatchGrid(icons, columns = 4) { i -> Swatch(i == sel, i, { sel = i }) { } }
        }
        rule.onNodeWithContentDescription("home").performClick()
        assertTrue(rule.onNodeWithContentDescription("home").isSelected())
        rule.onAllNodesWithContentDescription("flag").assertCountEquals(1)
        assertEquals(false, rule.onNodeWithContentDescription("flag").isSelected())
    }

    @Test fun toggleTilesFlipAndSayOnOrOff() {
        val flips = mutableListOf<Pair<String, Boolean>>()
        rule.show(320.dp, 1.3f) {
            val icon = painterResource(R.drawable.ic_check_regular)
            ToggleTiles(listOf(Toggle("note", "Note", icon, true), Toggle("time", "Time", icon, false), Toggle("lend", "Lend or borrow", icon, false)), { k, on -> flips += k to on }, "On", "Off")
        }
        rule.onNodeWithText("Note").performClick()
        rule.onNodeWithText("Time").performClick()
        assertEquals(listOf("note" to false, "time" to true), flips)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On")).assertIsDisplayed()
        rule.onNodeWithText("Lend or borrow", useUnmergedTree = true).assertTextFits()
        rule.onNodeWithText("Note").assertHeightIsAtLeast(48.dp)
    }
}
