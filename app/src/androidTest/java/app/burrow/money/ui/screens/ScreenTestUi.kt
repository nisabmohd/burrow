package app.burrow.money.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.UndoSnackbar
import app.burrow.money.ui.theme.BurrowTheme

/*
 * Screen-level tests: a whole screen drawn from a state made up in the test, no database, no view
 * model. Each screen has a `…Content(state, callbacks)` beside the `…Screen(vm)` the app uses.
 */

/** A screen in Burrow's theme, with the Undo snackbar every screen expects. */
fun ComposeContentTestRule.showScreen(content: @Composable () -> Unit) = setContent {
    val scope = rememberCoroutineScope()
    val snackbar = remember { UndoSnackbar(scope, "Undo") }
    CompositionLocalProvider(LocalSnackbar provides snackbar) { BurrowTheme { content() } }
}

private val res get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

fun text(id: Int, vararg args: Any): String = res.getString(id, *args)

fun plural(id: Int, n: Int, vararg args: Any): String = res.getQuantityString(id, n, *args)
