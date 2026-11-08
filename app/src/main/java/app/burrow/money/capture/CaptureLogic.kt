package app.burrow.money.capture

import kotlin.math.abs

/*
 * The decisions around a parsed message (docs/CAPTURE.md § Pipeline), pure and unit-tested.
 */

/** A payment already captured, as far as duplicate checks need to know. */
data class Seen(val id: Long, val source: String, val amount: Long, val account: String?, val reference: String?, val at: Long)

const val DEDUPE_WINDOW_MS = 10 * 60 * 1000L

/**
 * The same payment twice (the bank SMS and the payment app's notification): same amount within
 * 10 minutes and the same account or reference. A notification from a payment app often names
 * neither, so a pair from different sources where one has no account also counts.
 */
fun findDuplicate(source: String, amount: Long, account: String?, reference: String?, at: Long, seen: List<Seen>): Seen? =
    seen.firstOrNull { s ->
        s.amount == amount && abs(s.at - at) <= DEDUPE_WINDOW_MS && (
            (account != null && s.account != null && sameAccount(account, s.account)) ||
                (reference != null && reference == s.reference) ||
                (source != s.source && (account == null || s.account == null))
            )
    }

/** "XX12", "*1234", "••123456" and "1234" can be the same account: compare the last digits both have (at least 2). */
fun sameAccount(a: String, b: String): Boolean {
    val da = a.filter { it.isDigit() }
    val db = b.filter { it.isDigit() }
    if (da.isEmpty() || db.isEmpty()) return a.equals(b, ignoreCase = true)
    val n = minOf(da.length, db.length)
    return n >= 2 && da.takeLast(n) == db.takeLast(n)
}

/** How many last digits two account numbers share. */
fun sharedDigits(a: String, b: String): Int {
    val da = a.filter { it.isDigit() }
    val db = b.filter { it.isDigit() }
    var n = 0
    while (n < da.length && n < db.length && da[da.length - 1 - n] == db[db.length - 1 - n]) n++
    return n
}

private val REFERENCE = Regex("(?i)(?:ref(?:erence)?\\s*(?:no\\.?)?|refno|utr|txn\\s*id|upi)[\\s:#./-]*(\\d{6,})")

/** The UPI / transaction reference in a message, if any. */
fun referenceIn(body: String): String? = REFERENCE.find(body)?.groupValues?.get(1)

private val MONEY = Regex("(?i)(?:rs\\.?|inr|₹|aed|usd|eur|gbp|[$€£])\\s?\\d")
private val MONEY_WORDS = Regex("(?i)\\b(debited|credited|spent|paid|sent|received|withdrawn|purchase|dr\\.?|cr\\.?)\\b")

/**
 * A message worth keeping for Teach when no rule reads it: an amount and a money word, from a
 * business sender (alphanumeric ids like "AX-KOTAKB", or an app). Personal numbers never count.
 */
fun looksLikePayment(sender: String, body: String): Boolean {
    val personal = sender.filter { it.isLetter() }.isEmpty()
    return !personal && MONEY.containsMatchIn(body) && MONEY_WORDS.containsMatchIn(body)
}

/** An account the user has, for matching. */
data class AccountRef(val id: Long, val name: String, val number: String?, val type: String)

/**
 * Which account a message is about: by its last digits; else the one whose name has the bank's
 * first word ("HDFC Bank" → "HDFC Savings", "Paytm Wallet" → "Paytm wallet"); else the first
 * everyday account.
 */
fun matchAccount(captured: String?, bankName: String?, accounts: List<AccountRef>): Long? {
    if (captured != null) {
        // Banks show 2 to 6 last digits; when several accounts fit, the longest overlap wins.
        accounts.filter { it.number != null && sameAccount(captured, it.number) }
            .maxByOrNull { sharedDigits(captured, it.number!!) }?.let { return it.id }
        accounts.firstOrNull { it.name.equals(captured, ignoreCase = true) }?.let { return it.id }
    }
    val word = bankName?.substringBefore(' ')?.takeIf { it.length >= 3 }
    if (word != null) accounts.firstOrNull { it.name.contains(word, ignoreCase = true) }?.let { return it.id }
    return accounts.firstOrNull { it.type != "LOAN" && it.type != "SAVINGS" }?.id
}

/** A card on a card account's bill, for matching (v8). */
data class CardRef(val id: Long, val accountId: Long, val number: String)

/**
 * Accounts to match against: each card's digits count as its account's ([cards] on one bill all
 * reach the same account).
 */
fun withCards(accounts: List<AccountRef>, cards: List<CardRef>): List<AccountRef> =
    accounts + cards.mapNotNull { c -> accounts.firstOrNull { it.id == c.accountId }?.copy(number = c.number) }

/**
 * The card a message on [accountId] was paid with: only when that account has two or more cards
 * (one card says nothing new), by the longest run of matching last digits.
 */
fun matchCard(captured: String?, accountId: Long, cards: List<CardRef>): Long? {
    val mine = cards.filter { it.accountId == accountId }
    if (captured == null || mine.size < 2) return null
    return mine.filter { sameAccount(captured, it.number) }.maxByOrNull { sharedDigits(captured, it.number) }?.id
}

/** The name people use for a bank ("ICICI Bank" → "ICICI", "State Bank of India" → "SBI"); a typed name's first word. */
fun bankShortName(bank: String): String = BANK_SHORT[bank] ?: bank.trim().substringBefore(' ')

private val BANK_SHORT = mapOf(
    "State Bank of India" to "SBI", "Bank of Baroda" to "BoB", "Punjab National Bank" to "PNB",
    "Kotak Mahindra Bank" to "Kotak", "IDFC FIRST Bank" to "IDFC FIRST",
)

/** The short name to show under a bank in the Bank sheet (196): only when it isn't the name's first word. */
fun bankShortLine(bank: String): String? = bankShortName(bank).takeIf { it != bank.substringBefore(' ') }

/** Banks to pick for a new card (196): the rule packs' bank names, one per short name (the fullest), A–Z. */
fun cardBanks(ruleNames: List<String>): List<String> = ruleNames
    .filter { it.contains("Bank", ignoreCase = true) && !it.equals("Bank", ignoreCase = true) }
    .groupBy { bankShortName(it).lowercase() }.values.map { names -> names.maxBy { it.length } }
    .sortedBy { it.lowercase() }

/** An account name that is the bank's ("ICICI Credit", "IDFC FIRST Wealth", "Kotak"). */
private fun ofBank(accountName: String, bank: String): Boolean {
    val short = bankShortName(bank)
    return accountName.equals(short, ignoreCase = true) || accountName.startsWith("$short ", ignoreCase = true)
}

/** Your banks (196): the banks some account of yours is named after. */
fun yourBanks(banks: List<String>, accountNames: List<String>): List<String> = banks.filter { b -> accountNames.any { ofBank(it, b) } }

/** The card account a new card from [bank] may join (191): its name starts with the bank's short name. */
fun cardOfBank(bank: String, cards: List<AccountRef>): AccountRef? = cards.firstOrNull { it.type == "CARD" && ofBank(it.name, bank) }
