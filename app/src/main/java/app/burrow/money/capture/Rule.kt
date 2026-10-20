package app.burrow.money.capture

import kotlinx.serialization.Serializable

/*
 * Message rules (docs/CAPTURE.md). Pure Kotlin, no Android imports: unit-tested on the JVM.
 * A rule turns one bank's message format into a payment. Built-in packs per country ship in
 * assets/rules/<country>.json; users teach their own; anyone can share a rules file.
 * A rules file NEVER contains message text from the user's phone, only the patterns.
 */

enum class Direction { OUT, IN }

/** How numbers are written in the message. */
enum class NumberStyle(val group: Char?, val decimal: Char) {
    COMMA_DOT(',', '.'),        // 1,234.56  and Indian 1,23,456.78 (grouping is ignored)
    DOT_COMMA('.', ','),        // 1.234,56
    SPACE_COMMA(' ', ','),      // 1 234,56
    PLAIN(null, '.'),           // 1234.56
}

@Serializable
data class RuleFile(val version: Int = 1, val name: String, val country: String? = null, val rules: List<Rule>)

/**
 * [sender] is a regex on the sender id ("(?i)^[A-Z]{2}-HDFCBK$").
 * [pattern] is a regex with named groups: amount (required), payee, account, balance, currency.
 * [direction] is fixed, or decided by [inWords]/[outWords] found in the message.
 */
@Serializable
data class Rule(
    val id: String,
    val name: String,
    val sender: String,
    val pattern: String,
    val language: String = "en",
    val numberStyle: NumberStyle = NumberStyle.COMMA_DOT,
    val direction: Direction? = Direction.OUT,
    val inWords: List<String> = listOf("credited", "received", "deposited"),
    val outWords: List<String> = listOf("debited", "spent", "paid", "sent", "withdrawn"),
    val currency: String? = null,
    val enabled: Boolean = true,
    val userMade: Boolean = false,
    /**
     * A sample message the rule reads (shown in Rule detail and used by its test). Packs ship
     * anonymised examples; a taught rule keeps the user's message on the phone only and
     * [RuleFiles.export] leaves it out, so shared files never carry message text.
     */
    val example: String? = null,
)

/** The parts of a message a rule picked out, for highlighting (Review, Rule, Teach). */
enum class Part { AMOUNT, PAYEE, ACCOUNT }

data class Parsed(
    val amountMinor: Long,
    val currency: String?,
    val direction: Direction,
    val payee: String?,
    val account: String?,
    val ruleId: String,
    /** Where each part sits in the message. */
    val spans: Map<Part, IntRange> = emptyMap(),
)
