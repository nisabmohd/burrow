package app.burrow.money.capture

/*
 * Imported rules are checked before they are saved (docs/CAPTURE.md § Rules file format): every
 * regex must compile and must finish on a hostile message within 50 ms, so a bad pattern can't
 * freeze capture (catastrophic backtracking).
 */
object RuleSafety {
    sealed interface Check {
        data object Ok : Check
        data class Bad(val ruleId: String, val reason: String) : Check
    }

    fun check(rule: Rule, budgetMs: Long = 50): Check {
        val sender = runCatching { Regex(rule.sender) }.getOrElse { return Check.Bad(rule.id, "sender: ${it.message}") }
        val pattern = runCatching { Regex(rule.pattern) }.getOrElse { return Check.Bad(rule.id, "pattern: ${it.message}") }
        if (!rule.pattern.contains("(?<amount>")) return Check.Bad(rule.id, "pattern has no amount group")
        for (probe in PROBES) {
            if (!finishes(pattern, probe, budgetMs) || !finishes(sender, probe, budgetMs)) return Check.Bad(rule.id, "too slow")
        }
        return Check.Ok
    }

    /** All rules of a file must pass; the first bad one is reported. */
    fun checkAll(rules: List<Rule>): Check = rules.asSequence().map { check(it) }.firstOrNull { it is Check.Bad } ?: Check.Ok

    private fun finishes(regex: Regex, text: String, budgetMs: Long): Boolean = try {
        regex.containsMatchIn(Deadline(text, System.nanoTime() + budgetMs * 1_000_000))
        true
    } catch (_: TooSlow) {
        false
    }

    private class TooSlow : RuntimeException()

    /** A CharSequence that gives up when its time is over: regex matching reads it char by char. */
    private class Deadline(private val s: CharSequence, private val until: Long) : CharSequence {
        private var reads = 0
        override val length get() = s.length
        override fun get(index: Int): Char {
            if (++reads and 0x3FF == 0 && System.nanoTime() > until) throw TooSlow()
            return s[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Deadline(s.subSequence(startIndex, endIndex), until)
        override fun toString() = s.toString()
    }

    private val PROBES = listOf(
        "a".repeat(3000) + "!",
        "1,".repeat(1500) + "x",
        "Rs. " + "9".repeat(2000) + " debited from A/c " + "X".repeat(500),
        " ".repeat(2000) + "\n".repeat(200),
    )
}
