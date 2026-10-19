package app.burrow.money.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.burrow.money.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's one snackbar (CLAUDE.md § Behaviour: Undo after every change, about 4 s). Screens call
 * [show]; the undo runs in [scope] (the app's scope), so it still works after the screen has closed.
 * The FAB moves up while one shows ([visible]).
 */
@Stable
class UndoSnackbar(private val scope: CoroutineScope, private val undoLabel: String) {
    val host = SnackbarHostState()
    val visible: Boolean get() = host.currentSnackbarData != null

    fun show(message: String, undo: (suspend () -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = if (undo != null) undoLabel else null, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf<UndoSnackbar> { error("No UndoSnackbar provided") }

/** 24 dp snackbar (one-shape rule), inverse colours, Undo in the light primary tone. Text is Material's bodyMedium = BurrowType.body. */
@Composable
fun BurrowSnackbarHost(state: UndoSnackbar, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    SnackbarHost(state.host, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = Shapes.surface,
            containerColor = c.inverseSurface,
            contentColor = c.inverseOnSurface,
            actionColor = c.primaryContainer,
        )
    }
}
