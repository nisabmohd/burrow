package app.burrow.money.ui.capture

import app.burrow.money.domain.BudgetPeriod
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.capture.onMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

@Immutable
data class AutoCaptureState(
    val recordedThisMonth: Int,
    val sms: Boolean,
    val apps: Boolean,
    val smsBanks: List<String>,
    val notUnderstood: Int,
    val yourRules: Int,
    /** "92% · Recorded by itself" (15). */
    val trust: app.burrow.money.data.Trust = app.burrow.money.data.Trust(0, 0),
    /** Who sent the messages it couldn't read ("Kotak and SBI Card"). */
    val unreadFrom: List<String> = emptyList(),
    /** The India pack is on: "Built-in for India" on the Message rules row. */
    val indiaPack: Boolean = false,
)

/** Auto-capture (48): the two switches, messages to teach, rules, Try it. */
class AutoCaptureViewModel(private val app: AppContainer, monthStartDay: Int) : ViewModel() {
    val state: StateFlow<AutoCaptureState?> = combine(
        combine(app.capture.savedThisMonth(monthStartDay), app.next.trust(BudgetPeriod.containing(java.time.LocalDate.now(), monthStartDay)), app.next.unreadSenders) { a, b, c -> Triple(a, b, c) },
        app.settings.settings,
        app.capture.banksSeen("SMS"),
        app.capture.notUnderstoodCount,
        app.capture.userRules,
    ) { (saved, trust, from), s, banks, unread, mine ->
        AutoCaptureState(saved, s.smsCapture, s.notificationCapture, banks, unread, mine.size, trust, from.map(::senderName).distinct(), "in" in s.enabledPacks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSms(on: Boolean) = viewModelScope.launch { app.settings.setSmsCapture(on) }
    fun setApps(on: Boolean) = viewModelScope.launch { app.settings.setNotificationCapture(on) }

    /**
     * Runs a made-up message through the real pipeline, so the capture notification shows: an HDFC
     * UPI message when the India pack is on, otherwise a plain card payment in the main currency.
     */
    fun tryIt() = viewModelScope.launch {
        if ("in" in app.settings.settings.first().enabledPacks) {
            val rupees = 100 + Random.nextInt(400)
            val ref = 600_000_000 + Random.nextInt(99_999_999)
            app.onMessage("SMS", "AX-HDFCBK", "Rs.$rupees.00 debited from A/c XX1234 on today to VPA thirdwave@okicici. UPI Ref $ref.")
        } else {
            app.onMessage("SMS", SAMPLE_SENDER, "Card XX1234 paid ${5 + Random.nextInt(35)} at Third Wave Coffee.", extra = listOf(SAMPLE_RULE))
        }
    }

    /** The newest message not understood, for Teach. */
    suspend fun firstNotUnderstood(): Long? = app.capture.notUnderstood.first().firstOrNull()?.id
}

private const val SAMPLE_SENDER = "BURROW"

/** Reads only Try it's own sample; never part of the saved rules. */
private val SAMPLE_RULE = app.burrow.money.capture.Rule(
    id = "sample", name = "Burrow", sender = "^$SAMPLE_SENDER$",
    pattern = "Card [Xx]*(?<account>\\d{2,}) paid (?<amount>\\d+) at (?<payee>.+?)\\.$",
)

/** "AX-HDFCBK" → "HDFCBK", "VM-KOTAKB" → "KOTAKB": the sender without its operator prefix. */
fun senderName(sender: String): String = sender.substringAfter('-', sender).ifBlank { sender }
