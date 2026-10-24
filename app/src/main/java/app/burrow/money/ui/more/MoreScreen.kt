package app.burrow.money.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.BuildConfig
import app.burrow.money.R
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.StatusTile
import app.burrow.money.ui.components.TabScreen
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.AccentSeeds
import app.burrow.money.ui.theme.BodyFont
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.ThemeMode
import java.time.LocalDate

/** Where each More row goes. */
enum class MoreDest { CAPTURE, BACKUP, LOCK, APPEARANCE, CATEGORIES, ACCOUNTS, CUSTOMISE_TODAY, CUSTOMISE_EXPENSE, CUSTOMISE_TRANSACTIONS, CUSTOMISE_REGION, CUSTOMISE_CURRENCY, FONT, NOTIFICATIONS, CSV, ABOUT }

/**
 * More (53, 54): four status tiles (Auto-capture, Backup, App lock, Appearance); Money (Categories,
 * Accounts, Main currency, Month starts on); App (Language, Font and text size, Notifications,
 * Hide amounts, Export or import CSV); About (About Burrow, Delete all data). Plain lists.
 */
@Composable
fun MoreScreen(vm: MoreViewModel, onOpen: (MoreDest) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val money = LocalMoney.current
    val dates = rememberDateText()
    TabScreen(title = stringResource(R.string.tab_more)) { padding ->
        val s = state ?: return@TabScreen
        val st = s.settings
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            // What really records, not just the saved switches (On while Android blocks it was wrong).
            val capture = app.burrow.money.ui.capture.rememberCaptureStatus(st.smsCapture, st.notificationCapture)
            val captureOn = capture.anyOn
            // Tiles pair up; at large text each takes a row (157).
            app.burrow.money.ui.components.PairRow(first = { m ->
                StatusTile(
                    painterResource(R.drawable.ic_sms_duotone), 28f,
                    // 53 / 147 / 148: On · "92% by itself", Needs access · "Tap to fix", Not set up · "Tap to turn on".
                    stringResource(
                        when (capture.tile) {
                            app.burrow.money.capture.CaptureStatus.Tile.ON -> R.string.on
                            app.burrow.money.capture.CaptureStatus.Tile.PAUSED -> R.string.needs_access
                            app.burrow.money.capture.CaptureStatus.Tile.OFF -> R.string.not_set_up
                        },
                    ),
                    stringResource(R.string.auto_capture), when (capture.tile) {
                        app.burrow.money.capture.CaptureStatus.Tile.PAUSED -> stringResource(R.string.tap_to_fix)
                        app.burrow.money.capture.CaptureStatus.Tile.OFF -> stringResource(R.string.tap_to_turn_on)
                        else -> if (s.trustPercent != null && s.capturedThisMonth > 0) stringResource(R.string.percent_by_itself, s.trustPercent)
                            else stringResource(R.string.n_this_month, s.capturedThisMonth)
                    },
                    captureOn, { onOpen(MoreDest.CAPTURE) }, m,
                )
            }) { m ->
                StatusTile(
                    painterResource(R.drawable.ic_cloud_done_duotone), 215f, st.lastBackupAt?.let { dates.time(it) },
                    stringResource(R.string.backup), backupLine(st.backupSchedule, st.lastBackupAt),
                    false, { onOpen(MoreDest.BACKUP) }, m,
                )
            }
            app.burrow.money.ui.components.PairRow(Modifier.padding(top = Space.block), first = { m ->
                StatusTile(
                    painterResource(R.drawable.ic_fingerprint_duotone), 140f, stringResource(if (st.appLock) R.string.on else R.string.off),
                    stringResource(R.string.app_lock), stringResource(if (st.appLock) R.string.pin_and_fingerprint else R.string.lock_off_line),
                    st.appLock, { onOpen(MoreDest.LOCK) }, m,
                )
            }) { m ->
                StatusTile(
                    painterResource(R.drawable.ic_palette_duotone), 275f, themeLabel(st.theme.mode),
                    stringResource(R.string.appearance), stringResource(R.string.dot_join, accentName(st.theme.accentHue, st.theme.dynamicColor), fontName(st.theme.font)),
                    false, { onOpen(MoreDest.APPEARANCE) }, m,
                )
            }
            SectionHeader(stringResource(R.string.money))
            ListGroup {
                ListRow(stringResource(R.string.accounts), 0, 2, supporting = pluralStringResource(R.plurals.accounts_line, s.accounts, s.accounts, money.short(s.netWorth).let { if (s.netWorth < 0) app.burrow.money.domain.MoneyFormat.MINUS + it else it }), lead = { IconCircle(painterResource(R.drawable.ic_account_balance_wallet_duotone), hue = 192f) }, onClick = { onOpen(MoreDest.ACCOUNTS) })
                ListRow(stringResource(R.string.categories), 1, 2, supporting = pluralStringResource(R.plurals.n_categories, s.categories, s.categories), lead = { IconCircle(painterResource(R.drawable.ic_category_duotone), hue = 28f) }, onClick = { onOpen(MoreDest.CATEGORIES) })
            }
            SectionHeader(stringResource(R.string.customise))
            ListGroup {
                ListRow(stringResource(R.string.today_screen), 0, 5, supporting = stringResource(R.string.today_screen_line), lead = { IconCircle(painterResource(R.drawable.ic_grid_view_duotone), hue = 345f) }, onClick = { onOpen(MoreDest.CUSTOMISE_TODAY) })
                ListRow(stringResource(R.string.new_expense_form), 1, 5, supporting = stringResource(R.string.new_expense_form_line), lead = { IconCircle(painterResource(R.drawable.ic_edit_note_duotone), hue = 140f) }, onClick = { onOpen(MoreDest.CUSTOMISE_EXPENSE) })
                ListRow(stringResource(R.string.transactions), 2, 5, supporting = stringResource(paymentFoundLabel(st.onPaymentFound)), lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone), hue = 215f) }, onClick = { onOpen(MoreDest.CUSTOMISE_TRANSACTIONS) })
                ListRow(stringResource(R.string.language_and_region), 3, 5, supporting = stringResource(R.string.dot_join, AppLanguage.currentName(context), money.sampleNumber()), lead = { IconCircle(painterResource(R.drawable.ic_translate_duotone), hue = 192f) }, onClick = { onOpen(MoreDest.CUSTOMISE_REGION) })
                ListRow(stringResource(R.string.currency), 4, 5, supporting = currencyLine2(st.mainCurrency, s.otherCurrencies), lead = { IconCircle(painterResource(app.burrow.money.ui.components.currencyIcon(st.mainCurrency)), hue = 50f) }, onClick = { onOpen(MoreDest.CUSTOMISE_CURRENCY) })
            }
            SectionHeader(stringResource(R.string.app))
            ListGroup {
                ListRow(stringResource(R.string.notifications), 0, 3, supporting = stringResource(R.string.notifications_line), lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone), hue = 10f) }, onClick = { onOpen(MoreDest.NOTIFICATIONS) })
                ListRow(stringResource(R.string.hide_amounts), 1, 3, supporting = stringResource(R.string.hide_amounts_line, money.withHidden(true).plain(0)), lead = { IconCircle(painterResource(R.drawable.ic_visibility_off_duotone), hue = 318f) }, trailing = { BurrowSwitch(st.hideAmounts, vm::setHideAmounts) })
                ListRow(stringResource(R.string.import_export), 2, 3, supporting = stringResource(R.string.import_export_line), lead = { IconCircle(painterResource(R.drawable.ic_import_duotone), hue = 168f) }, onClick = { onOpen(MoreDest.CSV) })
            }
            SectionHeader(stringResource(R.string.about))
            ListGroup {
                ListRow(
                    stringResource(R.string.about_burrow), 0, 2, supporting = stringResource(R.string.about_line, BuildConfig.VERSION_NAME),
                    lead = { IconCircle(painterResource(R.drawable.ic_notification), container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary) },
                    onClick = { onOpen(MoreDest.ABOUT) },
                )
                ListRow(
                    stringResource(R.string.delete_all), 1, 2, supporting = stringResource(R.string.back_up_first),
                    lead = { IconCircle(painterResource(R.drawable.ic_delete_duotone), container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.error) },
                    onClick = { confirmDelete = true },
                )
            }
        }
    }
    val s = state ?: return
    if (confirmDelete) {
        ConfirmDialog(
            painterResource(R.drawable.ic_delete_forever_regular), stringResource(R.string.delete_all_title), stringResource(R.string.delete_all_text),
            stringResource(R.string.delete), stringResource(R.string.cancel),
            onConfirm = { confirmDelete = false; vm.deleteAll { snackbar.show(context.getString(R.string.all_deleted)) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun themeLabel(mode: ThemeMode) = stringResource(when (mode) { ThemeMode.LIGHT -> R.string.light; ThemeMode.DARK -> R.string.dark; ThemeMode.SYSTEM -> R.string.auto })

@Composable
fun accentName(hue: Float, dynamic: Boolean): String = if (dynamic) stringResource(R.string.wallpaper) else stringResource(
    when (AccentSeeds.minByOrNull { kotlin.math.abs(it.first - hue) }?.first) {
        12f -> R.string.accent_rust
        38f -> R.string.accent_amber
        150f -> R.string.accent_green
        192f -> R.string.accent_teal
        215f -> R.string.accent_blue
        275f -> R.string.accent_purple
        else -> R.string.accent_rose
    },
)

@Composable
fun fontName(f: BodyFont) = stringResource(
    when (f) {
        BodyFont.LEXEND -> R.string.font_lexend
        BodyFont.OUTFIT -> R.string.font_outfit
        BodyFont.ATKINSON -> R.string.font_atkinson
        BodyFont.SPACE_GROTESK -> R.string.font_space_grotesk
        BodyFont.SYSTEM -> R.string.font_system
    },
)

@Composable
fun textSizeName(t: app.burrow.money.ui.theme.TextSize) = stringResource(
    when (t) {
        app.burrow.money.ui.theme.TextSize.SMALL -> R.string.size_small
        app.burrow.money.ui.theme.TextSize.LARGE -> R.string.size_large
        else -> R.string.size_default
    },
)

/** "Indian rupee · ₹". */
@Composable
fun currencyLine(code: String): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val name = runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
    return "${name.replaceFirstChar { it.uppercase() }} · ${app.burrow.money.domain.MoneyFormat.symbolFor(code, locale)}"
}

@Composable
private fun backupLine(schedule: String, last: Long?): String {
    val dates = rememberDateText()
    val when_ = last?.let { stringResource(R.string.last_on, dates.day(dates.date(it)).replaceFirstChar { c -> c.lowercase() }) } ?: stringResource(R.string.never)
    return when (schedule) {
        "DAILY" -> stringResource(R.string.dot_join, stringResource(R.string.daily), when_)
        "WEEKLY" -> stringResource(R.string.dot_join, stringResource(R.string.weekly), when_)
        else -> if (last == null) stringResource(R.string.not_set_up) else when_.replaceFirstChar { it.uppercase() }
    }
}

/** "1,00,000.00": the number style as Region shows it. */
private fun app.burrow.money.domain.MoneyFormat.sampleNumber(): String {
    val st = style
    val grouped = if (st.grouping == app.burrow.money.domain.Grouping.INDIAN) "1${st.groupSeparator}00${st.groupSeparator}000" else "100${st.groupSeparator}000"
    return grouped + st.decimalSeparator + "00"
}

/** "₹ primary · 3 others". */
@Composable
private fun currencyLine2(code: String, others: Int): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val primary = stringResource(R.string.primary_short, app.burrow.money.domain.MoneyFormat.symbolFor(code, locale))
    return if (others == 0) primary else stringResource(R.string.dot_join, primary, pluralStringResource(R.plurals.n_others, others, others))
}

fun paymentFoundLabel(v: String) = when (v) {
    app.burrow.money.data.PaymentFound.EDIT_FIRST -> R.string.found_edit
    app.burrow.money.data.PaymentFound.SAVE -> R.string.found_save
    else -> R.string.found_ask
}
