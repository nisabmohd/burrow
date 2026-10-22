package app.burrow.money.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.withTransaction
import app.burrow.money.BurrowApp
import app.burrow.money.domain.BackupCrypto
import app.burrow.money.notify.runMonthlySummary
import app.burrow.money.notify.runSettleReminders
import app.burrow.money.ui.theme.BodyFont
import app.burrow.money.ui.theme.TextSize
import app.burrow.money.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The settings a backup carries (not the lock, the PIN or the backup folder: those belong to the phone). */
@Serializable
data class BackupSettings(
    val mainCurrency: String,
    val monthStartDay: Int,
    val theme: String,
    val accentHue: Float,
    val dynamicColor: Boolean,
    val font: String,
    val textSize: String,
    val homeWidgets: Set<String>,
    val enabledPacks: Set<String>,
)

/** Everything in a backup file, as JSON before it is sealed. Message text is never included. */
@Serializable
data class BackupData(
    val version: Int = 3,
    val createdAt: Long,
    val settings: BackupSettings,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val txns: List<TxnEntity>,
    val bills: List<BillEntity>,
    val occurrences: List<BillOccurrenceEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val rules: List<CaptureRuleEntity>,
    // v2 (Room v4): lend / borrow, tags, attachments (with their files) and typed-in rates.
    val people: List<PersonEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val txnTags: List<TxnTagEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val files: List<BackupBlob> = emptyList(),
    val rates: List<RateEntity> = emptyList(),
    // v3 (Room v5): split parts, favourites, import mappings.
    val splits: List<SplitEntity> = emptyList(),
    val favourites: List<FavouriteEntity> = emptyList(),
    val importMaps: List<ImportMapEntity> = emptyList(),
)

/** An attachment's bytes (base64) at its path inside app storage. */
@Serializable
data class BackupBlob(val path: String, val data: String)

/** A backup in the folder: "burrow-2026-10-18-1204.burrow" (date and transaction count in the name). */
data class BackupFile(val uri: Uri, val name: String, val date: LocalDate?, val txns: Int?)

/**
 * Encrypted backups (Day 23, docs/DATA.md § Backup): one file in a folder the user picks (Storage
 * Access Framework), by hand or on a schedule, the last 7 kept. Restore replaces everything.
 */
class BackupManager(private val context: Context, private val db: BurrowDatabase, private val settings: SettingsRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val b = db.backup()
        BackupData(
            createdAt = System.currentTimeMillis(),
            settings = BackupSettings(
                s.mainCurrency, s.monthStartDay, s.theme.mode.name, s.theme.accentHue, s.theme.dynamicColor,
                s.theme.font.name, s.theme.textSize.name, s.homeWidgets, s.enabledPacks,
            ),
            accounts = b.accounts(), categories = b.categories(), txns = b.txns(), bills = b.bills(),
            occurrences = b.occurrences(), budgets = b.budgets(), goals = b.goals(), rules = b.rules(),
            people = b.people(), tags = b.tags(), txnTags = b.txnTags(), attachments = b.attachments(), rates = b.rates(),
            splits = b.splits(), favourites = b.favourites(), importMaps = b.importMaps(),
            files = b.attachments().mapNotNull { a ->
                val f = java.io.File(context.filesDir, a.path)
                if (f.isFile) BackupBlob(a.path, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)) else null
            },
        )
    }

    /** Writes a backup into the folder with the saved password; drops all but the newest 7. Returns its name. */
    suspend fun backUpNow(): String = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val folder = s.backupFolder?.let(Uri::parse) ?: error("no folder")
        val password = settings.sealedBackupPassword()?.let(PasswordVault::open) ?: error("no password")
        val data = snapshot()
        val name = "burrow-${LocalDate.now()}-${data.txns.count { !it.isSample }}.burrow"
        val sealed = BackupCrypto.seal(json.encodeToString(BackupData.serializer(), data).toByteArray(), password.toCharArray())
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        // Today's backup replaces an earlier one from today.
        list().filter { it.name.startsWith("burrow-${LocalDate.now()}") }.forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it.uri) } }
        val file = DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", name) ?: error("can't create file")
        context.contentResolver.openOutputStream(file)?.use { it.write(sealed) } ?: error("can't write file")
        list().drop(KEEP).forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it.uri) } }
        settings.setLastBackupAt(System.currentTimeMillis())
        name
    }

    /** Backups in the folder, newest first. */
    suspend fun list(): List<BackupFile> = withContext(Dispatchers.IO) {
        val folder = settings.settings.first().backupFolder?.let(Uri::parse) ?: return@withContext emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val out = mutableListOf<BackupFile>()
        runCatching {
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    val m = NAME.matchEntire(name) ?: continue
                    out += BackupFile(
                        DocumentsContract.buildDocumentUriUsingTree(folder, c.getString(0)), name,
                        runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull(), m.groupValues[2].toIntOrNull(),
                    )
                }
            }
        }
        out.sortedByDescending { it.name }
    }

    /** "Documents/Burrow" from the folder the user picked. */
    fun folderLabel(uri: String): String = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifEmpty { "/" } }.getOrDefault(uri)

    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("can't read file")
    }

    /** Opens a backup (throws WrongPassword / NotABackup) without changing anything. */
    suspend fun open(file: ByteArray, password: String): BackupData = withContext(Dispatchers.Default) {
        json.decodeFromString(BackupData.serializer(), String(BackupCrypto.open(file, password.toCharArray())))
    }

    /** The saved password, to try first when restoring. */
    suspend fun savedPassword(): String? = settings.sealedBackupPassword()?.let { runCatching { PasswordVault.open(it) }.getOrNull() }

    /** Replaces every table and the backed-up settings with [data], in one go. */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        val b = db.backup()
        db.withTransaction {
            b.clearOccurrences(); b.clearBills(); b.clearBudgets(); b.clearTxns(); b.clearGoals()
            b.clearRules(); b.clearCaptures(); b.clearCategories(); b.clearAccounts()
            b.clearPeople(); b.clearTxnTags(); b.clearTags(); b.clearAttachments(); b.clearRates()
            b.clearSplits(); b.clearFavourites(); b.clearImportMaps()
            b.putAccounts(data.accounts); b.putCategories(data.categories); b.putTxns(data.txns); b.putBills(data.bills)
            b.putOccurrences(data.occurrences); b.putBudgets(data.budgets); b.putGoals(data.goals); b.putRules(data.rules)
            b.putPeople(data.people); b.putTags(data.tags); b.putTxnTags(data.txnTags); b.putAttachments(data.attachments); b.putRates(data.rates)
            b.putSplits(data.splits); b.putFavourites(data.favourites); b.putImportMaps(data.importMaps)
        }
        // Attachment files go back into app storage (only under files/attachments/).
        java.io.File(context.filesDir, Attachments.DIR).deleteRecursively()
        data.files.forEach { blob ->
            val f = java.io.File(context.filesDir, blob.path)
            if (f.canonicalPath.startsWith(java.io.File(context.filesDir, Attachments.DIR).canonicalPath)) {
                f.parentFile?.mkdirs()
                f.writeBytes(Base64.decode(blob.data, Base64.NO_WRAP))
            }
        }
        val s = data.settings
        settings.setMainCurrency(s.mainCurrency)
        settings.setMonthStartDay(s.monthStartDay)
        ThemeMode.entries.firstOrNull { it.name == s.theme }?.let { settings.setThemeMode(it) }
        settings.setAccentHue(s.accentHue)
        settings.setDynamicColor(s.dynamicColor)
        BodyFont.entries.firstOrNull { it.name == s.font }?.let { settings.setFont(it) }
        TextSize.entries.firstOrNull { it.name == s.textSize }?.let { settings.setTextSize(it) }
        settings.setSamplesDone()
        settings.setOnboarded()
    }

    suspend fun setPassword(password: String) = settings.setSealedBackupPassword(PasswordVault.seal(password))

    /** Sets the daily check (an inexact alarm around 3 AM) when a schedule is on. */
    suspend fun schedule() {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(context, 2, Intent(context, BackupAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.cancel(pending)
        // Always on: the same daily check posts upcoming transactions and sends the monthly summary.
        val at = LocalDate.now().plusDays(1).atTime(3, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /** The scheduled backup: runs when one is due, quietly. */
    suspend fun runScheduled() {
        val s = settings.settings.first()
        val last = s.lastBackupAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        val due = when (s.backupSchedule) {
            "DAILY" -> last == null || last.isBefore(LocalDateTime.now().minusHours(20))
            "WEEKLY" -> last == null || last.isBefore(LocalDateTime.now().minusDays(6).minusHours(20))
            else -> false
        }
        if (due && s.backupFolder != null && s.hasBackupPassword) runCatching { backUpNow() }
        schedule()
    }

    private companion object {
        const val KEEP = 7
        val NAME = Regex("burrow-(\\d{4}-\\d{2}-\\d{2})(?:-(\\d+))?\\.burrow")
    }
}

/** The daily backup check. */
class BackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.money.postDue()
                container.runMonthlySummary()
                container.runSettleReminders()
                container.backup.runScheduled()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * The backup password, sealed with an AES key that lives in the Android Keystore (it never leaves
 * the phone's secure hardware). Lets scheduled backups run without asking.
 */
object PasswordVault {
    private const val ALIAS = "burrow.backup.password"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
        )
        return gen.generateKey()
    }

    fun seal(password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(password.toByteArray()), Base64.NO_WRAP)
    }

    fun open(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }
}
