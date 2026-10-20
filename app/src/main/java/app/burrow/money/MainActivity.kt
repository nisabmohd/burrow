package app.burrow.money

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.ui.nav.BurrowNavHost
import app.burrow.money.ui.nav.OpenFromNotification
import app.burrow.money.ui.theme.BurrowTheme
import app.burrow.money.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        /** From the capture notification: open To review. */
        const val ACTION_OPEN_REVIEW = "app.burrow.money.action.OPEN_REVIEW"
        /** From a bill reminder: open that bill ([EXTRA_ID]). */
        const val ACTION_OPEN_BILL = "app.burrow.money.action.OPEN_BILL"
        /** From a budget alert: open Plan › Budgets. */
        const val ACTION_OPEN_PLAN = "app.burrow.money.action.OPEN_PLAN"
        const val EXTRA_ID = "id"
    }

    /** Set when a notification was tapped; the nav host goes there and clears it. */
    private val pending = MutableStateFlow<OpenFromNotification?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as BurrowApp).container
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
            val money = remember(current.mainCurrency, locale) { MoneyFormat.of(current.mainCurrency, locale) }
            BurrowTheme(current.theme) {
                CompositionLocalProvider(LocalMoney provides money) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val open by pending.collectAsStateWithLifecycle()
                        BurrowNavHost(container, current, open, onOpened = { pending.value = null })
                    }
                }
            }
        }
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
            else -> pending.value
        }
    }
}
