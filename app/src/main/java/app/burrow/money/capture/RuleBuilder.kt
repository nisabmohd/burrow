package app.burrow.money.capture

/*
 * Teach (docs/CAPTURE.md § Teach): the user taps the amount, the payee and the account in a
 * message; this builds a rule from the taps. Pure Kotlin, unit-tested.
 *
 * The pattern is: a few words before the amount, then each tapped part as a named group with the
 * text between them kept literally, digits generalised (dates and references change). Long stretches
 * between parts shrink to their first and last words around a lazy gap.
 */
object RuleBuilder {

    /** The word at [offset] (a run of non-spaces), without trailing punctuation or brackets. */
    fun tokenAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        val o = offset.coerceIn(0, text.length - 1)
        if (text[o].isWhitespace()) return null
        var start = o
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = o
        while (end < text.length - 1 && !text[end + 1].isWhitespace()) end++
        while (end > start && text[end] in TRAILING) end--
        while (start < end && text[start] in "(\"'") start++
        return start..end
    }

    /** An amount token: optional currency letters/symbol, then a number. */
    fun isAmount(token: String): Boolean = AMOUNT_TOKEN.matches(token)

    fun build(id: String, name: String, sender: String?, text: String, amount: IntRange, payee: IntRange?, account: IntRange?): Rule {
        val parts = listOfNotNull(Part.AMOUNT to amount, payee?.let { Part.PAYEE to it }, account?.let { Part.ACCOUNT to it })
            .sortedBy { it.second.first }
        val sb = StringBuilder("(?s)")
        // A little context before the first part, so the rule only fires on this kind of message.
        sb.append(literal(lastWords(text.substring(0, parts.first().second.first), 2)))
        parts.forEachIndexed { i, (part, range) ->
            if (i > 0) sb.append(between(text.substring(parts[i - 1].second.last + 1, range.first)))
            sb.append(group(part, text.substring(range)))
        }
        val amountText = text.substring(amount)
        val lower = text.lowercase()
        val direction = when {
            Rule(id = "", name = "", sender = "", pattern = "").inWords.any { lower.contains(it) } -> Direction.IN
            Rule(id = "", name = "", sender = "", pattern = "").outWords.any { lower.contains(it) } -> Direction.OUT
            else -> null
        }
        return Rule(
            id = id,
            name = name,
            sender = senderPattern(sender),
            pattern = sb.toString(),
            numberStyle = if (DOT_COMMA_AMOUNT.containsMatchIn(amountText)) NumberStyle.DOT_COMMA else NumberStyle.COMMA_DOT,
            direction = direction ?: Direction.OUT,
            userMade = true,
            example = text,
        )
    }

    /** "AX-KOTAKB" → any sender ending in KOTAKB; a package name exactly; nothing → any sender. */
    fun senderPattern(sender: String?): String {
        val s = sender?.trim().orEmpty()
        return when {
            s.isEmpty() -> "(?s).*"
            s.contains('.') && !s.contains(' ') -> "^" + escape(s) + "$"
            else -> "(?i)" + escape(s.substringAfterLast('-')) + "$"
        }
    }

    private fun group(part: Part, token: String): String = when (part) {
        Part.AMOUNT -> {
            val prefix = token.takeWhile { !it.isDigit() }.trimEnd()
            (if (prefix.isNotEmpty()) "(?<currency>" + escape(prefix).replace("\\.", "\\.?") + ")\\s?" else "") + "(?<amount>\\d[\\d,. ]*\\d|\\d)"
        }
        // A word, ending where the word ends (before any closing punctuation), even as the last part.
        Part.PAYEE -> "(?<payee>\\S+?)$WORD_END"
        Part.ACCOUNT -> if (ACCOUNT_TOKEN.matches(token)) "(?<account>[Xx*•.]*\\d+)" else "(?<account>\\S+?)$WORD_END"
    }

    /** Text between two parts: kept, unless it is long, then its first and last two words around a gap. */
    private fun between(s: String): String {
        if (s.length <= 40) return literal(s)
        return literal(firstWords(s, 2)) + ".*?" + literal(lastWords(s, 2))
    }

    /** Escapes text, turns every number into "[\d,.]+" and every run of spaces into "\s+". */
    private fun literal(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isDigit() -> {
                    while (i < s.length && (s[i].isDigit() || (s[i] in ",." && i + 1 < s.length && s[i + 1].isDigit()))) i++
                    out.append("[\\d,.]+")
                    continue
                }
                ch.isWhitespace() -> {
                    while (i < s.length && s[i].isWhitespace()) i++
                    out.append("\\s+")
                    continue
                }
                else -> out.append(escape(ch.toString()))
            }
            i++
        }
        return out.toString()
    }

    private fun lastWords(s: String, n: Int): String {
        var count = 0
        var i = s.length
        while (i > 0) {
            if (s[i - 1].isWhitespace() && i < s.length && !s[i].isWhitespace()) {
                count++
                if (count == n) return s.substring(i)
            }
            i--
        }
        return s
    }

    private fun firstWords(s: String, n: Int): String {
        var count = 0
        for (i in s.indices) {
            if (i > 0 && s[i].isWhitespace() && !s[i - 1].isWhitespace()) {
                count++
                if (count == n) return s.substring(0, i + 1)
            }
        }
        return s
    }

    private fun escape(s: String): String = buildString { s.forEach { ch -> if (ch in META) append('\\'); append(ch) } }

    private const val WORD_END = "(?=[.,;:)\"']?(?:\\s|$))"
    private const val META = "\\.[]{}()<>*+-=!?^$|/"
    private const val TRAILING = ".,;:)\"'"
    private val AMOUNT_TOKEN = Regex("^[^\\d\\s]{0,4}\\s?\\d[\\d,. ]*$")
    private val ACCOUNT_TOKEN = Regex("^[Xx*•.]*\\d{3,}$")
    private val DOT_COMMA_AMOUNT = Regex("\\d\\.\\d{3},\\d{2}$|\\d,\\d{2}$")
}
