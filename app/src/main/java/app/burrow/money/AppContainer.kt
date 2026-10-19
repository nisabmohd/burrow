package app.burrow.money

import android.content.Context
import app.burrow.money.capture.RuleEngine
import app.burrow.money.data.BurrowDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the app's long-lived objects: the only dependency "graph" in the app (no DI framework).
 * Everything is lazy so cold start only pays for what the first screen needs.
 * Add repositories here as they are built (see docs/ROADMAP.md).
 */
class AppContainer(private val context: Context) {
    val database by lazy { BurrowDatabase.create(context) }
    val strings: Strings = ResourceStrings(context)
    val rules by lazy { RuleEngine() }
    // val money by lazy { MoneyRepository(database) }          // week 1
    // val settings by lazy { SettingsRepository(context) }     // week 1
    // val bills by lazy { BillRepository(database) }           // week 3
    // val reminders by lazy { ReminderScheduler(context, bills) } // week 3
    // val backup by lazy { BackupManager(database, settings) } // week 5

    /** Lives as long as the process, for work that isn't tied to a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
