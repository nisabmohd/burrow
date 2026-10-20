package app.burrow.money.data

import app.burrow.money.R
import app.burrow.money.Strings
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.Repeat
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
    Triple(R.string.cat_fees, "percent", 245f),
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
private data class SampleAccount(val key: String, val name: String, val type: String, val icon: String, val hue: Float, val balance: Long, val number: String? = null)

private val SAMPLE_ACCOUNTS = listOf(
    SampleAccount("hdfc", "HDFC Savings", AccountType.BANK, "account_balance", 215f, 142_300, "1234"),
    SampleAccount("icici", "ICICI Amazon Pay", AccountType.CARD, "credit_card", 28f, -8_120, "8890"),
    SampleAccount("cash", "Cash", AccountType.CASH, "payments", 140f, 1_500),
    SampleAccount("paytm", "Paytm wallet", AccountType.WALLET, "account_balance_wallet", 192f, 850),
    SampleAccount("kotak", "Kotak", AccountType.BANK, "account_balance", 345f, 16_600, "4471"),
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

/** A sample bill with its due dates (billId, accountId and toAccountId are indexes, mapped on insert). */
class SampleBill(val bill: BillEntity, val occurrences: List<BillOccurrenceEntity>)

/** A sample goal and its "Add money" transfers (accountId is an index, goalId mapped on insert). */
class SampleGoal(val goal: GoalEntity, val added: List<TxnEntity>)

/**
 * Everything the sample month adds, ready to insert. Account references are indexes into [accounts]
 * until [MoneyRepository] inserts them and maps the ids.
 */
class SampleData(
    val accounts: List<AccountEntity>,
    /** Transactions with accountId / toAccountId set to the index in [accounts]. */
    val txns: List<TxnEntity>,
    /** Overall budget first, then category budgets (category ids already real). */
    val budgets: List<BudgetEntity>,
    val bills: List<SampleBill>,
    val goals: List<SampleGoal>,
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
            fun millis(day: LocalDate, time: LocalTime = LocalTime.NOON): Long = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
            val accIndex = SAMPLE_ACCOUNTS.withIndex().associate { (i, a) -> a.key to i.toLong() }
            fun cat(c: Int) = categoryIds[c]
            val salaryId = categoryIds[EXPENSE_CATEGORIES.size]
            val r = Random(11)
            val txns = mutableListOf<TxnEntity>()
            fun at(day: LocalDate, time: LocalTime): Long = minOf(millis(day, time), now)
            fun txn(day: LocalDate, time: LocalTime, type: String, payee: String?, cat: Long?, acc: String, amount: Long, to: String? = null, sms: Boolean = r.nextDouble() < .6): TxnEntity {
                val t = at(day, time)
                return TxnEntity(
                    type = type, amount = amount, currency = currency, baseAmount = amount,
                    accountId = accIndex.getValue(acc), toAccountId = to?.let { accIndex.getValue(it) },
                    categoryId = cat, payee = payee, at = t, source = if (sms) "SMS" else "MANUAL",
                    isSample = true, createdAt = t, updatedAt = t,
                )
            }
            fun add(day: LocalDate, time: LocalTime, type: String, payee: String?, cat: Long?, acc: String, amount: Long, to: String? = null, sms: Boolean = r.nextDouble() < .6) {
                txns += txn(day, time, type, payee, cat, acc, amount, to, sms)
            }
            fun randomTime() = LocalTime.of(8 + r.nextInt(14), r.nextInt(60))
            val first = today.withDayOfMonth(1).minusMonths(2)
            var day = first
            while (!day.isAfter(today)) {
                val d = day.dayOfMonth
                val thisMonth = day.month == today.month && day.year == today.year
                if (day != today) TEMPLATES.forEach { tp ->
                    if (r.nextDouble() < tp.chance) {
                        val v = Math.round((tp.lo + r.nextDouble() * (tp.hi - tp.lo)) / 10) * 10
                        add(day, randomTime(), TxnType.EXPENSE, tp.payee, cat(tp.cat), tp.acc, money(v))
                    }
                }
                if (d == 1) add(day, LocalTime.of(9, 1), TxnType.INCOME, "ACME Pvt Ltd", salaryId, "hdfc", money(120_000), sms = true)
                if (d == 2) add(day, LocalTime.of(6, 0), TxnType.EXPENSE, "Spotify", cat(7), "icici", money(119))
                if (d == 5) add(day, LocalTime.of(11, 20), TxnType.EXPENSE, "Airtel", cat(4), "hdfc", money(499))
                if (d == 12 && day != today) add(day, LocalTime.of(21, 10), TxnType.EXPENSE, "Hotstar", cat(7), "icici", money(299)) // looks like a subscription
                // This month's electricity is still unpaid: the overdue bill on Plan.
                if (d == 17 && !thisMonth) add(day, LocalTime.of(11, 0), TxnType.EXPENSE, "BESCOM", cat(4), "hdfc", money(1100 + r.nextInt(31) * 10))
                if (d == 20 && !thisMonth) add(day, LocalTime.of(6, 0), TxnType.EXPENSE, "Netflix", cat(7), "icici", money(649))
                if (d == 18 && thisMonth && day != today) add(day, LocalTime.of(19, 30), TxnType.TRANSFER, null, null, "hdfc", money(8_120), to = "icici")
                day = day.plusDays(1)
            }
            add(today, LocalTime.of(8, 41), TxnType.EXPENSE, "Uber", cat(2), "icici", money(238), sms = true)
            add(today, LocalTime.of(9, 12), TxnType.EXPENSE, "Third Wave Coffee", cat(0), "hdfc", money(240), sms = true)

            // Goals and their "Add money" transfers from HDFC.
            fun goal(name: String, target: Long, by: LocalDate?, icon: String, hue: Float, added: List<Pair<LocalDate, Long>>, reached: LocalDate? = null) = SampleGoal(
                GoalEntity(
                    name = name, target = money(target), targetDate = by?.let { millis(it) }, accountId = accIndex.getValue("hdfc"),
                    icon = icon, colorHue = hue, isSample = true, reachedAt = reached?.let { millis(it) },
                ),
                added.map { (d, v) -> txn(d, LocalTime.of(10, 0), TxnType.TRANSFER, null, null, "hdfc", money(v), sms = false) },
            )
            val m0 = today.withDayOfMonth(1)
            val goals = listOf(
                goal("Japan trip", 2_50_000, m0.plusMonths(6).withMonth(4).let { if (it.isBefore(today)) it.plusYears(1) else it }, "flight", 168f,
                    listOf(m0.minusMonths(3) to 55_000L, m0.minusMonths(2) to 15_000L, m0.minusMonths(1) to 20_000L, m0 to 20_000L)),
                goal("Emergency fund", 3_00_000, null, "shield", 10f, listOf(m0.minusMonths(3) to 1_50_000L, m0.minusMonths(1) to 30_000L)),
                goal("New laptop", 50_000, null, "laptop_mac", 215f,
                    listOf(m0.minusMonths(2) to 15_000L, m0.minusMonths(1) to 15_000L, m0.plusDays(1).coerceAtMost(today) to 20_000L),
                    reached = m0.plusDays(1).coerceAtMost(today)),
            )

            // Bills: due dates from the first sample month to two months ahead; the past ones are paid
            // except this month's electricity (overdue).
            fun bill(
                kind: String, name: String, amount: Long, acc: String, cat: Int?, repeat: Repeat, onDue: String = "ASK",
                icon: String? = null, hue: Float? = null, to: String? = null, overdueThisMonth: Boolean = false,
            ): SampleBill {
                val start = first.minusMonths(if (repeat is Repeat.Yearly) 12 else 0)
                val dates = repeat.between(start, today.plusDays(62))
                val b = BillEntity(
                    kind = kind, name = name, amount = money(amount), accountId = accIndex.getValue(acc), categoryId = cat?.let(::cat),
                    repeatRule = repeat.encode(), startDate = millis(start), onDue = onDue, icon = icon, colorHue = hue,
                    toAccountId = to?.let { accIndex.getValue(it) }, isSample = true,
                )
                val occ = dates.map { d ->
                    val past = d.isBefore(today)
                    val overdue = overdueThisMonth && d.month == today.month && d.year == today.year
                    BillOccurrenceEntity(billId = 0, dueDate = millis(d), status = if (past && !overdue) "PAID" else "UPCOMING", amount = if (past && !overdue) b.amount else null)
                }
                return SampleBill(b, occ)
            }
            val bills = listOf(
                bill("BILL", "Electricity", 1_800, "hdfc", 4, Repeat.Monthly(17), overdueThisMonth = true),
                bill("SUBSCRIPTION", "Netflix", 649, "icici", 7, Repeat.Monthly(19)),
                bill("SUBSCRIPTION", "Spotify", 119, "icici", 7, Repeat.Monthly(2)),
                bill("BILL", "Airtel", 499, "hdfc", 4, Repeat.Monthly(5)),
                bill("SUBSCRIPTION", "Google One", 1_300, "icici", 4, Repeat.Yearly(1, 9), icon = "cloud", hue = 215f),
                bill("BILL", "Rent", 18_000, "hdfc", 5, Repeat.Monthly(1), onDue = "RECORD"),
                bill("EMI", "Home loan EMI", 32_400, "hdfc", null, Repeat.Monthly(25), icon = "real_estate_agent", hue = 245f, to = "loan"),
                bill("CARD", "ICICI card bill", 8_120, "hdfc", null, Repeat.Monthly(28), icon = "credit_card", hue = 28f, to = "icici"),
            )

            // Opening balance = today's balance minus what the sample transactions did to it.
            val net = LongArray(SAMPLE_ACCOUNTS.size)
            (txns + goals.flatMap { it.added }).forEach { t ->
                val i = t.accountId.toInt()
                when (t.type) {
                    TxnType.INCOME -> net[i] += t.amount
                    else -> net[i] -= t.amount
                }
                t.toAccountId?.let { net[it.toInt()] += t.amount }
            }
            val accounts = SAMPLE_ACCOUNTS.mapIndexed { i, a ->
                val base = AccountEntity(
                    name = a.name, type = a.type, currency = currency, openingBalance = signedMoney(a.balance) - net[i],
                    colorHue = a.hue, icon = a.icon, sortOrder = i, isSample = true, createdAt = now, number = a.number,
                    includeInNetWorth = defaultInNetWorth(a.type),
                )
                when (a.key) {
                    "icici" -> base.copy(creditLimit = money(1_50_000), statementDay = 5, dueDay = 28)
                    "loan" -> base.copy(interestRate = "8.5", emi = money(32_400), startDate = millis(today.withDayOfMonth(25).minusMonths(41)))
                    "fd" -> base.copy(
                        interestRate = "7.1", principal = money(2_00_000),
                        startDate = millis(LocalDate.of(today.year - 1, 3, 12)), maturityDate = millis(LocalDate.of(today.year + 1, 3, 12)),
                        payoutAccountId = accIndex.getValue("hdfc"),
                    )
                    else -> base
                }
            }
            val month = BudgetPeriod.containing(today, monthStartDay).month.toString()
            val budgets = listOf(BudgetEntity(month = month, categoryId = null, amount = money(30_000), isSample = true)) +
                listOf(0 to 9_000, 1 to 7_000, 2 to 4_000, 3 to 4_000, 4 to 2_500, 7 to 1_500).map { (c, v) ->
                    BudgetEntity(month = month, categoryId = cat(c), amount = money(v), isSample = true)
                }
            return SampleData(accounts, txns, budgets, bills, goals)
        }
    }
}
