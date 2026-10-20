package app.burrow.money.ui.more

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.ui.theme.Space
import androidx.compose.foundation.layout.padding
import app.burrow.money.data.BackupData
import app.burrow.money.data.BackupFile
import app.burrow.money.data.Settings
import app.burrow.money.domain.NotABackup
import app.burrow.money.domain.WrongPassword
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import kotlinx.coroutines.launch

private enum class BackupSheet { PASSWORD, SCHEDULE }

/**
 * Backup and restore (69): Back up now; the folder, the password, the schedule, keep last 7;
 * the backups in the folder and Restore from another file. Restoring asks for the password if the
 * saved one doesn't open it (wrong → dialog) and confirms before replacing everything.
 */
@Composable
fun BackupScreen(app: AppContainer, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val dates = rememberDateText()
    var sheet by rememberSaveable { mutableStateOf<BackupSheet?>(null) }
    var files by remember { mutableStateOf<List<BackupFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val restore = rememberRestoreFlow(app) { files = app.backup.list() }
    LaunchedEffect(settings.backupFolder, settings.lastBackupAt) { files = app.backup.list() }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        scope.launch { app.settings.setBackupFolder(uri.toString()) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(restore) }

    DetailFrame(onBack, stringResource(R.string.backup_and_restore)) {
        val ready = settings.backupFolder != null && settings.hasBackupPassword
        ListGroup {
            ListRow(
                stringResource(R.string.back_up_now), 0, 1,
                supporting = when {
                    settings.backupFolder == null -> stringResource(R.string.pick_folder_first)
                    !settings.hasBackupPassword -> stringResource(R.string.set_password_first)
                    settings.lastBackupAt != null -> stringResource(R.string.last_backup, dates.dayTime(settings.lastBackupAt))
                    else -> stringResource(R.string.never)
                },
                lead = { IconCircle(painterResource(R.drawable.ic_cloud_done_duotone)) },
                onClick = {
                    when {
                        settings.backupFolder == null -> pickFolder.launch(null)
                        !settings.hasBackupPassword -> sheet = BackupSheet.PASSWORD
                        !busy -> scope.launch {
                            busy = true
                            runCatching { app.backup.backUpNow() }
                                .onSuccess { snackbar.show(context.getString(R.string.backup_done)) }
                                .onFailure { snackbar.show(context.getString(R.string.backup_failed)) }
                            busy = false
                        }
                    }
                },
            )
        }
        SectionHeader(stringResource(R.string.settings_section))
        ListGroup {
            ListRow(
                settings.backupFolder?.let { app.backup.folderLabel(it) } ?: stringResource(R.string.pick_a_folder), 0, 4,
                supporting = stringResource(R.string.folder), lead = { IconCircle(painterResource(R.drawable.ic_folder_duotone)) },
                onClick = { pickFolder.launch(null) },
            )
            ListRow(
                stringResource(if (settings.hasBackupPassword) R.string.password_set else R.string.set_password), 1, 4,
                supporting = stringResource(R.string.needed_to_restore), lead = { IconCircle(painterResource(R.drawable.ic_key_duotone)) },
                onClick = { sheet = BackupSheet.PASSWORD },
            )
            ListRow(
                scheduleLabel(settings.backupSchedule), 2, 4, supporting = stringResource(R.string.automatic_backup),
                lead = { IconCircle(painterResource(R.drawable.ic_schedule_duotone)) }, onClick = { sheet = BackupSheet.SCHEDULE },
            )
            ListRow(
                stringResource(R.string.keep_last_7), 3, 4, supporting = stringResource(R.string.keep_last_7_line),
                lead = { IconCircle(painterResource(R.drawable.ic_history_duotone)) },
            )
        }
        SectionHeader(stringResource(R.string.restore))
        ListGroup {
            val n = files.size + 1
            files.forEachIndexed { i, f ->
                ListRow(
                    f.date?.let { dates.weekdayDay(it) } ?: f.name, i, n,
                    supporting = f.txns?.let { pluralStringResource(R.plurals.n_transactions, it, it) },
                    lead = { IconCircle(painterResource(R.drawable.ic_settings_backup_restore_duotone)) },
                    onClick = { restore(f.uri) },
                )
            }
            ListRow(
                stringResource(R.string.restore_from_file), files.size, n,
                lead = { IconCircle(painterResource(R.drawable.ic_upload_file_duotone)) },
                onClick = { pickFile.launch(arrayOf("*/*")) },
            )
        }
        if (!ready) Text(stringResource(R.string.backup_explain), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.grid).padding(top = Space.block))
    }

    when (sheet) {
        BackupSheet.PASSWORD -> PasswordSheet(stringResource(R.string.backup_password), stringResource(R.string.backup_password_line), confirm = true, onDismiss = { sheet = null }) { pw ->
            sheet = null
            scope.launch { app.backup.setPassword(pw); snackbar.show(context.getString(R.string.password_saved)) }
        }
        BackupSheet.SCHEDULE -> BurrowSheet({ sheet = null }) {
            SheetHeader(stringResource(R.string.automatic_backup), { sheet = null })
            SheetBody {
                val opts = listOf("OFF", "DAILY", "WEEKLY")
                ListGroup {
                    opts.forEachIndexed { i, o ->
                        ListRow(scheduleLabel(o), i, opts.size, selected = settings.backupSchedule == o, onClick = {
                            sheet = null
                            scope.launch { app.settings.setBackupSchedule(o); app.backup.schedule() }
                        })
                    }
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun scheduleLabel(s: String) = stringResource(when (s) { "DAILY" -> R.string.daily; "WEEKLY" -> R.string.weekly; else -> R.string.off })

/**
 * Restore from a file: try the saved password, else ask; a wrong one shows the dialog; then confirm
 * before replacing everything. Returns the function that starts it for a file.
 */
@Composable
fun rememberRestoreFlow(app: AppContainer, onRestored: suspend () -> Unit = {}): (Uri) -> Unit {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<ByteArray?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<BackupData?>(null) }

    suspend fun tryOpen(bytes: ByteArray, password: String?): Boolean {
        if (password == null) return false
        return try {
            ready = app.backup.open(bytes, password); true
        } catch (_: WrongPassword) {
            false
        } catch (_: NotABackup) {
            snackbar.show(context.getString(R.string.not_a_backup)); file = null; true
        } catch (_: Exception) {
            snackbar.show(context.getString(R.string.not_a_backup)); file = null; true
        }
    }

    if (askPassword) {
        PasswordSheet(stringResource(R.string.backup_password), stringResource(R.string.enter_backup_password), confirm = false, onDismiss = { askPassword = false; file = null }) { pw ->
            askPassword = false
            val bytes = file ?: return@PasswordSheet
            scope.launch { if (!tryOpen(bytes, pw)) wrong = true }
        }
    }
    if (wrong) {
        ConfirmDialog(
            painterResource(R.drawable.ic_key_regular), stringResource(R.string.wrong_password_title), stringResource(R.string.wrong_password_text),
            stringResource(R.string.try_again), stringResource(R.string.cancel),
            onConfirm = { wrong = false; askPassword = true }, onDismiss = { wrong = false; file = null },
        )
    }
    ready?.let { data ->
        ConfirmDialog(
            painterResource(R.drawable.ic_settings_backup_restore_regular), stringResource(R.string.replace_title),
            pluralStringResource(R.plurals.replace_text, data.txns.size, data.txns.size),
            stringResource(R.string.replace), stringResource(R.string.cancel),
            onConfirm = {
                ready = null; file = null
                scope.launch {
                    app.backup.restore(data)
                    snackbar.show(context.getString(R.string.restored))
                    onRestored()
                }
            },
            onDismiss = { ready = null; file = null },
        )
    }
    return { uri ->
        scope.launch {
            val bytes = runCatching { app.backup.read(uri) }.getOrNull() ?: return@launch snackbar.show(context.getString(R.string.not_a_backup))
            file = bytes
            if (!tryOpen(bytes, app.backup.savedPassword())) askPassword = true
        }
    }
}

/** A password (and its confirmation when setting one). */
@Composable
fun PasswordSheet(title: String, line: String, confirm: Boolean, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    val ok = a.length >= 6 && (!confirm || a == b)
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss, line)
        SheetBody {
            PasswordField(a, { a = it }, stringResource(R.string.password))
            if (confirm) PasswordField(b, { b = it }, stringResource(R.string.password_again))
            if (confirm && b.isNotEmpty() && a != b) Text(stringResource(R.string.passwords_differ), style = BurrowType.body, color = MaterialTheme.colorScheme.error)
            if (a.isNotEmpty() && a.length < 6) Text(stringResource(R.string.password_short), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.done), { if (ok) onDone(a) }, Modifier.fillMaxWidth(), if (ok) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom)
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, hint: String) {
    PillTextField(
        value, onChange, hint, leading = painterResource(R.drawable.ic_key_regular), clearLabel = stringResource(R.string.clear),
        keyboardType = KeyboardType.Password, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
        password = true,
    )
}
