package app.burrow.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.burrow.money.ui.theme.BurrowTheme
import app.burrow.money.ui.theme.NavMotion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Week 1: read ThemeSettings from SettingsRepository as state.
            BurrowTheme { Surface(Modifier.fillMaxSize()) { BurrowNav() } }
        }
    }
}

/**
 * One NavHost, transitions from Motion.kt only (docs/MOTION.md):
 * push/pop = shared axis X, tabs = fade through, predictive back is on in the manifest.
 * Routes become @Serializable objects as screens are built (type-safe navigation).
 */
@Composable
fun BurrowNav() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "today",
        enterTransition = NavMotion.push,
        exitTransition = NavMotion.pushExit,
        popEnterTransition = NavMotion.pop,
        popExitTransition = NavMotion.popExit,
    ) {
        composable("today") { /* TodayScreen(...) — design/screens/04-today.png */ }
    }
}
