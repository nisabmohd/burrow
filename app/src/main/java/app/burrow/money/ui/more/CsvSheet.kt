package app.burrow.money.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Export or import CSV (More): export writes every transaction to a file the user names; import
 * reads one (date, type, payee, amount, currency, category, account, note) and adds the rows.
 */
@Composable
fun CsvSheet(app: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val exported = stringResource(R.string.csv_exported)
    val failed = stringResource(R.string.csv_failed)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch {
            val ok = runCatching {
                val text = app.csv.export()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream") }
            }.isSuccess
            snackbar.show(if (ok) exported else failed)
        }
        onDismiss()
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch {
            val added = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: error("no stream") }
                app.csv.import(text)
            }.getOrNull()
            snackbar.show(if (added == null) failed else context.resources.getQuantityString(R.plurals.csv_imported, added, added))
        }
        onDismiss()
    }
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.csv), onDismiss, stringResource(R.string.csv_sheet_line))
        SheetBody {
            ListGroup {
                ListRow(
                    stringResource(R.string.csv_export), 0, 2, supporting = stringResource(R.string.csv_export_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_ios_share_duotone)) },
                    onClick = { scope.launch { export.launch("burrow-${LocalDate.now()}.csv") } },
                )
                ListRow(
                    stringResource(R.string.csv_import), 1, 2, supporting = stringResource(R.string.csv_import_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_download_duotone)) },
                    onClick = { import.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
                )
            }
        }
    }
}
