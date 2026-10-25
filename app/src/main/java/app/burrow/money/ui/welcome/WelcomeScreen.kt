package app.burrow.money.ui.welcome

import app.burrow.money.ui.theme.Shapes
import androidx.compose.ui.draw.clip
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.budgetChips
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.burrow.money.ui.components.tick
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.defaultCurrency
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.isLargeText
import app.burrow.money.ui.components.BurrowLogo
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.IntroCard
import app.burrow.money.ui.components.IntroPanel
import app.burrow.money.ui.components.IntroTile
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.RingPercent
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.StepDots
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.currencyIcon
import app.burrow.money.ui.more.rememberRestoreFlow
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.accountTypeLabel
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.fadeThrough
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class WelcomeState(val currency: String, val accounts: List<AccountWithBalance>)

/** Welcome (01–03): the pitch, the main currency, then the accounts the user pays from. */
class WelcomeViewModel(private val app: AppContainer) : ViewModel() {
    val currency: StateFlow<String> = app.settings.settings.map { it.mainCurrency }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultCurrency())

    /** The user's own accounts (not the sample month's). */
    val accounts: StateFlow<List<AccountWithBalance>> = app.money.accounts.map { l -> l.filter { !it.account.isSample } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCurrency(code: String) = viewModelScope.launch {
        app.settings.setMainCurrency(code)
        app.money.resampleIn(code)
    }

    fun remove(a: AccountEntity) = viewModelScope.launch { app.money.updateAccount(a.copy(archived = true)) }

    /** Step 2 (12): the monthly budget, from this month on. Skip leaves none. */
    fun setBudget(amount: Long) = viewModelScope.launch {
        val s = app.settings.settings.first()
        app.plan.setBudget(BudgetPeriod.containing(java.time.LocalDate.now(), s.monthStartDay), null, amount, false)
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        app.settings.setOnboarded()
        onDone()
    }
}

private const val STEPS = 3

/**
 * Welcome, then two steps in one screen (shared axis between them, the dots on top). Back on a step
 * goes to the one before. [onAddAccount] opens New account with that type.
 */
@Composable
fun WelcomeScreen(app: AppContainer, vm: WelcomeViewModel, onAddAccount: (String) -> Unit, onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val currency by vm.currency.collectAsStateWithLifecycle()
    // The budget step's amount, in minor units; starts on the second chip (₹30,000 for INR).
    val digits = MoneyFormat.of(currency).digits
    var unit = 1L
    repeat(digits) { unit *= 10 }
    var budget by rememberSaveable(currency) { androidx.compose.runtime.mutableLongStateOf(budgetChips(currency)[1] * unit) }
    val restore = rememberRestoreFlow(app) { vm.finish(onDone) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(restore) }
    BackHandler(page > 0) { page-- }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (page > 0) {
            ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), { page-- })
            StepDots(page - 1, STEPS, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Space.block))
        }
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeThrough() }, label = "welcome") { p ->
            when (p) {
                0 -> Pitch(onRestore = { pickFile.launch(arrayOf("*/*")) })
                1 -> CurrencyStep(vm)
                2 -> BudgetStep(currency, budget, unit) { budget = it }
                else -> AccountsStep(vm, onAddAccount)
            }
        }
        // Large text: the two buttons stack so "Skip for now" isn't cut.
        FlowRow(
            Modifier.fillMaxWidth().padding(Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.inline),
            verticalArrangement = Arrangement.spacedBy(Space.small), maxItemsInEachRow = if (isLargeText()) 1 else 2,
        ) {
            when (page) {
                0 -> BurrowButton(stringResource(R.string.get_started), { page = 1 }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
                1 -> BurrowButton(stringResource(R.string.continue_), { page = 2 }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
                2 -> {
                    BurrowButton(stringResource(R.string.skip_for_now), { page = 3 }, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
                    BurrowButton(stringResource(R.string.continue_), { if (budget > 0) vm.setBudget(budget); page = 3 }, Modifier.weight(1f), height = Size.buttonBottom)
                }
                else -> {
                    BurrowButton(stringResource(R.string.skip), { vm.finish(onDone) }, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
                    BurrowButton(stringResource(R.string.done), { vm.finish(onDone) }, Modifier.weight(1f), height = Size.buttonBottom)
                }
            }
        }
        if (page == 0) {
            // Wraps "Restore a backup" onto its own line when the text is large.
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(bottom = Space.block), horizontalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.welcome_footer) + " · ", style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.restore_backup), style = BurrowType.button, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(Shapes.pill).clickable(role = Role.Button) { pickFile.launch(arrayOf("*/*")) },
                )
            }
        }
    }
}

/** 01: the logo, a panel of what Burrow does, the promise. */
@Composable
private fun Pitch(onRestore: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(vertical = Space.sheet), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.block)) {
            BurrowLogo(Size.iconCircle)
            Text(stringResource(R.string.app_name), style = BurrowType.title, color = c.onSurface)
        }
        IntroPanel {
            IntroCard(Modifier.fillMaxWidth(), tilt = -1.5f) {
                CategoryCircle("restaurant", 28f)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.intro_saved_title, money.plain(240_00)), style = BurrowType.titleSmall, color = c.onSurface)
                    Text(stringResource(R.string.intro_saved_line), style = BurrowType.body, color = c.onSurfaceVariant)
                }
                IconCircle(painterResource(R.drawable.ic_check_regular), container = c.primary, content = c.onPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                IntroTile(Modifier.weight(1f), tilt = 1f) {
                    RingPercent(0.68f, c.primary, c.surfaceContainerHigh)
                    Text(stringResource(R.string.welcome_left, money.short(13_600_00)), style = BurrowType.titleSmall, color = c.onSurface)
                    Text(stringResource(R.string.welcome_budget, app.burrow.money.ui.rememberDateText().month(java.time.LocalDate.now())), style = BurrowType.label, color = c.onSurfaceVariant)
                }
                IntroTile(Modifier.weight(1f), tilt = 2f) {
                    CategoryCircle("movie", 285f)
                    Text(stringResource(R.string.welcome_bill), style = BurrowType.titleSmall, color = c.onSurface)
                    Text(stringResource(R.string.due_today), style = BurrowType.label, color = c.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Space.sheet * 2))
        Text(stringResource(R.string.welcome_headline), style = BurrowType.intro, color = c.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.block))
        Text(stringResource(R.string.welcome_line), style = BurrowType.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.small))
    }
}

/** A step's heading: "Step 1 of 2", the title, the line under it. */
@Composable
private fun StepHeading(step: Int, title: Int, line: Int) {
    val c = MaterialTheme.colorScheme
    Text(stringResource(R.string.step_of, step, STEPS), style = BurrowType.label, color = c.primary)
    Spacer(Modifier.height(Space.small))
    Text(stringResource(title), style = BurrowType.headline, color = c.onSurface)
    Spacer(Modifier.height(Space.small))
    Text(stringResource(line), style = BurrowType.body, color = c.onSurfaceVariant)
    Spacer(Modifier.height(Space.block))
}

/** 02: the main currency, the region's first. */
@Composable
private fun CurrencyStep(vm: WelcomeViewModel) {
    val current by vm.currency.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val region = defaultCurrency(locale)
    val options = (listOf(region, "USD", "EUR", "GBP", "INR")).distinct().take(4).let { if (current in it) it else it + current }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
        StepHeading(1, R.string.welcome_currency_title, R.string.welcome_currency_line)
        ListGroup {
            options.forEachIndexed { i, code ->
                val name = runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code).replaceFirstChar { it.uppercase() }
                val symbol = MoneyFormat.symbolFor(code, locale)
                app.burrow.money.ui.components.PickRow(
                    name, i, options.size, code == current, { vm.setCurrency(code) },
                    supporting = if (code == region) stringResource(R.string.from_your_region, symbol) else symbol,
                    lead = { IconCircle(painterResource(currencyIcon(code))) },
                )
            }
        }
    }
}

/** 03: the accounts so far, each removable, and chips to add more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsStep(vm: WelcomeViewModel, onAdd: (String) -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
        StepHeading(3, R.string.welcome_accounts_title, R.string.welcome_accounts_line)
        if (accounts.isNotEmpty()) {
            ListGroup {
                accounts.forEachIndexed { i, a ->
                    ListRow(
                        a.account.name, i, accounts.size,
                        supporting = if (a.account.type == AccountType.CARD && a.balance == 0L) accountTypeLabel(a.account.type) else money.balance(a.balance),
                        lead = { CategoryCircle(a.account.icon, a.account.colorHue) },
                        trailing = { TonalIconButton(painterResource(R.drawable.ic_close_regular), stringResource(R.string.remove_account, a.account.name), { vm.remove(a.account) }) },
                    )
                }
            }
            Spacer(Modifier.height(Space.block))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            listOf(AccountType.BANK to R.string.account_bank, AccountType.CARD to R.string.card, AccountType.WALLET to R.string.account_wallet, AccountType.CASH to R.string.account_cash, AccountType.LOAN to R.string.account_loan).forEach { (type, label) ->
                Chip(stringResource(label), false, { onAdd(type) }, icon = painterResource(R.drawable.ic_add_regular))
            }
        }
    }
}

/**
 * 2 of 3 (12): how much a month. − amount + (tap the amount to type it), quick chips, and what that
 * means a day on average this month.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetStep(currency: String, amount: Long, unit: Long, onAmount: (Long) -> Unit) {
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    val chips = app.burrow.money.domain.budgetChips(currency).map { it * unit }
    var typing by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val days = java.time.YearMonth.now().lengthOfMonth()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
        StepHeading(2, R.string.welcome_budget_title, R.string.welcome_budget_line)
        // The amount opens the value sheet (99 / 136): no − / + steppers.
        app.burrow.money.ui.components.ValueText(money.plain(amount), { typing = true }, stringResource(R.string.type_amount))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            chips.forEach { v -> app.burrow.money.ui.components.ValueChip(money.short(v), v == amount) { onAmount(v) } }
        }
        Spacer(Modifier.height(Space.block))
        ListGroup {
            ListRow(
                stringResource(R.string.n_a_day, money.plain(app.burrow.money.domain.perDay(amount, days, MoneyFormat.of(currency).digits))), 0, 1,
                supporting = stringResource(R.string.safe_on_average_day),
                lead = { IconCircle(painterResource(R.drawable.ic_today_duotone)) },
            )
        }
    }
    if (typing) app.burrow.money.ui.components.AmountSheet(
        title = stringResource(R.string.type_an_amount), line = stringResource(R.string.monthly_budget),
        amount = amount, digits = MoneyFormat.of(currency).digits, min = 1,
        onDone = { onAmount(it); typing = false }, onDismiss = { typing = false },
    )
}
