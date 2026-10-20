package app.burrow.money.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.burrow.money.BurrowApp
import app.burrow.money.data.CaptureStatus
import kotlinx.coroutines.launch

/** The capture notification's buttons: save with a category, or Ignore. Not exported. */
class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val captureId = intent.getLongExtra(EXTRA_CAPTURE, 0L).takeIf { it != 0L } ?: return
        val container = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_SAVE -> container.capture.save(captureId, intent.getLongExtra(EXTRA_CATEGORY, 0L).takeIf { it != 0L })
                    ACTION_IGNORE -> container.capture.setStatus(captureId, CaptureStatus.IGNORED)
                }
                container.notifier.cancel(captureId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SAVE = "app.burrow.money.capture.SAVE"
        const val ACTION_IGNORE = "app.burrow.money.capture.IGNORE"
        const val EXTRA_CAPTURE = "capture"
        const val EXTRA_CATEGORY = "category"
    }
}
