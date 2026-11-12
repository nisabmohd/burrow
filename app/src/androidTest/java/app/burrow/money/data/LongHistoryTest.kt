package app.burrow.money.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random

/**
 * docs/PERFORMANCE.md on a real database: 20,000 payments over three years, six accounts and two
 * cards, one of them used rarely. Each screen's query returns the right rows, fast, without reading
 * the whole history. In memory, so the app's own data is never touched.
 */
@RunWith(AndroidJUnit4::class)
class LongHistoryTest {
    private lateinit var db: BurrowDatabase
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private fun LocalDate.millis() = atStartOfDay(zone).toInstant().toEpochMilli()

    private var bank = 0L
    private var busyCard = 0L
    private var quietCard = 0L
    private val all = mutableListOf<TxnEntity>()

    @Before fun seed() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, BurrowDatabase::class.java).build()
        fun account(name: String, type: String) = AccountEntity(name = name, type = type, currency = "INR", openingBalance = 0, colorHue = 0f, icon = "x", createdAt = 0)
        val ids = listOf(
            "HDFC" to AccountType.BANK, "SBI" to AccountType.BANK, "Cash" to AccountType.CASH, "Paytm" to AccountType.WALLET,
            "Amazon Pay" to AccountType.CARD, "Regalia" to AccountType.CARD,
        ).map { (n, t) -> db.accounts().insert(account(n, t)) }
        bank = ids[0]; busyCard = ids[4]; quietCard = ids[5]
        val cats = db.categories().insertAll((1..12).map { CategoryEntity(name = "C$it", kind = "EXPENSE", icon = "x", colorHue = 0f) })
        val r = Random(42)
        val start = today.minusYears(3)
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt()
        val busy = ids.filter { it != quietCard }
        repeat(ROWS - QUIET_ROWS - CARD_PAYMENTS) {
            val at = start.plusDays(r.nextInt(days + 1).toLong()).millis() + r.nextLong(86_400_000)
            all += TxnEntity(
                type = if (r.nextInt(8) == 0) TxnType.INCOME else TxnType.EXPENSE, amount = 1_00L + r.nextLong(5_000_00), currency = "INR",
                baseAmount = 0, accountId = busy[r.nextInt(busy.size)], categoryId = cats[r.nextInt(cats.size)], payee = "P${r.nextInt(400)}",
                at = at, createdAt = at, updatedAt = at,
            )
        }
        // The quiet card: a payment every few months, so none is in the newest few hundred rows overall.
        (0 until QUIET_ROWS).forEach { i ->
            val at = today.minusDays(40L + i * 90L).millis()
            all += TxnEntity(type = TxnType.EXPENSE, amount = 2_000_00, currency = "INR", baseAmount = 0, accountId = quietCard, categoryId = cats[0], at = at, createdAt = at, updatedAt = at)
        }
        // Its bill, paid from the bank: transfers into the card belong on its page too.
        (0 until CARD_PAYMENTS).forEach { i ->
            val at = today.minusDays(55L + i * 90L).millis()
            all += TxnEntity(type = TxnType.TRANSFER, amount = 2_000_00, currency = "INR", baseAmount = 0, accountId = bank, toAccountId = quietCard, at = at, createdAt = at, updatedAt = at)
        }
        all.replaceAll { it.copy(baseAmount = it.amount) }
        all.chunked(2_000).forEach { db.txns().insertAll(it) }
    }

    @After fun close() = db.close()

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        block() // warm: the first read compiles the statement
        val times = (1..5).map { val t0 = System.nanoTime(); block(); (System.nanoTime() - t0) / 1_000_000 }
        return block() to times.sorted()[2]
    }

    @Test fun quietCardKeepsItsWholeHistory() = runBlocking {
        val page = db.txns().forAccount(quietCard, 1_000).first()
        assertEquals(QUIET_ROWS + CARD_PAYMENTS, page.size)
        assertTrue(page.all { it.txn.accountId == quietCard || it.txn.toAccountId == quietCard })
        assertEquals(page.map { it.txn.at }.sortedDescending(), page.map { it.txn.at })
        // What the page did before 0.4.1: the newest 400 rows of every account, filtered. It lost them.
        val before = db.txns().latest(400).first().filter { it.txn.accountId == quietCard || it.txn.toAccountId == quietCard }
        assertTrue("old way found ${before.size}", before.size < page.size)
    }

    @Test fun sinceStatementIsSummedOverEveryRow() = runBlocking {
        for (card in listOf(busyCard, quietCard)) {
            val from = today.minusDays(200).millis()
            val expected = all.filter { it.accountId == card && it.type == TxnType.EXPENSE && it.at >= from }.sumOf { it.amount }
            assertEquals(expected, db.txns().expenseSince(card, from).first())
        }
    }

    @Test fun queriesStayFastWith20kRows() = runBlocking {
        val month = YearMonth.from(today)
        val (rows, monthMs) = timed { runBlocking { db.txns().between(month.atDay(1).millis(), month.plusMonths(1).atDay(1).millis()).first() } }
        assertEquals(all.count { it.at >= month.atDay(1).millis() && it.at < month.plusMonths(1).atDay(1).millis() }, rows.size)
        val (_, accountMs) = timed { runBlocking { db.txns().forAccount(busyCard, 1_000).first() } }
        val (keys, keysMs) = timed { runBlocking { db.txns().postedKeys().first() } }
        assertEquals(ROWS, keys.size)
        android.util.Log.i("LongHistoryTest", "20k rows: month ${monthMs} ms, account ${accountMs} ms, filter keys ${keysMs} ms")
        assertTrue("month query $monthMs ms (budget 30)", monthMs < 30)
        assertTrue("account query $accountMs ms (budget 60)", accountMs < 60)
        assertTrue("filter keys $keysMs ms (budget 150)", keysMs < 150)
    }

    @Test fun activityFirstReadIsThreeMonthsNotEverything() = runBlocking {
        // Activity browsing reads from the 1st of the month two months back (ACTIVITY_MONTHS = 3).
        val from = YearMonth.from(today).minusMonths(2).atDay(1).millis()
        val first = db.txns().between(from, Long.MAX_VALUE).first()
        assertEquals(all.count { it.at >= from }, first.size)
        assertTrue("${first.size} of $ROWS", first.size < ROWS / 6)
    }

    private companion object {
        const val ROWS = 20_000
        const val QUIET_ROWS = 12
        const val CARD_PAYMENTS = 8
    }
}
