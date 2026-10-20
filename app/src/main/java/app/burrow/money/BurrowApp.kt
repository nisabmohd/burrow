package app.burrow.money

import android.app.Application
import android.content.Context
import app.burrow.money.ui.more.AppLanguage
import app.burrow.money.notify.runBillReminders
import app.burrow.money.notify.runMonthlySummary
import app.burrow.money.notify.runSettleReminders
import kotlinx.coroutines.launch

class BurrowApp : Application() {
    /** Created lazily; nothing heavy runs before the first frame. */
    val container by lazy { AppContainer(this) }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    override fun onCreate() {
        super.onCreate()
        // Message text older than 30 days goes (docs/CAPTURE.md § Keep). Off the main thread.
        container.appScope.launch { container.capture.forgetOldText() }
        // Bill reminders: catch up on anything due and set the next alarm.
        container.appScope.launch { container.runBillReminders() }
        // Scheduled backups: set the next daily check.
        container.appScope.launch { container.backup.schedule(); container.runMonthlySummary() }
        // Upcoming transactions whose day has come; attachment files of deleted transactions.
        container.appScope.launch { container.money.postDue(); container.attachments.cleanOrphans(); container.runSettleReminders() }
    }
}
