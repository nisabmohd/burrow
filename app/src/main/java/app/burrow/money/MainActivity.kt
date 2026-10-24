package app.burrow.money

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.ui.LocalMoney
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.ui.nav.BurrowNavHost
import app.burrow.money.ui.nav.OpenFromNotification
import app.burrow.money.ui.theme.BurrowTheme
import app.burrow.money.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import app.burrow.money.domain.PinHash
import app.burrow.money.ui.more.AppLanguage
import app.burrow.money.ui.more.PinMode
import app.burrow.money.ui.more.PinScreen
import app.burrow.money.ui.LocalRevealAmounts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    companion object {
        /** From the capture notification: open To review. */
        const val ACTION_OPEN_REVIEW = "app.burrow.money.action.OPEN_REVIEW"
        /** From a bill reminder: open that bill ([EXTRA_ID]). */
        const val ACTION_OPEN_BILL = "app.burrow.money.action.OPEN_BILL"
        /** From a budget alert: open Plan › Budgets. */
        const val ACTION_OPEN_PLAN = "app.burrow.money.action.OPEN_PLAN"
        /** From the monthly summary: open Insights. */
        const val ACTION_OPEN_INSIGHTS = "app.burrow.money.action.OPEN_INSIGHTS"
        /** "Open it to edit first": New expense filled in from a found payment ([EXTRA_ID]). */
        const val ACTION_OPEN_CAPTURE = "app.burrow.money.action.OPEN_CAPTURE"
        /** A payment saved for you: open it ([EXTRA_ID]). */
        const val ACTION_OPEN_TXN = "app.burrow.money.action.OPEN_TXN"
        /** Settle-up reminder: open Accounts (Lent and borrowed). */
        const val ACTION_OPEN_ACCOUNTS = "app.burrow.money.action.OPEN_ACCOUNTS"
        /** Widget, shortcut, quick settings tile: New expense / New income. */
        const val ACTION_NEW_EXPENSE = "app.burrow.money.action.NEW_EXPENSE"
        const val ACTION_NEW_INCOME = "app.burrow.money.action.NEW_INCOME"
        /** Shortcut: Activity with the search field focused. */
        const val ACTION_SEARCH = "app.burrow.money.action.SEARCH"
        /** Sunday review notification (13). */
        const val ACTION_SUNDAY_REVIEW = "app.burrow.money.action.SUNDAY_REVIEW"
        const val EXTRA_ID = "id"
        private const val KEY_LOCKED = "locked"
    }

    /** Set when a notification was tapped; the nav host goes there and clears it. */
    private val pending = MutableStateFlow<OpenFromNotification?>(null)

    /** App lock (70): true while the PIN pad covers the app. Starts locked when the lock is on. */
    private val locked = MutableStateFlow<Boolean?>(null)
    /** When the app went to the background, for "Lock after". */
    private var stoppedAt = 0L

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as BurrowApp).container
        if (locked.value == null) lifecycleScope.launch { locked.value = savedInstanceState?.getBoolean(KEY_LOCKED) ?: container.settings.settings.first().appLock }
        setContent {
            // Null until DataStore has loaded, so the first real frame already has the right theme.
            // Until then an empty frame over the window background (page colour, light or night).
            val settings by container.settings.settings.collectAsStateWithLifecycle(null)
            val current = settings ?: run {
                Spacer(Modifier.fillMaxSize())
                return@setContent
            }
            val dark = current.theme.isDark()
            // Status and nav bar icons follow the app's theme, not just the system's.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.Transparent.toArgb())
                } else {
                    SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val locale = LocalConfiguration.current.locales[0]
            // Hide amounts (More): every amount shows as "₹ ••••" until tapped to reveal (Today's hero).
            var revealed by rememberSaveable(current.hideAmounts) { mutableStateOf(false) }
            val hidden = current.hideAmounts && !revealed
            val money = remember(current.mainCurrency, locale, hidden, current.numberFormat, current.currencyDisplay, current.decimals) {
                MoneyFormat.of(current.mainCurrency, locale, current.numberFormat, current.currencyDisplay, current.decimals).withHidden(hidden)
            }
            val listPrefs = remember(current.showTime, current.confirmDelete, current.swipeRight, current.swipeLeft) {
                app.burrow.money.ui.ListPrefs(current.showTime, current.confirmDelete, current.swipeRight, current.swipeLeft)
            }
            val datePrefs = remember(current.dateFormat, current.weekStart) {
                app.burrow.money.ui.DatePrefs(current.dateFormat, runCatching { java.time.DayOfWeek.valueOf(current.weekStart) }.getOrDefault(java.time.DayOfWeek.MONDAY))
            }
            val isLocked by locked.collectAsStateWithLifecycle()
            val view = androidx.compose.ui.platform.LocalView.current
            val haptics = remember(view) { app.burrow.money.ui.components.BurrowHapticFeedback(view) }
            BurrowTheme(current.theme) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides haptics, LocalMoney provides money, app.burrow.money.ui.LocalDatePrefs provides datePrefs, app.burrow.money.ui.LocalListPrefs provides listPrefs, LocalRevealAmounts provides (if (current.hideAmounts) ({ revealed = !revealed }) else null)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val open by pending.collectAsStateWithLifecycle()
                        // The app stays composed under the PIN pad (so unlocking returns to the same place),
                        // hidden from TalkBack and covered by the pad's opaque page.
                        if (isLocked != null) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                                androidx.compose.foundation.layout.Box(
                                    if (isLocked == true) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize(),
                                ) { BurrowNavHost(container, current, open, onOpened = { pending.value = null }) }
                                if (isLocked == true) PinScreen(
                                    PinMode.UNLOCK,
                                    check = { pin -> container.settings.pinHash()?.let { PinHash.matches(pin, it) } == true },
                                    onUnlocked = { locked.value = false },
                                    onPin = {},
                                    onClose = null,
                                )
                                // GitHub build: once a day, after first run and never over the PIN pad.
                                if (isLocked == false && current.onboarded) app.burrow.money.ui.update.UpdatePrompt(container.updates)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - stoppedAt
        val container = (application as BurrowApp).container
        lifecycleScope.launch {
            val s = container.settings.settings.first()
            if (s.appLock && away >= s.lockAfterSeconds * 1_000L) locked.value = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        locked.value?.let { outState.putBoolean(KEY_LOCKED, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        pending.value = when (intent?.action) {
            ACTION_OPEN_REVIEW -> OpenFromNotification.Review
            ACTION_OPEN_BILL -> OpenFromNotification.Bill(intent.getLongExtra(EXTRA_ID, 0L))
            ACTION_OPEN_PLAN -> OpenFromNotification.Budgets
            ACTION_OPEN_INSIGHTS -> OpenFromNotification.Insights
            ACTION_OPEN_CAPTURE -> intent.getLongExtra(EXTRA_ID, 0L).let { id ->
                // Edit on the capture notification: the notification goes once New expense opens.
                (application as BurrowApp).container.notifier.cancel(id)
                OpenFromNotification.EditCapture(id)
            }
            ACTION_OPEN_TXN -> OpenFromNotification.Txn(intent.getLongExtra(EXTRA_ID, 0L))
            ACTION_OPEN_ACCOUNTS -> OpenFromNotification.Accounts
            ACTION_NEW_EXPENSE -> OpenFromNotification.NewTxn(app.burrow.money.data.TxnType.EXPENSE)
            ACTION_NEW_INCOME -> OpenFromNotification.NewTxn(app.burrow.money.data.TxnType.INCOME)
            ACTION_SEARCH -> OpenFromNotification.Search
            ACTION_SUNDAY_REVIEW -> OpenFromNotification.SundayReview
            else -> pending.value
        }
    }
}
