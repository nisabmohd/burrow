package app.burrow.money

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
import app.burrow.money.ui.theme.BurrowTheme
import app.burrow.money.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                        BurrowNavHost(container, current)
                    }
                }
            }
        }
    }
}
