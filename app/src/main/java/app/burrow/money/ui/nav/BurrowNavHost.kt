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
import app.burrow.money.ui.insights.InsightsScreen
import app.burrow.money.ui.more.MoreScreen
import app.burrow.money.ui.plan.PlanScreen
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
fun BurrowNavHost(container: AppContainer, settings: Settings, openReview: Boolean = false, onReviewOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination.tab()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.undo)
    val snackbar = remember(undoLabel) { UndoSnackbar(container.appScope, undoLabel) }
    val openAdd = { nav.navigate(AddRoute()) }
    val openTxn = { id: Long -> nav.navigate(TxnRoute(id)) }
    // From the capture notification.
    LaunchedEffect(openReview) {
        if (openReview) {
            nav.navigate(ReviewRoute) { launchSingleTop = true }
            onReviewOpened()
        }
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
                        val vm = viewModel { TodayViewModel(container.money, container.capture, container.settings, settings.monthStartDay, MoneyFormat.of(settings.mainCurrency).digits) }
                        TodayScreen(
                            vm, onAdd = openAdd, onOpen = openTxn, onSeeAll = { nav.openTab(ActivityRoute) },
                            onReview = { nav.navigate(ReviewRoute) },
                            onCaptureIntro = { nav.navigate(CaptureIntroRoute) },
                            onFixCapture = { nav.navigate(AutoCaptureRoute) },
                        )
                    }
                    composable<ActivityRoute> {
                        val vm = viewModel { ActivityViewModel(container.money) }
                        ActivityScreen(vm, onAdd = openAdd, onOpen = openTxn)
                    }
                    composable<PlanRoute> { PlanScreen() }
                    composable<InsightsRoute> { InsightsScreen() }
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
                        val vm = viewModel { AddViewModel(container.money, r.editId, r.duplicateId, r.type, settings.monthStartDay, settings.mainCurrency) }
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
