package app.burrow.money.ui.filters

import androidx.compose.runtime.Immutable
import app.burrow.money.data.FilterKey
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.DayRange
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId

/** Activity's Type filter (13). Insights has none: it is always spending. */
enum class TypeFilter { ALL, SPENT, INCOME, TRANSFERS }

/**
 * What the Filter and Dates sheets chose (docs/DESIGN.md rule 7). Empty sets mean "all".
 * [range] null means all dates.
 */
@Immutable
data class Filters(
    val type: TypeFilter = TypeFilter.ALL,
    val accounts: Set<Long> = emptySet(),
    val categories: Set<Long> = emptySet(),
    val range: DayRange? = null,
) {
    /** The funnel's badge: one per chip under the search bar, dates not counted. */
    val count get() = (if (type != TypeFilter.ALL) 1 else 0) + accounts.size + categories.size
    val isEmpty get() = count == 0 && range == null

    fun matches(t: TxnView, zone: ZoneId = ZoneId.systemDefault(), withRange: Boolean = true): Boolean =
        t.txn.let { x -> matches(x.type, x.accountId, x.toAccountId, x.categoryId, x.at, zone, withRange) }

    /** The same test on a [FilterKey]: the Filter sheet counts every row without loading them all. */
    fun matches(k: FilterKey, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        matches(k.type, k.accountId, k.toAccountId, k.categoryId, k.at, zone, withRange = true)

    private fun matches(txnType: String, accountId: Long, toAccountId: Long?, categoryId: Long?, at: Long, zone: ZoneId, withRange: Boolean): Boolean {
        val typeOk = when (type) {
            TypeFilter.ALL -> true
            TypeFilter.SPENT -> txnType == TxnType.EXPENSE || txnType == TxnType.REFUND
            TypeFilter.INCOME -> txnType == TxnType.INCOME
            TypeFilter.TRANSFERS -> txnType == TxnType.TRANSFER
        }
        if (!typeOk) return false
        if (accounts.isNotEmpty() && accountId !in accounts && toAccountId !in accounts) return false
        if (categories.isNotEmpty() && categoryId !in categories) return false
        if (withRange && range != null && Instant.ofEpochMilli(at).atZone(zone).toLocalDate() !in range) return false
        return true
    }
}

/** Filters stay while the app is open and reset on restart (CLAUDE.md § Behaviour): held in memory only. */
class FilterStore {
    val activity = MutableStateFlow(Filters())
    val insights = MutableStateFlow(Filters())
    /** The Search shortcut (02): Activity focuses its search field once. */
    val focusSearch = MutableStateFlow(false)
}
