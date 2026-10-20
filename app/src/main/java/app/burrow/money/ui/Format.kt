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

/** Customise › Region (85): how dates are written, and the first day of the week. */
@androidx.compose.runtime.Immutable
data class DatePrefs(val style: String = "WRITTEN", val weekStart: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY)

val LocalDatePrefs = staticCompositionLocalOf { DatePrefs() }

/** Customise › Transactions (82): what lists show and what gestures do. */
@androidx.compose.runtime.Immutable
data class ListPrefs(
    val showTime: Boolean = true,
    val confirmDelete: Boolean = false,
    val swipeRight: String = "PAID",
    val swipeLeft: String = "SKIP",
)

val LocalListPrefs = staticCompositionLocalOf { ListPrefs() }

/** Set while Hide amounts (More) is on: tapping a hero shows the amounts until tapped again. */
val LocalRevealAmounts = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Dates as the app writes them (docs/COPY.md): "Today", "Yesterday", "Monday, 13 Oct", and times in
 * the phone's 12 / 24-hour style. Formatters are made once per locale, not per row.
 */
@Stable
class DateText(private val context: Context, locale: Locale, style: String = "WRITTEN") {
    private val locale0 = locale
    private val zone: ZoneId = ZoneId.systemDefault()
    // Numeric styles (Customise › Region): 19/10/2026 day first, 10/19/2026 month first, 2026-10-19 year first.
    private val numeric: Pair<String, String>? = when (style) {
        "DMY" -> "dd/MM" to "dd/MM/yyyy"
        "MDY" -> "MM/dd" to "MM/dd/yyyy"
        "YMD" -> "MM-dd" to "yyyy-MM-dd"
        else -> null
    }
    private val dayFormat = DateTimeFormatter.ofPattern(numeric?.let { "EEEE, ${it.first}" } ?: DateFormat.getBestDateTimePattern(locale, "EEEEdMMM"), locale)
    private val shortDayFormat = DateTimeFormatter.ofPattern(numeric?.first ?: DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL", locale)
    private val language = locale.language
    private val weekdayDayFormat = DateTimeFormatter.ofPattern(numeric?.let { "EEE, ${it.first}" } ?: DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val fullDayFormat = DateTimeFormatter.ofPattern(numeric?.second ?: DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale)
    private val monthYearFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
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

    /** When a bill is due, from today: "Today", "Tomorrow", "in 6 days", "in 2 months"; past: "Late". */
    fun due(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return when {
            days < 0 -> context.getString(R.string.late)
            days == 0L -> context.getString(R.string.today)
            days == 1L -> context.getString(R.string.tomorrow)
            days <= 45 -> context.resources.getQuantityString(R.plurals.in_days, days.toInt(), days.toInt())
            else -> (days / 30).toInt().let { context.resources.getQuantityString(R.plurals.in_months, it, it) }
        }
    }

    /** "1st", "2nd", "23rd" in English; the plain number elsewhere. */
    fun ordinal(n: Int): String = if (language != "en") n.toString() else n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    /** "Tue, 25 Oct". */
    fun weekdayDay(date: LocalDate): String = weekdayDayFormat.format(date)

    /** "13 Oct 2026". */
    fun fullDay(date: LocalDate): String = fullDayFormat.format(date)

    /** "April 2027". */
    fun monthYear(date: LocalDate): String = monthYearFormat.format(date)

    /** "Monday". */
    fun weekdayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale0)

    /** "October" (standalone month name). */
    fun month(date: LocalDate): String = monthFormat.format(date)
}

@Composable
fun rememberDateText(): DateText {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val style = LocalDatePrefs.current.style
    return remember(context, locale, style) { DateText(context, locale, style) }
}

/** The account's short name in rows ("HDFC Savings" → "HDFC"). */
fun shortName(name: String): String = name.substringBefore(' ')

/** A row's title: the payee, "HDFC → ICICI" for a transfer without one, else the category. */
fun TxnView.title(): String = txn.payee
    ?: if (txn.type == TxnType.TRANSFER) "${shortName(accountName)} → ${goalName ?: shortName(toAccountName.orEmpty())}" else categoryName.orEmpty()

/** "−₹240" out, "+₹1,20,000" in, "₹8,120" for a transfer (docs/COPY.md: amounts never coloured). */
fun MoneyFormat.forTxn(type: String, amount: Long): String = when (type) {
    TxnType.EXPENSE, TxnType.ADJUST_DOWN -> signed(-amount)
    TxnType.INCOME, TxnType.ADJUST_UP -> signed(amount)
    else -> plain(amount)
}
