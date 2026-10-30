package app.burrow.money

import android.app.Application
import android.content.Context
import app.burrow.money.ui.more.AppLanguage
import app.burrow.money.notify.runBillReminders
import app.burrow.money.notify.runMonthlySummary
import app.burrow.money.notify.runSettleReminders
import app.burrow.money.notify.scheduleWeeklyReview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

class BurrowApp : Application() {
    /** Created lazily; nothing heavy runs before the first frame. */
    val container by lazy { AppContainer(this) }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    override fun onCreate() {
        super.onCreate()
        // Before anything reads the settings: the region's currency comes from the SIM country.
        app.burrow.money.data.Region.detect(this)
        // Message text older than 30 days goes (docs/CAPTURE.md § Keep). Off the main thread.
        container.appScope.launch { container.capture.forgetOldText() }
        // Bill reminders: catch up on anything due and set the next alarm.
        container.appScope.launch { container.runBillReminders() }
        // Scheduled backups: set the next daily check.
        container.appScope.launch { container.backup.schedule(); container.runMonthlySummary() }
        // Attachment files of deleted transactions (upcoming payments post in runBillReminders).
        container.appScope.launch { container.attachments.cleanOrphans(); container.next.cleanOrphans(); container.runSettleReminders() }
        // Sunday review (13): the next Sunday 6 PM alarm. Home-screen widget and shortcuts: fresh numbers.
        container.appScope.launch { container.scheduleWeeklyReview(); app.burrow.money.home.HomeSurfaces.refresh(container) }
        // The widgets and shortcuts follow every change wherever it comes from (a bill, a budget, an
        // account, Hide amounts, the currency), not only saved transactions. Bursts redraw once.
        container.appScope.launch { followChanges() }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private suspend fun followChanges() {
        val tables = arrayOf(
            "TxnEntity", "BillEntity", "BillOccurrenceEntity", "BudgetEntity", "AccountEntity", "CategoryEntity",
            "CaptureEntity", "GoalEntity", "SplitEntity",
        )
        kotlinx.coroutines.flow.merge(
            container.database.invalidationTracker.createFlow(*tables, emitInitialState = false).map { },
            container.settings.settings.distinctUntilChanged().drop(1).map { },
        ).debounce(CHANGE_SETTLE_MS).collect { app.burrow.money.home.HomeSurfaces.refresh(container) }
    }

    private companion object {
        const val CHANGE_SETTLE_MS = 250L
    }
}
