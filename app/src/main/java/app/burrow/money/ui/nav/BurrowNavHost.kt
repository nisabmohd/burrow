package app.burrow.money.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import app.burrow.money.ui.more.MoreScreen
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

/** Switches to a tab the way the bottom nav does: one copy of each, each keeps its scroll position. */
private fun NavHostController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
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
    var planSection by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(app.burrow.money.ui.plan.PlanSection.BILLS) }
    val digits = remember(settings.mainCurrency) { app.burrow.money.domain.MoneyFormat.of(settings.mainCurrency).digits }
    // From a notification: capture → To review, bill reminder → the bill, budget alert → Budgets.
    LaunchedEffect(open) {
        when (open) {
            OpenFromNotification.Review -> nav.navigate(ReviewRoute) { launchSingleTop = true }
            is OpenFromNotification.Bill -> nav.navigate(BillRoute(open.id)) { launchSingleTop = true }
            OpenFromNotification.Budgets -> { planSection = app.burrow.money.ui.plan.PlanSection.BUDGETS; nav.openTab(PlanRoute) }
            null -> return@LaunchedEffect
        }
        onOpened()
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = nav,
                    startDestination = TodayRoute,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabEnter(this) else NavMotion.push(this) },
                    exitTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabExit(this) else NavMotion.pushExit(this) },
                    popEnterTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabEnter(this) else NavMotion.pop(this) },
                    popExitTransition = { if (initialState.isTab() && targetState.isTab()) NavMotion.tabExit(this) else NavMotion.popExit(this) },
                ) {
                    composable<TodayRoute> {
                        val vm = viewModel { TodayViewModel(container.money, container.capture, container.settings, container.plan, settings.monthStartDay, MoneyFormat.of(settings.mainCurrency).digits) }
                        TodayScreen(
                            vm, onAdd = openAdd, onAccounts = { nav.navigate(AccountsRoute) }, onOpen = openTxn, onSeeAll = { nav.openTab(ActivityRoute) },
                            onReview = { nav.navigate(ReviewRoute) },
                            onCaptureIntro = { nav.navigate(CaptureIntroRoute) },
                            onFixCapture = { nav.navigate(AutoCaptureRoute) },
                            onBill = { nav.navigate(BillRoute(it)) },
                            onCategory = { nav.navigate(CategoryInsightsRoute(it)) },
                            onGoal = { nav.navigate(GoalRoute(it)) },
                        )
                    }
                    composable<ActivityRoute> {
                        val vm = viewModel { ActivityViewModel(container.money, container.filters) }
                        ActivityScreen(vm, onAdd = openAdd, onOpen = openTxn)
                    }
                    composable<PlanRoute> {
                        val vm = viewModel { PlanViewModel(container.plan, container.money, settings.monthStartDay, digits) }
                        PlanScreen(
                            vm, planSection,
                            onAddBill = { nav.navigate(NewBillRoute()) },
                            onOpenBill = { nav.navigate(BillRoute(it)) },
                            onSubscriptions = { nav.navigate(SubscriptionsRoute) },
                            onLoan = { id -> nav.navigate(if (id != null) AccountRoute(id) else AccountsRoute) },
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
                        val id = e.toRoute<NewAccountRoute>().editId
                        val vm = viewModel { NewAccountViewModel(container.money, id, digits, settings.mainCurrency) }
                        val saved = stringResource(R.string.saved)
                        NewAccountScreen(vm, onClose = { nav.popBackStack() }, onSaved = { snackbar.show(saved); nav.popBackStack() })
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
                        val vm = viewModel { InsightsViewModel(container.money, container.filters, settings.monthStartDay) }
                        InsightsScreen(vm, onCategory = { nav.navigate(CategoryInsightsRoute(it)) }, onDayInActivity = { nav.openTab(ActivityRoute) })
                    }
                    composable<CategoryInsightsRoute> { e ->
                        val id = e.toRoute<CategoryInsightsRoute>().categoryId
                        val vm = viewModel { CategoryInsightsViewModel(container.money, container.filters, settings.monthStartDay, id) }
                        CategoryInsightsScreen(vm, onBack = { nav.popBackStack() }, onOpenTxn = openTxn)
                    }
                    composable<MoreRoute> { MoreScreen(onAutoCapture = { nav.navigate(AutoCaptureRoute) }) }
                    composable<ReviewRoute> {
                        val vm = viewModel { ReviewViewModel(container.capture, container.money, container.rules) }
                        ReviewScreen(vm, onBack = { nav.popBackStack() })
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
                            onTeach = { nav.navigate(TeachRoute(captureId = it)) },
                            onRules = { nav.navigate(RulesRoute) },
                            onHowItWorks = { nav.navigate(CaptureIntroRoute) },
                        )
                    }
                    composable<RulesRoute> {
                        val vm = viewModel { RulesViewModel(container.capture, container.settings) }
                        RulesScreen(
                            vm,
                            onBack = { nav.popBackStack() },
                            onOpen = { nav.navigate(RuleRoute(it)) },
                            onTeach = { nav.navigate(TeachRoute(captureId = it)) },
                            onTeachText = { sender, text -> nav.navigate(TeachRoute(sender = sender, text = text)) },
                        )
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
                        val vm = viewModel { TxnViewModel(container.money, id, settings.monthStartDay) }
                        TxnScreen(
                            vm,
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(AddRoute(editId = it)) },
                            onDuplicate = { nav.navigate(AddRoute(duplicateId = it)) },
                        )
                    }
                    composable<AddRoute> { e ->
                        val r = e.toRoute<AddRoute>()
                        val vm = viewModel { AddViewModel(container.money, r.editId, r.duplicateId, r.type, settings.monthStartDay, settings.mainCurrency, r.accountId) }
                        AddScreen(
                            vm,
                            onClose = { nav.popBackStack() },
                            onSaved = { saved ->
                                snackbar.show(context.getString(savedMessage(saved)), saved.undo)
                                nav.popBackStack()
                            },
                        )
                    }
                }
                // Above the nav bar on tabs; above the gesture bar elsewhere.
                BurrowSnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = Space.screen)
                        .then(if (current == null) Modifier.navigationBarsPadding() else Modifier),
                )
            }
            if (current != null) {
                val items = Tab.entries.map { NavItem(stringResource(it.label), it.icon, it.selectedIcon) }
                BurrowNavBar(items, selected = current.ordinal, onSelect = { i -> nav.openTab(Tab.entries[i].route) })
            }
        }
    }
}
