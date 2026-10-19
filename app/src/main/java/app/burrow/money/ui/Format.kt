package app.burrow.money.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.burrow.money.R
import app.burrow.money.data.TxnType
import app.burrow.money.data.TxnView
import app.burrow.money.domain.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The main currency's formatter, from settings (provided in MainActivity). */
val LocalMoney = staticCompositionLocalOf { MoneyFormat.of("INR") }

/**
 * Dates as the app writes them (docs/COPY.md): "Today", "Yesterday", "Monday, 13 Oct", and times in
 * the phone's 12 / 24-hour style. Formatters are made once per locale, not per row.
 */
@Stable
class DateText(private val context: Context, locale: Locale) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMM"), locale)
    private val shortDayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL", locale)
    private val timeFormat = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"), locale,
    )

    fun date(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /** "Today", "Yesterday", or "Monday, 13 Oct". */
    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> dayFormat.format(date)
    }

    fun time(at: Long): String = timeFormat.format(Instant.ofEpochMilli(at).atZone(zone))

    /** "Today, 9:30 AM". */
    fun dayTime(at: Long): String = context.getString(R.string.day_time, day(date(at)), time(at))

    /** "13 Oct". */
    fun shortDay(date: LocalDate): String = shortDayFormat.format(date)

    /** "October" (standalone month name). */
    fun month(date: LocalDate): String = monthFormat.format(date)
}

@Composable
fun rememberDateText(): DateText {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(context, locale) { DateText(context, locale) }
}

/** The account's short name in rows ("HDFC Savings" → "HDFC"). */
fun shortName(name: String): String = name.substringBefore(' ')

/** A row's title: the payee, "HDFC → ICICI" for a transfer without one, else the category. */
fun TxnView.title(): String = txn.payee
    ?: if (txn.type == TxnType.TRANSFER) "${shortName(accountName)} → ${shortName(toAccountName.orEmpty())}" else categoryName.orEmpty()

/** "−₹240" out, "+₹1,20,000" in, "₹8,120" for a transfer (docs/COPY.md: amounts never coloured). */
fun MoneyFormat.forTxn(type: String, amount: Long): String = when (type) {
    TxnType.EXPENSE -> signed(-amount)
    TxnType.INCOME -> signed(amount)
    else -> plain(amount)
}
