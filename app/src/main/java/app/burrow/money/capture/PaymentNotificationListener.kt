package app.burrow.money.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads payment notifications from known payment apps only (package allow-list), hands the text
 * to RuleEngine, and posts the capture notification (docs/CAPTURE.md). Never stores other apps' text.
 * Week 2 in docs/ROADMAP.md.
 */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // if (sbn.packageName !in PAYMENT_APPS) return
        // val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: return
        // container.rules.parse(sbn.packageName, text, rules) → CaptureNotifier.show(...)
    }
}
