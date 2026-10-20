package app.burrow.money.ui.activity

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.data.minorDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day in Activity: its header total (transfers left out) and its transactions. */
@Immutable
data class DayGroup(val date: LocalDate, val total: Long, val txns: List<TxnView>)

@Immutable
data class ActivityUiState(val query: String, val groups: List<DayGroup>, val isEmpty: Boolean)

/**
 * Activity (11): every transaction, newest first, grouped by day, searchable by payee, note,
 * category, account or amount. Grouping and search run off the main thread.
 */
class ActivityViewModel(repo: MoneyRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val searchText: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<ActivityUiState?> = combine(repo.latest(), query) { all, q ->
        val zone = ZoneId.systemDefault()
        val hits = if (q.isBlank()) all else all.filter { it.matches(q.trim()) }
        val groups = hits.groupBy { Instant.ofEpochMilli(it.txn.at).atZone(zone).toLocalDate() }
            .map { (date, txns) -> DayGroup(date, txns.sumOf { it.signedAmount() }, txns) }
        ActivityUiState(q, groups, isEmpty = all.isEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun search(text: String) {
        query.value = text
    }
}

/** Money out is negative, in is positive; a transfer moves money without changing the total. */
fun TxnView.signedAmount(): Long = when (txn.type) {
    TxnType.EXPENSE -> -txn.amount
    TxnType.INCOME -> txn.amount
    else -> 0
}

/** Payee, note, category, account, or the amount's digits ("240" finds ₹240 and ₹1,240). */
private fun TxnView.matches(q: String): Boolean {
    val text = listOfNotNull(txn.payee, txn.note, categoryName, accountName, toAccountName)
    if (text.any { it.contains(q, ignoreCase = true) }) return true
    val digits = q.filter { it.isDigit() }
    return digits.isNotEmpty() && digits.length == q.count { it.isDigit() || it == ',' || it == '.' || it == '₹' } &&
        wholeUnits(txn.amount, txn.currency).toString().contains(digits)
}

private fun wholeUnits(minor: Long, currency: String): Long {
    var unit = 1L
    repeat(minorDigits(currency)) { unit *= 10 }
    return minor / unit
}
