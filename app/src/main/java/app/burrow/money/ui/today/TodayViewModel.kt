package app.burrow.money.ui.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.CaptureRepository
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.SettingsRepository
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.perDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The budget block: [budget] is null when none is set. */
@Immutable
data class HeroState(val period: BudgetPeriod, val daysLeft: Int, val spent: Long, val budget: Long?, val digits: Int = 2) {
    val left get() = (budget ?: 0) - spent
    val over get() = budget != null && left < 0
    val perDay get() = perDay(left, daysLeft, digits)
    val progress get() = if (budget == null || budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)
}

@Immutable
data class TodayUiState(
    val hero: HeroState,
    val recent: List<TxnView>,
    val hasSamples: Boolean,
    /** No transactions at all: Today shows only the empty state. */
    val isEmpty: Boolean,
    val toReview: Int,
    val smsCapture: Boolean,
    val appCapture: Boolean,
)

/** Today v1 (04, 06, 09): the budget hero, the sample note, Recent. Widgets come on Day 19. */
class TodayViewModel(private val repo: MoneyRepository, capture: CaptureRepository, settings: SettingsRepository, monthStartDay: Int, private val digits: Int) : ViewModel() {
    private val today = LocalDate.now()
    private val period = BudgetPeriod.containing(today, monthStartDay)

    private val money = combine(
        repo.spent(period),
        repo.overallBudget(period),
        repo.latest(RECENT),
        repo.hasSamples,
        repo.txnCount,
    ) { spent, budget, recent, samples, count ->
        TodayUiState(HeroState(period, period.daysLeft(today), spent, budget?.amount, digits), recent, samples, count == 0, 0, false, false)
    }

    val state: StateFlow<TodayUiState?> = combine(money, capture.reviewCount, settings.settings) { m, review, s ->
        m.copy(toReview = review, smsCapture = s.smsCapture, appCapture = s.notificationCapture)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun removeSamples() {
        viewModelScope.launch { repo.removeSamples() }
    }

    private companion object {
        const val RECENT = 4
    }
}
