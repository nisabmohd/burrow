package app.burrow.money.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.burrow.money.BuildConfig

/** What Android lets auto-capture do right now. */
object CaptureAccess {
    /** This build reads SMS at all (foss/github; play only after the SMS declaration). */
    val smsInThisBuild: Boolean get() = BuildConfig.SMS_CAPTURE

    fun smsGranted(context: Context): Boolean = smsInThisBuild &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    fun listenerEnabled(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Runtime permissions to ask for in the intro. */
    fun permissionsToAsk(): Array<String> = buildList {
        if (smsInThisBuild) {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun listenerSettings(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
