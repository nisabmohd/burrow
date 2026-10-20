package app.burrow.money.data

import app.burrow.money.domain.Csv
import app.burrow.money.domain.TypedAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Export or import CSV (More): every real transaction as a spreadsheet, and back. On import,
 * categories and accounts are matched by name; anything unknown goes to Other / the first account.
 */
class CsvTransfer(private val money: MoneyRepository, private val db: BurrowDatabase) {
    private val zone = ZoneId.systemDefault()
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    suspend fun export(): String = withContext(Dispatchers.Default) {
        val rows = money.latest().first().filter { !it.txn.isSample }.map { t ->
            val x = t.txn
            listOf(
                stamp.format(Instant.ofEpochMilli(x.at).atZone(zone)), x.type, x.payee.orEmpty(),
                java.math.BigDecimal.valueOf(x.amount, minorDigits(x.currency)).toPlainString(), x.currency,
                t.categoryName.orEmpty(), if (x.type == TxnType.TRANSFER) "${t.accountName} → ${t.toAccountName ?: t.goalName.orEmpty()}" else t.accountName,
                x.note.orEmpty(),
            )
        }
        Csv.write(rows)
    }

    /** Imports rows; returns how many were added. Transfers are skipped (accounts can't be matched both ways safely). */
    suspend fun import(text: String): Int = withContext(Dispatchers.Default) {
        val rows = Csv.read(text)
        val cats = money.categories.first()
        val accounts = money.accounts.first().map { it.account }
        val fallbackAccount = accounts.firstOrNull { it.type != AccountType.LOAN && it.type != AccountType.SAVINGS } ?: return@withContext 0
        val now = System.currentTimeMillis()
        val txns = rows.mapNotNull { r ->
            val raw = r["amount"] ?: return@mapNotNull null
            val negative = raw.trim().startsWith("-") || raw.trim().startsWith("−")
            val type = when (r["type"]?.uppercase()) {
                TxnType.INCOME -> TxnType.INCOME
                TxnType.EXPENSE -> TxnType.EXPENSE
                TxnType.TRANSFER -> return@mapNotNull null
                else -> if (negative || r["type"] == null) TxnType.EXPENSE else TxnType.INCOME
            }
            val currency = r["currency"]?.takeIf { it.length == 3 }?.uppercase() ?: fallbackAccount.currency
            val amount = TypedAmount.parse(raw.replace(",", ""), minorDigits(currency)).takeIf { it > 0 } ?: return@mapNotNull null
            val at = parseDate(r["date"].orEmpty()) ?: return@mapNotNull null
            val kind = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val cat = cats.firstOrNull { it.kind == kind && it.name.equals(r["category"], true) } ?: cats.firstOrNull { it.kind == kind && it.icon == "category" }
            val account = accounts.firstOrNull { it.name.equals(r["account"], true) } ?: fallbackAccount
            TxnEntity(
                type = type, amount = amount, currency = currency, baseAmount = amount, accountId = account.id, categoryId = cat?.id,
                payee = r["payee"]?.ifBlank { null }, note = r["note"]?.ifBlank { null }, at = at, source = "IMPORT", createdAt = now, updatedAt = now,
            )
        }
        if (txns.isNotEmpty()) {
            db.txns().insertAll(txns)
            money.removeSamples()
        }
        txns.size
    }

    /** "2026-10-19 09:12", "2026-10-19", "19/10/2026", "19-10-2026". */
    private fun parseDate(s: String): Long? {
        val t = s.trim()
        val dt = runCatching { LocalDateTime.parse(t, stamp) }.getOrNull()
            ?: runCatching { LocalDate.parse(t).atTime(12, 0) }.getOrNull()
            ?: runCatching { LocalDate.parse(t, DateTimeFormatter.ofPattern("d/M/yyyy")).atTime(12, 0) }.getOrNull()
            ?: runCatching { LocalDate.parse(t, DateTimeFormatter.ofPattern("d-M-yyyy")).atTime(12, 0) }.getOrNull()
            ?: return null
        return dt.atZone(zone).toInstant().toEpochMilli()
    }
}
