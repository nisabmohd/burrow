// Same signatures as the play and github builds' class, whose parameters this stand-in doesn't need.
@file:Suppress("UNUSED_PARAMETER", "unused", "RedundantSuspendModifier")

package app.burrow.money.data.drive

import android.content.Context
import android.content.Intent
import app.burrow.money.data.BackupManager
import app.burrow.money.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The foss build (F-Droid) has no Google code and no network: this stands in for Drive backup, which it doesn't have. */
class DriveBackup(context: Context, backup: BackupManager, settings: SettingsRepository) {
    val enabled: Boolean = false
    val backingUp: StateFlow<Boolean> = MutableStateFlow(false)
    fun online(): Boolean = false
    suspend fun connect(): DriveAuth = DriveAuth.Failed
    suspend fun authorize(): DriveAuth = DriveAuth.Failed
    fun tokenFrom(data: Intent?): String? = null
    fun backedOut(resultCode: Int, data: Intent?): Boolean = true
    fun driveRefused(data: Intent?): Boolean = false
    suspend fun finishConnect(token: String): DriveResult = DriveResult.FAILED
    suspend fun backUpNow(token: String, manual: Boolean = true): DriveResult = DriveResult.FAILED
    suspend fun list(token: String): Pair<DriveResult, List<DriveBackupInfo>> = DriveResult.FAILED to emptyList()
    suspend fun download(token: String, id: String): Pair<DriveResult, ByteArray?> = DriveResult.FAILED to null
    suspend fun deleteAll(token: String): DriveResult = DriveResult.FAILED
    suspend fun disconnect() = Unit
    suspend fun reschedule() = Unit
    suspend fun ensureScheduled() = Unit
}
