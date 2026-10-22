package app.burrow.money.ui.more

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.Settings
import app.burrow.money.domain.BudgetPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class MoreState(
    val settings: Settings,
    val capturedThisMonth: Int,
    val categories: Int,
    val accounts: Int,
    val netWorth: Long,
    /** Currencies with a typed-in rate (Currency row: "₹ primary · 3 others"). */
    val otherCurrencies: Int,
    /** "92% recorded by itself" on the Auto-capture tile (15). */
    val trustPercent: Int? = null,
)

/** More (53, 54). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MoreViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<MoreState?> = combine(
        app.settings.settings,
        app.settings.settings.flatMapLatest { st ->
            combine(app.capture.savedThisMonth(st.monthStartDay), app.next.trust(BudgetPeriod.containing(LocalDate.now(), st.monthStartDay))) { a, b -> a to b.percent }
        },
        app.money.categories,
        app.money.netWorth,
        app.money.rates,
    ) { s, (captured, trust), cats, nw, rates ->
        MoreState(s, captured, cats.count { it.kind == "EXPENSE" }, nw.count, nw.total, rates.count { it.currency != s.mainCurrency }, trust)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setHideAmounts(on: Boolean) = viewModelScope.launch { app.settings.setHideAmounts(on) }
    fun setMonthStart(day: Int) = viewModelScope.launch { app.settings.setMonthStartDay(day) }

    /** Delete all data: tables, settings (the welcome stays done), then defaults without the samples. */
    fun deleteAll(onDone: () -> Unit) = viewModelScope.launch {
        app.money.deleteAll()
        app.settings.clearAllButOnboarding()
        onDone()
    }
}

@Immutable
data class CategoryLine(val category: CategoryEntity, val spent: Long, val percent: Int)

@Immutable
data class CategoriesState(val spending: List<CategoryLine>, val income: List<CategoryLine>, val total: Long, val count: Int)

/** Categories (60, 61): sorted by what was spent this month. */
class CategoriesViewModel(app: AppContainer, monthStartDay: Int) : ViewModel() {
    private val period = BudgetPeriod.containing(LocalDate.now(), monthStartDay)
    val state: StateFlow<CategoriesState?> = combine(app.money.categories, app.money.spentByCategory(period)) { cats, spent ->
        val total = spent.values.sum()
        fun line(c: CategoryEntity) = CategoryLine(c, spent[c.id] ?: 0, if (total > 0) Math.round((spent[c.id] ?: 0) * 100f / total) else 0)
        CategoriesState(
            cats.filter { it.kind == CategoryKind.EXPENSE }.map(::line).sortedWith(compareByDescending<CategoryLine> { it.spent }.thenBy { it.category.sortOrder }),
            cats.filter { it.kind == CategoryKind.INCOME }.map(::line),
            total, cats.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Immutable
data class CategoryForm(val name: String = "", val hue: Float = 28f, val icon: String = "category", val kind: String = CategoryKind.EXPENSE, val tried: Boolean = false)

/** New / edit category (62). */
class EditCategoryViewModel(private val app: AppContainer, val id: Long) : ViewModel() {
    val form = MutableStateFlow<CategoryForm?>(null)
    private var original: CategoryEntity? = null

    init {
        viewModelScope.launch {
            val c = app.money.categories.first().firstOrNull { it.id == id }
            original = c
            form.value = c?.let { CategoryForm(it.name, it.colorHue, it.icon, it.kind) } ?: CategoryForm()
        }
    }

    fun edit(change: (CategoryForm) -> CategoryForm) = form.update { it?.let(change) }

    fun save(onDone: () -> Unit) {
        val f = form.value ?: return
        if (f.name.isBlank()) {
            edit { it.copy(tried = true) }
            return
        }
        viewModelScope.launch {
            app.money.saveCategory(
                (original ?: CategoryEntity(name = "", kind = f.kind, icon = f.icon, colorHue = f.hue, sortOrder = 90))
                    .copy(name = f.name.trim(), icon = f.icon, colorHue = f.hue),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        app.money.deleteCategory(id)
        onDone()
    }
}
