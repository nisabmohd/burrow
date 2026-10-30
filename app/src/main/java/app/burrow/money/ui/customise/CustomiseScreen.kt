package app.burrow.money.ui.customise

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.DateStyle
import app.burrow.money.data.FormExtra
import app.burrow.money.data.NumberStyle
import app.burrow.money.data.PaymentFound
import app.burrow.money.data.Settings
import app.burrow.money.data.SwipeLeft
import app.burrow.money.data.SwipeRight
import app.burrow.money.data.TopCard
import app.burrow.money.data.TxnType
import app.burrow.money.domain.Fx
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Choice
import app.burrow.money.ui.components.ChoiceTiles
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.OrderedRow
import app.burrow.money.ui.components.DragColumn
import app.burrow.money.ui.components.PickList
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.Toggle
import app.burrow.money.ui.components.ToggleTiles
import app.burrow.money.ui.components.currencyIcon
import app.burrow.money.ui.more.AppLanguage
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.fadeThrough
import app.burrow.money.ui.today.HOME_WIDGET_ROWS
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** The five tabs of Customise, in order. */
enum class CustomiseTab(val label: Int) {
    TODAY(R.string.tab_today), EXPENSE(R.string.type_expense), TRANSACTIONS(R.string.transactions),
    REGION(R.string.region), CURRENCY(R.string.currency),
}

private enum class CustomiseSheet { MONTH_START, SWIPE_RIGHT, SWIPE_LEFT, ACCOUNT, ADD_CURRENCY }

/**
 * Customise (80–85): ONE screen, tabs Today · Expense · Transactions · Region · Currency, built only
 * from choice tiles, toggle tiles, ordered rows and switch rows (docs/DESIGN.md § Simple, not bare).
 */
@Composable
fun CustomiseScreen(
    vm: CustomiseViewModel,
    settings: Settings,
    start: CustomiseTab,
    onBack: () -> Unit,
    onLanguage: () -> Unit,
    onMainCurrency: () -> Unit,
    onFavourites: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(start) }
    var sheet by rememberSaveable { mutableStateOf<CustomiseSheet?>(null) }
    var rateFor by rememberSaveable { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = stringResource(R.string.customise))
        // Pill tabs like Plan's, scrolling: five don't fit one row (80).
        app.burrow.money.ui.components.PillTabs(
            CustomiseTab.entries, tab, { stringResource(it.label) }, { tab = it }, Modifier.padding(vertical = Space.small), scrolling = true,
        )
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = { fadeThrough() }, label = "customiseTab") { t ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen)) {
                val s = state ?: return@Column
                when (t) {
                    CustomiseTab.TODAY -> TodayTab(vm, settings, s)
                    CustomiseTab.EXPENSE -> ExpenseTab(vm, settings, s, onFavourites) { sheet = CustomiseSheet.ACCOUNT }
                    CustomiseTab.TRANSACTIONS -> TransactionsTab(vm, settings, { sheet = CustomiseSheet.SWIPE_RIGHT }, { sheet = CustomiseSheet.SWIPE_LEFT })
                    CustomiseTab.REGION -> RegionTab(vm, settings, onLanguage) { sheet = CustomiseSheet.MONTH_START }
                    CustomiseTab.CURRENCY -> CurrencyTab(vm, settings, s, onMainCurrency, onRate = { rateFor = it }, onAdd = { sheet = CustomiseSheet.ADD_CURRENCY })
                }
            }
        }
    }

    val dismiss = { sheet = null }
    when (sheet) {
        CustomiseSheet.MONTH_START -> MonthStartSheet(settings.monthStartDay, { vm.monthStart(it); dismiss() }, dismiss)
        CustomiseSheet.SWIPE_RIGHT -> OptionSheet(
            stringResource(R.string.swipe_right), listOf(SwipeRight.PAID to R.string.swipe_paid, SwipeRight.EDIT to R.string.edit, SwipeRight.DUPLICATE to R.string.duplicate),
            settings.swipeRight, { vm.swipeRight(it); dismiss() }, dismiss,
        )
        CustomiseSheet.SWIPE_LEFT -> OptionSheet(
            stringResource(R.string.swipe_left), listOf(SwipeLeft.SKIP to R.string.swipe_skip, SwipeLeft.DELETE to R.string.delete),
            settings.swipeLeft, { vm.swipeLeft(it); dismiss() }, dismiss,
        )
        CustomiseSheet.ACCOUNT -> state?.let { s ->
            AccountSheet(s.accounts.filter { it.account.type != "LOAN" && it.account.type != "SAVINGS" }, settings.formDefaultAccount, { vm.defaultAccount(it.account.id); dismiss() }, dismiss)
        }
        CustomiseSheet.ADD_CURRENCY -> AddCurrencySheet(settings.mainCurrency, state?.rates.orEmpty().map { it.currency }.toSet(), { code -> dismiss(); rateFor = code }, dismiss)
        null -> Unit
    }
    rateFor?.let { code ->
        RateSheet(code, settings.mainCurrency, state?.rates?.firstOrNull { it.currency == code }, onSave = { vm.setRate(code, it); rateFor = null }, onRemove = { vm.removeRate(code); rateFor = null }, onDismiss = { rateFor = null })
    }
}

/** Today (80): top card, widgets (order + show), recent count, the To review note. */
@Composable
private fun TodayTab(vm: CustomiseViewModel, settings: Settings, s: CustomiseState) {
    val money = LocalMoney.current
    SectionHeader(stringResource(R.string.top_card))
    PickList(
        listOf(
            Choice(TopCard.BUDGET, stringResource(R.string.top_budget), s.budgetLeft?.let { stringResource(R.string.top_budget_line, money.short(it)) } ?: stringResource(R.string.hero_no_budget)),
            Choice(TopCard.SPENT, stringResource(R.string.spent), stringResource(R.string.top_spent_line, money.short(s.spent))),
            Choice(TopCard.BALANCE, stringResource(R.string.top_balance), stringResource(R.string.top_balance_line, money.short(s.netWorth))),
        ),
        settings.homeTopCard, vm::topCard,
    )
    val order = settings.homeWidgetOrder
    SectionHeader(stringResource(R.string.widgets), meta = stringResource(R.string.hold_and_drag))
    val names = HOME_WIDGET_ROWS.mapValues { (_, v) -> stringResource(v.first) }
    val upLabels = names.mapValues { (_, n) -> stringResource(R.string.move_up, n) }
    val downLabels = names.mapValues { (_, n) -> stringResource(R.string.move_down, n) }
    DragColumn(
        order, key = { it }, onMove = vm::setWidgetOrder,
        moveUpLabel = { upLabels.getValue(it) }, moveDownLabel = { downLabels.getValue(it) },
    ) { key, i, n, handle, lifted ->
        val (_, icon) = HOME_WIDGET_ROWS.getValue(key)
        OrderedRow(painterResource(icon), names.getValue(key), i, n, key in settings.homeWidgets, { vm.showWidget(key, it) }, handle, lifted)
    }
    SectionHeader(stringResource(R.string.recent_payments))
    ButtonGroup(listOf(3, 5, 10), settings.homeRecentCount, { it.toString() }, vm::recent, Modifier.fillMaxWidth())
    Spacer(Modifier.height(Space.block))
    ListGroup {
        ListRow(
            stringResource(R.string.to_review), 0, 1, supporting = stringResource(R.string.note_on_today),
            lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
            trailing = { BurrowSwitch(settings.homeShowReview, vm::showReview) },
        )
    }
}

/** New expense form (81): extras, opens as, default account. */
@Composable
private fun ExpenseTab(vm: CustomiseViewModel, settings: Settings, s: CustomiseState, onFavourites: () -> Unit, onAccount: () -> Unit) {
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    SectionHeader(stringResource(R.string.extras_title), meta = stringResource(R.string.tap_show_hide))
    val x = settings.formExtras
    ToggleTiles(
        listOf(
            Toggle(FormExtra.NOTE, stringResource(R.string.note), painterResource(R.drawable.ic_notes_duotone), FormExtra.NOTE in x),
            Toggle(FormExtra.REPEAT, stringResource(R.string.extra_repeat), painterResource(R.drawable.ic_event_repeat_duotone), FormExtra.REPEAT in x),
            Toggle(FormExtra.ATTACH, stringResource(R.string.extra_attach), painterResource(R.drawable.ic_attach_file_duotone), FormExtra.ATTACH in x),
            Toggle(FormExtra.LEND, stringResource(R.string.extra_lend), painterResource(R.drawable.ic_handshake_duotone), FormExtra.LEND in x),
            Toggle(FormExtra.TIME, stringResource(R.string.extra_time), painterResource(R.drawable.ic_schedule_duotone), FormExtra.TIME in x),
            Toggle(FormExtra.LAST_CATEGORY, stringResource(R.string.extra_last_category), painterResource(R.drawable.ic_history_duotone), FormExtra.LAST_CATEGORY in x),
            Toggle(FormExtra.FAVOURITE, stringResource(R.string.favourite), painterResource(R.drawable.ic_star_duotone), FormExtra.FAVOURITE in x),
            Toggle(FormExtra.RECENT, stringResource(R.string.extra_recent), painterResource(R.drawable.ic_history_duotone), FormExtra.RECENT in x),
        ),
        vm::extra, on, off,
    )
    SectionHeader(stringResource(R.string.favourites))
    ListGroup {
        ListRow(
            stringResource(R.string.favourites), 0, 1, supporting = pluralStringResource(R.plurals.n_favourites, s.favourites, s.favourites),
            lead = { IconCircle(painterResource(R.drawable.ic_star_duotone)) }, trailing = { app.burrow.money.ui.components.Chevron() }, onClick = onFavourites,
        )
    }
    SectionHeader(stringResource(R.string.opens_as))
    ChoiceTiles(
        listOf(
            Choice(TxnType.EXPENSE, stringResource(R.string.type_expense)),
            Choice(TxnType.INCOME, stringResource(R.string.type_income)),
            Choice(TxnType.TRANSFER, stringResource(R.string.type_transfer)),
        ),
        settings.formDefaultType, vm::defaultType,
    )
    SectionHeader(stringResource(R.string.default_account))
    val acc = s.accounts.firstOrNull { it.account.id == settings.formDefaultAccount }
        ?: s.accounts.firstOrNull { it.account.type != "LOAN" && it.account.type != "SAVINGS" }
    ListGroup {
        ListRow(
            acc?.account?.name ?: stringResource(R.string.choose_account), 0, 1, supporting = stringResource(R.string.change),
            lead = { CategoryCircle(acc?.account?.icon, acc?.account?.colorHue) }, onClick = onAccount,
        )
    }
}

/** Transactions (82): what a found payment does, list options, swipes. */
@Composable
private fun TransactionsTab(vm: CustomiseViewModel, settings: Settings, onRight: () -> Unit, onLeft: () -> Unit) {
    SectionHeader(stringResource(R.string.when_payment_found))
    ChoiceTiles(
        listOf(
            Choice(PaymentFound.ASK, stringResource(R.string.found_ask), stringResource(R.string.found_ask_line), painterResource(R.drawable.ic_notifications_active_duotone)),
            Choice(PaymentFound.EDIT_FIRST, stringResource(R.string.found_edit), stringResource(R.string.found_edit_line), painterResource(R.drawable.ic_edit_note_duotone)),
            Choice(PaymentFound.SAVE, stringResource(R.string.found_save), stringResource(R.string.found_save_line), painterResource(R.drawable.ic_task_alt_duotone)),
        ),
        settings.onPaymentFound, vm::paymentFound,
    )
    SectionHeader(stringResource(R.string.lists), meta = stringResource(R.string.tap_on_off))
    ToggleTiles(
        listOf(
            Toggle("time", stringResource(R.string.show_time), painterResource(R.drawable.ic_schedule_duotone), settings.showTime),
            Toggle("upcoming", stringResource(R.string.upcoming_in_balances), painterResource(R.drawable.ic_event_upcoming_duotone), settings.countUpcoming),
            Toggle("upcomingSafe", stringResource(R.string.upcoming_lowers_safe), painterResource(R.drawable.ic_pie_chart_duotone), settings.upcomingLowersSafe),
            Toggle("confirm", stringResource(R.string.ask_before_delete), painterResource(R.drawable.ic_delete_duotone), settings.confirmDelete),
        ),
        vm::listOption, stringResource(R.string.on), stringResource(R.string.off),
    )
    SectionHeader(stringResource(R.string.swipe))
    ListGroup {
        ListRow(stringResource(R.string.swipe_right), 0, 2, supporting = stringResource(swipeRightLabel(settings.swipeRight)), lead = { IconCircle(painterResource(R.drawable.ic_arrow_forward_duotone)) }, onClick = onRight)
        ListRow(stringResource(R.string.swipe_left), 1, 2, supporting = stringResource(swipeLeftLabel(settings.swipeLeft)), lead = { IconCircle(painterResource(R.drawable.ic_arrow_back_duotone)) }, onClick = onLeft)
    }
}

fun swipeRightLabel(v: String) = when (v) { SwipeRight.EDIT -> R.string.edit; SwipeRight.DUPLICATE -> R.string.duplicate; else -> R.string.swipe_paid }
fun swipeLeftLabel(v: String) = if (v == SwipeLeft.DELETE) R.string.delete else R.string.swipe_skip

/** Region (85): language, numbers, dates, week, month start. */
@Composable
private fun RegionTab(vm: CustomiseViewModel, settings: Settings, onLanguage: () -> Unit, onMonthStart: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dates = rememberDateText()
    // Same gap under the tab chips as the tabs that open with a section header.
    Spacer(Modifier.height(Space.sectionTop))
    ListGroup {
        ListRow(stringResource(R.string.language), 0, 1, supporting = AppLanguage.currentName(context), lead = { IconCircle(painterResource(R.drawable.ic_translate_duotone)) }, onClick = onLanguage)
    }
    SectionHeader(stringResource(R.string.numbers))
    val current = settings.numberFormat ?: if (MoneyFormat.of(settings.mainCurrency, locale).style.grouping == app.burrow.money.domain.Grouping.INDIAN) NumberStyle.INDIAN else NumberStyle.WESTERN
    PickList(
        listOf(
            Choice(NumberStyle.INDIAN, "1,00,000.00", stringResource(R.string.num_india)),
            Choice(NumberStyle.WESTERN, "100,000.00", stringResource(R.string.num_western)),
            Choice(NumberStyle.DOT, "100.000,00", stringResource(R.string.num_dot)),
            Choice(NumberStyle.SPACE, "100 000,00", stringResource(R.string.num_space)),
        ),
        current, vm::numbers,
    )
    SectionHeader(stringResource(R.string.dates))
    val sample = LocalDate.now()
    fun f(p: String) = java.time.format.DateTimeFormatter.ofPattern(p, locale).format(sample)
    PickList(
        listOf(
            Choice(DateStyle.WRITTEN, f("d MMM yyyy"), stringResource(R.string.date_written)),
            Choice(DateStyle.DMY, f("dd/MM/yyyy"), stringResource(R.string.date_dmy)),
            Choice(DateStyle.MDY, f("MM/dd/yyyy"), stringResource(R.string.date_mdy)),
            Choice(DateStyle.YMD, f("yyyy-MM-dd"), stringResource(R.string.date_ymd)),
        ),
        settings.dateFormat, vm::dates,
    )
    SectionHeader(stringResource(R.string.week_starts))
    val days = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
    ButtonGroup(days, DayOfWeek.valueOf(settings.weekStart), { it.getDisplayName(TextStyle.FULL, locale) }, { vm.weekStart(it.name) }, Modifier.fillMaxWidth())
    SectionHeader(stringResource(R.string.month_starts_on))
    ListGroup {
        ListRow(
            if (settings.monthStartDay == 1) stringResource(R.string.nth_of_month, dates.ordinal(1)) else stringResource(R.string.the_nth, dates.ordinal(settings.monthStartDay)),
            0, 1, supporting = stringResource(R.string.month_start_line_short),
            lead = { IconCircle(painterResource(R.drawable.ic_calendar_month_duotone)) }, onClick = onMonthStart,
        )
    }
}

/** Currency (83): the primary currency, symbol or code, decimals, typed-in rates. */
@Composable
private fun CurrencyTab(vm: CustomiseViewModel, settings: Settings, s: CustomiseState, onMain: () -> Unit, onRate: (String) -> Unit, onAdd: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = rememberDateText()
    val main = settings.mainCurrency
    val sym = MoneyFormat.symbolFor(main, locale)
    // Same gap under the tab chips as the tabs that open with a section header.
    Spacer(Modifier.height(Space.sectionTop))
    ListGroup {
        ListRow(
            "${currencyName(main, locale)} · $sym", 0, 1, supporting = stringResource(R.string.primary_currency_line),
            lead = { IconCircle(painterResource(currencyIcon(main))) }, onClick = onMain,
        )
    }
    SectionHeader(stringResource(R.string.show_amounts_as))
    ChoiceTiles(
        listOf(
            Choice("SYMBOL", stringResource(R.string.symbol), MoneyFormat.of(main, locale, settings.numberFormat, "SYMBOL", "AUTO").plain(1_240_00)),
            Choice("CODE", stringResource(R.string.code), MoneyFormat.of(main, locale, settings.numberFormat, "CODE", "AUTO").plain(1_240_00)),
        ),
        settings.currencyDisplay, vm::display, columns = 2,
    )
    SectionHeader(stringResource(R.string.decimal_places))
    ChoiceTiles(
        listOf(
            Choice("AUTO", stringResource(R.string.auto), MoneyFormat.of(main, locale, settings.numberFormat, "CODE", "AUTO").number(1_240_50).trimEnd('0')),
            Choice("NONE", stringResource(R.string.dec_none), MoneyFormat.of(main, locale, settings.numberFormat, "CODE", "NONE").number(1_240_00)),
            Choice("ALWAYS", stringResource(R.string.dec_always), MoneyFormat.of(main, locale, settings.numberFormat, "CODE", "ALWAYS").number(1_240_00)),
        ),
        settings.decimals, vm::decimals,
    )
    SectionHeader(stringResource(R.string.other_currencies), meta = stringResource(R.string.rates_you_type))
    val rates = s.rates.filter { it.currency != main }
    val mainFmt = MoneyFormat.of(main, locale)
    ListGroup {
        rates.forEachIndexed { i, r ->
            ListRow(
                stringResource(R.string.rate_row, r.currency, mainFmt.symbolOnly() + r.perPrimary.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()),
                i, rates.size + 1,
                supporting = stringResource(R.string.dot_join, currencyName(r.currency, locale), stringResource(R.string.set_on, dates.shortDay(dates.date(r.setAt)))),
                lead = { app.burrow.money.ui.components.CurrencyCircle(r.currency) },
                onClick = { onRate(r.currency) },
            )
        }
        ListRow(stringResource(R.string.add_currency), rates.size, rates.size + 1, lead = { IconCircle(painterResource(R.drawable.ic_add_duotone)) }, onClick = onAdd)
    }
}

private fun MoneyFormat.symbolOnly() = plain(0).filter { !it.isDigit() && it != '.' && it != ',' }

fun currencyName(code: String, locale: java.util.Locale) =
    runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code).replaceFirstChar { it.uppercase() }

/** Month starts on (68): the day value sheet, quick days, the period it makes. */
@Composable
private fun MonthStartSheet(day0: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val dates = rememberDateText()
    app.burrow.money.ui.components.DaySheet(
        title = stringResource(R.string.month_starts_on), line = stringResource(R.string.month_starts_line),
        day = day0, onDone = onDone, onDismiss = onDismiss, ordinal = dates::ordinal, quick = listOf(1, 5, 7, 25),
        result = { d ->
            val period = app.burrow.money.domain.BudgetPeriod.containing(LocalDate.now(), d)
            "${dates.shortDay(period.start)} – ${dates.shortDay(period.endExclusive.minusDays(1))}"
        },
    )
}

/** A plain pick-one sheet (swipe actions). */
@Composable
private fun OptionSheet(title: String, options: List<Pair<String, Int>>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss)
        SheetBody {
            ListGroup {
                options.forEachIndexed { i, (key, label) ->
                    app.burrow.money.ui.components.PickRow(stringResource(label), i, options.size, key == selected, { onPick(key) })
                }
            }
        }
    }
}

/**
 * Add a currency (139): search, then every currency (common ones first) with its symbol in a circle;
 * the ones you already have a rate for show the round check. A tap opens its rate (84).
 */
@Composable
private fun AddCurrencySheet(main: String, have: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    val common = listOf("USD", "EUR", "GBP", "AED", "SGD", "JPY", "AUD", "CAD", "CHF", "SAR", "CNY", "INR")
    val all = (have.sorted() + common + java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()).distinct()
        .filter { it != main }
        .filter { query.isBlank() || it.contains(query, true) || currencyName(it, locale).contains(query, true) }
        .take(40)
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.add_currency), onDismiss, stringResource(R.string.add_currency_line))
        SheetBody {
            PillTextField(query, { query = it }, stringResource(R.string.search_currencies), leading = painterResource(R.drawable.ic_search_regular), clearLabel = stringResource(R.string.clear))
            ListGroup {
                all.forEachIndexed { i, code ->
                    app.burrow.money.ui.components.PickRow(
                        currencyName(code, locale), i, all.size, code in have, { onPick(code) }, supporting = code,
                        lead = { app.burrow.money.ui.components.CurrencyCircle(code) },
                    )
                }
            }
        }
    }
}

/** 1 USD in ₹ (84): the value sheet, typed. Saved per currency; each transaction keeps its own. */
@Composable
private fun RateSheet(code: String, main: String, current: app.burrow.money.data.RateEntity?, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = rememberDateText()
    var text by rememberSaveable { mutableStateOf(current?.perPrimary?.toBigDecimal()?.setScale(2, java.math.RoundingMode.HALF_EVEN)?.toPlainString() ?: "1.00") }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val sym = MoneyFormat.symbolFor(main, locale)
    val valid = Fx.parseRate(text)
    app.burrow.money.ui.components.ValueSheet(
        title = stringResource(R.string.rate_title, code, sym), line = stringResource(R.string.rate_sheet_line),
        value = sym + text.ifEmpty { "0" },
        onKey = { key -> text = app.burrow.money.ui.components.typeRate(if (fresh && key != app.burrow.money.ui.components.Key.Backspace) "" else text, key); fresh = false },
        below = current?.let { stringResource(R.string.rate_note, dates.shortDay(dates.date(it.setAt))) } ?: stringResource(R.string.rate_note_new),
        confirm = stringResource(R.string.save), confirmEnabled = valid != null,
        onConfirm = { valid?.let(onSave) }, onDismiss = onDismiss,
        secondary = if (current != null) stringResource(R.string.remove) to onRemove else null,
    )
}
