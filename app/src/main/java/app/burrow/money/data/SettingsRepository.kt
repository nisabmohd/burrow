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
    /** False until Welcome → currency → accounts is done (01–03). */
    val onboarded: Boolean = false,
    /** More › Hide amounts: "₹ ••••" until tapped. */
    val hideAmounts: Boolean = false,
    /** App lock (70): on when a PIN is set and the switch is on. */
    val appLock: Boolean = false,
    val hasPin: Boolean = false,
    /** Seconds in the background before the app locks again (0 = at once). */
    val lockAfterSeconds: Int = 60,
    /** App lock › Hide amounts in notifications. */
    val hideInNotifications: Boolean = false,
    // Notifications (66)
    val notifyCapture: Boolean = true,
    val notifyBills: Boolean = true,
    val notifyBudgets: Boolean = true,
    val notifySummary: Boolean = false,
    /** Sunday review (13): Sunday 6 PM, on by default. */
    val notifyWeekly: Boolean = true,
    // Backup (69)
    val backupFolder: String? = null,
    val backupSchedule: String = "OFF", // OFF, DAILY, WEEKLY
    val lastBackupAt: Long? = null,
    val hasBackupPassword: Boolean = false,
    // Customise › Today (80)
    val homeTopCard: String = TopCard.BUDGET,
    /** Widget keys in the order Today shows them (hidden ones too; [homeWidgets] says which are on). */
    val homeWidgetOrder: List<String> = HOME_WIDGET_ORDER,
    val homeRecentCount: Int = 5,
    val homeShowReview: Boolean = true,
    // Customise › Expense (81)
    val formExtras: Set<String> = DEFAULT_FORM_EXTRAS,
    val formDefaultType: String = TxnType.EXPENSE,
    val formDefaultAccount: Long? = null,
    // Customise › Transactions (82)
    val onPaymentFound: String = PaymentFound.ASK,
    val showTime: Boolean = true,
    val countUpcoming: Boolean = false,
    val confirmDelete: Boolean = false,
    val swipeRight: String = SwipeRight.PAID,
    val swipeLeft: String = SwipeLeft.SKIP,
    // Customise › Region (85) and Currency (83)
    /** Null = from the language (Indian grouping for India, Western otherwise). */
    val numberFormat: String? = null,
    val dateFormat: String = DateStyle.WRITTEN,
    val weekStart: String = "MONDAY",
    val currencyDisplay: String = "SYMBOL", // SYMBOL, CODE
    val decimals: String = "AUTO", // AUTO, NONE, ALWAYS
)

val ALL_HOME_WIDGETS = setOf("week", "bill", "networth", "topcat", "goal", "inout")
val HOME_WIDGET_ORDER = listOf("week", "bill", "networth", "topcat", "inout", "goal")

/** Today's top card (Customise › Today). */
object TopCard { const val BUDGET = "BUDGET"; const val SPENT = "SPENT"; const val BALANCE = "BALANCE" }

/** New expense extras (Customise › Expense): chips under the rows, plus the time and last-category options. */
object FormExtra {
    const val NOTE = "note"; const val REPEAT = "repeat"; const val ATTACH = "attach"; const val LEND = "lend"
    const val TIME = "time"; const val LAST_CATEGORY = "lastcat"
    /** The Favourite chip (04): saves the expense as a one-tap favourite too. */
    const val FAVOURITE = "favourite"
}
val ALL_FORM_EXTRAS = setOf(FormExtra.NOTE, FormExtra.REPEAT, FormExtra.ATTACH, FormExtra.LEND, FormExtra.TIME, FormExtra.LAST_CATEGORY)

/**
 * The New expense extras before any customising. "Last category" starts off: it would pick a
 * category on every new form, hiding Favourites (they show on an empty form) and letting Save
 * through without a choice. It can be switched on in Customise › Expense.
 */
val DEFAULT_FORM_EXTRAS = ALL_FORM_EXTRAS - FormExtra.LAST_CATEGORY

/** What a captured payment does (Customise › Transactions). */
object PaymentFound { const val ASK = "ASK"; const val EDIT_FIRST = "EDIT_FIRST"; const val SAVE = "SAVE" }
object SwipeRight { const val PAID = "PAID"; const val EDIT = "EDIT"; const val DUPLICATE = "DUPLICATE" }
object SwipeLeft { const val SKIP = "SKIP"; const val DELETE = "DELETE" }
object NumberStyle { const val INDIAN = "INDIAN"; const val WESTERN = "WESTERN"; const val DOT = "DOT"; const val SPACE = "SPACE" }
object DateStyle { const val WRITTEN = "WRITTEN"; const val DMY = "DMY"; const val MDY = "MDY"; const val YMD = "YMD" }

/** The phone region's currency, or INR when the region has none. */
/**
 * The phone's country from the SIM or mobile network ("IN"), set once at app start. Many phones in
 * India run English (United States), so the language alone would suggest dollars.
 */
object Region {
    @Volatile var simCountry: String? = null
        private set

    fun detect(context: android.content.Context) {
        simCountry = runCatching {
            val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
            listOf(tm?.simCountryIso, tm?.networkCountryIso).firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT)
        }.getOrNull()
    }
}

/** The region's currency: the SIM or network country first, then the language's country. */
fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
    Region.simCountry?.let { c -> runCatching { Currency.getInstance(Locale.Builder().setRegion(c).build()).currencyCode }.getOrNull() }
        ?: runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "INR"

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
    suspend fun setTopCard(v: String) = store.edit { it[TOP_CARD] = v }
    suspend fun setWidgetOrder(order: List<String>) = store.edit { it[WIDGET_ORDER] = order.joinToString(",") }
    suspend fun setRecentCount(n: Int) = store.edit { it[RECENT_COUNT] = n }
    suspend fun setShowReview(on: Boolean) = store.edit { it[SHOW_REVIEW] = on }
    suspend fun setFormExtra(key: String, on: Boolean) = store.edit {
        // The Favourite chip came later: it is on unless switched off, also for forms customised before it.
        if (key == FormExtra.FAVOURITE) { it[FAVOURITE_OFF] = !on; return@edit }
        val now = it[FORM_EXTRAS] ?: DEFAULT_FORM_EXTRAS
        it[FORM_EXTRAS] = if (on) now + key else now - key
    }
    suspend fun setFormDefaultType(t: String) = store.edit { it[FORM_TYPE] = t }
    suspend fun setFormDefaultAccount(id: Long?) = store.edit { if (id == null) it.remove(FORM_ACCOUNT) else it[FORM_ACCOUNT] = id }
    suspend fun setPaymentFound(v: String) = store.edit { it[PAYMENT_FOUND] = v }
    suspend fun setShowTime(on: Boolean) = store.edit { it[SHOW_TIME] = on }
    suspend fun setCountUpcoming(on: Boolean) = store.edit { it[COUNT_UPCOMING] = on }
    suspend fun setConfirmDelete(on: Boolean) = store.edit { it[CONFIRM_DELETE] = on }
    suspend fun setSwipeRight(v: String) = store.edit { it[SWIPE_RIGHT] = v }
    suspend fun setSwipeLeft(v: String) = store.edit { it[SWIPE_LEFT] = v }
    suspend fun setNumberFormat(v: String) = store.edit { it[NUMBER_FORMAT] = v }
    suspend fun setDateFormat(v: String) = store.edit { it[DATE_FORMAT] = v }
    suspend fun setWeekStart(v: String) = store.edit { it[WEEK_START] = v }
    suspend fun setCurrencyDisplay(v: String) = store.edit { it[CURRENCY_DISPLAY] = v }
    suspend fun setDecimals(v: String) = store.edit { it[DECIMALS] = v }
    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = true }
    suspend fun setHideAmounts(on: Boolean) = store.edit { it[HIDE_AMOUNTS] = on }
    suspend fun setAppLock(on: Boolean) = store.edit { it[APP_LOCK] = on }
    suspend fun setLockAfter(seconds: Int) = store.edit { it[LOCK_AFTER] = seconds }
    suspend fun setHideInNotifications(on: Boolean) = store.edit { it[HIDE_IN_NOTIFICATIONS] = on }
    suspend fun setNotify(key: String, on: Boolean) = store.edit {
        when (key) {
            "capture" -> it[NOTIFY_CAPTURE] = on
            "bills" -> it[NOTIFY_BILLS] = on
            "budgets" -> it[NOTIFY_BUDGETS] = on
            "summary" -> it[NOTIFY_SUMMARY] = on
            "weekly" -> it[NOTIFY_WEEKLY] = on
        }
    }

    /** The PIN as a salted PBKDF2 hash ("salt:hash", hex); never the PIN itself. */
    suspend fun pinHash(): String? = store.data.first()[PIN_HASH]
    suspend fun setPinHash(hash: String?) = store.edit { if (hash == null) it.remove(PIN_HASH) else it[PIN_HASH] = hash }

    suspend fun setBackupFolder(uri: String?) = store.edit { if (uri == null) it.remove(BACKUP_FOLDER) else it[BACKUP_FOLDER] = uri }
    suspend fun setBackupSchedule(s: String) = store.edit { it[BACKUP_SCHEDULE] = s }
    suspend fun setLastBackupAt(at: Long) = store.edit { it[LAST_BACKUP_AT] = at }

    /** The backup password, sealed with a key from the Android Keystore (so scheduled backups can run). */
    suspend fun sealedBackupPassword(): String? = store.data.first()[BACKUP_PASSWORD]
    suspend fun setSealedBackupPassword(sealed: String?) = store.edit { if (sealed == null) it.remove(BACKUP_PASSWORD) else it[BACKUP_PASSWORD] = sealed }

    /** Delete all data: every setting goes, except that the welcome isn't shown again. */
    suspend fun clearAllButOnboarding() = store.edit { prefs ->
        val keep = prefs[ONBOARDED]
        prefs.clear()
        if (keep != null) prefs[ONBOARDED] = keep
        prefs[SAMPLES_DONE] = true
    }

    /** True once the sample month was seeded or declined: it never comes back after Delete all data. */
    suspend fun samplesDone(): Boolean = store.data.first()[SAMPLES_DONE] == true
    suspend fun setSamplesDone() = store.edit { it[SAMPLES_DONE] = true }

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
            onboarded = this[ONBOARDED] ?: false,
            hideAmounts = this[HIDE_AMOUNTS] ?: false,
            appLock = (this[APP_LOCK] ?: false) && this[PIN_HASH] != null,
            hasPin = this[PIN_HASH] != null,
            lockAfterSeconds = this[LOCK_AFTER] ?: 60,
            hideInNotifications = this[HIDE_IN_NOTIFICATIONS] ?: false,
            notifyCapture = this[NOTIFY_CAPTURE] ?: true,
            notifyBills = this[NOTIFY_BILLS] ?: true,
            notifyBudgets = this[NOTIFY_BUDGETS] ?: true,
            notifySummary = this[NOTIFY_SUMMARY] ?: false,
            notifyWeekly = this[NOTIFY_WEEKLY] ?: true,
            backupFolder = this[BACKUP_FOLDER],
            backupSchedule = this[BACKUP_SCHEDULE] ?: "OFF",
            lastBackupAt = this[LAST_BACKUP_AT],
            hasBackupPassword = this[BACKUP_PASSWORD] != null,
            homeTopCard = this[TOP_CARD] ?: TopCard.BUDGET,
            homeWidgetOrder = this[WIDGET_ORDER]?.split(",")?.filter { it in ALL_HOME_WIDGETS }
                ?.let { saved -> saved + HOME_WIDGET_ORDER.filter { it !in saved } } ?: HOME_WIDGET_ORDER,
            homeRecentCount = this[RECENT_COUNT] ?: 5,
            homeShowReview = this[SHOW_REVIEW] ?: true,
            formExtras = (this[FORM_EXTRAS] ?: DEFAULT_FORM_EXTRAS).let { if (this[FAVOURITE_OFF] == true) it - FormExtra.FAVOURITE else it + FormExtra.FAVOURITE },
            formDefaultType = this[FORM_TYPE] ?: TxnType.EXPENSE,
            formDefaultAccount = this[FORM_ACCOUNT],
            onPaymentFound = this[PAYMENT_FOUND] ?: PaymentFound.ASK,
            showTime = this[SHOW_TIME] ?: true,
            countUpcoming = this[COUNT_UPCOMING] ?: false,
            confirmDelete = this[CONFIRM_DELETE] ?: false,
            swipeRight = this[SWIPE_RIGHT] ?: SwipeRight.PAID,
            swipeLeft = this[SWIPE_LEFT] ?: SwipeLeft.SKIP,
            numberFormat = this[NUMBER_FORMAT],
            dateFormat = this[DATE_FORMAT] ?: DateStyle.WRITTEN,
            weekStart = this[WEEK_START] ?: "MONDAY",
            currencyDisplay = this[CURRENCY_DISPLAY] ?: "SYMBOL",
            decimals = this[DECIMALS] ?: "AUTO",
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
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val SAMPLES_DONE = booleanPreferencesKey("samplesDone")
        val HIDE_AMOUNTS = booleanPreferencesKey("hideAmounts")
        val APP_LOCK = booleanPreferencesKey("appLock")
        val PIN_HASH = stringPreferencesKey("pinHash")
        val LOCK_AFTER = intPreferencesKey("lockAfter")
        val HIDE_IN_NOTIFICATIONS = booleanPreferencesKey("hideInNotifications")
        val NOTIFY_CAPTURE = booleanPreferencesKey("notifyCapture")
        val NOTIFY_BILLS = booleanPreferencesKey("notifyBills")
        val NOTIFY_BUDGETS = booleanPreferencesKey("notifyBudgets")
        val NOTIFY_SUMMARY = booleanPreferencesKey("notifySummary")
        val NOTIFY_WEEKLY = booleanPreferencesKey("weeklyReview")
        val BACKUP_FOLDER = stringPreferencesKey("backupFolderUri")
        val BACKUP_SCHEDULE = stringPreferencesKey("backupSchedule")
        val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt")
        val BACKUP_PASSWORD = stringPreferencesKey("backupPassword")
        val TOP_CARD = stringPreferencesKey("homeTopCard")
        val WIDGET_ORDER = stringPreferencesKey("homeWidgetsOrder")
        val RECENT_COUNT = intPreferencesKey("homeRecentCount")
        val SHOW_REVIEW = booleanPreferencesKey("homeShowReview")
        val FORM_EXTRAS = stringSetPreferencesKey("formExtras")
        val FAVOURITE_OFF = booleanPreferencesKey("formFavouriteOff")
        val FORM_TYPE = stringPreferencesKey("formDefaultType")
        val FORM_ACCOUNT = longPreferencesKey("formDefaultAccount")
        val PAYMENT_FOUND = stringPreferencesKey("onPaymentFound")
        val SHOW_TIME = booleanPreferencesKey("showTime")
        val COUNT_UPCOMING = booleanPreferencesKey("countUpcoming")
        val CONFIRM_DELETE = booleanPreferencesKey("confirmDelete")
        val SWIPE_RIGHT = stringPreferencesKey("swipeRight")
        val SWIPE_LEFT = stringPreferencesKey("swipeLeft")
        val NUMBER_FORMAT = stringPreferencesKey("numberFormat")
        val DATE_FORMAT = stringPreferencesKey("dateFormat")
        val WEEK_START = stringPreferencesKey("weekStart")
        val CURRENCY_DISPLAY = stringPreferencesKey("currencyDisplay")
        val DECIMALS = stringPreferencesKey("decimals")
    }
}

/** A stored enum name, or [default] when it is missing or from an older version. */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
