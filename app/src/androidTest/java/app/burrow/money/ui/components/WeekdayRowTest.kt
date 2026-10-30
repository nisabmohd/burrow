package app.burrow.money.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The weekday row of "Due every week on" (163): seven circles, one chosen, from the week-start day. */
@RunWith(Parameterized::class)
class WeekdayRowTest(private val widthDp: Float, private val fontScale: Float) {
    // JUnit builds the test by reflection, which can't pass a Dp (a value class): the width comes in as a Float.
    private val width: Dp get() = widthDp.dp

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} at {1}x") fun sizes() = SIZES.map { arrayOf<Any>(it.first.value, it.second) }
    }

    @get:Rule val rule = createComposeRule()

    private fun name(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.getDefault())
    private fun day(d: DayOfWeek) = rule.onNodeWithContentDescription(name(d))

    @Test fun onlyTheChosenDayIsMarkedAndATapPicksAnother() {
        val picks = mutableListOf<DayOfWeek>()
        rule.show(width, fontScale) { WeekdayRow(DayOfWeek.MONDAY, DayOfWeek.MONDAY, { picks += it }) }
        assertTrue(day(DayOfWeek.MONDAY).isSelected())
        DayOfWeek.entries.filter { it != DayOfWeek.MONDAY }.forEach { assertFalse(day(it).isSelected()) }
        day(DayOfWeek.FRIDAY).performClick()
        day(DayOfWeek.MONDAY).performClick() // already chosen: nothing
        rule.runOnIdle { assertEquals(listOf(DayOfWeek.FRIDAY), picks) }
    }

    @Test fun theWeekStartsOnTheChosenDay() {
        rule.show(width, fontScale) { WeekdayRow(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, {}) }
        val lefts = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY).map { day(it).getUnclippedBoundsInRoot().left }
        assertTrue("Sunday first, Saturday last: $lefts", lefts[0] < lefts[1] && lefts[1] < lefts[2])
    }

    @Test fun sevenFitAcrossAndEachIsTallEnoughToTap() {
        rule.show(width, fontScale) { WeekdayRow(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY, {}) }
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        DayOfWeek.entries.forEach { d ->
            day(d).assertHeightIsAtLeast(48.dp).assertNotClipped()
            val b = day(d).getUnclippedBoundsInRoot()
            assertTrue("${name(d)} inside the row", b.left >= root.left - 0.5.dp && b.right <= root.right + 0.5.dp)
        }
    }
}
