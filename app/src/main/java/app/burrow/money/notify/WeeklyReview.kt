package app.burrow.money.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import app.burrow.money.AppContainer
import app.burrow.money.BurrowApp
import app.burrow.money.R
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.TxnStatus
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.DayRange
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.nextReviewAt
import app.burrow.money.domain.reviewWeek
import app.burrow.money.domain.safeToSpendToday
import app.burrow.money.ui.plan.comingUpDue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Sunday review (13, 14): the week's spending against the week before, payments to review,
 * bills due in the next 7 days, what is left this month and safe today, and the category most was
 * spent on. The notification and the screen read the same numbers.
 */
@Immutable
data class WeeklySummary(
    val week: DayRange,
    val spent: Long,
    val weekBefore: Long,
    val toReview: Int,
    val bills: List<OccurrenceView>,
    val budget: Long?,
    val left: Long,
    val safeToday: Long,
    val month: java.time.YearMonth,
    val topCategory: CategoryEntity?,
    val topCategoryTotal: Long,
)

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun TxnView.day(): LocalDate = Instant.ofEpochMilli(txn.at).atZone(zone).toLocalDate()

/** Pure: builds the summary from what the app already has ([stats] has splits and refunds unfolded). */
fun weeklySummary(
    today: LocalDate,
    weekStart: java.time.DayOfWeek,
    monthStartDay: Int,
    stats: List<TxnView>,
    toReview: Int,
    occurrences: List<OccurrenceView>,
    budget: Long?,
    monthSpent: Long,
    spentToday: Long,
    digits: Int,
    comingUp: Long = 0,
): WeeklySummary {
    val week = reviewWeek(today, weekStart)
    val before = DayRange(week.start.minusDays(7), week.start.minusDays(1))
    val spending = stats.filter { it.txn.type == TxnType.EXPENSE && !it.txn.excludeFromStats && it.txn.status == TxnStatus.POSTED && !it.txn.isSample }
    val thisWeek = spending.filter { it.day() in week }
    val top = thisWeek.filter { it.txn.categoryId != null }.groupBy { it.txn.categoryId!! }
        .mapValues { (_, l) -> l.sumOf { it.txn.amount } }.maxByOrNull { it.value }?.takeIf { it.value > 0 }
    val topView = top?.let { (id, _) -> thisWeek.first { it.txn.categoryId == id } }
    val period = BudgetPeriod.containing(today, monthStartDay)
    val left = (budget ?: 0) - monthSpent
    val due = occurrences.filter {
        it.occurrence.status == OccurrenceStatus.UPCOMING &&
            !Instant.ofEpochMilli(it.occurrence.dueDate).atZone(zone).toLocalDate().isAfter(today.plusDays(6))
    }.sortedBy { it.occurrence.dueDate }
    return WeeklySummary(
        week = week,
        spent = thisWeek.sumOf { it.txn.amount }.coerceAtLeast(0),
        weekBefore = spending.filter { it.day() in before }.sumOf { it.txn.amount }.coerceAtLeast(0),
        toReview = toReview,
        bills = due,
        budget = budget,
        left = left,
        safeToday = if (budget == null) 0 else safeToSpendToday(left, spentToday, period.daysLeft(today), digits, comingUp),
        month = period.month,
        topCategory = topView?.let { CategoryEntity(it.txn.categoryId!!, it.categoryName.orEmpty(), kind = "EXPENSE", icon = it.categoryIcon.orEmpty(), colorHue = it.categoryHue ?: 0f) },
        topCategoryTotal = top?.value ?: 0,
    )
}

/** The summary as a Flow, for the Sunday review screen. */
fun AppContainer.weeklySummaryFlow(today: LocalDate = LocalDate.now()): Flow<WeeklySummary> {
    val settingsFlow = settings.settings
    return combine(
        combine(next.stats, capture.reviewCount, plan.occurrences(today.minusDays(31), today.plusDays(7))) { a, b, c -> Triple(a, b, c) },
        settingsFlow,
        next.spentToday(today),
    ) { (stats, review, occ), s, spentToday ->
        val period = BudgetPeriod.containing(today, s.monthStartDay)
        val budget = money.overallBudget(period).first()?.amount
        val monthSpent = money.spent(period).first()
        weeklySummary(
            today, runCatching { java.time.DayOfWeek.valueOf(s.weekStart) }.getOrDefault(java.time.DayOfWeek.MONDAY), s.monthStartDay,
            stats, review, occ, budget, monthSpent, spentToday, MoneyFormat.of(s.mainCurrency).digits,
            comingUpDue(period, today),
        )
    }
}

/**
 * Sunday 6 PM: "Your week: ₹4,738 spent" · "3 to review · 2 bills due · ₹13.6k left. Takes 30 seconds."
 * Once a week, only with Notifications › Sunday review on, never for an empty week.
 */
suspend fun AppContainer.runWeeklyReview(now: LocalDateTime = LocalDateTime.now()) {
    val s = settings.settings.first()
    scheduleWeeklyReview(now)
    if (!s.notifyWeekly || now.dayOfWeek != java.time.DayOfWeek.SUNDAY || now.hour < REVIEW_HOUR) return
    val key = "weekly:${now.toLocalDate()}"
    if (settings.budgetAlertSent(key)) return
    val sum = weeklySummaryFlow(now.toLocalDate()).first()
    settings.markBudgetAlert(key)
    if (sum.spent == 0L && sum.toReview == 0 && sum.bills.isEmpty()) return
    val money = MoneyFormat.of(s.mainCurrency).withHidden(s.hideInNotifications)
    val parts = listOfNotNull(
        sum.toReview.takeIf { it > 0 }?.let { strings(R.string.weekly_to_review, it) },
        sum.bills.size.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.weekly_bills_due, it, it) },
        sum.budget?.let { strings(R.string.weekly_left, money.short(sum.left.coerceAtLeast(0))) },
    )
    val line = (parts.joinToString(" · ").takeIf { it.isNotEmpty() }?.let { "$it. " } ?: "") + strings(R.string.weekly_takes_30s)
    planNotifier.show(60_000, strings(R.string.weekly_title, money.plain(sum.spent)), line, Notifier.openSundayReview(context), strings(R.string.sunday_review))
}

/** Sets one inexact alarm for the next Sunday 6 PM (no exact-alarm permission). */
fun AppContainer.scheduleWeeklyReview(now: LocalDateTime = LocalDateTime.now()) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = PendingIntent.getBroadcast(context, 3, Intent(context, WeeklyReviewReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    alarms.cancel(pending)
    val at = nextReviewAt(now, REVIEW_HOUR).atZone(zone).toInstant().toEpochMilli()
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
}

private const val REVIEW_HOUR = 18

/** The Sunday alarm went off (or the app was asked to check, from the smoke test). */
class WeeklyReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.runWeeklyReview()
            } finally {
                pending.finish()
            }
        }
    }
}
