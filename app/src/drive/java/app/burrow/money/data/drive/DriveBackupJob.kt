package app.burrow.money.data.drive

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import app.burrow.money.BurrowApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The scheduled Drive backup (JobScheduler: overnight, charging, on the right network). It never
 * shows a screen: if Google asks the user to allow Burrow again, the backup is marked failed and
 * Backup shows "Last Drive backup failed".
 */
@SuppressLint("SpecifyJobSchedulerIdRange")
class DriveBackupJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as BurrowApp).container
        running = scope.launch {
            val drive = container.drive
            val s = container.settings.settings.first()
            // Backed up by hand a few hours ago: nothing new to keep, wait for the next slot.
            val recent = s.lastDriveBackupAt != null && System.currentTimeMillis() - s.lastDriveBackupAt in 0 until RECENT_MS
            val result = when {
                s.driveAccount == null || recent -> DriveResult.DONE
                else -> when (val auth = drive.authorize()) {
                    is DriveAuth.Token -> drive.backUpNow(auth.value, manual = false)
                    else -> DriveResult.NEEDS_CONSENT.also { container.settings.setDriveBackupFailed(it.name) }
                }
            }
            // A dropped connection is worth another try soon; anything else waits for the next slot.
            val retry = result == DriveResult.OFFLINE
            if (!retry) drive.reschedule()
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // The system stopped it (charger out, Wi-Fi lost): try again when it can.
        running?.cancel()
        return true
    }

    private companion object {
        const val RECENT_MS = 6 * 60 * 60 * 1000L
    }
}
