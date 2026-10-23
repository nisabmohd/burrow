package app.burrow.money.ui.plan

import androidx.compose.runtime.Immutable
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import app.burrow.money.AppContainer
import app.burrow.money.domain.BudgetPeriod

/** Where a coming-up item sits in Plan › Coming up (22). */
enum class ComingGroup { OVERDUE, THIS_WEEK, THIS_MONTH, LATER }

/**
 * One row of Coming up: an unpaid bill's due date ([occurrence]) or a one-off payment dated in the
 * future ([txn]). Bills repeat (↻); one-offs don't (📅).
 */
@Immutable
data class ComingItem(
    val key: String,
    val date: LocalDate,
    val name: String,
    val amount: Long,
    val income: Boolean,
    val icon: String?,
    val hue: Float?,
    val accountName: String,
    val group: ComingGroup,
    val occurrence: OccurrenceView? = null,
    val txn: TxnView? = null,
) {
    val repeats get() = occurrence != null
    /** Expenses only lower "safe to spend"; transfers (card bill, EMI) and income don't. */
    val countsAsSpending get() = !income && (txn?.txn?.type ?: TxnType.EXPENSE) == TxnType.EXPENSE &&
        occurrence?.kind.let { it != "EMI" && it != "CARD" }
}

private fun Long.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/**
 * Bills and future-dated payments in one list, soonest first (docs/DESIGN.md § Upcoming): every
 * overdue due date, then each bill's next unpaid date and every one-off payment. This week = today
 * and the 6 days after; this month = the rest of this calendar month; later = after that.
 */
fun comingUp(occurrences: List<OccurrenceView>, txns: List<TxnView>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<ComingItem> {
    val unpaid = occurrences.filter { it.occurrence.status == OccurrenceStatus.UPCOMING }
    val overdue = unpaid.filter { it.occurrence.dueDate.date(zone).isBefore(today) }
    val next = unpaid.filter { !it.occurrence.dueDate.date(zone).isBefore(today) }
        .sortedBy { it.occurrence.dueDate }.distinctBy { it.occurrence.billId }
    val weekEnd = today.plusDays(7)
    val monthEnd = YearMonth.from(today).atEndOfMonth()
    fun group(d: LocalDate) = when {
        d.isBefore(today) -> ComingGroup.OVERDUE
        d.isBefore(weekEnd) -> ComingGroup.THIS_WEEK
        !d.isAfter(monthEnd) -> ComingGroup.THIS_MONTH
        else -> ComingGroup.LATER
    }
    val bills = (overdue + next).map { o ->
        val d = o.occurrence.dueDate.date(zone)
        ComingItem("o${o.occurrence.id}", d, o.name, o.amount, false, o.icon, o.hue, o.accountName, group(d), occurrence = o)
    }
    val once = txns.map { t ->
        val d = t.txn.at.date(zone)
        val name = t.txn.payee ?: t.categoryName ?: t.toAccountName.orEmpty()
        ComingItem("t${t.txn.id}", d, name, t.txn.amount, t.txn.type == TxnType.INCOME, t.categoryIcon ?: t.accountIcon, t.categoryHue ?: t.accountHue, t.accountName, group(d), txn = t)
    }
    return (bills + once).sortedWith(compareBy({ it.date }, { it.key }))
}

/** What still leaves before [monthEnd]: unpaid expenses due by then, overdue ones too (Safe to spend, 88). */
fun dueThisMonth(items: List<ComingItem>, monthEnd: LocalDate): Long =
    items.filter { it.countsAsSpending && !it.date.isAfter(monthEnd) }.sumOf { it.amount }

/** Coming up as a Flow (Plan, Today's row, Activity's row). */
fun AppContainer.comingUpFlow(today: LocalDate): Flow<List<ComingItem>> =
    combine(plan.occurrences(today.minusDays(62), today.plusDays(63)), money.upcoming) { o, t -> comingUp(o, t, today) }

/**
 * What Safe to spend takes off for [period]: unpaid expenses due before it ends, or 0 when Customise ›
 * Transactions › "Coming up lowers safe to spend" is off. Today, the widget and Sunday review share it.
 */
fun comingUpDue(items: List<ComingItem>, period: BudgetPeriod, on: Boolean): Long =
    if (on) dueThisMonth(items, period.endExclusive.minusDays(1)) else 0L

suspend fun AppContainer.comingUpDue(period: BudgetPeriod, today: LocalDate): Long =
    comingUpDue(comingUpFlow(today).first(), period, settings.settings.first().upcomingLowersSafe)
