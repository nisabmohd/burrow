package app.burrow.money

import android.app.Application
import app.burrow.money.notify.runBillReminders
import kotlinx.coroutines.launch

class BurrowApp : Application() {
    /** Created lazily; nothing heavy runs before the first frame. */
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Message text older than 30 days goes (docs/CAPTURE.md § Keep). Off the main thread.
        container.appScope.launch { container.capture.forgetOldText() }
        // Bill reminders: catch up on anything due and set the next alarm.
        container.appScope.launch { container.runBillReminders() }
    }
}
