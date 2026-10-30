package app.burrow.money.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Activity's chart card: the header row is 24 dp, so whatever sits in it must fit. */
@RunWith(Parameterized::class)
class ChartCardTest(private val widthDp: Float, private val fontScale: Float) {
    // JUnit builds the test by reflection, which can't pass a Dp (a value class): the width comes in as a Float.
    private val width: Dp get() = widthDp.dp

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first.value, it.second) }
    }

    @get:Rule val rule = createComposeRule()

    @Test fun allDaysInTheHeaderIsFullyVisibleAndTappable() {
        // 2026-10-24: a TextButton in the 24 dp header row showed only the top half of "All days".
        var taps = 0
        rule.show(width, fontScale) {
            ChartCard(painterResource(R.drawable.ic_check_regular), "Last 14 days", "₹789", "spent on Mon, Oct 12 · 4 transactions", trailing = {
                ChartHeaderLink("All days") { taps++ }
            }) { }
        }
        rule.onNodeWithText("All days", useUnmergedTree = true).assertIsDisplayed().assertNotClipped()
        rule.onNodeWithText("All days").performClick()
        assertEquals(1, taps)
    }

    @Test fun valueAndLineDontOverlapTheHeader() {
        rule.show(width, fontScale) { ChartCard(painterResource(R.drawable.ic_check_regular), "Last 14 days", "₹10,607", "spent · 25 transactions", trailing = { AverageLegend("₹758 a day") }) { } }
        val label = rule.onNodeWithText("Last 14 days", useUnmergedTree = true)
        val value = rule.onNodeWithText("₹10,607", useUnmergedTree = true).assertNotClipped()
        assertNoOverlap(label, value)
        assertNoOverlap(value, rule.onNodeWithText("spent · 25 transactions", useUnmergedTree = true))
        rule.onNodeWithText("₹758 a day", useUnmergedTree = true).assertNotClipped()
    }
}
