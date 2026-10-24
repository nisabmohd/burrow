package app.burrow.money

import android.content.Context
import app.burrow.money.capture.RuleEngine
import app.burrow.money.capture.CaptureNotifier
import app.burrow.money.data.BurrowDatabase
import app.burrow.money.data.CaptureRepository
import app.burrow.money.data.PlanRepository
import app.burrow.money.notify.Notifier
import app.burrow.money.notify.checkBudgets
import app.burrow.money.data.MoneyRepository
import app.burrow.money.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the app's long-lived objects: the only dependency "graph" in the app (no DI framework).
 * Everything is lazy so cold start only pays for what the first screen needs.
 * Add repositories here as they are built (see docs/ROADMAP.md).
 */
class AppContainer(val context: Context) {
    val database by lazy { BurrowDatabase.create(context) }
    val strings: Strings = ResourceStrings(context)
    val rules by lazy { RuleEngine() }
    val settings by lazy { SettingsRepository(context) }
    val money: MoneyRepository by lazy {
        MoneyRepository(database, settings, strings).also { repo ->
            // A captured payment may pay a due bill; any expense may cross a budget line.
            repo.afterSave = { saved, before ->
                if (saved.source == "SMS" || saved.source == "NOTIFICATION") plan.settleFromPayment(saved)
                checkBudgets(saved, before)
            }
            // The home-screen widget and the Review shortcut follow every change.
            repo.changed = { app.burrow.money.home.HomeSurfaces.refresh(this) }
        }
    }
    val plan: PlanRepository by lazy { PlanRepository(database, money, strings) }
    /** Bill reminders and budget alerts (the capture notification is [notifier]). */
    val planNotifier by lazy { Notifier(context) }
    val capture by lazy { CaptureRepository(context, database, settings, money, rules) }
    val notifier by lazy { CaptureNotifier(context) }
    val backup by lazy { app.burrow.money.data.BackupManager(context, database, settings) }
    val csv by lazy { app.burrow.money.data.CsvTransfer(money, database) }
    val attachments by lazy { app.burrow.money.data.Attachments(context, database) }
    /** Splits, refunds, favourites, import and trust in capture (burrow-next). */
    val next by lazy { app.burrow.money.data.NextRepository(database, money) }
    /** GitHub Releases update check; does nothing outside the github build. */
    val updates by lazy { app.burrow.money.data.update.UpdateManager(context, settings) }

    /** Activity's and Insights' filters: kept while the app is open, gone on restart. */
    val filters = app.burrow.money.ui.filters.FilterStore()

    /** Lives as long as the process, for work that isn't tied to a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
