package app.burrow.money.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.burrow.money.AppContainer
import app.burrow.money.BurrowApp
import app.burrow.money.R
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.data.OccurrenceView
import app.burrow.money.data.OnDue
import app.burrow.money.data.TxnEntity
import app.burrow.money.data.TxnType
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.budgetAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Something a bill asks for at a time: a reminder before it, or the due day itself. */
private data class BillEvent(val at: Long, val o: OccurrenceView, val dueDay: Boolean)

private fun events(list: List<OccurrenceView>, zone: ZoneId): List<BillEvent> = list
    .filter { it.occurrence.status == OccurrenceStatus.UPCOMING }
    .flatMap { o ->
        val due = Instant.ofEpochMilli(o.occurrence.dueDate).atZone(zone).toLocalDate()
        fun at(d: LocalDate) = d.atStartOfDay(zone).plusMinutes(o.remindAtMinutes.toLong()).toInstant().toEpochMilli()
        listOfNotNull(
            if (o.remindersOn && o.remindDaysBefore > 0) BillEvent(at(due.minusDays(o.remindDaysBefore.toLong())), o, dueDay = false) else null,
            if (o.remindersOn || o.onDue == OnDue.RECORD) BillEvent(at(due), o, dueDay = true) else null,
        )
    }
    .sortedBy { it.at }

/**
 * Bill reminders (Day 11). Runs what came due since the last run (at app start and when the alarm
 * fires), then sets one inexact alarm for the next event: no WorkManager, no exact-alarm permission
 * (docs/PERFORMANCE.md § Battery). The first ever run only schedules, so nothing old fires at once.
 */
suspend fun AppContainer.runBillReminders(now: Long = System.currentTimeMillis()) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val list = plan.occurrences(today.minusDays(7), today.plusDays(62)).first()
    val all = events(list, zone)
    val last = settings.billsCheckedAt()
    if (last != null) {
        val money = MoneyFormat.of(settings.settings.first().mainCurrency)
        for (e in all.filter { it.at in (last + 1)..now }) {
            val o = e.o
            val amount = money.plain(o.amount)
            val id = 20_000 + (o.occurrence.id % 1_000_000).toInt()
            when {
                !e.dueDay -> planNotifier.show(id, if (o.remindDaysBefore == 1) string(R.string.reminder_soon, o.name) else context.resources.getQuantityString(R.plurals.reminder_in_days, o.remindDaysBefore, o.name, o.remindDaysBefore), string(R.string.reminder_line, amount, o.accountName), Notifier.openBill(context, o.occurrence.billId))
                o.onDue == OnDue.RECORD -> {
                    plan.markPaid(o.occurrence.id, e.at)
                    planNotifier.show(id, string(R.string.reminder_recorded, o.name), string(R.string.reminder_line, amount, o.accountName), Notifier.openBill(context, o.occurrence.billId))
                }
                else -> planNotifier.show(id, string(R.string.reminder_today, o.name), string(R.string.reminder_line, amount, o.accountName), Notifier.openBill(context, o.occurrence.billId))
            }
        }
    }
    settings.setBillsCheckedAt(now)
    scheduleNext(all.firstOrNull { it.at > now }?.at)
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
    override fun onReceive(context: Context, intent: Intent) = runInBackground(context) { runBillReminders() }
}

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
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(saved.at).atZone(zone).toLocalDate()
    val period = BudgetPeriod.containing(LocalDate.now(zone), s.monthStartDay)
    if (day.isBefore(period.start) || !day.isBefore(period.endExclusive)) return
    val added = saved.amount - (before?.takeIf { it.type == TxnType.EXPENSE }?.amount ?: 0)
    if (added <= 0) return
    val (overall, cats) = plan.budgets(period).first()
    val money = MoneyFormat.of(s.mainCurrency)
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
