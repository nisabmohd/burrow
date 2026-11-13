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

    // --- Check a message (52, final 12 Nov): Burrow's own reading of a message no rule could read. ---

    /** Words after which a name follows: "to VPA zomato@…", "at DECATHLON", "towards ZOMATO", "from RAHUL". */
    private val NAME_AFTER = Regex("""(?i)\b(?:vpa|towards|at|to|from|by)\s+(?=(\S+))""")  // lookahead: "to VPA zomato" tries both

    /** Words that are never the payee or account ("to your", "from A/c", "at the"). */
    private val NOT_A_NAME = setOf(
        "a/c", "ac", "acct", "account", "your", "you", "the", "a", "an", "vpa", "upi", "card", "bank", "on", "rs", "rs.", "inr",
        "ref", "info", "txn", "via", "of", "for", "with", "is", "has", "been", "debited", "credited", "sent", "paid", "received",
        "not", "call", "avl", "bal", "lmt", "wallet", "counter", "mobile", "number", "beneficiary", "upi-",
        // How the money moved is never who: "credited by NEFT from ACME" is ACME.
        "neft", "imps", "rtgs", "nach", "ecs", "cheque", "cash", "atm", "pos", "ach", "swift",
    )

    /** "XX4471", "**4471", "••4471", "x1234": the masked account or card number banks write. */
    private val MASKED = Regex("""(?i)^[x*•.]+\d{2,6}$""")

    /** Words a card or account number follows: "Card 7711", "ending 3321", "A/c 4471". */
    private val NUMBER_AFTER = Regex("""(?i)\b(?:ending|card|a/c|ac|acct|account)(?:\s+no\.?)?\s+(?=(\S+))""")

    private fun tokens(text: String): List<IntRange> =
        Regex("\\S+").findAll(text).mapNotNull { m -> tokenAt(text, m.range.first) }.distinct().toList()

    private fun nameLike(word: String): Boolean =
        word.length >= 2 && word.lowercase() !in NOT_A_NAME && (word.first().isLetter() && word.none { it.isDigit() } || '@' in word)

    /** Who was paid (or who paid): the first name-like word after to / at / towards / VPA / from. Null rather than a wrong guess. */
    fun guessPayee(text: String): IntRange? = NAME_AFTER.findAll(text).firstNotNullOfOrNull { m ->
        val g = m.groups[1] ?: return@firstNotNullOfOrNull null
        tokenAt(text, g.range.first)?.takeIf { nameLike(text.substring(it)) }
    }

    /** The account or card: the first masked number ("XX4471"), else the 3–6 digits after Card / ending / A/c. */
    fun guessAccount(text: String): IntRange? =
        tokens(text).firstOrNull { MASKED.matches(text.substring(it)) }
            ?: NUMBER_AFTER.findAll(text).firstNotNullOfOrNull { m ->
                val g = m.groups[1] ?: return@firstNotNullOfOrNull null
                tokenAt(text, g.range.first)?.takeIf { Regex("""^\d{3,6}$""").matches(text.substring(it)) }
            }

    /**
     * Fix it (239): the words [part] could be, ringed in the message; [taken] (the parts already read) are left out.
     * Amount: every word with a digit that isn't a masked account. Paid to: names (after to / at / VPA…, a UPI id, or a
     * word in capitals). Account: masked numbers and plain 3–6 digit numbers.
     */
    fun candidates(text: String, part: Part, taken: List<IntRange>): List<IntRange> {
        val after = NAME_AFTER.findAll(text).mapNotNull { m -> m.groups[1]?.let { tokenAt(text, it.range.first) } }.toSet()
        return tokens(text).filter { r -> taken.none { it.overlaps(r) } }.filter { r ->
            val w = text.substring(r)
            when (part) {
                Part.AMOUNT -> w.any { it.isDigit() } && !MASKED.matches(w)
                Part.PAYEE -> nameLike(w) && ('@' in w || r in after || w.length >= 3 && w.all { it.isUpperCase() || !it.isLetter() } && w.any { it.isLetter() })
                else -> MASKED.matches(w) || Regex("""^\d{3,6}$""").matches(w)
            }
        }
    }

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
