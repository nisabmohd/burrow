package app.burrow.money.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.CaptureEntity
import app.burrow.money.data.tidyPayee
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.AmountTrailing
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.Ghost
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.MessageBox
import app.burrow.money.ui.components.PillTabs
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.SwipeAction
import app.burrow.money.ui.components.SwipeRow
import app.burrow.money.ui.components.SwipeTone
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.marksFor
import app.burrow.money.ui.findActivity
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.teach.MissedViewModel
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.fadeThrough

/** The two tabs of Review (44, 116). */
enum class ReviewTab { TO_REVIEW, NOT_UNDERSTOOD }

/**
 * Review (44, 116, 117, 170, 234): one screen, `PillTabs` To review · N | Not understood · N (no
 * count at 0). To review: plain transaction rows (category circle · payee · "Food & drinks · 8:05
 * AM" · amount); swipe right Save, left Ignore; tap opens the Review sheet (234); one hint line; the
 * docked "Save N payments". Not understood: SMS circle · sender · the start of the message · date;
 * tap = Teach, swipe left = Ignore. Both entry points land here; "not understood" opens its tab.
 */
@Composable
fun ReviewScreen(
    vm: ReviewViewModel,
    missedVm: MissedViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onTeach: (Long) -> Unit,
    start: ReviewTab = ReviewTab.TO_REVIEW,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val missedState by missedVm.state.collectAsStateWithLifecycle()
    val items = state ?: return
    val missed = missedState ?: return
    // To review just emptied (every payment saved or ignored): a calm moment for Play's rating card.
    val activity = LocalContext.current.findActivity()
    var hadItems by remember { mutableStateOf(false) }
    LaunchedEffect(items.isEmpty()) {
        if (items.isNotEmpty()) hadItems = true
        else if (hadItems && activity != null) vm.askForReview(activity)
    }
    var tab by rememberSaveable { mutableStateOf(start) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var moreFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val save = { id: Long -> haptics.confirm(); vm.save(id) { undo -> snackbar.show(context.getString(R.string.saved), undo) } }
    val ignore = { id: Long -> vm.ignore(id) { undo -> snackbar.show(context.getString(R.string.ignored), undo) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = stringResource(R.string.review))
        PillTabs(
            ReviewTab.entries, tab,
            {
                val (name, n) = when (it) {
                    ReviewTab.TO_REVIEW -> stringResource(R.string.to_review) to items.size
                    ReviewTab.NOT_UNDERSTOOD -> stringResource(R.string.not_understood_title) to missed.size
                }
                if (n > 0) stringResource(R.string.dot_join, name, n.toString()) else name
            },
            { tab = it },
            Modifier.padding(horizontal = Space.screen).padding(bottom = Space.block),
        )
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = { fadeThrough() }, label = "review") { t ->
            when (t) {
                ReviewTab.TO_REVIEW -> ToReviewTab(items, missed.size, onOpen = { open = it }, onSave = save, onIgnore = ignore, onBack = onBack, onMissed = { tab = ReviewTab.NOT_UNDERSTOOD })
                ReviewTab.NOT_UNDERSTOOD -> NotUnderstoodTab(
                    missed, onTeach,
                    onIgnore = { id -> missedVm.ignore(id) { undo -> snackbar.show(context.getString(R.string.ignored), undo) } },
                    onBack = onBack,
                )
            }
        }
        if (tab == ReviewTab.TO_REVIEW && items.isNotEmpty()) BurrowButton(
            pluralStringResource(R.plurals.save_n_payments, items.size, items.size),
            {
                haptics.confirm()
                vm.saveAll { n, undo -> snackbar.show(context.resources.getQuantityString(R.plurals.n_payments_saved, n, n), undo) }
            },
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.block),
            icon = painterResource(R.drawable.ic_check_regular),
            height = Size.buttonBottom,
        )
    }

    items.firstOrNull { it.capture.id == open }?.let { item ->
        ReviewSheet(
            item,
            onChoose = { vm.choose(item.capture.id, it) }, onMore = { moreFor = item.capture.id },
            onEdit = { open = null; onEdit(item.capture.id) },
            onIgnore = { open = null; ignore(item.capture.id) },
            onSave = { open = null; save(item.capture.id) },
            onDismiss = { open = null },
        )
    }
    items.firstOrNull { it.capture.id == moreFor }?.let { item ->
        CategorySheet(item.allCategories, null, item.category?.id, { vm.choose(item.capture.id, it.id); moreFor = null }, { moreFor = null })
    }
}

@Composable
private fun ToReviewTab(
    items: List<ReviewItem>,
    missed: Int,
    onOpen: (Long) -> Unit,
    onSave: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
    onBack: () -> Unit,
    onMissed: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            shape = { Ghost(painterResource(R.drawable.ic_task_alt_regular)) },
            title = stringResource(R.string.all_caught_up),
            line = stringResource(R.string.all_caught_up_line),
            // Back to Today, and under it the messages it missed (170) when there are any.
            button = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    BurrowButton(stringResource(R.string.back_to_today), onBack, kind = ButtonKind.TONAL)
                    if (missed > 0) LinkButton(pluralStringResource(R.plurals.teach_n_missed, missed, missed), onMissed, Modifier.padding(top = Space.small))
                }
            },
        )
        return
    }
    val money = LocalMoney.current
    val dates = rememberDateText()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
        item(key = "rows") {
            ListGroup {
                items.forEachIndexed { i, item ->
                    androidx.compose.runtime.key(item.capture.id) {
                        val cap = item.capture
                        SwipeRow(
                            SwipeAction(painterResource(R.drawable.ic_check_regular), stringResource(R.string.save), SwipeTone.SUCCESS) { onSave(cap.id) },
                            SwipeAction(painterResource(R.drawable.ic_close_regular), stringResource(R.string.ignore), SwipeTone.NEUTRAL) { onIgnore(cap.id) },
                            Shapes.grouped(i, items.size),
                        ) {
                            ListRow(
                                item.title(), i, items.size,
                                supporting = listOfNotNull(item.category?.name, whenText(cap, dates)).joinToString(" · "),
                                lead = { CategoryCircle(item.category?.icon, item.category?.colorHue) },
                                trailing = { AmountTrailing(item.amountText(money)) },
                                onClick = { onOpen(cap.id) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "hint") { Hint(stringResource(R.string.review_hint)) }
    }
}

@Composable
private fun NotUnderstoodTab(missed: List<CaptureEntity>, onTeach: (Long) -> Unit, onIgnore: (Long) -> Unit, onBack: () -> Unit) {
    if (missed.isEmpty()) {
        // An empty list: the Ghost, like every other list (DESIGN § Empty states).
        EmptyState(
            shape = { Ghost(painterResource(R.drawable.ic_help_regular)) },
            title = stringResource(R.string.nothing_to_teach),
            line = stringResource(R.string.nothing_to_teach_line),
            button = { BurrowButton(stringResource(R.string.done), onBack, kind = ButtonKind.TONAL) },
        )
        return
    }
    val dates = rememberDateText()
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
        item(key = "rows") {
            ListGroup {
                missed.forEachIndexed { i, m ->
                    androidx.compose.runtime.key(m.id) {
                        // "AX-HDFCBK-S" → "HDFCBK"; a notification → its app's name, not the package.
                        val from = remember(m.sender, m.source) {
                            if (m.source == "SMS") m.sender
                            else runCatching { context.packageManager.run { getApplicationLabel(getApplicationInfo(m.sender, 0)).toString() } }
                                .getOrElse { m.sender.substringAfterLast('.') }
                        }
                        SwipeRow(
                            null,
                            SwipeAction(painterResource(R.drawable.ic_close_regular), stringResource(R.string.ignore), SwipeTone.NEUTRAL) { onIgnore(m.id) },
                            Shapes.grouped(i, missed.size),
                        ) {
                            ListRow(
                                from.ifBlank { m.sender }, i, missed.size,
                                // The start of the message, on one line (ListRow ends it with …).
                                supporting = m.body.replace(Regex("\\s+"), " ").trim(),
                                lead = { IconCircle(painterResource(if (m.source == "SMS") R.drawable.ic_sms_duotone else R.drawable.ic_notifications_duotone)) },
                                trailing = { Text(dates.shortDay(dates.date(m.receivedAt)), style = BurrowType.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false) },
                                onClick = { onTeach(m.id) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "hint") { Hint(stringResource(R.string.not_understood_line)) }
    }
}

/** The one line under a list that says what the rows do. */
@Composable
private fun Hint(text: String) {
    Text(
        text, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sheet, vertical = Space.block),
    )
}

/**
 * Review sheet (234): the payee, "−₹180 · HDFC Savings · 8:05 AM", the message with what was read
 * highlighted, Category chips (the guess, two more, More), Edit details, then Ignore · Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSheet(
    item: ReviewItem,
    onChoose: (Long) -> Unit,
    onMore: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val cap = item.capture
    BurrowSheet(onDismiss) {
        SheetHeader(item.title(), onDismiss, listOfNotNull(item.amountText(money), item.accountName, whenText(cap, dates)).joinToString(" · "))
        SheetBody {
            if (cap.body.isNotEmpty()) MessageBox(item.from, cap.body, marksFor(item.spans))
            Column {
                SectionHeader(stringResource(R.string.category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    item.chips.forEach { cat -> Chip(cat.name, cat.id == item.category?.id, { onChoose(cat.id) }) }
                    Chip(stringResource(R.string.more), false, onMore, icon = painterResource(R.drawable.ic_more_horiz_regular))
                }
            }
            ListGroup {
                ListRow(
                    stringResource(R.string.edit_details), 0, 1, supporting = stringResource(R.string.edit_details_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_edit_duotone)) },
                    trailing = { Chevron() },
                    onClick = onEdit,
                )
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.ignore), onIgnore, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(stringResource(R.string.save), onSave, Modifier.weight(1f), icon = painterResource(R.drawable.ic_check_regular), height = Size.buttonBottom)
        }
    }
}

/** The expense name ("Medicines") when the message had one, else the payee, else the sender. */
private fun ReviewItem.title(): String = capture.name ?: capture.payee?.let { tidyPayee(it) } ?: from

private fun ReviewItem.amountText(money: app.burrow.money.domain.MoneyFormat): String =
    if (isIncome) money.signed(capture.amount ?: 0) else money.signed(-(capture.amount ?: 0))

/** "8:05 AM" today, else "Yesterday" / the day. */
@Composable
private fun whenText(cap: CaptureEntity, dates: app.burrow.money.ui.DateText): String =
    dates.day(dates.date(cap.receivedAt)).let { d -> if (d == stringResource(R.string.today)) dates.time(cap.receivedAt) else d }
