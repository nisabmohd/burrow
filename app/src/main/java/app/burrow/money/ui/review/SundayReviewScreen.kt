package app.burrow.money.ui.review

import app.burrow.money.ui.filters.range
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.capture.Direction
import app.burrow.money.data.TxnType
import app.burrow.money.notify.WeeklySummary
import app.burrow.money.notify.weeklySummaryFlow
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Sunday review (14): the week in four rows, and one button that saves what waits in To review. */
class SundayReviewViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<WeeklySummary?> = app.weeklySummaryFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Save N and finish": every payment waiting in To review, with its chosen or suggested category. */
    fun saveAll(onDone: (Int, (suspend () -> Unit)?) -> Unit) = viewModelScope.launch {
        val waiting = app.capture.toReview.first()
        val saved = waiting.mapNotNull { c ->
            val type = if (c.direction == Direction.IN.name) TxnType.INCOME else TxnType.EXPENSE
            val category = c.categoryId ?: app.capture.suggestions(c.payee, type).firstOrNull()
            app.capture.save(c.id, category)?.let { c.id }
        }
        onDone(saved.size, if (saved.isEmpty()) null else ({ saved.forEach { app.capture.unsave(it) } }))
    }
}

/**
 * Sunday review (14): centred header with the week's spending against the week before; rows for
 * payments to review, bills due in the next 7 days, what is left and safe today, the category most
 * went to. "Save N and finish" at the bottom (Done when nothing waits).
 */
@Composable
fun SundayReviewScreen(
    vm: SundayReviewViewModel,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onBills: () -> Unit,
    onBudget: () -> Unit,
    onCategory: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val dates = rememberDateText()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val today = LocalDate.now()
    DetailFrame(
        onBack, stringResource(R.string.sunday_review),
        bottom = {
            if (s.toReview > 0) {
                BurrowButton(pluralStringResource(R.plurals.save_n_and_finish, s.toReview, s.toReview), {
                    vm.saveAll { n, undo ->
                        if (n > 0) snackbar.show(context.resources.getQuantityString(R.plurals.n_payments_saved, n, n), undo)
                        onBack()
                    }
                }, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_check_regular), height = Size.buttonBottom)
            } else {
                BurrowButton(stringResource(R.string.done), onBack, Modifier.fillMaxWidth(), height = Size.buttonBottom)
            }
        },
    ) {
        val diff = s.spent - s.weekBefore
        CenterHeader(
            icon = { IconCircle(painterResource(R.drawable.ic_event_available_duotone), size = Size.iconCircleBig) },
            title = stringResource(R.string.your_week, dates.range(s.week)),
            value = money.plain(s.spent),
            line = when {
                s.weekBefore == 0L -> null
                diff < 0 -> stringResource(R.string.less_than_week_before, money.plain(-diff))
                diff > 0 -> stringResource(R.string.more_than_week_before, money.plain(diff))
                else -> stringResource(R.string.same_as_week_before)
            },
        )
        val rows = buildList<@Composable (Int, Int) -> Unit> {
            add { i, n ->
                ListRow(
                    if (s.toReview > 0) pluralStringResource(R.plurals.n_to_review, s.toReview, s.toReview) else stringResource(R.string.all_caught_up),
                    i, n, supporting = stringResource(if (s.toReview > 0) R.string.saved_when_you_finish else R.string.nothing_waiting),
                    lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
                    trailing = if (s.toReview > 0) ({ Chevron() }) else null,
                    onClick = if (s.toReview > 0) onReview else null,
                )
            }
            add { i, n ->
                ListRow(
                    if (s.bills.isEmpty()) stringResource(R.string.no_bills_this_week) else pluralStringResource(R.plurals.bills_due_this_week, s.bills.size, s.bills.size),
                    i, n,
                    supporting = s.bills.take(2).joinToString(" · ") { o ->
                        val due = dates.date(o.occurrence.dueDate)
                        if (due.isBefore(today)) context.getString(R.string.name_not_paid, o.name) else context.getString(R.string.name_due, o.name, dates.due(due, today).replaceFirstChar { it.lowercase() })
                    }.ifEmpty { null },
                    lead = { IconCircle(painterResource(R.drawable.ic_event_available_duotone)) },
                    onClick = onBills,
                )
            }
            if (s.budget != null) add { i, n ->
                val month = dates.month(s.month.atDay(1))
                ListRow(
                    if (s.left >= 0) stringResource(R.string.left_in_month, money.short(s.left), month) else stringResource(R.string.over_in_month, money.short(-s.left), month),
                    i, n, supporting = stringResource(R.string.safe_today_line, money.plain(s.safeToday)),
                    supportingColor = if (s.left < 0) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    lead = { IconCircle(painterResource(R.drawable.ic_donut_large_duotone)) },
                    onClick = onBudget,
                )
            }
            add { i, n ->
                val top = s.topCategory
                ListRow(
                    top?.let { stringResource(R.string.most_on, it.name) } ?: stringResource(R.string.nothing_spent_week),
                    i, n, supporting = top?.let { stringResource(R.string.this_week_amount, money.plain(s.topCategoryTotal)) },
                    lead = { if (top != null) CategoryCircle(top.icon, top.colorHue) else IconCircle(painterResource(R.drawable.ic_pie_duotone)) },
                    onClick = top?.let { { onCategory(it.id) } },
                )
            }
        }
        ListGroup(Modifier.padding(top = Space.block)) { rows.forEachIndexed { i, r -> r(i, rows.size) } }
    }
}
