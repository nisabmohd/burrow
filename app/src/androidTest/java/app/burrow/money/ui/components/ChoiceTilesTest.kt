package app.burrow.money.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Customise › Currency "Decimal places", "Show amounts as"; Transactions "When a payment is found". */
@RunWith(Parameterized::class)
class ChoiceTilesTest(private val widthDp: Float, private val fontScale: Float) {
    // JUnit builds the test by reflection, which can't pass a Dp (a value class): the width comes in as a Float.
    private val width: Dp get() = widthDp.dp

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first.value, it.second) }
    }

    @get:Rule val rule = createComposeRule()
    private val decimals = listOf(Choice("AUTO", "Auto", "1,240.5"), Choice("NONE", "None", "1,240"), Choice("ALWAYS", "Always", "1,240.00"))

    @Test fun valuesFitInAThirdOfTheScreen() {
        // 2026-10-24: "1,240...." in the Always tile, and the check covering "1,240.5".
        rule.show(width, fontScale) { ChoiceTiles(decimals, "AUTO", {}) }
        for (c in decimals) rule.onNodeWithText(c.line!!, useUnmergedTree = true).assertIsDisplayed().assertTextFits()
    }

    @Test fun titleAndValueNeverOverlap() {
        rule.show(width, fontScale) { ChoiceTiles(decimals, "AUTO", {}) }
        for (c in decimals) assertNoOverlap(rule.onNodeWithText(c.title, useUnmergedTree = true), rule.onNodeWithText(c.line!!, useUnmergedTree = true))
    }

    @Test fun textStaysInsideItsTile() {
        rule.show(width, fontScale) { ChoiceTiles(decimals, "ALWAYS", {}) }
        for (c in decimals) {
            val tile = rule.onNodeWithText(c.title)
            assertInside(rule.onNodeWithText(c.line!!, useUnmergedTree = true), tile)
            assertInside(rule.onNodeWithText(c.title, useUnmergedTree = true), tile)
        }
    }

    @Test fun onlyTheSelectedTileIsSelectedAndTapPicks() {
        val picks = mutableListOf<String>()
        rule.show(width, fontScale) { ChoiceTiles(decimals, "NONE", { picks += it }) }
        assertTrue(rule.onNodeWithText("None").isSelected())
        assertFalse(rule.onNodeWithText("Auto").isSelected())
        rule.onNodeWithText("Always").performClick()
        assertEquals(listOf("ALWAYS"), picks)
    }

    @Test fun tilesInARowAreTheSameHeight() {
        rule.show(width, fontScale) { ChoiceTiles(decimals, "AUTO", {}) }
        val heights = decimals.map { rule.onNodeWithText(it.title).getUnclippedBoundsInRoot().let { r -> (r.bottom - r.top).value } }
        assertEquals(heights.max(), heights.min(), 1f)
    }

    @Test fun iconRowsShowTitleLineAndPick() {
        val picks = mutableListOf<String>()
        rule.show(width, fontScale) {
            val icon = painterResource(R.drawable.ic_check_regular)
            ChoiceTiles(
                listOf(Choice("ASK", "Ask on the notification", "One tap saves it", icon), Choice("SAVE", "Save it for me", "Uses the usual category", icon)),
                "ASK", { picks += it },
            )
        }
        rule.onNodeWithText("Uses the usual category", useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
        assertTrue(rule.onNodeWithText("Ask on the notification").isSelected())
        rule.onNodeWithText("Save it for me").performClick()
        assertEquals(listOf("SAVE"), picks)
    }
}
