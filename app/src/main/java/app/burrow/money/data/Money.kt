package app.burrow.money.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money is a Long in minor units (paise, cents). Never Float or Double (CLAUDE.md hard rule).
 * Currency decides the minor-unit count (JPY 0, INR/USD/EUR 2, KWD 3).
 */
@JvmInline
value class Minor(val units: Long) {
    operator fun plus(o: Minor) = Minor(units + o.units)
    operator fun minus(o: Minor) = Minor(units - o.units)
    operator fun unaryMinus() = Minor(-units)
    val isNegative get() = units < 0
}

fun minorDigits(currency: String): Int = java.util.Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)

fun BigDecimal.toMinor(currency: String): Minor =
    Minor(setScale(minorDigits(currency), RoundingMode.HALF_EVEN).movePointRight(minorDigits(currency)).longValueExact())

fun Minor.toBigDecimal(currency: String): BigDecimal = BigDecimal.valueOf(units, minorDigits(currency))

/**
 * EMI on a reducing balance: P·r·(1+r)^n / ((1+r)^n − 1), r = monthly rate. BigDecimal throughout,
 * rounded once at the end, HALF_EVEN (CLAUDE.md).
 */
fun emi(principal: BigDecimal, annualRatePercent: BigDecimal, months: Int): BigDecimal {
    if (annualRatePercent.signum() == 0) return principal.divide(BigDecimal(months), 2, RoundingMode.HALF_EVEN)
    val mc = java.math.MathContext.DECIMAL128
    val r = annualRatePercent.divide(BigDecimal(1200), mc)
    val pow = BigDecimal.ONE.add(r).pow(months, mc)
    return principal.multiply(r, mc).multiply(pow, mc).divide(pow.subtract(BigDecimal.ONE), mc).setScale(2, RoundingMode.HALF_EVEN)
}
