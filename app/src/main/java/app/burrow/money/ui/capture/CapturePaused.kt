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

/**
 * Why auto-capture can't run although it is on (Today's paused note, 07), checked again each time
 * the app comes back (the user may have just changed it in system settings). Null when all is well.
 */
@Composable
fun rememberCapturePaused(smsOn: Boolean, appsOn: Boolean): Int? {
    val context = LocalContext.current
    fun check(): Int? = when {
        appsOn && !CaptureAccess.listenerEnabled(context) -> R.string.capture_paused_listener
        smsOn && CaptureAccess.smsInThisBuild && !CaptureAccess.smsGranted(context) -> R.string.capture_paused_sms
        else -> null
    }
    var reason by remember(smsOn, appsOn) { mutableStateOf(check()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reason = check() }
    return reason
}
