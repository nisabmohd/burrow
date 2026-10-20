package app.burrow.money.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.burrow.money.ui.theme.BodyFont
import app.burrow.money.ui.theme.ThemeMode
import app.burrow.money.ui.theme.ThemeSettings
import app.burrow.money.ui.theme.TextSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale

/**
 * Everything the user sets, from DataStore (keys in docs/DATA.md § DataStore keys).
 * Grows as screens need more: main currency, month start, capture switches, app lock…
 */
@Immutable
data class Settings(
    val theme: ThemeSettings = ThemeSettings(),
    /** ISO 4217 code; totals and budgets use it. Defaults to the phone's region. */
    val mainCurrency: String = defaultCurrency(),
    /** The day budget months start on, 1–28. */
    val monthStartDay: Int = 1,
    /** Auto-capture switches (More › Auto-capture); both off until the intro's Allow. */
    val smsCapture: Boolean = false,
    val notificationCapture: Boolean = false,
    /** Built-in rule packs that are on (ids from BUILT_IN_PACKS). */
    val enabledPacks: Set<String> = setOf("in"),
    /** Widgets Today shows (Edit home, 10), by key: week, bill, networth, topcat, goal, inout. */
    val homeWidgets: Set<String> = ALL_HOME_WIDGETS,
)

val ALL_HOME_WIDGETS = setOf("week", "bill", "networth", "topcat", "goal", "inout")

/** The phone region's currency, or INR when the region has none. */
fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
    runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "INR"

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store: DataStore<Preferences> = context.dataStore

    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }
    suspend fun setAccentHue(hue: Float) = store.edit { it[ACCENT_HUE] = hue }
    suspend fun setFont(font: BodyFont) = store.edit { it[FONT] = font.name }
    suspend fun setTextSize(size: TextSize) = store.edit { it[TEXT_SIZE] = size.name }
    suspend fun setMainCurrency(code: String) = store.edit { it[MAIN_CURRENCY] = code }
    suspend fun setMonthStartDay(day: Int) = store.edit { it[MONTH_START_DAY] = day.coerceIn(1, 28) }
    suspend fun setHomeWidget(key: String, on: Boolean) = store.edit {
        val now = it[HOME_WIDGETS] ?: ALL_HOME_WIDGETS
        it[HOME_WIDGETS] = if (on) now + key else now - key
    }
    suspend fun setSmsCapture(on: Boolean) = store.edit { it[SMS_CAPTURE] = on }
    suspend fun setNotificationCapture(on: Boolean) = store.edit { it[NOTIFICATION_CAPTURE] = on }
    suspend fun setPackEnabled(id: String, on: Boolean) = store.edit {
        val now = it[ENABLED_PACKS] ?: setOf("in")
        it[ENABLED_PACKS] = if (on) now + id else now - id
    }

    /** When bill reminders last ran (epoch millis); null before the first run. */
    suspend fun billsCheckedAt(): Long? = store.data.first()[BILLS_CHECKED_AT]
    suspend fun setBillsCheckedAt(at: Long) = store.edit { it[BILLS_CHECKED_AT] = at }

    /** Budget alerts already sent ("2026-10:overall:80"), so each goes once a month. */
    suspend fun budgetAlertSent(key: String): Boolean = key in (store.data.first()[BUDGET_ALERTS] ?: emptySet())
    suspend fun markBudgetAlert(key: String) = store.edit { it[BUDGET_ALERTS] = ((it[BUDGET_ALERTS] ?: emptySet()) + key).toList().takeLast(200).toSet() }

    private fun Preferences.toSettings(): Settings {
        val theme = ThemeSettings()
        return Settings(
            theme = ThemeSettings(
                mode = enumOrDefault(this[THEME], theme.mode),
                dynamicColor = this[DYNAMIC_COLOR] ?: theme.dynamicColor,
                accentHue = this[ACCENT_HUE] ?: theme.accentHue,
                font = enumOrDefault(this[FONT], theme.font),
                textSize = enumOrDefault(this[TEXT_SIZE], theme.textSize),
            ),
            mainCurrency = this[MAIN_CURRENCY] ?: defaultCurrency(),
            monthStartDay = this[MONTH_START_DAY] ?: 1,
            smsCapture = this[SMS_CAPTURE] ?: false,
            notificationCapture = this[NOTIFICATION_CAPTURE] ?: false,
            enabledPacks = this[ENABLED_PACKS] ?: setOf("in"),
            homeWidgets = this[HOME_WIDGETS] ?: ALL_HOME_WIDGETS,
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamicColor")
        val ACCENT_HUE = floatPreferencesKey("accentHue")
        val FONT = stringPreferencesKey("font")
        val TEXT_SIZE = stringPreferencesKey("textSize")
        val MAIN_CURRENCY = stringPreferencesKey("mainCurrency")
        val MONTH_START_DAY = intPreferencesKey("monthStartDay")
        val SMS_CAPTURE = booleanPreferencesKey("smsCapture")
        val NOTIFICATION_CAPTURE = booleanPreferencesKey("notificationCapture")
        val ENABLED_PACKS = stringSetPreferencesKey("enabledPacks")
        val BILLS_CHECKED_AT = longPreferencesKey("billsCheckedAt")
        val BUDGET_ALERTS = stringSetPreferencesKey("budgetAlerts")
        val HOME_WIDGETS = stringSetPreferencesKey("homeWidgets")
    }
}

/** A stored enum name, or [default] when it is missing or from an older version. */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
