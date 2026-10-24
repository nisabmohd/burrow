package app.burrow.money.ui.teach

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.capture.Part
import app.burrow.money.capture.Rule
import app.burrow.money.capture.RuleBuilder
import app.burrow.money.capture.RuleEngine
import app.burrow.money.data.CaptureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TeachState(
    val text: String,
    val sender: String,
    val receivedAt: Long?,
    /** The part the next tap picks; null when all are picked or skipped. */
    val step: Part?,
    val picks: Map<Part, IntRange>,
    val notAmount: Boolean = false,
    /** The rule the taps make, when it reads the message back with the picked amount. */
    val rule: Rule? = null,
    /** This payment's category (149), when teaching a message that came in (not a pasted one). */
    val categoryId: Long? = null,
    /** "Use for messages like this" (149): the rule stays on for the next ones. */
    val useRule: Boolean = true,
) {
    val canSave get() = rule != null
}

/**
 * Teach a message (52): from a message not understood ([captureId]) or a pasted one.
 * Each tap picks the word under the finger for the current part.
 */
class TeachViewModel(
    private val capture: CaptureRepository,
    private val engine: RuleEngine,
    money: app.burrow.money.data.MoneyRepository,
    private val captureId: Long,
    pastedSender: String,
    pastedText: String,
    private val fallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow<TeachState?>(null)
    val state: StateFlow<TeachState?> = _state.asStateFlow()

    /** For the category row's sheet. */
    val categories: StateFlow<List<app.burrow.money.data.CategoryEntity>> =
        money.categories.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A payment to categorise: only a message that came in has one. */
    val hasPayment get() = captureId != 0L

    fun setCategory(id: Long) = _state.update { it?.copy(categoryId = id) }
    fun setUseRule(on: Boolean) = _state.update { it?.copy(useRule = on) }

    init {
        viewModelScope.launch {
            val c = if (captureId != 0L) capture.get(captureId) else null
            _state.value = TeachState(
                text = c?.body ?: pastedText,
                sender = c?.sender ?: pastedSender,
                receivedAt = c?.receivedAt,
                step = Part.AMOUNT,
                picks = emptyMap(),
                categoryId = c?.categoryId,
            )
        }
    }

    fun tap(offset: Int) = _state.update { s ->
        s ?: return@update null
        val step = s.step ?: return@update s
        val token = RuleBuilder.tokenAt(s.text, offset) ?: return@update s
        if (step == Part.AMOUNT && !RuleBuilder.isAmount(s.text.substring(token))) return@update s.copy(notAmount = true)
        val picks = s.picks + (step to token)
        rebuild(s.copy(picks = picks, step = next(step, picks), notAmount = false))
    }

    fun choose(part: Part) = _state.update { it?.copy(step = part, notAmount = false) }

    fun skip() = _state.update { s ->
        s ?: return@update null
        val step = s.step ?: return@update s
        rebuild(s.copy(picks = s.picks - step, step = next(step, s.picks)))
    }

    private fun next(after: Part, picks: Map<Part, IntRange>): Part? =
        Part.entries.dropWhile { it != after }.drop(1).firstOrNull { it !in picks }

    private fun rebuild(s: TeachState): TeachState {
        val amount = s.picks[Part.AMOUNT] ?: return s.copy(rule = null)
        val rule = RuleBuilder.build(
            id = "user.${System.currentTimeMillis()}", name = name(s.sender), sender = s.sender.ifBlank { null },
            text = s.text, amount = amount, payee = s.picks[Part.PAYEE], account = s.picks[Part.ACCOUNT],
            directionWord = s.picks[Part.DIRECTION],
        )
        // Good only if the rule finds the amount exactly where it was tapped.
        val read = engine.parseWith(rule, s.sender.ifBlank { null }, s.text)
        val ok = read != null && read.spans[Part.AMOUNT]?.last == amount.last
        return s.copy(rule = if (ok) rule else null)
    }

    private fun name(sender: String): String =
        sender.substringAfterLast('-').trim().ifBlank { fallbackName }

    /** A message from the inbox can be ignored instead (not a payment, or not worth a rule). */
    val canIgnore get() = captureId != 0L

    fun ignore(onDone: (suspend () -> Unit) -> Unit) {
        if (captureId == 0L) return
        viewModelScope.launch {
            capture.setStatus(captureId, app.burrow.money.data.CaptureStatus.IGNORED)
            onDone { capture.setStatus(captureId, app.burrow.money.data.CaptureStatus.UNREAD) }
        }
    }

    /**
     * Saves the rule and tries it on every message waiting; returns how many moved to To review.
     * This message keeps the category picked here. With "Use for messages like this" off, the rule
     * reads this message and is then switched off.
     */
    fun save(onDone: (Int) -> Unit) {
        val s = _state.value ?: return
        val rule = s.rule ?: return
        viewModelScope.launch {
            capture.saveRule(rule)
            if (s.categoryId == null && captureId != 0L) {
                // Suggest the payee's usual category for this payment, as capture does.
                val payee = s.picks[Part.PAYEE]?.let { s.text.substring(it) }
                capture.suggestions(payee, if (rule.direction == app.burrow.money.capture.Direction.IN) app.burrow.money.data.TxnType.INCOME else app.burrow.money.data.TxnType.EXPENSE).firstOrNull()
                    ?.let { _state.update { st -> st?.copy(categoryId = it) } }
            }
            val moved = capture.retryNotUnderstood()
            _state.value?.categoryId?.let { if (captureId != 0L) capture.setCategory(captureId, it) }
            if (!s.useRule) capture.setRuleEnabled(rule.id, false)
            onDone(moved)
        }
    }
}
