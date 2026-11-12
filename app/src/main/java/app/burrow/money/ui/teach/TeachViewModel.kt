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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The parts Burrow reads and the user can fix (52, 235); Type is the fourth row. */
val TAPPED_PARTS = listOf(Part.AMOUNT, Part.PAYEE, Part.ACCOUNT)

/** Check a message (52): Burrow's reading and "Is this right?"; Fix it (235, 239): What's wrong? and the ringed choices. */
enum class TeachMode { CHECK, FIX }

@Immutable
data class TeachState(
    val text: String,
    val sender: String,
    val receivedAt: Long?,
    /** Fix it: the part the next tap picks (239); null = the What's wrong? list (235), or nothing to pick (CHECK). */
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
    val mode: TeachMode = TeachMode.CHECK,
    /** "1 of 2": where this message is among the ones waiting; 0 = a pasted message (no counter). */
    val position: Int = 0,
    val total: Int = 0,
    /** Every waiting message checked (149): All checked · Back to Review. */
    val done: Boolean = false,
    /** The bank, by name ("Kotak Bank"), for the message header and "from Kotak ••4471". */
    val bank: String = "",
    /** What the rule reads back: the amount in minor units and its currency, for the big number. */
    val read: app.burrow.money.capture.Parsed? = null,
) {
    /** The word in the message that says what it is ("debited"), from the built-in list and the rule's own. */
    val found: TypeWord? get() = TypeWords.detect(text, ownWords, language, removedWords)
    /** The words that mean [type] for this rule, in the Type sheet's chips. */
    fun wordsFor(type: MoneyType): List<String> = TypeWords.merged(ownWords, language, removedWords)[type].orEmpty()
    /** What the message is: the user's pick, else its word, else an expense. */
    val type: MoneyType get() = pickedType ?: found?.type ?: MoneyType.EXPENSE
    /** The message has no word that says out or in, and nothing is picked yet: "Type unknown", "Did money go out or in?" (240). */
    val typeUnknown get() = found == null && pickedType == null
    /** Ask out or in under the message while its words don't say (240), the chips staying once one is picked. */
    val asksType get() = found == null
    /** Yes, save: a rule that reads the amount back, and a type (240 waits for a pick). */
    val canSave get() = rule != null && !typeUnknown
    /** Fix it (239): the words the part being fixed could be, ringed in the message. */
    val ringed: List<IntRange> get() = step?.let { p -> RuleBuilder.candidates(text, p, listOfNotNull(picks[p])) }.orEmpty()  // the current pick is filled, not ringed; other parts may be ringed (PNG 240: 3321)
}

/**
 * Check a message (52, 235, 236, 239, 240, 149; final 12 Nov): from a message not understood ([captureId], then the
 * next ones waiting) or a pasted one. Burrow reads the amount, who was paid, the account and the type itself and asks
 * "Is this right?"; No, fix it lists the parts, and fixing one rings what it could be in the message. Yes, save keeps
 * the rule, so messages like it are read from then on.
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

    /** The messages waiting when Check a message opened, in order: "1 of 2", then the next after each save. */
    private var queue: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            if (captureId != 0L) {
                queue = capture.notUnderstood.first().map { it.id }.let { ids -> if (captureId in ids) ids.dropWhile { it != captureId } else listOf(captureId) }
                load(captureId, 1)
            } else {
                ruleName = capture.ruleNameFor(pastedSender) ?: fallbackName
                _state.value = read(TeachState(text = pastedText, sender = pastedSender, receivedAt = null, step = null, picks = emptyMap(), bank = bankOf(pastedSender)))
            }
        }
    }

    private suspend fun load(id: Long, position: Int) {
        val c = capture.get(id) ?: return
        ruleName = capture.ruleNameFor(c.sender) ?: fallbackName
        current = id
        _state.value = read(
            TeachState(
                text = c.body, sender = c.sender, receivedAt = c.receivedAt, step = null, picks = emptyMap(),
                position = position, total = queue.size, bank = bankOf(c.sender),
            ),
        )
    }

    /** The bank by name when a rule knows the sender; "" otherwise (never the fallback rule name: "from My ••4471"). */
    private suspend fun bankOf(sender: String): String = sender.ifBlank { null }?.let { capture.bankName(it) ?: capture.ruleNameFor(it) }.orEmpty()

    /** The message on screen (the queue moves on after each save). */
    private var current: Long = captureId

    /**
     * Burrow reads it first (52): the amount, who was paid and the account it can find, and the type from its words.
     * With no amount it opens on Fix it › Tap the right amount.
     */
    private fun read(s: TeachState): TeachState {
        val picks = listOfNotNull(
            RuleBuilder.firstAmount(s.text)?.let { Part.AMOUNT to it },
            RuleBuilder.guessPayee(s.text)?.let { Part.PAYEE to it },
            RuleBuilder.guessAccount(s.text)?.let { Part.ACCOUNT to it },
        ).fold(emptyMap<Part, IntRange>()) { kept, (p, r) -> if (kept.values.any { it.first <= r.last && r.first <= it.last }) kept else kept + (p to r) }
        val built = rebuild(s.copy(picks = picks))
        return if (Part.AMOUNT in picks) built else built.copy(mode = TeachMode.FIX, step = Part.AMOUNT)
    }

    /** No, fix it: What's wrong? (235). */
    fun fix() = _state.update { it?.copy(mode = TeachMode.FIX, step = null, notAmount = false) }

    /** Done on Fix it: back to Is this right? with what was fixed. */
    fun doneFixing() = _state.update { it?.copy(mode = TeachMode.CHECK, step = null, notAmount = false, addingWord = false) }

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
        // Picked: back to What's wrong? with the new reading.
        rebuild(s.copy(picks = s.picks + (step to token), step = null, notAmount = false))
    }

    /** Fix it › a row: Tap the right amount / name / account (239). */
    fun choose(part: Part) = _state.update { it?.copy(mode = TeachMode.FIX, step = part, notAmount = false, addingWord = false) }

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
        return s.copy(rule = if (ok) rule else null, read = if (ok) read else null)
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
        if (!s.canSave) return
        viewModelScope.launch {
            capture.saveRule(rule)
            val moved = capture.retryNotUnderstood()
            if (!s.useRule) capture.setRuleEnabled(rule.id, false)
            onDone(moved)
            // The next message still waiting (the new rule may have read some of them), else All checked (149).
            if (s.total == 0) return@launch
            val waiting = capture.notUnderstood.first().map { it.id }.toSet()
            val next = queue.dropWhile { it != current }.drop(1).firstOrNull { it in waiting }
            if (next != null) load(next, queue.indexOf(next) + 1) else _state.update { it?.copy(done = true) }
        }
    }
}
