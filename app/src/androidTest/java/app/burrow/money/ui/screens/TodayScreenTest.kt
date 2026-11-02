package app.burrow.money.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.burrow.money.R
import app.burrow.money.data.TopCard
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.ui.today.HeroState
import app.burrow.money.ui.today.TodayContent
import app.burrow.money.ui.today.TodayUiState
import app.burrow.money.ui.today.Widgets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Today (04–10) from a made-up state: what shows, and where each tap goes. */
class TodayScreenTest {
    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val today = LocalDate.now()

    private fun coffee(id: Long = 7) = TxnView(
        TxnEntity(
            id = id, type = TxnType.EXPENSE, amount = 240_00, currency = "INR", baseAmount = 240_00, accountId = 1, categoryId = 1,
            payee = "Third Wave Coffee", at = now, createdAt = now, updatedAt = now,
        ),
        categoryName = "Food & drinks", categoryIcon = "restaurant", categoryHue = 30f,
        accountName = "HDFC Savings", accountIcon = "account_balance", accountHue = 215f,
        toAccountName = null, toAccountIcon = null, toAccountHue = null,
    )

    private fun state(
        recent: List<TxnView> = listOf(coffee()),
        isEmpty: Boolean = false,
        hasSamples: Boolean = false,
        toReview: Int = 0,
        notUnderstood: Int = 0,
        sms: Boolean = false,
    ) = TodayUiState(
        hero = HeroState(BudgetPeriod(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)), daysLeft = 10, spent = 12_000_00, budget = 30_000_00),
        recent = recent, hasSamples = hasSamples, isEmpty = isEmpty, toReview = toReview, notUnderstood = notUnderstood,
        smsCapture = sms, appCapture = false, shown = emptySet(),
        widgets = Widgets(emptyList(), null, 0, emptyList(), 0, null, 0, emptyList(), null, null, 0, 0),
        topCard = TopCard.BUDGET, order = emptyList(), showReview = true, balance = 0, showTime = true,
    )

    private var opened = mutableListOf<Long>()
    private var calls = mutableListOf<String>()

    private fun show(s: TodayUiState?) = rule.showScreen {
        TodayContent(
            s, onAdd = { calls += "add" }, onAccounts = { calls += "accounts" }, onOpen = { opened += it }, onSeeAll = { calls += "all" },
            onReview = { calls += "review" }, onMissed = { calls += "missed" }, onCaptureIntro = { calls += "intro" },
            onFixCapture = { calls += "fix" }, onBill = {}, onCategory = {}, onGoal = {}, onComingUp = { calls += "coming" },
            scrollToTop = 0, onRemoveSamples = { calls += "samples" }, onMarkPaid = { _, _ -> },
        )
    }

    @Test fun nothingUnderTheHeaderUntilTheDataIsIn() {
        // No "₹0" flash: the header only, while the state is still null.
        show(null)
        rule.onNodeWithText(text(R.string.tab_today)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.recent)).assertDoesNotExist()
        rule.onNodeWithText("₹0", substring = true).assertDoesNotExist()
    }

    @Test fun recentRowOpensItsTransactionAndSeeAllGoesToActivity() {
        show(state())
        rule.onNodeWithText("Third Wave Coffee").performClick()
        assertEquals(listOf(7L), opened)
        rule.onNodeWithText(text(R.string.see_all)).performClick()
        assertEquals(listOf("all"), calls)
    }

    @Test fun emptyShowsOnlyTheEmptyStateAndOffersCapture() {
        show(state(recent = emptyList(), isEmpty = true))
        rule.onNodeWithText(text(R.string.empty_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.recent)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.record_for_me)).performClick()
        assertEquals(listOf("intro"), calls)
    }

    @Test fun paymentsWaitingInToReviewShowEvenWithNoTransactions() {
        show(state(recent = emptyList(), isEmpty = true, toReview = 2))
        rule.onNodeWithText(text(R.string.empty_title)).assertDoesNotExist()
        rule.onNodeWithText(plural(R.plurals.n_to_review, 2, 2)).performClick()
        assertEquals(listOf("review"), calls)
    }

    @Test fun samplesNoteComesFirstAndRemoveClearsThem() {
        show(state(hasSamples = true))
        rule.onNodeWithText(text(R.string.sample_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.remove)).performClick()
        assertEquals(listOf("samples"), calls)
    }

    @Test fun toReviewNoteOpensToReview() {
        show(state(toReview = 3, notUnderstood = 2))
        rule.onNodeWithText(plural(R.plurals.n_to_review, 3, 3)).performClick()
        assertEquals(listOf("review"), calls)
        // One note at a time: To review wins over Not understood.
        rule.onNodeWithText(plural(R.plurals.n_not_understood, 2, 2)).assertDoesNotExist()
    }

    @Test fun notUnderstoodNoteShowsWhenNothingWaitsInToReview() {
        show(state(notUnderstood = 2))
        rule.onNodeWithText(plural(R.plurals.n_not_understood, 2, 2)).performClick()
        assertEquals(listOf("missed"), calls)
    }
}
