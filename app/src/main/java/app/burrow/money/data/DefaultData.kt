package app.burrow.money.data

import app.burrow.money.R
import app.burrow.money.Strings
import app.burrow.money.domain.BudgetPeriod
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/*
 * First open (docs/DATA.md § Sample data): the default categories (kept), plus a realistic sample
 * month flagged isSample (accounts, transactions, a ₹30,000 budget) that goes away on the first real
 * entry or "Remove". Mirrors the prototype's data so screens match the PNGs.
 */

/** Default categories in picker order: name, icon, hue. Names are written in the app language of the first open. */
private val EXPENSE_CATEGORIES = listOf(
    Triple(R.string.cat_food, "restaurant", 28f),
    Triple(R.string.cat_groceries, "shopping_cart", 140f),
    Triple(R.string.cat_transport, "directions_car", 215f),
    Triple(R.string.cat_shopping, "local_mall", 318f),
    Triple(R.string.cat_bills, "bolt", 192f),
    Triple(R.string.cat_rent, "home", 50f),
    Triple(R.string.cat_health, "favorite", 10f),
    Triple(R.string.cat_entertainment, "movie", 275f),
    Triple(R.string.cat_travel, "flight", 168f),
    Triple(R.string.cat_education, "school", 245f),
    Triple(R.string.cat_gifts, "redeem", 345f),
    Triple(R.string.cat_other, "category", 95f),
)
private val INCOME_CATEGORIES = listOf(
    Triple(R.string.cat_salary, "payments", 140f),
    Triple(R.string.cat_interest, "percent", 192f),
    Triple(R.string.cat_other_income, "category", 95f),
)

fun defaultCategories(strings: Strings): List<CategoryEntity> =
    EXPENSE_CATEGORIES.mapIndexed { i, (name, icon, hue) -> CategoryEntity(name = strings(name), kind = CategoryKind.EXPENSE, icon = icon, colorHue = hue, sortOrder = i) } +
        INCOME_CATEGORIES.mapIndexed { i, (name, icon, hue) -> CategoryEntity(name = strings(name), kind = CategoryKind.INCOME, icon = icon, colorHue = hue, sortOrder = 100 + i) }

/** A sample account and the balance it should show today. */
private data class SampleAccount(val key: String, val name: String, val type: String, val icon: String, val hue: Float, val balance: Long)

private val SAMPLE_ACCOUNTS = listOf(
    SampleAccount("hdfc", "HDFC Savings", AccountType.BANK, "account_balance", 215f, 142_300),
    SampleAccount("icici", "ICICI Amazon Pay", AccountType.CARD, "credit_card", 28f, -8_120),
    SampleAccount("cash", "Cash", AccountType.CASH, "payments", 140f, 1_500),
    SampleAccount("paytm", "Paytm wallet", AccountType.WALLET, "account_balance_wallet", 192f, 850),
    SampleAccount("kotak", "Kotak", AccountType.BANK, "account_balance", 345f, 16_600),
    SampleAccount("loan", "Home loan", AccountType.LOAN, "real_estate_agent", 245f, -2_140_560),
    SampleAccount("fd", "Fixed deposit", AccountType.SAVINGS, "savings", 50f, 200_000),
)

/** Everyday spending: payee, category index in EXPENSE_CATEGORIES, account, min, max, chance per day. */
private data class Template(val payee: String, val cat: Int, val acc: String, val lo: Int, val hi: Int, val chance: Double)

private val TEMPLATES = listOf(
    Template("Swiggy", 0, "hdfc", 160, 620, .3), Template("Zomato", 0, "icici", 180, 690, .2),
    Template("Third Wave Coffee", 0, "hdfc", 180, 320, .16), Template("Chaayos", 0, "cash", 90, 240, .12),
    Template("DMart", 1, "hdfc", 700, 2600, .09), Template("Blinkit", 1, "paytm", 150, 890, .18),
    Template("Uber", 2, "icici", 120, 420, .3), Template("Metro card", 2, "paytm", 200, 500, .06),
    Template("Rapido", 2, "paytm", 50, 150, .18), Template("Amazon", 3, "icici", 299, 2499, .08),
    Template("Myntra", 3, "icici", 499, 1899, .04), Template("Apollo Pharmacy", 6, "hdfc", 150, 900, .05),
    Template("BookMyShow", 7, "icici", 300, 900, .05),
)

/** Everything the sample month adds, ready to insert. Account ids are filled in by [SampleData.insert]. */
class SampleData(
    val accounts: List<AccountEntity>,
    /** Transactions with accountId / toAccountId set to the index in [accounts]. */
    val txns: List<TxnEntity>,
    val budget: BudgetEntity,
) {
    companion object {
        /**
         * Amounts are Indian; elsewhere they are scaled down so a salary still looks like a salary.
         * Deterministic (seeded), so screenshots are stable.
         */
        fun build(
            today: LocalDate,
            now: Long,
            currency: String,
            digits: Int,
            monthStartDay: Int,
            categoryIds: List<Long>, // expense categories in default order, then income
            zone: ZoneId = ZoneId.systemDefault(),
        ): SampleData {
            val scale = if (currency == "INR") 1.0 else 1.0 / 80
            var unit = 1L
            repeat(digits) { unit *= 10 }
            fun money(rupees: Number): Long = Math.round(rupees.toDouble() * scale).coerceAtLeast(1L) * unit
            fun signedMoney(rupees: Long): Long = if (rupees < 0) -money(-rupees) else money(rupees)
            val accIndex = SAMPLE_ACCOUNTS.withIndex().associate { (i, a) -> a.key to i.toLong() }
            val salaryId = categoryIds[EXPENSE_CATEGORIES.size]
            val r = Random(11)
            val txns = mutableListOf<TxnEntity>()
            fun at(day: LocalDate, time: LocalTime): Long = minOf(day.atTime(time).atZone(zone).toInstant().toEpochMilli(), now)
            fun add(day: LocalDate, time: LocalTime, type: String, payee: String?, cat: Long?, acc: String, amount: Long, to: String? = null, sms: Boolean = r.nextDouble() < .6) {
                val t = at(day, time)
                txns += TxnEntity(
                    type = type, amount = amount, currency = currency, baseAmount = amount,
                    accountId = accIndex.getValue(acc), toAccountId = to?.let { accIndex.getValue(it) },
                    categoryId = cat, payee = payee, at = t, source = if (sms) "SMS" else "MANUAL",
                    isSample = true, createdAt = t, updatedAt = t,
                )
            }
            fun randomTime() = LocalTime.of(8 + r.nextInt(14), r.nextInt(60))
            var day = today.withDayOfMonth(1).minusMonths(2)
            while (!day.isAfter(today)) {
                val d = day.dayOfMonth
                if (day != today) TEMPLATES.forEach { tp ->
                    if (r.nextDouble() < tp.chance) {
                        val v = Math.round((tp.lo + r.nextDouble() * (tp.hi - tp.lo)) / 10) * 10
                        add(day, randomTime(), TxnType.EXPENSE, tp.payee, categoryIds[tp.cat], tp.acc, money(v))
                    }
                }
                if (d == 1) add(day, LocalTime.of(9, 1), TxnType.INCOME, "ACME Pvt Ltd", salaryId, "hdfc", money(120_000), sms = true)
                if (d == 2) add(day, LocalTime.of(6, 0), TxnType.EXPENSE, "Spotify", categoryIds[7], "icici", money(119))
                if (d == 5) add(day, LocalTime.of(11, 20), TxnType.EXPENSE, "Airtel", categoryIds[4], "hdfc", money(499))
                if (d == 17) add(day, LocalTime.of(11, 0), TxnType.EXPENSE, "BESCOM", categoryIds[4], "hdfc", money(1100 + r.nextInt(31) * 10))
                if (d == 20 && day.month != today.month) add(day, LocalTime.of(6, 0), TxnType.EXPENSE, "Netflix", categoryIds[7], "icici", money(649))
                if (d == 18 && day.month == today.month && day != today) add(day, LocalTime.of(19, 30), TxnType.TRANSFER, null, null, "hdfc", money(8_120), to = "icici")
                day = day.plusDays(1)
            }
            add(today, LocalTime.of(8, 41), TxnType.EXPENSE, "Uber", categoryIds[2], "icici", money(238), sms = true)
            add(today, LocalTime.of(9, 12), TxnType.EXPENSE, "Third Wave Coffee", categoryIds[0], "hdfc", money(240), sms = true)

            // Opening balance = today's balance minus what the sample transactions did to it.
            val net = LongArray(SAMPLE_ACCOUNTS.size)
            txns.forEach { t ->
                val i = t.accountId.toInt()
                when (t.type) {
                    TxnType.INCOME -> net[i] += t.amount
                    else -> net[i] -= t.amount
                }
                t.toAccountId?.let { net[it.toInt()] += t.amount }
            }
            val accounts = SAMPLE_ACCOUNTS.mapIndexed { i, a ->
                AccountEntity(
                    name = a.name, type = a.type, currency = currency, openingBalance = signedMoney(a.balance) - net[i],
                    colorHue = a.hue, icon = a.icon, sortOrder = i, isSample = true, createdAt = now,
                )
            }
            val month = BudgetPeriod.containing(today, monthStartDay).month.toString()
            return SampleData(accounts, txns, BudgetEntity(month = month, categoryId = null, amount = money(30_000), isSample = true))
        }
    }
}
