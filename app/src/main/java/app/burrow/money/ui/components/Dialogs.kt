package app.burrow.money.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes

/**
 * The only dialog (CLAUDE.md: dialogs only confirm delete, replace data, wrong password).
 * 24 dp surfaceContainer card, icon, `title`, one paragraph, text buttons.
 */
@Composable
fun ConfirmDialog(
    icon: Painter,
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title, style = BurrowType.title) },
        text = { Text(text, style = BurrowType.body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, style = BurrowType.button, color = c.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, style = BurrowType.button, color = c.primary) } },
        shape = Shapes.surface,
        containerColor = c.surfaceContainer,
        iconContentColor = c.primary,
        titleContentColor = c.onSurface,
        textContentColor = c.onSurfaceVariant,
    )
}
