package app.burrow.money.ui.nav

import androidx.core.net.toUri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.Settings
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.ui.activity.ActivityScreen
import app.burrow.money.ui.activity.ActivityViewModel
import app.burrow.money.ui.txn.TxnScreen
import app.burrow.money.ui.txn.TxnViewModel
import app.burrow.money.ui.add.AddScreen
import app.burrow.money.ui.add.AddViewModel
import app.burrow.money.ui.add.savedMessage
import app.burrow.money.ui.components.BurrowNavBar
import app.burrow.money.ui.components.BurrowSnackbarHost
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.NavItem
import app.burrow.money.ui.components.UndoSnackbar
import app.burrow.money.ui.insights.CategoryInsightsScreen
import app.burrow.money.ui.insights.CategoryInsightsViewModel
import app.burrow.money.ui.insights.InsightsScreen
import app.burrow.money.ui.insights.InsightsViewModel
import app.burrow.money.ui.more.AboutScreen
import app.burrow.money.ui.customise.CustomiseScreen
import app.burrow.money.ui.customise.CustomiseTab
import app.burrow.money.ui.customise.CustomiseViewModel
import app.burrow.money.ui.more.AppLockScreen
import app.burrow.money.ui.more.AppearanceActions
import app.burrow.money.ui.more.AppearanceScreen
import app.burrow.money.ui.more.BackupScreen
import app.burrow.money.ui.more.CategoriesScreen
import app.burrow.money.ui.more.CategoriesViewModel
import app.burrow.money.ui.more.CurrencyScreen
import app.burrow.money.ui.more.EditCategoryScreen
import app.burrow.money.ui.more.EditCategoryViewModel
import app.burrow.money.ui.more.FontScreen
import app.burrow.money.ui.more.LanguageScreen
import app.burrow.money.ui.more.LicencesScreen
import app.burrow.money.ui.more.MoreDest
import app.burrow.money.ui.more.MoreScreen
import app.burrow.money.ui.more.MoreViewModel
import app.burrow.money.ui.more.NotificationSettingsScreen
import app.burrow.money.ui.more.PinMode
import app.burrow.money.ui.more.PinScreen
import app.burrow.money.ui.welcome.WelcomeScreen
import app.burrow.money.ui.welcome.WelcomeViewModel
import app.burrow.money.domain.PinHash
import app.burrow.money.ui.accounts.AccountScreen
import app.burrow.money.ui.accounts.AccountViewModel
import app.burrow.money.ui.accounts.AccountsScreen
import app.burrow.money.ui.accounts.AccountsViewModel
import app.burrow.money.ui.accounts.NewAccountScreen
import app.burrow.money.ui.accounts.NewAccountViewModel
import app.burrow.money.ui.plan.BillForm
import app.burrow.money.ui.plan.BillScreen
import app.burrow.money.ui.plan.BillViewModel
import app.burrow.money.ui.plan.BudgetScreen
import app.burrow.money.ui.plan.BudgetViewModel
import app.burrow.money.ui.plan.GoalScreen
import app.burrow.money.ui.plan.GoalViewModel
import app.burrow.money.ui.plan.NewBillScreen
import app.burrow.money.ui.plan.NewBillViewModel
import app.burrow.money.ui.plan.NewGoalScreen
import app.burrow.money.ui.plan.NewGoalViewModel
import app.burrow.money.ui.plan.PlanScreen
import app.burrow.money.ui.plan.PlanViewModel
import app.burrow.money.ui.plan.SubscriptionsScreen
import app.burrow.money.ui.plan.SubscriptionsViewModel
import androidx.compose.runtime.setValue
import app.burrow.money.ui.theme.NavMotion
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.capture.AutoCaptureScreen
import app.burrow.money.ui.capture.AutoCaptureViewModel
import app.burrow.money.ui.capture.CaptureIntroScreen
import app.burrow.money.ui.review.ReviewScreen
import app.burrow.money.ui.review.ReviewViewModel
import app.burrow.money.ui.rules.RuleScreen
import app.burrow.money.ui.rules.RuleViewModel
import app.burrow.money.ui.rules.RulesScreen
import app.burrow.money.ui.rules.RulesViewModel
import app.burrow.money.ui.teach.TeachScreen
import app.burrow.money.ui.teach.TeachViewModel
import app.burrow.money.ui.today.TodayScreen
import kotlinx.coroutines.launch
import app.burrow.money.notify.scheduleWeeklyReview
import app.burrow.money.ui.today.TodayViewModel
import kotlin.reflect.KClass

/** The five tabs, in nav order (docs/DESIGN.md § Screens, by tab). */
private enum class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayRoute, TodayRoute::class, R.string.tab_today, R.drawable.ic_home_regular, R.drawable.ic_home_fill),
    ACTIVITY(ActivityRoute, ActivityRoute::class, R.string.tab_activity, R.drawable.ic_receipt_long_regular, R.drawable.ic_receipt_long_fill),
    PLAN(PlanRoute, PlanRoute::class, R.string.tab_plan, R.drawable.ic_calendar_month_regular, R.drawable.ic_calendar_month_fill),
    INSIGHTS(InsightsRoute, InsightsRoute::class, R.string.tab_insights, R.drawable.ic_insights_regular, R.drawable.ic_insights_fill),
    MORE(MoreRoute, MoreRoute::class, R.string.tab_more, R.drawable.ic_more_horiz_regular, R.drawable.ic_more_horiz_fill),
}

private fun NavDestination?.tab(): Tab? = this?.let { d -> Tab.entries.firstOrNull { t -> d.hierarchy.any { it.hasRoute(t.routeClass) } } }

private fun NavBackStackEntry.isTab() = destination.tab() != null

/**
 * Switches to a tab the way the bottom nav does: one copy of each, opened at the top, and back from
 * any tab goes to Today, then out. Filters live in AppContainer, so they stay while the app is open.
 *
 * Pops to Today, not the graph's start: on the first run the start is Welcome, which is gone after
 * onboarding, so popping to it did nothing and every tab visited stayed on the stack (back walked
 * through them all, in a loop). No saved state either: a tab came back at an old scroll offset.
 */
private fun NavHostController.openTab(route: Any) {
    // Navigating to a tab that is already on the stack swaps its back stack entry, and after a
    // rotation that screen stopped getting new data (Today froze on old numbers until a restart).
    // So: already there, stay; Today, pop back down to it.
    if (currentDestination.tab()?.route == route) return
    // Today is always at the bottom: go back down to it rather than navigating to it again.
    if (route == TodayRoute && popBackStack<TodayRoute>(inclusive = false)) return
    navigate(route) {
        popUpTo<TodayRoute>()
        launchSingleTop = true
    }
}

/**
 * One NavHost for the app. Motion from Motion.kt only (docs/MOTION.md): switching tabs fades
 * through; pushing a screen uses shared axis X, reversed on back (predictive back is on in the
 * manifest). The bottom nav shows on tabs only. The Undo snackbar sits above the nav, for every screen.
 */
@Composable
fun BurrowNavHost(container: AppContainer, settings: Settings, open: OpenFromNotification? = null, onOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination.tab()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.undo)
    val snackbar = remember(undoLabel) { UndoSnackbar(container.appScope, undoLabel) }
    val openAdd = { nav.navigate(AddRoute()) }
    val openTxn = { id: Long -> nav.navigate(TxnRoute(id)) }
    // Today stays at the bottom of the stack, so it keeps its scroll; a tap on its tab brings it to the top.
    var todayToTop by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val selectTab = { i: Int ->
        val tab = Tab.entries[i]
        if (tab == Tab.TODAY) todayToTop++
        nav.openTab(tab.route)
    }
    var planSection by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(app.burrow.money.ui.plan.PlanSection.BILLS) }
    // First open: Welcome (01–03); afterwards Today. Read once, so finishing Welcome doesn't jump.
    val start: Any = remember { if (settings.onboarded) TodayRoute else WelcomeRoute }
    val appearance = remember {
        object : AppearanceActions {
            override fun mode(m: app.burrow.money.ui.theme.ThemeMode) { container.appScope.launch { container.settings.setThemeMode(m) } }
            override fun accent(hue: Float) { container.appScope.launch { container.settings.setAccentHue(hue) } }
            override fun dynamic(on: Boolean) { container.appScope.launch { container.settings.setDynamicColor(on) } }
            override fun font(f: app.burrow.money.ui.theme.BodyFont) { container.appScope.launch { container.settings.setFont(f) } }
            override fun size(s: app.burrow.money.ui.theme.TextSize) { container.appScope.launch { container.settings.setTextSize(s) } }
        }
    }
    val digits = remember(settings.mainCurrency) { app.burrow.money.domain.MoneyFormat.of(settings.mainCurrency).digits }
    // Long-press on any transaction row (06), and the refund sheet (09): one host for the whole app.
    val txnActions = remember { app.burrow.money.ui.txn.TxnActions(container, settings.monthStartDay) { id -> nav.navigate(SplitRoute(id)) } }
    // From a notification: capture → To review, bill reminder → the bill, budget alert → Budgets.
    LaunchedEffect(open) {
        when (open) {
            OpenFromNotification.Review -> nav.navigate(ReviewRoute) { launchSingleTop = true }
            is OpenFromNotification.Bill -> nav.navigate(BillRoute(open.id)) { launchSingleTop = true }
            OpenFromNotification.Budgets -> { planSection = app.burrow.money.ui.plan.PlanSection.BUDGETS; nav.openTab(PlanRoute) }
            OpenFromNotification.Insights -> nav.openTab(InsightsRoute)
            is OpenFromNotification.EditCapture -> nav.navigate(AddRoute(captureId = open.captureId))
            is OpenFromNotification.Txn -> nav.navigate(TxnRoute(open.id))
            OpenFromNotification.Accounts -> nav.navigate(AccountsRoute) { launchSingleTop = true }
            is OpenFromNotification.NewTxn -> if (settings.onboarded) nav.navigate(AddRoute(type = open.type)) { launchSingleTop = true }
            OpenFromNotification.Search -> { container.filters.focusSearch.value = true; nav.openTab(ActivityRoute) }
            OpenFromNotification.SundayReview -> nav.navigate(SundayReviewRoute) { launchSingleTop = true }
            null -> return@LaunchedEffect
        }
        onOpened()
    }

    // Responsive (Day 26): from 600 dp the content keeps a readable width, centred; from 840 dp the
    // bottom nav becomes a rail on the start side.
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = with(density) { window.width.toDp() }
    val rail = width >= app.burrow.money.ui.theme.Size.railFrom
    val short = with(density) { window.height.toDp() } < app.burrow.money.ui.theme.Size.landscapeMaxHeight
    val maxWidth = if (short) app.burrow.money.ui.theme.Size.contentMaxLandscape else app.burrow.money.ui.theme.Size.contentMax
    val items = Tab.entries.map { NavItem(stringResource(it.label), it.icon, it.selectedIcon) }
    CompositionLocalProvider(LocalSnackbar provides snackbar, app.burrow.money.ui.txn.LocalTxnActions provides txnActions) {
        // Landscape: the system's nav buttons and the camera cutout sit at the sides; keep clear of them.
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxSize().windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.navigationBars.union(androidx.compose.foundation.layout.WindowInsets.displayCutout)
                    .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal),
            ),
        ) {
        if (rail && current != null) {
            app.burrow.money.ui.components.BurrowNavRail(items, selected = current.ordinal, onSelect = selectTab, modifier = Modifier.fillMaxHeight())
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                NavHost(
                    navController = nav,
                    startDestination = start,
                    modifier = Modifier.fillMaxHeight().widthIn(max = maxWidth).fillMaxWidth(),
                    enterTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabEnter(this) else NavMotion.push(this) },
                    exitTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabExit(this) else NavMotion.pushExit(this) },
                    popEnterTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabEnter(this) else NavMotion.pop(this) },
                    popExitTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabExit(this) else NavMotion.popExit(this) },
                ) {
                    composable<TodayRoute> {
                        val vm = viewModel { TodayViewModel(container.money, container.capture, container.settings, container.plan, settings.monthStartDay, MoneyFormat.of(settings.mainCurrency).digits, container.next) }
                        TodayScreen(
                            vm, onAdd = openAdd, onAccounts = { nav.navigate(AccountsRoute) }, onOpen = openTxn, onSeeAll = { nav.openTab(ActivityRoute) },
                            onReview = { nav.navigate(ReviewRoute) },
                            onCaptureIntro = { nav.navigate(CaptureIntroRoute) },
                            onFixCapture = { nav.navigate(AutoCaptureRoute) },
                            onBill = { nav.navigate(BillRoute(it)) },
                            onCategory = { nav.navigate(CategoryInsightsRoute(it)) },
                            onGoal = { nav.navigate(GoalRoute(it)) },
                            onComingUp = { planSection = app.burrow.money.ui.plan.PlanSection.BILLS; nav.openTab(PlanRoute) },
                            scrollToTop = todayToTop,
                        )
                    }
                    composable<ActivityRoute> {
                        val vm = viewModel { ActivityViewModel(container.money, container.filters, container.plan) }
                        ActivityScreen(
                            vm, onAdd = openAdd, onOpen = openTxn, onEdit = { nav.navigate(AddRoute(editId = it)) }, onDuplicate = { nav.navigate(AddRoute(duplicateId = it)) }, focusSearch = container.filters.focusSearch,
                            onComingUp = { planSection = app.burrow.money.ui.plan.PlanSection.BILLS; nav.openTab(PlanRoute) },
                        )
                    }
                    composable<PlanRoute> {
                        val vm = viewModel { PlanViewModel(container.plan, container.money, settings.monthStartDay, digits) }
                        PlanScreen(
                            vm, planSection,
                            onAddBill = { nav.navigate(NewBillRoute()) },
                            onOpenBill = { nav.navigate(BillRoute(it)) },
                            onSubscriptions = { nav.navigate(SubscriptionsRoute) },
                            onLoan = { id -> nav.navigate(if (id != null) AccountRoute(id) else AccountsRoute) },
                            onOpenTxn = openTxn,
                            onBudget = { nav.navigate(BudgetRoute(it ?: -1L)) },
                            onAddGoal = { nav.navigate(NewGoalRoute()) },
                            onOpenGoal = { nav.navigate(GoalRoute(it)) },
                        )
                    }
                    composable<BillRoute> { e ->
                        val id = e.toRoute<BillRoute>().id
                        val vm = viewModel { BillViewModel(container.plan, container.money, id) }
                        BillScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(NewBillRoute(editId = it)) })
                    }
                    composable<NewBillRoute> { e ->
                        val r = e.toRoute<NewBillRoute>()
                        val prefill = if (r.name.isNotEmpty() || r.kind != "BILL") BillForm(
                            kind = r.kind, name = r.name, amount = r.amount, categoryId = r.categoryId.takeIf { it != 0L },
                            accountId = r.accountId.takeIf { it != 0L },
                        ) else null
                        val vm = viewModel { NewBillViewModel(container.plan, container.money, r.editId, prefill, digits) }
                        val saved = stringResource(R.string.saved)
                        NewBillScreen(vm, onClose = { nav.popBackStack() }, onSaved = { snackbar.show(saved); nav.popBackStack() })
                    }
                    composable<SubscriptionsRoute> {
                        val vm = viewModel { SubscriptionsViewModel(container.plan, container.money) }
                        SubscriptionsScreen(
                            vm, onBack = { nav.popBackStack() },
                            onAdd = { f -> nav.navigate(NewBillRoute(kind = f?.kind ?: "SUBSCRIPTION", name = f?.name.orEmpty(), amount = f?.amount.orEmpty(), categoryId = f?.categoryId ?: 0L, accountId = f?.accountId ?: 0L)) },
                            onOpen = { nav.navigate(BillRoute(it)) },
                        )
                    }
                    composable<BudgetRoute> { e ->
                        val cat = e.toRoute<BudgetRoute>().categoryId.takeIf { it != -1L }
                        val vm = viewModel { BudgetViewModel(container.plan, container.money, cat, settings.monthStartDay) }
                        BudgetScreen(vm, onBack = { nav.popBackStack() }, onOpenCategory = { nav.navigate(BudgetRoute(it)) }, onOpenTxn = openTxn)
                    }
                    composable<AccountsRoute> {
                        val vm = viewModel { AccountsViewModel(container.money) }
                        AccountsScreen(vm, onBack = { nav.popBackStack() }, onAdd = { nav.navigate(NewAccountRoute()) }, onOpen = { nav.navigate(AccountRoute(it)) })
                    }
                    composable<AccountRoute> { e ->
                        val id = e.toRoute<AccountRoute>().id
                        val vm = viewModel { AccountViewModel(container.money, container.strings, id) }
                        AccountScreen(
                            vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(NewAccountRoute(it)) },
                            onTransfer = { nav.navigate(AddRoute(type = "TRANSFER", accountId = it)) }, onOpenTxn = openTxn,
                        )
                    }
                    composable<NewAccountRoute> { e ->
                        val r = e.toRoute<NewAccountRoute>()
                        val vm = viewModel { NewAccountViewModel(container.money, r.editId, digits, settings.mainCurrency, r.type.ifEmpty { null }) }
                        val saved = stringResource(R.string.saved)
                        NewAccountScreen(
                            vm, onClose = { nav.popBackStack() }, onSaved = { snackbar.show(saved); nav.popBackStack() },
                            onArchived = { undo ->
                                snackbar.show(context.getString(R.string.account_archived), undo)
                                // Back to the list: the account's own screen has nothing to show now.
                                if (!nav.popBackStack<AccountsRoute>(inclusive = false)) nav.popBackStack()
                            },
                        )
                    }
                    composable<GoalRoute> { e ->
                        val id = e.toRoute<GoalRoute>().id
                        val vm = viewModel { GoalViewModel(container.plan, container.money, id) }
                        GoalScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(NewGoalRoute(it)) })
                    }
                    composable<NewGoalRoute> { e ->
                        val id = e.toRoute<NewGoalRoute>().editId
                        val vm = viewModel { NewGoalViewModel(container.plan, container.money, id, digits) }
                        val saved = stringResource(R.string.saved)
                        NewGoalScreen(vm, onClose = { nav.popBackStack() }, onSaved = { snackbar.show(saved); nav.popBackStack() })
                    }
                    composable<InsightsRoute> {
                        val vm = viewModel { InsightsViewModel(container.money, container.filters, settings.monthStartDay, container.next.stats) }
                        InsightsScreen(vm, onCategory = { nav.navigate(CategoryInsightsRoute(it)) }, onDayInActivity = { nav.openTab(ActivityRoute) })
                    }
                    composable<CategoryInsightsRoute> { e ->
                        val id = e.toRoute<CategoryInsightsRoute>().categoryId
                        val vm = viewModel { CategoryInsightsViewModel(container.money, container.filters, settings.monthStartDay, id, container.next.stats) }
                        CategoryInsightsScreen(vm, onBack = { nav.popBackStack() }, onOpenTxn = openTxn)
                    }
                    composable<MoreRoute> {
                        val vm = viewModel { MoreViewModel(container) }
                        MoreScreen(vm) { dest ->
                            when (dest) {
                                MoreDest.CAPTURE -> nav.navigate(AutoCaptureRoute)
                                MoreDest.BACKUP -> nav.navigate(BackupRoute)
                                MoreDest.LOCK -> nav.navigate(AppLockRoute)
                                MoreDest.APPEARANCE -> nav.navigate(AppearanceRoute)
                                MoreDest.CATEGORIES -> nav.navigate(CategoriesRoute)
                                MoreDest.ACCOUNTS -> nav.navigate(AccountsRoute)
                                MoreDest.CUSTOMISE_TODAY -> nav.navigate(CustomiseRoute("TODAY"))
                                MoreDest.CUSTOMISE_EXPENSE -> nav.navigate(CustomiseRoute("EXPENSE"))
                                MoreDest.CUSTOMISE_TRANSACTIONS -> nav.navigate(CustomiseRoute("TRANSACTIONS"))
                                MoreDest.CUSTOMISE_REGION -> nav.navigate(CustomiseRoute("REGION"))
                                MoreDest.CUSTOMISE_CURRENCY -> nav.navigate(CustomiseRoute("CURRENCY"))
                                MoreDest.FONT -> nav.navigate(FontRoute)
                                MoreDest.NOTIFICATIONS -> nav.navigate(NotificationsRoute)
                                MoreDest.CSV -> nav.navigate(ImportRoute)
                                MoreDest.ABOUT -> nav.navigate(AboutRoute)
                            }
                        }
                    }
                    composable<CustomiseRoute> { e ->
                        val tab = e.toRoute<CustomiseRoute>().tab
                        val vm = viewModel { CustomiseViewModel(container, settings.monthStartDay) }
                        CustomiseScreen(
                            vm, settings, runCatching { CustomiseTab.valueOf(tab) }.getOrDefault(CustomiseTab.TODAY),
                            onBack = { nav.popBackStack() }, onLanguage = { nav.navigate(LanguageRoute) }, onMainCurrency = { nav.navigate(CurrencyRoute) },
                            onFavourites = { nav.navigate(FavouritesRoute) },
                        )
                    }
                    composable<CategoriesRoute> {
                        val vm = viewModel { CategoriesViewModel(container, settings.monthStartDay) }
                        CategoriesScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(EditCategoryRoute(it)) })
                    }
                    composable<EditCategoryRoute> { e ->
                        val id = e.toRoute<EditCategoryRoute>().id
                        val vm = viewModel { EditCategoryViewModel(container, id) }
                        EditCategoryScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable<CurrencyRoute> {
                        CurrencyScreen(
                            settings.mainCurrency,
                            onPick = { code, move ->
                                val old = settings.mainCurrency
                                container.appScope.launch {
                                    if (move) container.money.moveCurrency(old, code)
                                    container.settings.setMainCurrency(code)
                                    container.money.resampleIn(code) // an untouched sample month follows the new currency
                                }
                                nav.popBackStack()
                            },
                            onBack = { nav.popBackStack() },
                            accountsIn = { container.money.accountsIn(it) },
                        )
                    }
                    composable<AppearanceRoute> {
                        AppearanceScreen(settings, appearance, onBack = { nav.popBackStack() }, onFont = { nav.navigate(FontRoute) }, onLanguage = { nav.navigate(LanguageRoute) })
                    }
                    composable<FontRoute> { FontScreen(settings, appearance, onBack = { nav.popBackStack() }) }
                    composable<LanguageRoute> { LanguageScreen(onBack = { nav.popBackStack() }) }
                    composable<NotificationsRoute> {
                        NotificationSettingsScreen(settings, onBack = { nav.popBackStack() }, onSundayReview = { nav.navigate(SundayReviewRoute) }) { key, on ->
                            container.appScope.launch {
                                container.settings.setNotify(key, on)
                                if (key == "summary") container.backup.schedule()
                                if (key == "weekly") container.scheduleWeeklyReview()
                            }
                        }
                    }
                    composable<BackupRoute> { BackupScreen(container, settings, onBack = { nav.popBackStack() }) }
                    composable<AppLockRoute> {
                        AppLockScreen(
                            settings, onBack = { nav.popBackStack() }, onSetPin = { nav.navigate(SetPinRoute) },
                            onLock = { on -> container.appScope.launch { container.settings.setAppLock(on) } },
                            onLockAfter = { sec -> container.appScope.launch { container.settings.setLockAfter(sec) } },
                            onHideInNotifications = { on -> container.appScope.launch { container.settings.setHideInNotifications(on) } },
                        )
                    }
                    composable<SetPinRoute> {
                        val pinSaved = stringResource(R.string.pin_saved)
                        PinScreen(
                            PinMode.SET, check = { false }, onUnlocked = {},
                            onPin = { pin ->
                                container.appScope.launch {
                                    container.settings.setPinHash(PinHash.make(pin))
                                    container.settings.setAppLock(true)
                                }
                                snackbar.show(pinSaved)
                                nav.popBackStack()
                            },
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }, onLicences = { nav.navigate(LicencesRoute) }) }
                    composable<LicencesRoute> { LicencesScreen(onBack = { nav.popBackStack() }) }
                    composable<WelcomeRoute> {
                        val vm = viewModel { WelcomeViewModel(container) }
                        WelcomeScreen(
                            container, vm, onAddAccount = { type -> nav.navigate(NewAccountRoute(type = type)) },
                            onDone = { nav.navigate(TodayRoute) { popUpTo(WelcomeRoute) { inclusive = true } } },
                        )
                    }
                    composable<ReviewRoute> {
                        val vm = viewModel { ReviewViewModel(container.capture, container.money, container.rules) }
                        ReviewScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(AddRoute(captureId = it)) })
                    }
                    composable<CaptureIntroRoute> {
                        CaptureIntroScreen(
                            onClose = { nav.popBackStack() },
                            onAllowed = { sms ->
                                container.appScope.launch {
                                    container.settings.setSmsCapture(sms)
                                    container.settings.setNotificationCapture(true)
                                }
                                nav.navigate(AutoCaptureRoute) { popUpTo<CaptureIntroRoute> { inclusive = true } }
                            },
                        )
                    }
                    composable<AutoCaptureRoute> {
                        val vm = viewModel { AutoCaptureViewModel(container, settings.monthStartDay) }
                        AutoCaptureScreen(
                            vm,
                            onBack = { nav.popBackStack() },
                            onTeach = { nav.navigate(MissedRoute) },
                            onRules = { nav.navigate(RulesRoute) },
                            onHowItWorks = { nav.navigate(CaptureIntroRoute) },
                            paymentFound = settings.onPaymentFound,
                            onPaymentFound = { nav.navigate(CustomiseRoute("TRANSACTIONS")) },
                        )
                    }
                    composable<RulesRoute> {
                        val vm = viewModel { RulesViewModel(container.capture, container.settings) }
                        RulesScreen(
                            vm,
                            onBack = { nav.popBackStack() },
                            onOpen = { nav.navigate(RuleRoute(it)) },
                            onTeach = { nav.navigate(MissedRoute) },
                            onTeachText = { sender, text -> nav.navigate(TeachRoute(sender = sender, text = text)) },
                        )
                    }
                    composable<MissedRoute> {
                        val vm = viewModel { app.burrow.money.ui.teach.MissedViewModel(container.capture) }
                        app.burrow.money.ui.teach.MissedScreen(vm, onBack = { nav.popBackStack() }, onTeach = { nav.navigate(TeachRoute(captureId = it)) })
                    }
                    composable<RuleRoute> { e ->
                        val id = e.toRoute<RuleRoute>().id
                        val vm = viewModel { RuleViewModel(container.capture, container.rules, id) }
                        RuleScreen(vm, onBack = { nav.popBackStack() })
                    }
                    composable<TeachRoute> { e ->
                        val r = e.toRoute<TeachRoute>()
                        val fallback = stringResource(R.string.my_rule)
                        val vm = viewModel { TeachViewModel(container.capture, container.rules, r.captureId, r.sender, r.text, fallback) }
                        TeachScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable<TxnRoute> { e ->
                        val id = e.toRoute<TxnRoute>().id
                        val vm = viewModel { TxnViewModel(container.money, id, settings.monthStartDay, container.next) }
                        TxnScreen(
                            vm,
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(AddRoute(editId = it)) },
                            onDuplicate = { nav.navigate(AddRoute(duplicateId = it)) },
                            onSplit = { nav.navigate(SplitRoute(it)) },
                            onOpenTxn = openTxn,
                        )
                    }
                    composable<AddRoute> { e ->
                        val r = e.toRoute<AddRoute>()
                        val vm = viewModel { AddViewModel(container, r.editId, r.duplicateId, r.type, settings.monthStartDay, settings.mainCurrency, r.accountId, r.captureId) }
                        AddScreen(
                            container,
                            vm,
                            onClose = { nav.popBackStack() },
                            onSaved = { saved ->
                                snackbar.show(saved.label ?: context.getString(savedMessage(saved)), saved.undo)
                                nav.popBackStack()
                            },
                            onFavourites = { nav.navigate(FavouritesRoute) },
                        )
                    }
                    composable<FavouritesRoute> {
                        val vm = viewModel { app.burrow.money.ui.favourites.FavouritesViewModel(container.next) }
                        app.burrow.money.ui.favourites.FavouritesScreen(vm, onBack = { nav.popBackStack() })
                    }
                    composable<SplitRoute> { e ->
                        val id = e.toRoute<SplitRoute>().txnId
                        val vm = viewModel { app.burrow.money.ui.txn.SplitViewModel(container, id, settings.monthStartDay) }
                        app.burrow.money.ui.txn.SplitScreen(vm, onClose = { nav.popBackStack() })
                    }
                    composable<SundayReviewRoute> {
                        val vm = viewModel { app.burrow.money.ui.review.SundayReviewViewModel(container) }
                        app.burrow.money.ui.review.SundayReviewScreen(
                            vm, onBack = { nav.popBackStack() },
                            onReview = { nav.navigate(ReviewRoute) },
                            onBills = { planSection = app.burrow.money.ui.plan.PlanSection.BILLS; nav.openTab(PlanRoute) },
                            onBudget = { nav.navigate(BudgetRoute()) },
                            onCategory = { nav.navigate(CategoryInsightsRoute(it)) },
                        )
                    }
                    composable<ImportRoute> {
                        app.burrow.money.ui.importer.ImportScreen(container, onBack = { nav.popBackStack() }) { source, uri ->
                            nav.navigate(ImportCheckRoute(source.name, uri.toString()))
                        }
                    }
                    composable<ImportCheckRoute> { e ->
                        val r = e.toRoute<ImportCheckRoute>()
                        val vm = viewModel {
                            app.burrow.money.ui.importer.ImportCheckViewModel(container, app.burrow.money.domain.ImportSource.valueOf(r.source), r.uri.toUri())
                        }
                        app.burrow.money.ui.importer.ImportCheckScreen(vm, onBack = { nav.popBackStack() }) { n ->
                            snackbar.show(context.resources.getQuantityString(R.plurals.csv_imported, n, n))
                            nav.popBackStack(MoreRoute, inclusive = false)
                        }
                    }
                }
                app.burrow.money.ui.txn.TxnActionsHost(txnActions)
                // Above the nav bar on tabs; above the gesture bar elsewhere.
                BurrowSnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = Space.screen)
                        .then(if (current == null) Modifier.navigationBarsPadding() else Modifier),
                )
            }
            if (current != null && !rail) {
                BurrowNavBar(items, selected = current.ordinal, onSelect = selectTab)
            }
        }
        }
    }
}
