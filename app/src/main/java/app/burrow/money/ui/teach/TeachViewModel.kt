package app.burrow.money.ui.teach

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.capture.MoneyType
import app.burrow.money.capture.Part
import app.burrow.money.capture.Rule
import app.burrow.money.capture.RuleBuilder
import app.burrow.money.capture.RuleEngine
import app.burrow.money.capture.TypeWord
import app.burrow.money.capture.TypeWords
import app.burrow.money.data.CaptureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three parts the user taps (52); Type is filled in by Burrow (235). */
val TAPPED_PARTS = listOf(Part.AMOUNT, Part.PAYEE, Part.ACCOUNT)

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
    /** "Use for messages like this" (149): the rule stays on for the next ones. */
    val useRule: Boolean = true,
    /** The words this rule adds per type ("Add a word", 236). */
    val ownWords: Map<MoneyType, List<String>> = emptyMap(),
    /** Built-in words taken away for this rule (✕, 236). */
    val removedWords: Map<MoneyType, List<String>> = emptyMap(),
    /** The type the user picked in the Type sheet; null = what the words say. */
    val pickedType: MoneyType? = null,
    /** "Add a word": the next tap on the message adds that word to [type]'s list. */
    val addingWord: Boolean = false,
    /** The phone's language: its word list, then English (TypeWords.builtIn). */
    val language: String = java.util.Locale.getDefault().language,
) {
    /** The word in the message that says what it is ("debited"), from the built-in list and the rule's own. */
    val found: TypeWord? get() = TypeWords.detect(text, ownWords, language, removedWords)
    /** The words that mean [type] for this rule, in the Type sheet's chips. */
    fun wordsFor(type: MoneyType): List<String> = TypeWords.merged(ownWords, language, removedWords)[type].orEmpty()
    /** What the message is: the user's pick, else its word, else an expense. */
    val type: MoneyType get() = pickedType ?: found?.type ?: MoneyType.EXPENSE
    /** Type is always shown and can be changed; Save needs only a rule that reads the amount back. */
    val canSave get() = rule != null
    /** The amount the message seems to have, ringed before the first tap (52). */
    val guess: IntRange? get() = if (Part.AMOUNT in picks) null else RuleBuilder.firstAmount(text)
}

/**
 * Teach a message (52, 235, 236): from a message not understood ([captureId]) or a pasted one. The
 * user taps the amount, who was paid and the account; the type comes from the words in the message
 * and can be changed in the Type sheet, which also adds words for it.
 */
class TeachViewModel(
    private val capture: CaptureRepository,
    private val engine: RuleEngine,
    private val captureId: Long,
    pastedSender: String,
    pastedText: String,
    private val fallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow<TeachState?>(null)
    val state: StateFlow<TeachState?> = _state.asStateFlow()

    /** "HDFC Bank" for a message from JM-HDFCBK-S; looked up once the message is loaded. */
    private var ruleName: String = fallbackName

    fun setUseRule(on: Boolean) = _state.update { it?.copy(useRule = on) }

    init {
        viewModelScope.launch {
            val c = if (captureId != 0L) capture.get(captureId) else null
            ruleName = capture.ruleNameFor(c?.sender ?: pastedSender) ?: fallbackName
            _state.value = TeachState(
                text = c?.body ?: pastedText,
                sender = c?.sender ?: pastedSender,
                receivedAt = c?.receivedAt,
                step = Part.AMOUNT,
                picks = emptyMap(),
            )
        }
    }

    fun tap(offset: Int) = _state.update { s ->
        s ?: return@update null
        val token = RuleBuilder.tokenAt(s.text, offset) ?: return@update s
        if (s.addingWord) {
            val word = s.text.substring(token).trim().trim('.', ',', ':', ';').lowercase()
            if (word.isEmpty() || RuleBuilder.isAmount(word)) return@update s.copy(addingWord = false)
            val type = s.type
            val words = (s.ownWords[type].orEmpty() + word).distinct()
            // Adding back a built-in word that was taken away just brings it back.
            val removed = s.removedWords[type].orEmpty().filterNot { it.equals(word, ignoreCase = true) }
            return@update rebuild(s.copy(ownWords = s.ownWords + (type to words), removedWords = s.removedWords + (type to removed), addingWord = false))
        }
        val step = s.step ?: return@update s
        if (step == Part.AMOUNT && !RuleBuilder.isAmount(s.text.substring(token))) return@update s.copy(notAmount = true)
        // A word already picked for another part stays there (a second tap on the amount did crash).
        if (s.picks.any { (part, r) -> part != step && r.first <= token.last && token.first <= r.last }) return@update s
        val picks = s.picks + (step to token)
        rebuild(s.copy(picks = picks, step = next(step, picks), notAmount = false))
    }

    fun choose(part: Part) = _state.update { it?.copy(step = part, notAmount = false, addingWord = false) }

    fun skip() = _state.update { s ->
        s ?: return@update null
        val step = s.step ?: return@update s
        rebuild(s.copy(picks = s.picks - step, step = next(step, s.picks)))
    }

    /** Type sheet (236): what a message like this means. */
    fun pickType(type: MoneyType) = _state.update { s -> s?.let { rebuild(it.copy(pickedType = type)) } }

    /** "Add a word": the sheet closes and the next tap on the message adds that word. */
    fun startAddingWord() = _state.update { it?.copy(addingWord = true) }

    /** ✕ on a word: the user's own goes; a built-in one is taken away for this rule. */
    fun removeWord(type: MoneyType, word: String) = _state.update { s ->
        s ?: return@update null
        val own = s.ownWords[type].orEmpty()
        if (word in own) {
            val left = own - word
            rebuild(s.copy(ownWords = if (left.isEmpty()) s.ownWords - type else s.ownWords + (type to left)))
        } else {
            rebuild(s.copy(removedWords = s.removedWords + (type to (s.removedWords[type].orEmpty() + word).distinct())))
        }
    }

    private fun next(after: Part, picks: Map<Part, IntRange>): Part? =
        TAPPED_PARTS.dropWhile { it != after }.drop(1).firstOrNull { it !in picks }

    private fun rebuild(s: TeachState): TeachState {
        val amount = s.picks[Part.AMOUNT] ?: return s.copy(rule = null)
        val built = RuleBuilder.build(
            id = "user.${System.currentTimeMillis()}", name = ruleName, sender = s.sender.ifBlank { null },
            text = s.text, amount = amount, payee = s.picks[Part.PAYEE], account = s.picks[Part.ACCOUNT],
        )
        // The words decide when they agree with the type shown, so a "credited" message from the same
        // bank is still read as income. When they don't (no word, or the user picked another type),
        // the rule keeps the picked type.
        val byWords = s.found?.type == s.type
        val rule = built.copy(
            direction = if (byWords) null else s.type.direction,
            type = if (byWords) null else s.type,
            typeWords = s.ownWords,
            language = s.language,
            removedWords = s.removedWords.filterValues { it.isNotEmpty() },
        )
        // Good only if the rule finds the amount exactly where it was tapped.
        val read = engine.parseWith(rule, s.sender.ifBlank { null }, s.text)
        val ok = read != null && read.spans[Part.AMOUNT]?.last == amount.last && read.type == s.type
        return s.copy(rule = if (ok) rule else null)
    }

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
     * A rule reads a kind of message, not one payment, so it has no category: each payment it reads
     * gets its payee's usual one in To review, where it can be changed. With "Use for messages like
     * this" off, the rule reads this message and is then switched off.
     */
    fun save(onDone: (Int) -> Unit) {
        val s = _state.value ?: return
        val rule = s.rule ?: return
        viewModelScope.launch {
            capture.saveRule(rule)
            val moved = capture.retryNotUnderstood()
            if (!s.useRule) capture.setRuleEnabled(rule.id, false)
            onDone(moved)
        }
    }
}
