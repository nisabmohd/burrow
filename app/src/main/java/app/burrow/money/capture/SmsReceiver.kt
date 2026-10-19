package app.burrow.money.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Bank SMS → RuleEngine → capture notification (foss/github flavors; play after the SMS declaration).
 * Runs in goAsync(), no work on the main thread. Week 2 in docs/ROADMAP.md.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        // goAsync() + container.rules.parse(sender, body, rules) → CaptureNotifier.show(...)
        if (sender.isEmpty() || body.isEmpty()) return
    }
}
