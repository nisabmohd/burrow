package app.burrow.money.data

import app.burrow.money.domain.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Export to CSV (More › Import or export): every real transaction as a spreadsheet. Importing goes
 * through Import (domain/Import.kt), which also reads this file back.
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

}
