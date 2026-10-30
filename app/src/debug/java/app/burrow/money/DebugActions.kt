package app.burrow.money

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.CaptureEntity
import app.burrow.money.data.CaptureStatus
import app.burrow.money.home.HomeSurfaces
import app.burrow.money.notify.runWeeklyReview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Debug builds only (never in a release): `adb shell am broadcast -a app.burrow.money.debug.SUNDAY_REVIEW`
 * sends the Sunday review now (as if it were this Sunday, 6:30 PM); `…debug.HOME` redraws the widgets
 * and shortcuts. Used by tools/smoke.py. `…debug.SEED_REVIEW` adds data to check the 2026-10-27 changes
 * by hand (TODO.md › Design todo): messages not understood, and three cards on one credit line.
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
                    "app.burrow.money.debug.SEED_REVIEW" -> seedReview(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Not understood: a card debit, a salary credit (Teach's "From / to") and a wallet message, in
 * wordings no built-in rule reads. To review is emptied (ignored) so Today shows its Not understood note.
 * Cards: Regalia holds a ₹2,00,000 limit; Millennia and an add-on share it (what's left counts all three).
 */
private suspend fun seedReview(app: AppContainer) {
    val db = app.database
    val now = System.currentTimeMillis()
    val hour = 3_600_000L
    db.captures().listWithStatus(CaptureStatus.REVIEW).forEach {
        app.capture.setStatus(it.id, CaptureStatus.IGNORED)
    }
    listOf(
        Triple("VM-ZETACD", "Txn alert: 1,249.00 INR went out from card 7781 to SWIGGY BLR. Ref 88213. Not you? Call 1800-000-111", 1L),
        Triple("JD-JUPTER", "Hey! 85,000.00 INR landed in your Jupiter pot 4410 from ACME PAYROLL. Happy spending!", 5L),
        Triple("AD-FAMPAY", "FamX: 320.00 INR moved out of wallet 0912 for RAPIDO RIDE on 27 Oct.", 26L),
    ).forEach { (sender, body, ago) ->
        db.captures().insert(
            CaptureEntity(
                source = "SMS", sender = sender, body = body, receivedAt = now - ago * hour,
                status = CaptureStatus.UNREAD,
            ),
        )
    }
    val currency = app.settings.settings.first().mainCurrency
    fun card(name: String, number: String, owes: Long, hue: Float, limit: Long? = null, sharesWith: Long? = null) =
        AccountEntity(
            name = name, type = AccountType.CARD, currency = currency, openingBalance = -owes * 100,
            colorHue = hue, icon = "credit_card", createdAt = now, number = number, creditLimit = limit?.times(100),
            statementDay = 5, dueDay = 25, includeInNetWorth = false, sharesLimitWith = sharesWith, sortOrder = 40,
        )
    val regalia = app.money.addAccount(card("HDFC Regalia", "4471", 45_000, 345f, limit = 2_00_000))
    app.money.addAccount(card("HDFC Millennia", "8820", 12_500, 210f, sharesWith = regalia))
    app.money.addAccount(card("Regalia add-on", "4472", 3_200, 30f, sharesWith = regalia))
}
