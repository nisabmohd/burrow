package app.burrow.money.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.Settings
import app.burrow.money.data.drive.DriveAuth
import app.burrow.money.data.drive.DriveBackupInfo
import app.burrow.money.data.drive.DriveResult
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LetterCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.rememberDateText
import kotlinx.coroutines.launch

/** What a Drive tap is waiting to do once Google gives a token. */
private enum class DriveStep { CONNECT, BACK_UP, LIST, DELETE }

private enum class DriveSheet { RESTORE, ACCOUNT }

/**
 * Google Drive on Backup (play and github; ported from Weft's Drive backup, docs/DATA.md § Drive
 * backup). Built only from Backup's own pieces: a section of rows, the Restore sheet as a list of rows
 * like the folder's backups, the account sheet like a transaction's actions sheet (06), and the
 * delete dialog. Backups are sealed with the backup password, so the password comes first.
 */
@Composable
fun DriveSection(app: AppContainer, settings: Settings, onNeedPassword: () -> Unit, restore: (Any) -> Unit) {
    val drive = app.drive
    if (!drive.enabled) return
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val dates = rememberDateText()
    val c = MaterialTheme.colorScheme
    val uploading by drive.backingUp.collectAsState()
    var busy by remember { mutableStateOf<DriveStep?>(null) }
    var waiting by remember { mutableStateOf<DriveStep?>(null) }
    var sheet by rememberSaveable { mutableStateOf<DriveSheet?>(null) }
    var backups by remember { mutableStateOf<List<DriveBackupInfo>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun say(result: DriveResult) {
        val text = when (result) {
            DriveResult.DONE -> return
            DriveResult.OFFLINE -> R.string.drive_offline
            DriveResult.WIFI_NEEDED -> R.string.drive_wifi_needed
            DriveResult.FULL -> R.string.drive_full
            DriveResult.NEEDS_CONSENT -> R.string.drive_allow_again
            DriveResult.NO_PASSWORD -> R.string.set_password_first
            DriveResult.FAILED -> R.string.drive_failed
        }
        scope.launch { snackbar.show(context.getString(text)) }
    }

    fun run(step: DriveStep, token: String) {
        scope.launch {
            when (step) {
                DriveStep.CONNECT -> {
                    val r = drive.finishConnect(token)
                    // Connect runs a first backup straight away (as Weft).
                    if (r == DriveResult.DONE) say(drive.backUpNow(token, manual = false).also { if (it == DriveResult.DONE) snackbar.show(context.getString(R.string.drive_backed_up)) }) else say(r)
                }
                DriveStep.BACK_UP -> drive.backUpNow(token).let { if (it == DriveResult.DONE) snackbar.show(context.getString(R.string.drive_backed_up)) else say(it) }
                DriveStep.LIST -> drive.list(token).let { (r, list) -> if (r == DriveResult.DONE) backups = list else { sheet = null; say(r) } }
                DriveStep.DELETE -> drive.deleteAll(token).let { if (it == DriveResult.DONE) snackbar.show(context.getString(R.string.drive_deleted)) else say(it) }
            }
            busy = null
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val step = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        val token = drive.tokenFrom(result.data)
        when {
            token != null -> run(step, token)
            else -> {
                busy = null
                if (step == DriveStep.LIST) sheet = null
                when {
                    drive.driveRefused(result.data) -> scope.launch { snackbar.show(context.getString(R.string.drive_refused)) }
                    // Backed out of Google's screen: nothing to say.
                    drive.backedOut(result.resultCode, result.data) -> Unit
                    else -> say(DriveResult.FAILED)
                }
            }
        }
    }

    fun start(step: DriveStep) {
        // One at a time: a second tap while one runs (or Google's screen is open) does nothing.
        if (busy != null || (step == DriveStep.BACK_UP && uploading)) return
        if (!drive.online()) { if (step == DriveStep.LIST) sheet = null; say(DriveResult.OFFLINE); return }
        busy = step
        scope.launch {
            when (val auth = if (step == DriveStep.CONNECT) drive.connect() else drive.authorize()) {
                is DriveAuth.Token -> run(step, auth.value)
                is DriveAuth.NeedsConsent -> { waiting = step; consent.launch(auth.request) }
                DriveAuth.DriveRefused -> { busy = null; if (step == DriveStep.LIST) sheet = null; snackbar.show(context.getString(R.string.drive_refused)) }
                DriveAuth.Failed -> { busy = null; if (step == DriveStep.LIST) sheet = null; say(DriveResult.FAILED) }
            }
        }
    }

    SectionHeader(stringResource(R.string.google_drive))
    val account = settings.driveAccount
    if (account == null) {
        ListGroup {
            ListRow(
                stringResource(R.string.connect_google_drive), 0, 1,
                supporting = stringResource(if (busy == DriveStep.CONNECT) R.string.connecting else R.string.connect_google_drive_line),
                lead = { IconCircle(painterResource(R.drawable.ic_cloud_duotone)) },
                onClick = { if (!settings.hasBackupPassword) onNeedPassword() else start(DriveStep.CONNECT) },
            )
        }
    } else {
        val failed = settings.driveBackupError
        ListGroup {
            val sending = uploading || busy == DriveStep.BACK_UP
            ListRow(
                stringResource(if (sending) R.string.backing_up_to_drive else R.string.back_up_to_drive), 0, 4,
                supporting = when {
                    failed != null && !sending -> stringResource(R.string.drive_last_failed)
                    settings.lastDriveBackupAt != null -> stringResource(R.string.last_backup, dates.dayTime(settings.lastDriveBackupAt))
                    else -> stringResource(R.string.never)
                },
                supportingColor = if (failed != null && !sending) c.error else c.onSurfaceVariant,
                lead = { IconCircle(painterResource(R.drawable.ic_cloud_upload_duotone)) },
                onClick = { if (!settings.hasBackupPassword) onNeedPassword() else start(DriveStep.BACK_UP) },
            )
            ListRow(
                stringResource(R.string.restore_from_drive), 1, 4,
                supporting = if (settings.driveBackupCount > 0) pluralStringResource(R.plurals.n_backups, settings.driveBackupCount, settings.driveBackupCount) else stringResource(R.string.restore_from_drive_line),
                lead = { IconCircle(painterResource(R.drawable.ic_cloud_download_duotone)) },
                onClick = { backups = null; sheet = DriveSheet.RESTORE; start(DriveStep.LIST) },
            )
            ListRow(
                stringResource(R.string.wifi_only), 2, 4, supporting = stringResource(R.string.wifi_only_line),
                lead = { IconCircle(painterResource(R.drawable.ic_wifi_duotone)) },
                trailing = { BurrowSwitch(settings.driveWifiOnly, { on -> scope.launch { app.settings.setDriveWifiOnly(on); drive.reschedule() } }) },
            )
            ListRow(
                account, 3, 4, supporting = stringResource(R.string.google_account),
                lead = { LetterCircle(account.take(1).uppercase()) },
                trailing = { Chevron() },
                onClick = { sheet = DriveSheet.ACCOUNT },
            )
        }
    }

    when (sheet) {
        // Restore from Drive: the backups as rows, like the folder's backups on Backup.
        DriveSheet.RESTORE -> BurrowSheet({ sheet = null }) {
            SheetHeader(stringResource(R.string.restore_from_drive), { sheet = null }, stringResource(R.string.replaces_this_phone))
            SheetBody {
                val list = backups
                ListGroup {
                    when {
                        list == null -> ListRow(stringResource(R.string.loading), 0, 1)
                        list.isEmpty() -> ListRow(stringResource(R.string.no_drive_backups), 0, 1)
                        else -> list.forEachIndexed { i, b ->
                            ListRow(
                                dates.dayTime(b.createdAt), i, list.size,
                                supporting = stringResource(R.string.dot_join, pluralStringResource(R.plurals.n_transactions, b.txns, b.txns), fileSize(b.sizeBytes)),
                                lead = { IconCircle(painterResource(R.drawable.ic_cloud_download_duotone)) },
                                trailing = { Chevron() },
                                onClick = {
                                    sheet = null
                                    scope.launch {
                                        when (val auth = drive.authorize()) {
                                            is DriveAuth.Token -> drive.download(auth.value, b.id).let { (r, bytes) -> if (bytes != null) restore(bytes) else say(r) }
                                            else -> say(DriveResult.FAILED)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        // Google account: an actions sheet like a transaction's (06), the delete row last in red.
        DriveSheet.ACCOUNT -> BurrowSheet({ sheet = null }) {
            SheetHeader(account.orEmpty(), { sheet = null }, stringResource(R.string.google_account_line))
            SheetBody {
                ListGroup {
                    ListRow(
                        stringResource(R.string.switch_account), 0, 2, supporting = stringResource(R.string.switch_account_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_account_switch_duotone)) },
                        onClick = { sheet = null; start(DriveStep.CONNECT) },
                    )
                    ListRow(
                        stringResource(R.string.disconnect), 1, 2, supporting = stringResource(R.string.disconnect_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_logout_duotone)) },
                        onClick = {
                            sheet = null
                            scope.launch { drive.disconnect(); snackbar.show(context.getString(R.string.drive_disconnected)) }
                        },
                    )
                }
                ListGroup {
                    ListRow(
                        stringResource(R.string.delete_drive_backups), 0, 1, headlineColor = c.error,
                        supporting = settings.driveBackupCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.removes_n_backups, it, it) },
                        lead = { IconCircle(painterResource(R.drawable.ic_delete_duotone), container = c.errorContainer, content = c.error) },
                        onClick = { sheet = null; confirmDelete = true },
                    )
                }
            }
        }
        null -> Unit
    }
    if (confirmDelete) {
        ConfirmDialog(
            painterResource(R.drawable.ic_delete_regular), stringResource(R.string.delete_drive_backups_title), stringResource(R.string.delete_drive_backups_text),
            stringResource(R.string.delete), stringResource(R.string.cancel),
            onConfirm = { confirmDelete = false; start(DriveStep.DELETE) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** "412 KB" / "1.2 MB" for a Drive backup row (181). */
@Composable
private fun fileSize(bytes: Long): String =
    if (bytes < 1024L * 1024) stringResource(R.string.size_kb, (bytes + 1023) / 1024)
    else stringResource(R.string.size_mb, String.format(java.util.Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024)))
