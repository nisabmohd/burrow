package app.burrow.money.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/*
 * Bills, loans, deposits, budgets and goals: the maths behind Plan, pure and unit-tested.
 */

/** How a bill repeats, stored as text in BillEntity.repeatRule ("MONTHLY:17", "YEARLY:01-09", "WEEKLY:MON"). */
sealed interface Repeat {
    /** Due on [day] each month; on short months, the last day. */
    data class Monthly(val day: Int) : Repeat
    data class Yearly(val month: Int, val day: Int) : Repeat
    data class Weekly(val day: DayOfWeek) : Repeat

    fun encode(): String = when (this) {
        is Monthly -> "MONTHLY:$day"
        is Yearly -> "YEARLY:%02d-%02d".format(month, day)
        is Weekly -> "WEEKLY:${day.name.take(3)}"
    }

    /** How often, for the Weekly · Monthly · Yearly choice on New bill. */
    enum class Every { WEEK, MONTH, YEAR }

    val every: Every get() = when (this) {
        is Weekly -> Every.WEEK
        is Monthly -> Every.MONTH
        is Yearly -> Every.YEAR
    }

    /** This rule as [to], keeping its day of the month where it has one, else [today]'s. */
    fun every(to: Every, today: LocalDate): Repeat {
        if (to == every) return this
        val dayOfMonth = when (this) {
            is Monthly -> day
            is Yearly -> day
            is Weekly -> today.dayOfMonth
        }
        return when (to) {
            Every.WEEK -> Weekly(today.dayOfWeek)
            Every.MONTH -> Monthly(dayOfMonth)
            Every.YEAR -> Yearly(today.monthValue, dayOfMonth.coerceAtMost(YearMonth.from(today).lengthOfMonth()))
        }
    }

    /** The first due date on or after [date]. */
    fun nextOnOrAfter(date: LocalDate): LocalDate = when (this) {
        is Monthly -> {
            val here = YearMonth.from(date).atDayClamped(day)
            if (!here.isBefore(date)) here else YearMonth.from(date).plusMonths(1).atDayClamped(day)
        }
        is Yearly -> {
            val here = YearMonth.of(date.year, month).atDayClamped(day)
            if (!here.isBefore(date)) here else YearMonth.of(date.year + 1, month).atDayClamped(day)
        }
        is Weekly -> date.plusDays(((day.value - date.dayOfWeek.value + 7) % 7).toLong())
    }

    /** Every due date from [from] to [to], both included. */
    fun between(from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = nextOnOrAfter(from)
        while (!d.isAfter(to)) {
            out += d
            d = nextOnOrAfter(d.plusDays(1))
        }
        return out
    }

    companion object {
        fun parse(text: String): Repeat? {
            val (kind, arg) = text.split(':', limit = 2).let { if (it.size == 2) it[0] to it[1] else return null }
            return when (kind) {
                "MONTHLY" -> arg.toIntOrNull()?.takeIf { it in 1..31 }?.let(::Monthly)
                "YEARLY" -> arg.split('-').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { Yearly(it[0], it[1]) }
                "WEEKLY" -> DayOfWeek.entries.firstOrNull { it.name.startsWith(arg) }?.let(::Weekly)
                else -> null
            }
        }
    }
}

private fun YearMonth.atDayClamped(day: Int): LocalDate = atDay(day.coerceAtMost(lengthOfMonth()))

/** A bill occurrence that a payment might settle. */
data class DueBill(val occurrenceId: Long, val name: String, val amount: Long, val due: LocalDate)

/**
 * The bill a captured payment pays (docs/CAPTURE.md § Bill match): the payee names the bill, the
 * amount is within 10% and the date within 3 days of the due date. The closest due date wins.
 */
fun matchBill(payee: String?, amount: Long, date: LocalDate, due: List<DueBill>): DueBill? {
    val p = payee?.let(::letters) ?: return null
    return due.filter { b ->
        val name = letters(b.name)
        name.length >= 3 && (p.contains(name) || name.contains(p) && p.length >= 4) &&
            abs(amount - b.amount) * 10 <= b.amount &&
            abs(ChronoUnit.DAYS.between(b.due, date)) <= 3
    }.minByOrNull { abs(ChronoUnit.DAYS.between(it.due, date)) }
}

private fun letters(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** One EMI: number, date, and how it splits into principal and interest (minor units). */
data class Instalment(val number: Int, val date: LocalDate, val principal: Long, val interest: Long)

/**
 * The EMIs from now on, for an [outstanding] loan at [annualRatePercent] paying [emi] a month,
 * the first on [firstDue] numbered [firstNumber]. Interest is outstanding × monthly rate, rounded
 * once per EMI (HALF_EVEN) to whole [unit]s; the last EMI clears what is left.
 */
fun loanSchedule(outstanding: Long, annualRatePercent: BigDecimal, emi: Long, firstDue: LocalDate, firstNumber: Int, max: Int = 600, unit: Long = 1): List<Instalment> {
    val r = annualRatePercent.divide(BigDecimal(1200), MathContext.DECIMAL64)
    var left = outstanding
    val out = mutableListOf<Instalment>()
    var n = 0
    while (left > 0 && n < max) {
        // Rounded once per EMI to whole [unit]s (₹1 = 100 minor), as banks print it.
        val interest = BigDecimal(left).multiply(r).divide(BigDecimal(unit), 0, RoundingMode.HALF_EVEN).toLong() * unit
        if (emi <= interest) break // never pays off
        val principal = minOf(emi - interest, left)
        out += Instalment(firstNumber + n, firstDue.plusMonths(n.toLong()), principal, interest)
        left -= principal
        n++
    }
    return out
}

/**
 * A fixed deposit's value on [on], compounded quarterly like Indian banks; at most its value at
 * [maturity]. Rounded once, HALF_EVEN.
 */
fun depositValue(principal: Long, annualRatePercent: BigDecimal, start: LocalDate, maturity: LocalDate, on: LocalDate, unit: Long = 1): Long {
    val end = if (on.isAfter(maturity)) maturity else on
    val days = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
    val quarters = BigDecimal(days).divide(BigDecimal("91.3125"), MathContext.DECIMAL64)
    val q = BigDecimal.ONE + annualRatePercent.divide(BigDecimal(400), MathContext.DECIMAL64)
    val growth = Math.pow(q.toDouble(), quarters.toDouble())
    return BigDecimal(principal).multiply(BigDecimal(growth), MathContext.DECIMAL64).divide(BigDecimal(unit), 0, RoundingMode.HALF_EVEN).toLong() * unit
}

/** A budget at a moment: spent against [amount]. */
data class BudgetStatus(val spent: Long, val amount: Long) {
    val left get() = amount - spent
    val over get() = spent > amount
    /** 0..∞ (1.1 = 110%). */
    val fraction get() = if (amount <= 0) 0f else spent.toFloat() / amount
    val percent get() = Math.round(fraction * 100)
}

/** The alert to send when spending goes from [before] to [after]: 100, 80, or none (CLAUDE.md § Behaviour). */
fun budgetAlert(before: Long, after: Long, amount: Long): Int? = when {
    amount <= 0 -> null
    before * 100 < amount * 100 && after >= amount -> 100
    before * 10 < amount * 8 && after * 10 >= amount * 8 -> 80
    else -> null
}

/** What a goal needs each month to be saved in full by [by]; null without a date or when done. */
fun goalPerMonth(target: Long, saved: Long, today: LocalDate, by: LocalDate?): Long? {
    if (by == null || saved >= target) return null
    val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(by)).coerceAtLeast(1)
    return (target - saved + months - 1) / months
}

/** Typed amounts in forms ("1,800", "649.50") → minor units; 0 when not a number. */
object TypedAmount {
    fun parse(text: String, digits: Int): Long {
        val clean = text.filter { it.isDigit() || it == '.' }
        val value = clean.toBigDecimalOrNull() ?: return 0
        return runCatching { value.setScale(digits, RoundingMode.HALF_EVEN).movePointRight(digits).longValueExact() }.getOrDefault(0)
    }
}
