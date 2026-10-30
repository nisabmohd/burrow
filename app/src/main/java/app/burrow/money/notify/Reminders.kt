package app.burrow.money.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.burrow.money.AppContainer
import app.burrow.money.BurrowApp
import app.burrow.money.R
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.OnDue
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.budgetAlert
import app.burrow.money.domain.depositValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Something a bill asks for at a time: a reminder before it, or the due day itself. */
private data class BillEvent(val at: Long, val o: OccurrenceView, val dueDay: Boolean)

/** Bill events, all at the one reminder time set in Notifications (160). */
private fun events(list: List<OccurrenceView>, zone: ZoneId, atMinutes: Int): List<BillEvent> = list
    .filter { it.occurrence.status == OccurrenceStatus.UPCOMING }
    .flatMap { o ->
        val due = Instant.ofEpochMilli(o.occurrence.dueDate).atZone(zone).toLocalDate()
        fun at(d: LocalDate) = d.atStartOfDay(zone).plusMinutes(atMinutes.toLong()).toInstant().toEpochMilli()
        listOfNotNull(
            if (o.remindersOn && o.remindDaysBefore > 0) BillEvent(at(due.minusDays(o.remindDaysBefore.toLong())), o, dueDay = false) else null,
            if (o.remindersOn || o.onDue == OnDue.RECORD) BillEvent(at(due), o, dueDay = true) else null,
        )
    }
    .sortedBy { it.at }

/** A deposit with "Remind me a week before": one note at the reminder time, 7 days before it matures. */
internal data class DepositEvent(val at: Long, val deposit: AccountEntity)

internal fun depositEvents(accounts: List<AccountEntity>, zone: ZoneId, atMinutes: Int): List<DepositEvent> = accounts
    .filter { it.type == AccountType.SAVINGS && it.remind && !it.archived && !it.isSample && it.maturityDate != null }
    .map { a ->
        val maturity = Instant.ofEpochMilli(a.maturityDate!!).atZone(zone).toLocalDate()
        DepositEvent(maturity.minusDays(7).atStartOfDay(zone).plusMinutes(atMinutes.toLong()).toInstant().toEpochMilli(), a)
    }

/**
 * Bill reminders (Day 11). Runs what came due since the last run (at app start and when the alarm
 * fires), then sets one inexact alarm for the next event: no WorkManager, no exact-alarm permission
 * (docs/PERFORMANCE.md § Battery). The first ever run only schedules, so nothing old fires at once.
 */
suspend fun AppContainer.runBillReminders(now: Long = System.currentTimeMillis()) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val list = plan.occurrences(today.minusDays(7), today.plusDays(62)).first()
    val all = events(list, zone, settings.settings.first().billReminderMinutes)
    val accounts = money.accounts.first().map { it.account }
    val deposits = depositEvents(accounts, zone, settings.settings.first().billReminderMinutes)
    val last = settings.billsCheckedAt()
    // Upcoming one-offs whose day has come are added, with a quiet note each (115).
    val posted = money.postDueList(now)
    if (last != null && posted.isNotEmpty()) {
        val st = settings.settings.first()
        val fmt = MoneyFormat.of(st.mainCurrency).withHidden(st.hideInNotifications)
        if (st.notifyBills) posted.forEach { t ->
            val name = t.payee ?: money.categories.first().firstOrNull { it.id == t.categoryId }?.name.orEmpty()
            val open = android.content.Intent(context, app.burrow.money.MainActivity::class.java)
                .setAction(app.burrow.money.MainActivity.ACTION_OPEN_TXN).putExtra(app.burrow.money.MainActivity.EXTRA_ID, t.id)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            planNotifier.show(30_000 + (t.id % 1_000_000).toInt(), string(R.string.upcoming_added_title, name, fmt.plain(t.amount)), string(R.string.upcoming_added_line), open)
        }
    }
    if (last != null) {
        val st = settings.settings.first()
        val money = MoneyFormat.of(st.mainCurrency).withHidden(st.hideInNotifications)
        // "Record it" still records with reminders switched off in Notifications; it just stays quiet.
        // Deposit reminders follow the same Bills switch.
        val notifier = if (st.notifyBills) planNotifier else null
        for (e in all.filter { it.at in (last + 1)..now }) {
            val o = e.o
            val amount = money.plain(o.amount)
            val id = 20_000 + (o.occurrence.id % 1_000_000).toInt()
            when {
                !e.dueDay -> notifier?.show(id, if (o.remindDaysBefore == 1) string(R.string.reminder_soon, o.name) else context.resources.getQuantityString(R.plurals.reminder_in_days, o.remindDaysBefore, o.name, o.remindDaysBefore), string(R.string.reminder_line, amount, o.accountName), Notifier.openBill(context, o.occurrence.billId))
                o.onDue == OnDue.RECORD -> {
                    plan.markPaid(o.occurrence.id, e.at)
                    notifier?.show(id, string(R.string.reminder_recorded, o.name), string(R.string.reminder_line, amount, o.accountName), Notifier.openBill(context, o.occurrence.billId))
                }
                else -> notifier?.show(id, string(R.string.reminder_today, o.name), string(R.string.reminder_line, amount, o.accountName), Notifier.openBill(context, o.occurrence.billId))
            }
        }
        for (e in deposits.filter { it.at in (last + 1)..now }) {
            val a = e.deposit
            val zoneDay = { ms: Long -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
            val maturity = zoneDay(a.maturityDate!!)
            var unit = 1L
            repeat(app.burrow.money.data.minorDigits(a.currency)) { unit *= 10 }
            val total = depositValue(
                a.principal ?: 0, a.interestRate?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
                a.startDate?.let(zoneDay) ?: maturity, maturity, maturity, unit,
            )
            val fmt = MoneyFormat.of(a.currency).withHidden(st.hideInNotifications)
            val payout = accounts.firstOrNull { it.id == a.payoutAccountId }?.name
            // "₹1,12,000 goes to HDFC Savings on Mon, 26 Oct" (166).
            val day = app.burrow.money.ui.DateText(context, java.util.Locale.getDefault(), st.dateFormat).weekdayDay(maturity)
            val line = if (payout != null) string(R.string.deposit_reminder_line, fmt.plain(total), payout, day) else string(R.string.deposit_reminder_line_alone, fmt.plain(total), day)
            notifier?.show(60_000 + (a.id % 10_000).toInt(), string(R.string.deposit_reminder, a.name), line, Notifier.openAccounts(context))
        }
    }
    settings.setBillsCheckedAt(now)
    // The next bill event or the next upcoming payment's time, whichever comes first.
    scheduleNext(listOfNotNull(all.firstOrNull { it.at > now }?.at, deposits.filter { it.at > now }.minOfOrNull { it.at }, money.nextUpcoming()).minOrNull())
}

private fun AppContainer.string(id: Int, vararg args: Any) = strings(id, *args)

private fun AppContainer.scheduleNext(at: Long?) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = PendingIntent.getBroadcast(context, 0, Intent(context, BillAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    alarms.cancel(pending)
    if (at != null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
}

/** The bill alarm went off. */
class BillAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runInBackground(context) { runBillReminders() }
}

/** Reboot, app update, clock or time zone change: alarms are gone or wrong, set them again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        runInBackground(context) { runBillReminders(); backup.schedule(); scheduleWeeklyReview() }
    }
}

/** The system broadcasts RescheduleReceiver listens for (AndroidManifest.xml). */
private val RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
)

private fun BroadcastReceiver.runInBackground(context: Context, work: suspend AppContainer.() -> Unit) {
    val container = (context.applicationContext as BurrowApp).container
    val pending = goAsync()
    container.appScope.launch {
        try {
            container.work()
        } finally {
            pending.finish()
        }
    }
}

/**
 * Budget alerts (Day 14): after a saved expense, a budget that crosses 80% or 100% this month says
 * so, once each per month (CLAUDE.md § Behaviour).
 */
suspend fun AppContainer.checkBudgets(saved: TxnEntity, before: TxnEntity?) {
    if (saved.type != TxnType.EXPENSE || saved.isSample) return
    val s = settings.settings.first()
    if (!s.notifyBudgets) return
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(saved.at).atZone(zone).toLocalDate()
    val period = BudgetPeriod.containing(LocalDate.now(zone), s.monthStartDay)
    if (day.isBefore(period.start) || !day.isBefore(period.endExclusive)) return
    val added = saved.amount - (before?.takeIf { it.type == TxnType.EXPENSE }?.amount ?: 0)
    if (added <= 0) return
    val (overall, cats) = plan.budgets(period).first()
    val money = MoneyFormat.of(s.mainCurrency).withHidden(s.hideInNotifications)
    val lines = listOfNotNull(overall) + cats.filter { it.category?.id == saved.categoryId }
    for (line in lines) {
        val level = budgetAlert(line.spent - added, line.spent, line.amount) ?: continue
        val key = "${period.month}:${line.category?.id ?: "overall"}:$level"
        if (settings.budgetAlertSent(key)) continue
        settings.markBudgetAlert(key)
        val name = line.category?.name ?: strings(R.string.budget_overall)
        val title = if (level == 100) strings(R.string.alert_100, name) else strings(R.string.alert_80, name)
        val text = if (line.over) strings(R.string.alert_over_line, money.plain(line.spent - line.amount), money.plain(line.amount))
        else strings(R.string.alert_left_line, money.plain(line.left), money.plain(line.amount))
        planNotifier.show(30_000 + (line.category?.id ?: 0).toInt() * 2 + if (level == 100) 1 else 0, title, text, Notifier.openPlan(context))
    }
}

/**
 * Monthly summary (66, off by default): on the first days of a new month, one notification with
 * last month's spending and what was kept. Sent once per month; runs at app start and with the
 * daily 3 AM check.
 */
suspend fun AppContainer.runMonthlySummary(today: LocalDate = LocalDate.now()) {
    val s = settings.settings.first()
    if (!s.notifySummary) return
    val now = BudgetPeriod.containing(today, s.monthStartDay)
    if (today.toEpochDay() - now.start.toEpochDay() > 2) return
    val last = BudgetPeriod.containing(now.start.minusDays(1), s.monthStartDay)
    val key = "summary:${last.month}"
    if (settings.budgetAlertSent(key)) return
    val zone = ZoneId.systemDefault()
    val txns = money.between(last.start.atStartOfDay(zone).toInstant().toEpochMilli(), last.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()).first()
        .filter { !it.txn.isSample && !it.txn.excludeFromStats }
    val spent = txns.filter { it.txn.type == TxnType.EXPENSE }.sumOf { it.txn.baseAmount }
    val income = txns.filter { it.txn.type == TxnType.INCOME }.sumOf { it.txn.baseAmount }
    settings.markBudgetAlert(key)
    if (spent == 0L && income == 0L) return
    val money = MoneyFormat.of(s.mainCurrency).withHidden(s.hideInNotifications)
    val monthName = last.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    val kept = app.burrow.money.domain.keptPercent(income, spent)
    val line = if (kept != null) strings(R.string.summary_line_kept, money.plain(spent), kept) else strings(R.string.summary_line, money.plain(spent))
    planNotifier.show(40_000, strings(R.string.summary_title, monthName), line, Notifier.openInsights(context))
}

/**
 * Lend or borrow › "Remind me to settle" (77): two weeks on, one notification per person that
 * still owes or is owed. Runs at app start and with the daily check.
 */
suspend fun AppContainer.runSettleReminders(now: Long = System.currentTimeMillis()) {
    val s = settings.settings.first()
    val due = money.people.first().filter { p -> p.person.remindAt?.let { it <= now } == true }
    if (due.isEmpty()) return
    val money0 = MoneyFormat.of(s.mainCurrency).withHidden(s.hideInNotifications)
    for (p in due) {
        money.setPersonReminder(p.person.id, null)
        if (!s.notifyBills) continue
        val line = if (p.balance > 0) strings(R.string.settle_owes_you, money0.plain(p.balance)) else strings(R.string.settle_you_owe, money0.plain(-p.balance))
        planNotifier.show(50_000 + (p.person.id % 10_000).toInt(), strings(R.string.settle_up_with, p.person.name), line, Notifier.openAccounts(context))
    }
}
