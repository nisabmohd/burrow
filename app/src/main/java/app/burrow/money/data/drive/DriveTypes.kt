package app.burrow.money.data.drive

import androidx.activity.result.IntentSenderRequest

/** The answer to "may Burrow use the user's Google Drive?" (play and github builds, see `DriveBackup`). */
sealed interface DriveAuth {
    /** Allowed: an access token for Drive's hidden app folder. */
    data class Token(val value: String) : DriveAuth
    /** Google's account or consent screen has to be shown first. */
    data class NeedsConsent(val request: IntentSenderRequest) : DriveAuth
    data object Failed : DriveAuth
    /** The account allowed only its email: Google's screen lets the user untick Google Drive. */
    data object DriveRefused : DriveAuth
}

/** How a Drive action ended. */
enum class DriveResult {
    DONE,
    /** No internet connection. */
    OFFLINE,
    /** Wi-Fi only is on and the phone is on mobile data. */
    WIFI_NEEDED,
    /** The Google account's storage is full. */
    FULL,
    /** The scheduled backup found that Google wants the user to allow Burrow again. */
    NEEDS_CONSENT,
    /** No backup password yet: Drive backups are encrypted with it, like folder backups. */
    NO_PASSWORD,
    FAILED,
}

/** One backup in Drive, as the Restore from Drive sheet lists it. */
data class DriveBackupInfo(
    val id: String,
    val createdAt: Long,
    /** The phone that made it ("Pixel 8"). */
    val device: String,
    val txns: Int,
    val sizeBytes: Long,
)
