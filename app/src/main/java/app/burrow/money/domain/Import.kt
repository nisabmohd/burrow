package app.burrow.money.domain

import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/*
 * Import from another app (10, 11): Cashew, Walnut, Money Manager or any spreadsheet. Everything is
 * read on the phone. Columns are found by their names; "Any spreadsheet" lets the user change them.
 * Pure: no Android, unit-tested with each app's real export shape.
 */

enum class ImportSource { CASHEW, WALNUT, MONEY_MANAGER, CSV }

/** What a column holds. Only DATE and AMOUNT (or DEBIT / CREDIT) are needed. */
enum class ImportField { DATE, TIME, AMOUNT, DEBIT, CREDIT, TYPE, PAYEE, CATEGORY, ACCOUNT, NOTE, CURRENCY }

/** One payment read from the file. [amount] is positive, in minor units; [income] gives the side. */
data class ImportRow(
    val date: LocalDateTime,
    val amount: Long,
    val income: Boolean,
    val payee: String?,
    val category: String?,
    val account: String?,
    val note: String?,
    val currency: String?,
    /** Cashew's "Balance Correction": money moved between the user's own accounts, never spending. */
    val adjustment: Boolean = false,
)

/** Two balance corrections that are one move between accounts: [from] lost the money, [to] got it. */
data class ImportTransfer(val from: ImportRow, val to: ImportRow)

/** A file read through: its header, which column holds what, the rows, and how many lines were left out. */
data class ParsedImport(
    val headers: List<String>,
    val columns: Map<ImportField, Int>,
    val rows: List<ImportRow>,
    val skipped: Int,
) {
    val first get() = rows.minOfOrNull { it.date }
    val last get() = rows.maxOfOrNull { it.date }
    /** Category names of real payments (balance corrections aren't spending). */
    val categories get() = rows.filter { !it.adjustment }.mapNotNull { it.category?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount()
    /** Their category names used mostly for money in: they become income categories. */
    val incomeCategories: Set<String> get() = rows.filter { !it.adjustment && it.category != null }.groupBy { it.category!! }
        .filterValues { r -> r.count { it.income } * 2 > r.size }.keys
    val accounts get() = rows.mapNotNull { it.account?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount()
}

object Importer {
    /** Header names each field goes by, lower case, in the order they're tried. */
    private val ALIASES: Map<ImportField, List<String>> = mapOf(
        ImportField.DATE to listOf("date", "transaction date", "txn date", "date & time", "datetime", "date/time", "value date", "posting date", "period"),
        ImportField.TIME to listOf("time"),
        ImportField.AMOUNT to listOf("amount", "value", "sum", "amount (inr)", "amount (₹)", "transaction amount"),
        ImportField.DEBIT to listOf("debit", "withdrawal", "withdrawal amt.", "withdrawal amount", "expense", "spent", "money out", "paid out"),
        ImportField.CREDIT to listOf("credit", "deposit", "deposit amt.", "deposit amount", "income amount", "money in", "paid in"),
        ImportField.TYPE to listOf("type", "income/expense", "income/expenses", "dr/cr", "cr/dr", "income", "transaction type", "direction", "kind"),
        ImportField.PAYEE to listOf("payee", "title", "place", "merchant", "description", "name", "narration", "particulars", "details", "to"),
        ImportField.CATEGORY to listOf("category", "category name", "categories"),
        ImportField.ACCOUNT to listOf("account", "account name", "bank", "wallet", "payment mode", "mode"),
        ImportField.NOTE to listOf("note", "notes", "memo", "remark", "remarks", "comment", "tags"),
        ImportField.CURRENCY to listOf("currency", "currency code"),
    )

    /** Splits the file into rows of cells: commas, semicolons or tabs, quotes and line breaks in quotes. */
    fun table(text: String): List<List<String>> {
        val t = text.removePrefix("﻿")
        val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val sep = listOf('\t', ';', ',').maxByOrNull { c -> firstLine.count { it == c } }?.takeIf { c -> firstLine.count { it == c } > 0 } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            when {
                quoted && ch == '"' && i + 1 < t.length && t[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { row.add(cell.toString().trim()); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++
                    row.add(cell.toString().trim()); cell.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString().trim()); if (row.any { it.isNotBlank() }) rows.add(row) }
        return rows
    }

    /** Which column holds what, by header name; an exact name beats one that only contains it. */
    fun detect(headers: List<String>): Map<ImportField, Int> {
        val h = headers.map { it.trim().lowercase(Locale.ROOT) }
        val taken = mutableSetOf<Int>()
        val out = linkedMapOf<ImportField, Int>()
        // Exact names first for every field, so "Income/Expense" isn't taken as an expense column.
        for (field in ImportField.entries) {
            val exact = ALIASES.getValue(field).firstNotNullOfOrNull { n -> h.indices.firstOrNull { it !in taken && h[it] == n } }
            if (exact != null) { out[field] = exact; taken += exact }
        }
        for (field in ImportField.entries) {
            if (field in out || field == ImportField.TIME || field == ImportField.TYPE) continue
            val loose = ALIASES.getValue(field).firstNotNullOfOrNull { n -> h.indices.firstOrNull { it !in taken && n.length > 3 && h[it].contains(n) } }
            if (loose != null) { out[field] = loose; taken += loose }
        }
        // Money Manager writes the amount under the currency's code ("INR") as well as "Amount".
        if (ImportField.AMOUNT !in out) h.indexOfFirst { it.length == 3 && it.all(Char::isLetter) && runCatching { java.util.Currency.getInstance(it.uppercase()) }.isSuccess }
            .takeIf { it >= 0 && it !in taken }?.let { out[ImportField.AMOUNT] = it }
        return out
    }

    /**
     * Reads [text] exported by [source]. [columns] overrides what was found (Any spreadsheet).
     * [digitsOf] gives the minor digits of a currency code (null = the main currency).
     */
    fun parse(text: String, source: ImportSource, digitsOf: (String?) -> Int, columns: Map<ImportField, Int>? = null): ParsedImport {
        val table = table(text)
        if (table.isEmpty()) return ParsedImport(emptyList(), emptyMap(), emptyList(), 0)
        val headers = table.first()
        val cols = columns ?: detect(headers)
        // Signed amounts (Cashew, most spreadsheets): negative is money out, positive money in. When
        // every amount is positive and nothing says which way, they're all spending.
        val signedFile = cols[ImportField.AMOUNT]?.let { c -> table.drop(1).any { r -> r.getOrNull(c)?.trim()?.let { it.startsWith("-") || it.startsWith("−") || it.startsWith("(") } == true } } == true
        var skipped = 0
        val rows = table.drop(1).mapNotNull { r ->
            fun cell(f: ImportField) = cols[f]?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            val row = runCatching { readRow(::cell, signedFile, digitsOf, source) }.getOrNull()
            if (row == null) skipped++
            row
        }
        return ParsedImport(headers, cols, rows, skipped)
    }

    private fun readRow(cell: (ImportField) -> String?, signedFile: Boolean, digitsOf: (String?) -> Int, source: ImportSource): ImportRow? {
        val typeText = cell(ImportField.TYPE)?.lowercase(Locale.ROOT)
        if (typeText != null && "transfer" in typeText) return null // moving money between accounts isn't a payment
        if (typeText != null && typeText.startsWith("adjust")) return null // Burrow's "Fix balance" rows
        val currency = cell(ImportField.CURRENCY)?.uppercase(Locale.ROOT)?.takeIf { it.length == 3 }
        val digits = digitsOf(currency)
        val debit = cell(ImportField.DEBIT)?.let { amount(it, digits) }?.takeIf { it != 0L }
        val credit = cell(ImportField.CREDIT)?.let { amount(it, digits) }?.takeIf { it != 0L }
        val signed = cell(ImportField.AMOUNT)?.let { amount(it, digits) }
        val value = signed ?: debit?.let { -kotlin.math.abs(it) } ?: credit?.let { kotlin.math.abs(it) } ?: return null
        if (value == 0L) return null
        val income = when {
            typeText != null && typeText in listOf("true", "income", "credit", "cr", "in", "inc", "deposit", "received", "refund") -> true
            typeText != null && (typeText in listOf("false", "expense", "expenses", "exp", "exp.", "debit", "dr", "out", "withdrawal", "spent", "paid")) -> false
            typeText != null && typeText.startsWith("inc") -> true
            typeText != null && typeText.startsWith("exp") -> false
            signed == null && credit != null && debit == null -> true
            signed == null -> false
            else -> signedFile && value > 0
        }
        val day = date(cell(ImportField.DATE) ?: return null, source) ?: return null
        val time = cell(ImportField.TIME)?.let(::time)
        val at = if (time != null && day.toLocalTime() == LocalTime.NOON) day.toLocalDate().atTime(time) else day
        val category = cell(ImportField.CATEGORY)
        return ImportRow(
            date = at, amount = kotlin.math.abs(value), income = income,
            payee = cell(ImportField.PAYEE), category = category, account = cell(ImportField.ACCOUNT),
            note = cell(ImportField.NOTE), currency = currency,
            adjustment = source == ImportSource.CASHEW && category.equals(CASHEW_BALANCE_CORRECTION, ignoreCase = true),
        )
    }

    /**
     * "₹1,240.50", "-1240.5", "(1,240.50)", "1.240,50", "1 240,50", "Rs. 240" → signed minor units.
     * A comma is the decimal mark only when it is the last mark and two digits follow it.
     */
    fun amount(raw: String, digits: Int): Long? {
        var s = raw.trim()
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        if (s.startsWith("-") || s.startsWith("−") || s.endsWith("-")) negative = true
        // Only what lies between the first and last digit: "Rs. 240" must not keep the dot of "Rs.".
        val from = s.indexOfFirst(Char::isDigit)
        if (from < 0) return null
        s = s.substring(from, s.indexOfLast(Char::isDigit) + 1).filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isEmpty() || s.none(Char::isDigit)) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalComma = lastComma > lastDot && s.length - lastComma - 1 in 1..2 && (lastDot >= 0 || s.count { it == ',' } == 1)
        s = if (decimalComma) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        if (s.count { it == '.' } > 1) s = s.replace(".", "") // "1.240.000" grouping dots
        val value = s.toBigDecimalOrNull() ?: return null
        val minor = runCatching { value.setScale(digits, RoundingMode.HALF_EVEN).movePointRight(digits).longValueExact() }.getOrNull() ?: return null
        return if (negative) -minor else minor
    }

    private val DATE_TIMES = listOf(
        "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
    )
    private val DAY_FIRST = listOf("d/M/yyyy H:mm:ss", "d/M/yyyy H:mm", "d-M-yyyy H:mm:ss", "d-M-yyyy H:mm", "d.M.yyyy H:mm", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "d/M/yy", "d-M-yy")
    private val MONTH_FIRST = listOf("M/d/yyyy H:mm:ss", "M/d/yyyy H:mm", "M/d/yyyy h:mm:ss a", "M/d/yyyy h:mm a", "M/d/yyyy", "M/d/yy")
    private val WORDS = listOf("d MMM yyyy", "d MMM yy", "d-MMM-yyyy", "d-MMM-yy", "MMM d, yyyy", "d MMMM yyyy", "MMMM d, yyyy", "EEE, d MMM yyyy", "d MMM yyyy, h:mm a", "d MMM yyyy H:mm")

    private fun formatter(p: String) = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)

    /** A date in any of the common shapes; Money Manager writes month first, the others day first. */
    fun date(raw: String, source: ImportSource = ImportSource.CSV): LocalDateTime? {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        val numeric = if (source == ImportSource.MONEY_MANAGER) MONTH_FIRST + DAY_FIRST else DAY_FIRST + MONTH_FIRST
        for (p in DATE_TIMES + numeric + WORDS) {
            val f = formatter(p)
            val hasTime = p.contains('H') || p.contains('h')
            val parsed = runCatching { if (hasTime) LocalDateTime.parse(t, f) else LocalDate.parse(t, f).atTime(LocalTime.NOON) }.getOrNull()
            if (parsed != null && parsed.year in 1990..2100) return parsed
        }
        return runCatching { LocalDate.parse(t.take(10)).atTime(LocalTime.NOON) }.getOrNull()
    }

    fun time(raw: String): LocalTime? = listOf("H:mm:ss", "H:mm", "h:mm a", "h:mm:ss a", "hh:mm a").firstNotNullOfOrNull { p ->
        runCatching { LocalTime.parse(raw.trim().uppercase(Locale.ROOT), formatter(p)) }.getOrNull()
    }

    /** Cashew writes a move between accounts as two balance corrections, a second apart. */
    private const val CASHEW_BALANCE_CORRECTION = "Balance Correction"

    /**
     * Balance corrections that pair up (same amount, opposite sides, different accounts, within a
     * minute) become transfers; the rest stay single corrections ("Fix balance").
     */
    fun pairTransfers(rows: List<ImportRow>): Pair<List<ImportTransfer>, List<ImportRow>> {
        val open = rows.filter { it.adjustment }.sortedBy { it.date }.toMutableList()
        val pairs = mutableListOf<ImportTransfer>()
        val single = mutableListOf<ImportRow>()
        while (open.isNotEmpty()) {
            val a = open.removeAt(0)
            val i = open.indexOfFirst { b ->
                b.amount == a.amount && b.income != a.income && b.account != a.account &&
                    kotlin.math.abs(java.time.Duration.between(a.date, b.date).seconds) <= 60
            }
            if (i < 0) { single += a; continue }
            val b = open.removeAt(i)
            pairs += if (a.income) ImportTransfer(from = b, to = a) else ImportTransfer(from = a, to = b)
        }
        return pairs to single
    }

    /** The same payment already here: same day, same amount, same payee (case and spaces ignored). */
    fun isDuplicate(row: ImportRow, existing: Set<Triple<LocalDate, Long, String>>): Boolean =
        Triple(row.date.toLocalDate(), row.amount, key(row.payee)) in existing

    fun key(payee: String?): String = payee.orEmpty().lowercase(Locale.ROOT).filter { !it.isWhitespace() }

    /**
     * Their category name → one of Burrow's category icons ("Dining" → restaurant), so names in any
     * language map onto the right default category. Null = no guess.
     */
    fun guessIcon(name: String, income: Boolean): String? {
        val n = name.lowercase(Locale.ROOT)
        fun has(vararg w: String) = w.any { it in n }
        if (income) return when {
            has("salary", "pay", "wage", "income") -> "payments"
            has("interest", "dividend") -> "percent"
            has("refund", "cashback", "received", "gift") -> "redeem"
            else -> null
        }
        return when {
            has("grocer", "supermarket", "vegetable", "kirana") -> "shopping_cart"
            has("food", "dining", "restaurant", "eat", "coffee", "cafe", "drink", "snack", "lunch", "dinner", "swiggy", "zomato") -> "restaurant"
            has("fuel", "petrol", "taxi", "cab", "uber", "ola", "metro", "bus", "transport", "commute", "auto") -> "directions_car"
            has("travel", "trip", "flight", "holiday", "vacation", "hotel") -> "flight"
            has("shop", "cloth", "fashion", "amazon", "electronics") -> "local_mall"
            has("bill", "utilit", "electric", "water", "phone", "mobile", "internet", "recharge", "gas") -> "bolt"
            has("rent", "home", "house", "maintenance") -> "home"
            has("health", "medic", "pharma", "doctor", "hospital", "fitness", "gym") -> "favorite"
            has("entertain", "movie", "subscription", "stream", "netflix", "music", "game", "fun") -> "movie"
            has("educat", "school", "course", "book", "tuition") -> "school"
            has("gift", "donat", "charity") -> "redeem"
            has("fee", "interest", "charge", "tax", "emi") -> "percent"
            has("saving", "sip", "invest", "mutual fund", "deposit") -> "savings"
            has("transfer", "send", "sent") -> "swap_horiz"
            has("groom", "salon", "haircut", "beauty", "self care") -> "favorite"
            has("gym", "sport") -> "fitness_center"
            has("pet") -> "pets"
            has("kid", "child", "baby") -> "child_care"
            else -> null
        }
    }
}
