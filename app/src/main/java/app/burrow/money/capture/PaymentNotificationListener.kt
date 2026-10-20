package app.burrow.money.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.burrow.money.BurrowApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reads notifications from payment and bank apps only (the allow-list below) and hands their text
 * to the rules (docs/CAPTURE.md). Other apps' notifications are never read past the package name.
 */
class PaymentNotificationListener : NotificationListenerService() {
    /** Notifications already handled (apps re-post the same one when it updates). */
    private val seen = ArrayDeque<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PAYMENT_APPS) return
        val extras = sbn.notification.extras
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        val key = "${sbn.packageName}|$text"
        synchronized(seen) {
            if (key in seen) return
            seen.addLast(key)
            if (seen.size > 50) seen.removeFirst()
        }
        val container = (applicationContext as BurrowApp).container
        container.appScope.launch {
            if (container.settings.settings.first().notificationCapture) container.onMessage("NOTIFICATION", sbn.packageName, text, sbn.postTime)
        }
    }

    companion object {
        /** Payment and bank apps whose notifications may hold a payment. Keep in step with the packs' senders. */
        val PAYMENT_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp", // BHIM
            "com.dreamplug.androidapp", // CRED
            "com.snapwork.hdfc",
            "com.csam.icici.bank.imobile",
            "com.sbi.lotusintouch",
            "com.msf.kbank.mobile",
            "com.axis.mobile",
        )
    }
}
