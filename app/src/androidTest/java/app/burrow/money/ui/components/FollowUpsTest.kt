package app.burrow.money.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.burrow.money.R
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.domain.Repeat
import app.burrow.money.ui.DateText
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.pickers.DateSheet
import app.burrow.money.ui.pickers.LocalNewCategory
import app.burrow.money.ui.theme.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** The design follow-ups of 2026-10-30: date sheet (78, 176, 164), grouped category sheet (167), Edit · Save (44), swipe (171–173). */
class FollowUpsTest {
    @get:Rule val rule = createComposeRule()

    private val dates = DateText(InstrumentationRegistry.getInstrumentation().targetContext, Locale.getDefault())
    private val today = LocalDate.now()

    // Date sheet (78, 176)

    @Test fun aDayChipPicksWithoutClosingAndDoneSavesIt() {
        var picked: LocalDate? = null
        rule.show { DateSheet(today, { picked = it }, {}, anyDay = true, quick = true, line = string(R.string.when_you_paid)) }
        rule.onNodeWithText(string(R.string.when_you_paid)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.yesterday)).performClick()
        rule.runOnIdle { assertNull("a chip only picks; Done saves", picked) }
        assertTrue(rule.onNodeWithText(string(R.string.yesterday)).isSelected())
        rule.onNodeWithText(string(R.string.done)).performClick()
        rule.runOnIdle { assertEquals(today.minusDays(1), picked) }
    }

    @Test fun aFutureDaySaysWhenItIsAdded() {
        rule.show { DateSheet(today, {}, {}, anyDay = true, quick = true, line = string(R.string.when_you_paid)) }
        rule.onNodeWithText(string(R.string.tomorrow)).performClick()
        rule.onNodeWithText(string(R.string.upcoming_added_on, dates.weekdayDay(today.plusDays(1)))).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.when_you_paid)).assertDoesNotExist()
    }

    @Test fun theTimeRowShowsTheTimeAndAnUnchangedTimeIsNotSaved() {
        var time: LocalTime? = null
        var picked: LocalDate? = null
        rule.show { DateSheet(today, { picked = it }, {}, anyDay = true, quick = true, time = LocalTime.of(9, 30), onTime = { time = it }) }
        rule.onNodeWithText(dates.clock(LocalTime.of(9, 30))).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.time)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.done)).performClick()
        rule.runOnIdle { assertEquals(today, picked); assertNull(time) }
    }

    @Test fun theTimeRowOpensTheWheel() {
        rule.show { DateSheet(today, {}, {}, anyDay = true, quick = true, time = LocalTime.of(9, 30)) }
        rule.onNodeWithText(dates.clock(LocalTime.of(9, 30))).performClick()
        // The wheel replaces the calendar: its AM/PM column shows, the day chips are gone.
        rule.onNodeWithText(string(R.string.am)).assertExists()
        rule.onNodeWithText(string(R.string.yesterday)).assertDoesNotExist()
    }

    @Test fun withoutATimeThereIsNoTimeRow() {
        rule.show { DateSheet(today, {}, {}) }
        rule.onNodeWithText(string(R.string.time)).assertDoesNotExist()
    }

    // Due every year on (164)

    @Test fun theYearlySheetShowsTheMonthAloneAndTheNextDate() {
        val start = LocalDate.of(2027, 1, 9)
        rule.show { DateSheet(start, {}, {}, title = string(R.string.due_every_year_on), anyDay = true, yearly = true) }
        rule.onNodeWithText(dates.month(start)).assertIsDisplayed()
        rule.onNodeWithText(dates.monthYear(start)).assertDoesNotExist()
        val next = Repeat.Yearly(1, 9).nextOnOrAfter(today)
        rule.onNodeWithText(string(R.string.next_on, dates.fullDay(next))).assertIsDisplayed()
    }

    // Category sheet (167)

    private fun cat(id: Long, name: String, kind: String) = CategoryEntity(id = id, name = name, kind = kind, icon = "category", colorHue = 0f)

    @Test fun bothKindsAreGroupedExpenseFirstThenNewCategory() {
        var newCategory = 0
        val cats = listOf(cat(1, "Salary", CategoryKind.INCOME), cat(2, "Groceries", CategoryKind.EXPENSE), cat(3, "Interest", CategoryKind.INCOME))
        rule.show {
            CompositionLocalProvider(LocalNewCategory provides { newCategory++ }) { CategorySheet(cats, emptyMap(), null, {}, {}) }
        }
        val top = { text: String -> rule.onNodeWithText(text).getUnclippedBoundsInRoot().top }
        val order = listOf(string(R.string.type_expense), "Groceries", string(R.string.type_income), "Salary", "Interest", string(R.string.new_category))
        val tops = order.map(top)
        assertEquals("in order: $order", tops.sorted(), tops)
        rule.onNodeWithText(string(R.string.new_category)).performClick()
        rule.runOnIdle { assertEquals(1, newCategory) }
    }

    @Test fun oneKindHasNoHeadersAndNoNewCategoryWithoutAWayThere() {
        rule.show { CategorySheet(listOf(cat(1, "Groceries", CategoryKind.EXPENSE), cat(2, "Food", CategoryKind.EXPENSE)), emptyMap(), 2, {}, {}) }
        rule.onNodeWithText(string(R.string.type_expense)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.new_category)).assertDoesNotExist()
        assertTrue(rule.onNodeWithText("Food").isSelected())
    }

    // To review card: Edit · Save (44)

    private fun reviewCard(fontScale: Float, onEdit: () -> Unit, onSave: () -> Unit) = rule.show(360.dp, fontScale) {
        MessageCard(
            lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
            title = "Chaayos", line = "HDFC Savings · 8:05 AM", amount = "−₹180",
            ignoreLabel = "Ignore", onIgnore = {},
            button = "Save", buttonIcon = painterResource(R.drawable.ic_check_regular), onButton = onSave,
            second = "Edit", secondIcon = painterResource(R.drawable.ic_edit_regular), onSecond = onEdit,
        ) { MessageBox("HDFC Bank", "Rs.180.00 debited from A/c XX1234 to VPA chaayos.paytm@hdfc.", emptyList()) }
    }

    @Test fun editAndSaveAreTwoEqualButtonsEditFirst() {
        var edits = 0; var saves = 0
        reviewCard(1f, { edits++ }, { saves++ })
        val edit = rule.onNodeWithText("Edit").getUnclippedBoundsInRoot()
        val save = rule.onNodeWithText("Save").getUnclippedBoundsInRoot()
        assertEquals("same row", edit.top.value, save.top.value, 1f)
        assertTrue("Edit first", edit.right <= save.left)
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle { assertEquals(1, edits); assertEquals(1, saves) }
    }

    @Test fun editAndSaveStackAtLargeText() {
        reviewCard(2f, {}, {})
        val edit = rule.onNodeWithText("Edit").getUnclippedBoundsInRoot()
        val save = rule.onNodeWithText("Save").getUnclippedBoundsInRoot()
        assertTrue("Edit above Save", edit.bottom <= save.top)
        // The pill grows with the text (48 dp is the least), so the label is whole inside it.
        // (assertTextFits can't be used here: inside a Material Button the layout reports the full row width.)
        rule.onNodeWithText("Save").assertHeightIsAtLeast(48.dp)
        val label = rule.onNodeWithText("Save", useUnmergedTree = true)
        label.assertNotClipped()
        assertInside(label, rule.onNodeWithText("Save"))
    }

    // Swipe (171–173)

    @Test fun theLabelShowsOnlyOnceArmedAndLettingGoThenRunsIt() {
        var paid = 0
        rule.show {
            SwipeRow(SwipeAction(null, "Mark paid", primary = true, showLabel = true) { paid++ }, null, Shapes.surface) {
                Text("Netflix", Modifier.fillMaxWidth().height(72.dp))
            }
        }
        val row = rule.onNodeWithText("Netflix")
        val density = rule.density
        row.performTouchInput { down(centerLeft + Offset(8f, 0f)); moveBy(Offset(with(density) { 40.dp.toPx() }, 0f)) }
        rule.onNodeWithText("Mark paid").assertDoesNotExist()
        row.performTouchInput { moveBy(Offset(with(density) { 160.dp.toPx() }, 0f)) }
        rule.onNodeWithText("Mark paid").assertExists()
        row.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, paid) }
    }

    @Test fun aShortSwipeSpringsBackAndDoesNothing() {
        var paid = 0
        rule.show {
            SwipeRow(SwipeAction(null, "Mark paid", primary = true, showLabel = true) { paid++ }, null, Shapes.surface) {
                Text("Netflix", Modifier.fillMaxWidth().height(72.dp))
            }
        }
        val density = rule.density
        rule.onNodeWithText("Netflix").performTouchInput { down(centerLeft + Offset(8f, 0f)); moveBy(Offset(with(density) { 50.dp.toPx() }, 0f)); up() }
        rule.runOnIdle { assertEquals(0, paid) }
    }
}
