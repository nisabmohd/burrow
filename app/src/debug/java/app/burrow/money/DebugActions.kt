package app.burrow.money

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.burrow.money.home.HomeSurfaces
import app.burrow.money.notify.runWeeklyReview
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Debug builds only (never in a release): `adb shell am broadcast -a app.burrow.money.debug.SUNDAY_REVIEW`
 * sends the Sunday review now (as if it were this Sunday, 6:30 PM); `…debug.HOME` redraws the widgets
 * and shortcuts. Used by tools/smoke.py.
 */
class DebugActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    "app.burrow.money.debug.SUNDAY_REVIEW" -> {
                        val sunday = LocalDateTime.now().toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                        app.runWeeklyReview(sunday.atTime(18, 30))
                    }
                    "app.burrow.money.debug.HOME" -> HomeSurfaces.refresh(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
