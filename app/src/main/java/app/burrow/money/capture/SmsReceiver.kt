package app.burrow.money.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.burrow.money.BurrowApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bank SMS → rules → capture notification (foss/github flavors; play after the SMS declaration).
 * Work runs in goAsync() off the main thread and finishes in well under a second.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (sender.isEmpty() || body.isEmpty()) return
        val at = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val container = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (container.settings.settings.first().smsCapture) container.onMessage("SMS", sender, body, at)
            } finally {
                pending.finish()
            }
        }
    }
}
