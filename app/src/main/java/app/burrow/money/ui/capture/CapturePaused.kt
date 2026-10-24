package app.burrow.money.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.burrow.money.R
import app.burrow.money.capture.CaptureAccess
import app.burrow.money.capture.CaptureStatus

/**
 * [CaptureStatus] for the saved switches, with Android's answer checked again each time the app
 * comes back (the user may have just changed it in system settings).
 */
@Composable
fun rememberCaptureStatus(smsWanted: Boolean, appsWanted: Boolean): CaptureStatus {
    val context = LocalContext.current
    var smsAllowed by remember { mutableStateOf(CaptureAccess.smsGranted(context)) }
    var appsAllowed by remember { mutableStateOf(CaptureAccess.listenerEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        smsAllowed = CaptureAccess.smsGranted(context)
        appsAllowed = CaptureAccess.listenerEnabled(context)
    }
    return CaptureStatus(smsWanted, appsWanted, smsAllowed, appsAllowed, CaptureAccess.smsInThisBuild)
}

/** Why auto-capture can't run although it is on (Today's paused note, 07). Null when all is well. */
@Composable
fun rememberCapturePaused(smsOn: Boolean, appsOn: Boolean): Int? =
    when (rememberCaptureStatus(smsOn, appsOn).blocked) {
        CaptureStatus.Blocked.NOTIFICATIONS -> R.string.capture_paused_listener
        CaptureStatus.Blocked.SMS -> R.string.capture_paused_sms
        null -> null
    }
