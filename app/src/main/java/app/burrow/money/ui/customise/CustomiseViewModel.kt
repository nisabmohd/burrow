package app.burrow.money.ui.customise

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.RateEntity
import app.burrow.money.domain.BudgetPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the Customise tabs show besides the settings: the top-card previews, accounts, rates. */
@Immutable
data class CustomiseState(
    val budgetLeft: Long?,
    val spent: Long,
    val netWorth: Long,
    val accounts: List<AccountWithBalance>,
    val rates: List<RateEntity>,
    val favourites: Int,
)

/** Customise (80–85): one screen, five tabs; every change is saved at once. */
class CustomiseViewModel(private val app: AppContainer, monthStartDay: Int) : ViewModel() {
    private val period = BudgetPeriod.containing(LocalDate.now(), monthStartDay)
    private val s = app.settings

    val state: StateFlow<CustomiseState?> = combine(
        combine(app.money.overallBudget(period), app.money.spent(period)) { a, b -> a to b }, app.money.netWorth, app.money.accounts, app.money.rates, app.next.favourites,
    ) { (budget, spent), nw, accounts, rates, favs ->
        CustomiseState(budget?.let { it.amount - spent }, spent, nw.total, accounts, rates, favs.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun go(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    // Today (80)
    fun topCard(v: String) = go { s.setTopCard(v) }
    fun setWidgetOrder(order: List<String>) = go { s.setWidgetOrder(order) }
    fun showWidget(key: String, on: Boolean) = go { s.setHomeWidget(key, on) }
    fun recent(n: Int) = go { s.setRecentCount(n) }
    fun showReview(on: Boolean) = go { s.setShowReview(on) }

    // Expense (81)
    fun extra(key: String, on: Boolean) = go { s.setFormExtra(key, on) }
    fun defaultType(t: String) = go { s.setFormDefaultType(t) }
    fun defaultAccount(id: Long) = go { s.setFormDefaultAccount(id) }

    // Transactions (82)
    fun paymentFound(v: String) = go { s.setPaymentFound(v) }
    fun listOption(key: String, on: Boolean) = go {
        when (key) {
            "time" -> s.setShowTime(on)
            "upcoming" -> s.setCountUpcoming(on)
            "upcomingSafe" -> s.setUpcomingLowersSafe(on)
            "confirm" -> s.setConfirmDelete(on)
        }
    }
    fun swipeRight(v: String) = go { s.setSwipeRight(v) }
    fun swipeLeft(v: String) = go { s.setSwipeLeft(v) }

    // Region (85)
    fun numbers(v: String) = go { s.setNumberFormat(v) }
    fun dates(v: String) = go { s.setDateFormat(v) }
    fun weekStart(v: String) = go { s.setWeekStart(v) }
    fun monthStart(day: Int) = go { s.setMonthStartDay(day) }

    // Currency (83, 84)
    fun display(v: String) = go { s.setCurrencyDisplay(v) }
    fun decimals(v: String) = go { s.setDecimals(v) }
    fun setRate(currency: String, rate: String) = go { app.money.setRate(currency, rate) }
    fun removeRate(currency: String) = go { app.money.removeRate(currency) }
}
