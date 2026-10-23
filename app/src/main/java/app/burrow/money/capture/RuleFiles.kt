package app.burrow.money.capture

import kotlinx.serialization.json.Json

/*
 * Reading and writing rules files (docs/CAPTURE.md § Rules file format). Pure Kotlin.
 * A built-in pack is the same format in assets/rules/<country>.json.
 */
object RuleFiles {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

    fun decode(text: String): RuleFile = json.decodeFromString(RuleFile.serializer(), text)

    fun decodeRule(text: String): Rule = json.decodeFromString(Rule.serializer(), text)

    fun encodeRule(rule: Rule): String = json.encodeToString(Rule.serializer(), rule)

    /** A file to share: the user's rules without any message text from the phone. */
    fun export(name: String, rules: List<Rule>): String =
        json.encodeToString(RuleFile.serializer(), RuleFile(name = name, rules = rules.map { it.copy(example = null, enabled = true, userMade = false) }))
}

/** A built-in pack: [id] is the file name in assets/rules; on by default only in its [country]. */
data class PackInfo(val id: String, val country: String)

val BUILT_IN_PACKS = listOf(PackInfo("in", "IN"))
