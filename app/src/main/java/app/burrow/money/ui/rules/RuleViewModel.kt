package app.burrow.money.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.capture.Parsed
import app.burrow.money.capture.Rule
import app.burrow.money.capture.RuleEngine
import app.burrow.money.data.CaptureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One rule (50). Test it runs the rule on pasted text as you type. */
class RuleViewModel(private val capture: CaptureRepository, private val engine: RuleEngine, val id: String) : ViewModel() {
    val rule: StateFlow<Rule?> = capture.rule(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val test = MutableStateFlow("")
    val testText: StateFlow<String> = test.asStateFlow()

    fun setTest(text: String) {
        test.value = text
    }

    /** The rule on any text, sender ignored (the user pasted it). */
    fun read(r: Rule, text: String): Parsed? = if (text.isBlank()) null else engine.parseWith(r, null, text)

    fun setEnabled(on: Boolean) = viewModelScope.launch { capture.setRuleEnabled(id, on) }

    fun rename(name: String) = viewModelScope.launch { rule.first()?.let { capture.renameRule(it, name) } }

    fun delete(onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch {
        val r = rule.first() ?: return@launch
        capture.deleteRule(id)
        onDone { capture.saveRule(r) }
    }
}
