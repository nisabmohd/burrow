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

    /**
     * The first word that looks like an amount with a currency ("Rs.2,350.00", "₹180"), else the first
     * plain number with decimals: Teach rings it before the first tap (52). Null when there is none.
     */
    fun firstAmount(text: String): IntRange? {
        val tokens = Regex("\\S+").findAll(text).mapNotNull { m -> tokenAt(text, m.range.first) }.filter { isAmount(text.substring(it)) }.toList()
        // "XX4471" looks like an amount too: only a currency in front ("Rs.", "INR", "₹") counts first.
        val currency = Regex("(?i)^(rs\\.?|inr|aed|usd|eur|gbp|[₹$€£])\\s?\\d")
        return tokens.firstOrNull { currency.containsMatchIn(text.substring(it)) } ?: tokens.firstOrNull { text[it.first].isDigit() && text.substring(it).contains('.') }
    }

    /** An amount token: optional currency letters/symbol, then a number. */
    fun isAmount(token: String): Boolean = AMOUNT_TOKEN.matches(token)

    fun build(id: String, name: String, sender: String?, text: String, amount: IntRange, payee: IntRange?, account: IntRange?, directionWord: IntRange? = null): Rule {
        // A word picked for two parts counts once (the amount wins), so the text between parts never runs backwards.
        val parts = listOfNotNull(Part.AMOUNT to amount, payee?.let { Part.PAYEE to it }, account?.let { Part.ACCOUNT to it })
            .fold(emptyList<Pair<Part, IntRange>>()) { kept, p -> if (kept.any { it.second.overlaps(p.second) }) kept else kept + p }
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
        // The word the user tapped decides; otherwise guess from the whole message.
        val tapped = directionWord?.let { directionOf(text.substring(it)) }
        val direction = tapped ?: when {
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

    /**
     * What a tapped word means: money in for "credited", "received", "deposited", "refund", "added",
     * "cashback" (and their stems), money out for anything else ("debited", "spent", "paid", "sent").
     */
    fun directionOf(word: String): Direction {
        val w = word.lowercase()
        return if (IN_STEMS.any { w.contains(it) }) Direction.IN else Direction.OUT
    }

    private val IN_STEMS = listOf("credit", "receiv", "deposit", "refund", "added", "cashback", "reversal", "jama")

    /**
     * "AX-KOTAKB" or "JM-HDFCBK-S" → any sender with KOTAKB / HDFCBK as its name part (the operator
     * prefix and the -S / -T / -P / -G type tag change); a package name exactly; nothing → any sender.
     */
    fun senderPattern(sender: String?): String {
        val s = sender?.trim().orEmpty()
        return when {
            s.isEmpty() -> "(?s).*"
            isPackage(s) -> "^" + escape(s) + "$"
            else -> "(?i)" + escape(senderCore(s)) + TYPE_TAG + "$"
        }
    }

    /** "JM-HDFCBK-S" → "HDFCBK", "AX-KOTAKB" → "KOTAKB": the bank's part of an SMS sender. A package stays whole. */
    fun senderCore(sender: String): String {
        val s = sender.trim()
        if (isPackage(s)) return s
        var parts = s.split('-').filter { it.isNotBlank() }
        if (parts.size > 1 && parts.last().length == 1) parts = parts.dropLast(1)
        if (parts.size > 1 && parts.first().length == 2) parts = parts.drop(1)
        return parts.joinToString("-").ifBlank { s }
    }

    /** The optional "-S" / "-T" / "-P" / "-G" an Indian sender ends with. */
    const val TYPE_TAG = "(?:-[A-Z])?"

    fun isPackage(s: String) = s.contains('.') && !s.contains(' ')

    private fun group(part: Part, token: String): String = when (part) {
        Part.AMOUNT -> {
            val prefix = token.takeWhile { !it.isDigit() }.trimEnd()
            (if (prefix.isNotEmpty()) "(?<currency>" + escape(prefix).replace("\\.", "\\.?") + ")\\s?" else "") + "(?<amount>\\d[\\d,. ]*\\d|\\d)"
        }
        // A word, ending where the word ends (before any closing punctuation), even as the last part.
        Part.PAYEE -> "(?<payee>\\S+?)$WORD_END"
        Part.ACCOUNT -> if (ACCOUNT_TOKEN.matches(token)) "(?<account>[Xx*•.]*\\d+)" else "(?<account>\\S+?)$WORD_END"
        Part.DIRECTION -> literal(token) // never a group: it only sets the rule's direction
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

    private fun IntRange.overlaps(o: IntRange) = first <= o.last && o.first <= last

    private fun escape(s: String): String = buildString { s.forEach { ch -> if (ch in META) append('\\'); append(ch) } }

    private const val WORD_END = "(?=[.,;:)\"']?(?:\\s|$))"
    private const val META = "\\.[]{}()<>*+-=!?^$|/"
    private const val TRAILING = ".,;:)\"'"
    private val AMOUNT_TOKEN = Regex("^[^\\d\\s]{0,4}\\s?\\d[\\d,. ]*$")
    private val ACCOUNT_TOKEN = Regex("^[Xx*•.]*\\d{2,}$")
    private val DOT_COMMA_AMOUNT = Regex("\\d\\.\\d{3},\\d{2}$|\\d,\\d{2}$")
}
