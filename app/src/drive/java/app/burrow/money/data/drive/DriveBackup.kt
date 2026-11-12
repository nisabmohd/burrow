package app.burrow.money.data.drive

import android.accounts.Account
import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import app.burrow.money.data.BackupManager
import app.burrow.money.data.SettingsRepository
import app.burrow.money.domain.nextDriveSlot
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Backup to the user's own Google Drive (play and github builds; ported from Weft 1.5.0). Each backup
 * is the same encrypted file as a folder backup (sealed with the backup password, so Google can't read
 * it), as `burrow-backup-<time>.burrow` in Drive's hidden app folder (`drive.appdata`, which only
 * Burrow can see), with the phone's name and the transaction count in its `appProperties`. The last
 * [KEEP] are kept. Google's AuthorizationClient picks the account and asks for consent; the Drive calls
 * are plain HTTPS, with no Google API client library.
 *
 * Scheduled backups ([reschedule], [DriveBackupJob]) follow Backup's Automatic backup (daily or weekly),
 * overnight while charging, on Wi-Fi when Wi-Fi only is on.
 *
 * Edge cases: no connection (OFFLINE, nothing sent); mobile data with Wi-Fi only on (WIFI_NEEDED);
 * no backup password (NO_PASSWORD); an expired or revoked token (cleared, asked for again, retried
 * once); a full Drive (FULL); rate limits and server errors (two retries with backoff); one at a time.
 */
class DriveBackup(
    private val context: Context,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
) {
    /** Off until the Google Cloud OAuth clients exist (BuildConfig.DRIVE_BACKUP, user 2026-11-12): no Drive UI, no job. */
    val enabled: Boolean = app.burrow.money.BuildConfig.DRIVE_BACKUP
    private val json = Json { ignoreUnknownKeys = true }
    private val oneAtATime = Mutex()
    private val running = MutableStateFlow(false)

    /** A backup is uploading (Back up now or the nightly job), for "Backing up…" on Backup. */
    val backingUp: StateFlow<Boolean> = running.asStateFlow()
    private val client get() = Identity.getAuthorizationClient(context)
    private val scopes = listOf(Scope(SCOPE_APPDATA), Scope(SCOPE_EMAIL))

    /** True when the phone has a connection; checked before Google's screen, so offline says so. */
    fun online(): Boolean = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private fun unmetered(): Boolean = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

    private fun capabilities(): NetworkCapabilities? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.getNetworkCapabilities(cm.activeNetwork)
    }

    /** Connect or Switch account: Google's account picker, then consent if the account hasn't allowed Burrow yet. */
    suspend fun connect(): DriveAuth = authorize(pickAccount = true)

    /** A token for the connected account: silently if it still allows Burrow, else its consent screen. */
    suspend fun authorize(): DriveAuth = authorize(pickAccount = false)

    private suspend fun authorize(pickAccount: Boolean): DriveAuth {
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes).apply {
            if (pickAccount) setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            else settings.settings.first().driveAccount?.let { setAccount(Account(it, ACCOUNT_TYPE)) }
        }.build()
        val result = withTimeoutOrNull(TIMEOUT_MS) { client.authorize(request).awaitOrNull() }
            ?: return DriveAuth.Failed.also { Log.w(TAG, "Google authorization failed or timed out") }
        return result.toAuth()
    }

    /** The token from Google's screen, or null if the user backed out. */
    fun tokenFrom(data: Intent?): String? =
        runCatching { client.getAuthorizationResultFromIntent(data) }.getOrNull()?.takeIf { it.allowsDrive() }?.accessToken

    /** Google's screen ended with a token but Google Drive unticked: Burrow can see the email but not keep backups. */
    fun driveRefused(data: Intent?): Boolean =
        runCatching { client.getAuthorizationResultFromIntent(data) }.getOrNull()?.let { it.accessToken != null && !it.allowsDrive() } == true

    private fun AuthorizationResult.allowsDrive(): Boolean = SCOPE_APPDATA in grantedScopes

    /** Google's screen ended without a token: true when the user backed out (nothing to say), false when it failed. */
    fun backedOut(resultCode: Int, data: Intent?): Boolean {
        val error = runCatching { client.getAuthorizationResultFromIntent(data) }.exceptionOrNull()
        val status = (error as? ApiException)?.statusCode
        if (status != CommonStatusCodes.CANCELED) Log.w(TAG, "Google's screen ended without a token: result $resultCode, status $status", error)
        return status == CommonStatusCodes.CANCELED || (data == null && resultCode == Activity.RESULT_CANCELED)
    }

    /** After Connect or Switch account: keeps the picked account and starts the schedule. */
    suspend fun finishConnect(token: String): DriveResult {
        val (result, email) = withDrive(token) { t ->
            json.parseToJsonElement(request("GET", USERINFO, t).decodeToString()).jsonObject["email"]?.jsonPrimitive?.content
        }
        if (result != DriveResult.DONE || email == null) return if (result == DriveResult.DONE) DriveResult.FAILED else result
        val previous = settings.settings.first().driveAccount
        if (previous != null && previous != email) revoke(previous)
        settings.setDriveAccount(email)
        reschedule()
        return DriveResult.DONE
    }

    /** Uploads a fresh backup and keeps the newest [KEEP]. [manual] is "Back up now", which honours Wi-Fi only too. */
    suspend fun backUpNow(token: String, manual: Boolean = true): DriveResult = oneAtATime.withLock {
        val s = settings.settings.first()
        if (manual && s.driveWifiOnly && online() && !unmetered()) return@withLock DriveResult.WIFI_NEEDED
        running.value = true
        try {
            val (sealed, txns) = backup.sealedWithSavedPassword() ?: return@withLock DriveResult.NO_PASSWORD
            val (result, count) = withDrive(token) { t ->
                create(t, sealed, txns)
                val all = listFiles(t)
                all.drop(KEEP).forEach { old -> runCatching { request("DELETE", "$API/files/${old.id}", t) } }
                all.size.coerceAtMost(KEEP)
            }
            // Offline isn't a failed backup, only one that didn't start.
            when (result) {
                DriveResult.DONE -> settings.setDriveBackupDone(System.currentTimeMillis(), count ?: 0)
                DriveResult.OFFLINE -> Unit
                else -> settings.setDriveBackupFailed(result.name)
            }
            result
        } finally {
            running.value = false
        }
    }

    /** The backups in Drive, newest first (up to [KEEP]). */
    suspend fun list(token: String): Pair<DriveResult, List<DriveBackupInfo>> {
        val (result, files) = withDrive(token) { t -> listFiles(t).take(KEEP) }
        if (result == DriveResult.DONE) settings.setDriveBackupCount(files.orEmpty().size)
        return result to files.orEmpty()
    }

    /** One backup's encrypted bytes; Backup's restore flow opens it with the password. */
    suspend fun download(token: String, id: String): Pair<DriveResult, ByteArray?> = oneAtATime.withLock {
        withDrive(token) { t -> request("GET", "$API/files/$id?alt=media", t) }
    }

    /** Deletes every Burrow backup in the connected account's Drive. */
    suspend fun deleteAll(token: String): DriveResult = oneAtATime.withLock {
        withDrive(token) { t -> listFiles(t).forEach { request("DELETE", "$API/files/${it.id}", t) } }.first
            .also { if (it == DriveResult.DONE) settings.setDriveBackupsDeleted() }
    }

    /** Stops backing up: the schedule is cancelled and Burrow's access removed. The backups stay in Drive. */
    suspend fun disconnect() {
        val account = settings.settings.first().driveAccount
        settings.setDriveAccount(null)
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        account?.let { revoke(it) }
    }

    /** Schedules the next backup for Automatic backup (daily or weekly), while charging and on the right network. */
    suspend fun reschedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val s = settings.settings.first()
        if (!enabled || s.driveAccount == null || s.backupSchedule == "OFF") {
            scheduler.cancel(JOB_ID)
            return
        }
        val now = ZonedDateTime.now()
        val wait = ChronoUnit.MILLIS.between(now, nextDriveSlot(now, s.backupSchedule)).coerceAtLeast(0)
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, DriveBackupJob::class.java))
            .setMinimumLatency(wait)
            .setRequiresCharging(true)
            .setRequiredNetworkType(if (s.driveWifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(BACKOFF_JOB_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }

    /** On app start: schedules only if nothing is pending (scheduling would stop a running backup). */
    suspend fun ensureScheduled() {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        // Off (DRIVE_BACKUP false): reschedule cancels a job an earlier build left behind.
        if (!enabled || scheduler.getPendingJob(JOB_ID) == null) reschedule()
    }

    /** Runs [block] with retries; see the class comment for what each failure becomes. */
    private suspend fun <T> withDrive(token: String, block: (String) -> T): Pair<DriveResult, T?> = withContext(Dispatchers.IO) {
        if (!online()) return@withContext DriveResult.OFFLINE to null
        var t = token
        var renewed = false
        repeat(ATTEMPTS) { attempt ->
            try {
                return@withContext DriveResult.DONE to block(t)
            } catch (e: DriveHttpException) {
                val reason = e.body.lowercase()
                when {
                    e.code == 401 && !renewed -> {
                        renewed = true
                        clearToken(t)
                        t = (authorize() as? DriveAuth.Token)?.value ?: return@withContext DriveResult.FAILED to null
                    }
                    e.code == 403 && "storagequotaexceeded" in reason -> return@withContext DriveResult.FULL to null
                    e.code == 429 || e.code >= 500 || (e.code == 403 && "ratelimitexceeded" in reason) ->
                        if (attempt < ATTEMPTS - 1) delay(BACKOFF_MS shl attempt) else return@withContext DriveResult.FAILED to null
                    else -> return@withContext DriveResult.FAILED to null
                }
            } catch (_: IOException) {
                if (!online()) return@withContext DriveResult.OFFLINE to null
                if (attempt < ATTEMPTS - 1) delay(BACKOFF_MS shl attempt) else return@withContext DriveResult.FAILED to null
            }
        }
        DriveResult.FAILED to null
    }

    /** Burrow's backups in the app folder, newest first. */
    private fun listFiles(token: String): List<DriveBackupInfo> {
        val q = URLEncoder.encode("name contains '$FILE_PREFIX' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,createdTime,size,appProperties)", "UTF-8")
        val list = request("GET", "$API/files?spaces=appDataFolder&pageSize=100&orderBy=createdTime%20desc&fields=$fields&q=$q", token)
        val files = json.parseToJsonElement(list.decodeToString()).jsonObject["files"]?.jsonArray.orEmpty()
        return files.mapNotNull { element ->
            val f = element.jsonObject
            val id = f["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val props = f["appProperties"] as? JsonObject
            DriveBackupInfo(
                id = id,
                createdAt = f["createdTime"]?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                device = props?.get(PROP_DEVICE)?.jsonPrimitive?.content.orEmpty(),
                txns = props?.get(PROP_TXNS)?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                sizeBytes = f["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }
    }

    private fun create(token: String, body: ByteArray, txns: Int) {
        val metadata = buildJsonObject {
            put("name", "$FILE_PREFIX${Instant.now().truncatedTo(ChronoUnit.SECONDS)}.burrow")
            put("mimeType", "application/octet-stream")
            putJsonArray("parents") { add(JsonPrimitive("appDataFolder")) }
            putJsonObject("appProperties") {
                put(PROP_DEVICE, deviceName())
                put(PROP_TXNS, txns.toString())
            }
        }.toString()
        val boundary = "burrow-${System.nanoTime()}"
        val multipart = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(body)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        request("POST", "$UPLOAD/files?uploadType=multipart", token, multipart, "multipart/related; boundary=$boundary")
    }

    /** The phone's name as the user set it ("Pixel 8"), else its model. */
    private fun deviceName(): String =
        AndroidSettings.Global.getString(context.contentResolver, AndroidSettings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private fun request(method: String, url: String, token: String, body: ByteArray? = null, type: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = TIMEOUT_MS.toInt()
            c.readTimeout = TIMEOUT_MS.toInt()
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw DriveHttpException(code, c.errorStream?.use { it.readBytes().decodeToString() }.orEmpty())
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun clearToken(token: String) {
        withTimeoutOrNull(TIMEOUT_MS) { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitOrNull() }
    }

    private suspend fun revoke(email: String) {
        withTimeoutOrNull(TIMEOUT_MS) {
            client.revokeAccess(RevokeAccessRequest.builder().setAccount(Account(email, ACCOUNT_TYPE)).setScopes(scopes).build()).awaitOrNull()
        }
    }

    private fun AuthorizationResult.toAuth(): DriveAuth {
        val intent = pendingIntent
        val token = accessToken
        return when {
            hasResolution() && intent != null -> DriveAuth.NeedsConsent(IntentSenderRequest.Builder(intent.intentSender).build())
            token != null && allowsDrive() -> DriveAuth.Token(token)
            token != null -> DriveAuth.DriveRefused
            else -> DriveAuth.Failed
        }
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { Log.w(TAG, "Google task failed: status ${(it as? ApiException)?.statusCode}", it); cont.resume(null) }
    }

    private class DriveHttpException(val code: Int, val body: String) : IOException("Drive answered $code")

    companion object {
        /** Burrow has no WorkManager, so no other JobScheduler ids to stay clear of. */
        const val JOB_ID = 3101
        /** Backups kept in Drive; older ones are deleted after each upload. */
        const val KEEP = 10
        private const val TAG = "BurrowDrive"
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val SCOPE_EMAIL = "email"
        private const val ACCOUNT_TYPE = "com.google"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val FILE_PREFIX = "burrow-backup-"
        private const val PROP_DEVICE = "device"
        private const val PROP_TXNS = "txns"
        private const val TIMEOUT_MS = 20_000L
        private const val ATTEMPTS = 3
        private const val BACKOFF_MS = 2_000L
        private const val BACKOFF_JOB_MS = 30 * 60 * 1000L
    }
}
