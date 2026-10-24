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

    /**
     * Android 13+ "Restricted settings": an app installed from a downloaded APK (browser, WhatsApp,
     * file manager) can't get SMS or notification access until the user allows it in App info ›
     * ⋮ › Allow restricted settings. Store installs (Play, F-Droid's installer) aren't affected.
     */
    fun restrictedSettingsLikely(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return false
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).packageSource }.getOrNull() ?: return false
        return source == android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ||
            source == android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    }

    /** Burrow's App info page, where "Allow restricted settings" lives (⋮ menu). */
    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun listenerSettings(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
