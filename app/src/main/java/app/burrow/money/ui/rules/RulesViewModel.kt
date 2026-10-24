package app.burrow.money.ui.rules

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.burrow.money.capture.BUILT_IN_PACKS
import app.burrow.money.capture.Rule
import app.burrow.money.capture.RuleFiles
import app.burrow.money.capture.RuleSafety
import app.burrow.money.data.CaptureRepository
import app.burrow.money.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PackRow(val id: String, val ruleCount: Int, val on: Boolean)

@Immutable
data class RulesState(val mine: List<Rule>, val uses: Map<String, Int>, val packs: List<PackRow>, val waiting: Int)

/** Message rules (49): yours with switches, built-in packs on/off, import and share. */
class RulesViewModel(private val capture: CaptureRepository, private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<RulesState?> = combine(capture.userRules, capture.ruleUses, settings.settings, capture.notUnderstoodCount) { mine, uses, s, waiting ->
        RulesState(mine, uses, BUILT_IN_PACKS.map { PackRow(it.id, capture.pack(it.id)?.rules?.size ?: 0, it.id in s.enabledPacks) }, waiting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A built-in pack's rules and how many payments each has read (the pack screen). */
    val packUses: StateFlow<Map<String, Int>> = capture.packRuleUses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun packRules(id: String): List<Rule> = capture.pack(id)?.rules.orEmpty()

    fun setEnabled(id: String, on: Boolean) = viewModelScope.launch { capture.setRuleEnabled(id, on) }
    fun setPack(id: String, on: Boolean) = viewModelScope.launch { settings.setPackEnabled(id, on) }

    /** Reads a rules file; every rule must pass the safety check or nothing is added. Returns how many were added, or null for a bad file. */
    suspend fun import(text: String): Int? = withContext(Dispatchers.Default) {
        val file = runCatching { RuleFiles.decode(text) }.getOrNull() ?: return@withContext null
        if (file.rules.isEmpty() || RuleSafety.checkAll(file.rules) is RuleSafety.Check.Bad) return@withContext null
        val stamp = System.currentTimeMillis()
        file.rules.forEachIndexed { i, r -> capture.saveRule(r.copy(id = "user.$stamp.$i", userMade = true, enabled = true, example = null)) }
        capture.retryNotUnderstood()
        file.rules.size
    }

    /** The user's rules as a rules file, without message text. */
    suspend fun export(name: String): String = RuleFiles.export(name, capture.userRules.first())

    suspend fun firstWaiting(): Long? = capture.notUnderstood.first().firstOrNull()?.id
}
