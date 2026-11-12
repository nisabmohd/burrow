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
import app.burrow.money.capture.Part
import app.burrow.money.capture.MoneyType
import app.burrow.money.ui.components.MarkStyle
import app.burrow.money.ui.components.Mark
import app.burrow.money.ui.components.FormChipStyle
import app.burrow.money.ui.components.FormChip
import app.burrow.money.ui.components.CheckCard
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
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
 * Review (44, 243, 241, 242, 116, 244, 117, 170; final 12 Nov, the design's CheckCards): one screen, `PillTabs`
 * To review · N | Not understood · N (no count at 0). Both tabs are a list of [CheckCard]s, each approved right there.
 * To review: the payment, its SMS, category chips (the guess picked; a new payee "?" with none) · Ignore · Edit · Save,
 * and the tonal "Save 2 ready" / "Save all 3". Not understood: what Burrow read · "Is this right?" (or out / in chips)
 * · Ignore · Edit (Fix it) · Yes, save.
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
    var gridFor by rememberSaveable { mutableStateOf<Long?>(null) }
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
                ReviewTab.TO_REVIEW -> ToReviewTab(
                    items, missed.size, onChoose = vm::choose, onMore = { gridFor = it }, onEdit = onEdit,
                    onSave = save, onIgnore = ignore, onBack = onBack, onMissed = { tab = ReviewTab.NOT_UNDERSTOOD },
                )
                ReviewTab.NOT_UNDERSTOOD -> NotUnderstoodTab(
                    missed, onEdit = onTeach, onType = missedVm::pickType,
                    onSave = { id ->
                        haptics.confirm()
                        missedVm.save(id) { undo -> snackbar.show(context.getString(R.string.saved_read_from_now), undo) }
                    },
                    onIgnore = { id -> missedVm.ignore(id) { undo -> snackbar.show(context.getString(R.string.ignored), undo) } },
                    onBack = onBack,
                )
            }
        }
        if (tab == ReviewTab.TO_REVIEW && items.isNotEmpty()) {
            // The tonal "Save 2 ready" while some wait for a category; "Save all 3" once none do (44, 242).
            val ready = items.count { it.sure }
            val unsure = items.size - ready
            BurrowButton(
                when {
                    unsure > 0 && ready == 0 -> stringResource(R.string.pick_categories_first)
                    unsure > 0 -> pluralStringResource(R.plurals.save_n_ready, ready, ready)
                    else -> pluralStringResource(R.plurals.save_n_payments, items.size, items.size)
                },
                {
                    if (ready > 0) {
                        haptics.confirm()
                        vm.saveReady { n, undo -> snackbar.show(context.resources.getQuantityString(R.plurals.n_payments_saved, n, n), undo) }
                    }
                },
                Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.block),
                kind = if (ready > 0) ButtonKind.FILLED else ButtonKind.OFF,
                icon = painterResource(R.drawable.ic_check_regular),
                height = Size.buttonBottom,
            )
        }
    }

    items.firstOrNull { it.capture.id == gridFor }?.let { item ->
        // More: the one category list sheet, as everywhere (user, 12 Nov).
        app.burrow.money.ui.pickers.CategorySheet(item.allCategories, item.category?.id.takeIf { item.sure }, { vm.choose(item.capture.id, it.id); gridFor = null }, { gridFor = null })
    }
}

@Composable
private fun ToReviewTab(
    items: List<ReviewItem>,
    missed: Int,
    onChoose: (Long, Long) -> Unit,
    onMore: (Long) -> Unit,
    onEdit: (Long) -> Unit,
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
            // The one next step (170, user 13 Nov): the messages it missed when there are some, else back to Today.
            button = {
                if (missed > 0) BurrowButton(pluralStringResource(R.plurals.teach_n_missed, missed, missed), onMissed, kind = ButtonKind.TONAL)
                else BurrowButton(stringResource(R.string.back_to_today), onBack, kind = ButtonKind.TONAL)
            },
        )
        return
    }
    val money = LocalMoney.current
    val dates = rememberDateText()
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        items(items, key = { it.capture.id }, contentType = { "card" }) { item ->
            val cap = item.capture
            val picked = item.category?.id.takeIf { item.sure }
            CheckCard(
                lead = {
                    if (item.sure && item.category != null) CategoryCircle(item.category.icon, item.category.colorHue)
                    else IconCircle(painterResource(R.drawable.ic_help_duotone))
                },
                title = item.title(),
                line = listOfNotNull(item.accountName, whenText(cap, dates)).joinToString(" · "),
                amount = item.amountText(money),
                messageFrom = item.from,
                message = cap.body,
                // What was read, all marked (44), each as its whole word ("XX1234", not just the digits the rule takes).
                marks = item.spans.values.map { r ->
                    val word = app.burrow.money.capture.RuleBuilder.tokenAt(cap.body, r.first)
                    Mark(if (word == null) r else minOf(word.first, r.first)..maxOf(word.last, r.last), MarkStyle.STRONG)
                },
                question = if (item.sure) null else stringResource(R.string.review_not_sure),
                questionColor = MaterialTheme.colorScheme.primary,
                chips = if (item.allCategories.isEmpty()) null else {
                    {
                        item.chips.forEach { cat ->
                            FormChip(
                                cat.name, { onChoose(cap.id, cat.id) },
                                if (cat.id == picked) FormChipStyle.SELECTED else FormChipStyle.OUTLINED,
                                circle = { CategoryCircle(cat.icon, cat.colorHue, Size.favouriteCircle) },
                            )
                        }
                        FormChip(stringResource(R.string.more), { onMore(cap.id) }, icon = painterResource(R.drawable.ic_more_horiz_regular))
                    }
                },
                onIgnore = { onIgnore(cap.id) },
                secondary = stringResource(R.string.edit), onSecondary = { onEdit(cap.id) },
                primary = stringResource(R.string.save), onPrimary = { if (item.sure) onSave(cap.id) }, primaryEnabled = item.sure,
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.height(Space.block)) }
    }
}

/**
 * Not understood (116, 244): each message as a CheckCard with what Burrow read in it: the type's circle, who and
 * where from, the signed amount, the SMS with those parts marked, then "Burrow read it as expense. Is this right?" (or
 * "Did money go out or in?" with type chips; Yes, save waits) · Ignore · Edit (Fix it) · Yes, save. Empty (117): Nothing to check.
 */
@Composable
private fun NotUnderstoodTab(
    missed: List<app.burrow.money.ui.teach.MissedRow>,
    onEdit: (Long) -> Unit,
    onType: (Long, MoneyType) -> Unit,
    onSave: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
    onBack: () -> Unit,
) {
    if (missed.isEmpty()) {
        // An empty list: the Ghost, like every other list (DESIGN § Empty states).
        EmptyState(
            shape = { Ghost(painterResource(R.drawable.ic_check_regular)) },
            title = stringResource(R.string.nothing_to_check),
            line = stringResource(R.string.nothing_to_check_line),
            button = { BurrowButton(stringResource(R.string.done), onBack, kind = ButtonKind.TONAL) },
        )
        return
    }
    val money = LocalMoney.current
    val dates = rememberDateText()
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        item(key = "hint", contentType = "hint") { Hint(stringResource(R.string.missed_hint)) }
        items(missed, key = { it.capture.id }, contentType = { "card" }) { row ->
            val m = row.capture
            val r = row.reading
            CheckCard(
                lead = { IconCircle(painterResource(if (r.typeUnknown) R.drawable.ic_help_duotone else app.burrow.money.ui.teach.typeIcon(r.type))) },
                title = r.picks[Part.PAYEE]?.let { app.burrow.money.ui.teach.payeeName(r.text.substring(it)) } ?: row.bank ?: app.burrow.money.ui.capture.senderName(m.sender),
                line = listOfNotNull(app.burrow.money.ui.teach.accountText(r), dates.weekdayDay(dates.date(m.receivedAt))).joinToString(" · "),
                // Unsigned until the type is known (116: "₹899"), then "−₹899" (244).
                amount = if (r.typeUnknown) (r.read?.let { money.plain(it.amountMinor) } ?: "") else app.burrow.money.ui.teach.amountText(r, money),
                messageFrom = row.bank ?: app.burrow.money.ui.capture.senderName(m.sender),
                message = m.body,
                marks = app.burrow.money.ui.teach.readMarks(r),
                question = stringResource(if (r.typeUnknown) R.string.did_money_go else R.string.read_marked_right),
                chips = if (!r.asksType) null else {
                    {
                        listOf(MoneyType.EXPENSE, MoneyType.INCOME, MoneyType.REFUND, MoneyType.TRANSFER).forEach { t ->
                            // Type chips as every pick-one chip: the picked one with ✓ (244).
                            Chip(stringResource(app.burrow.money.ui.teach.typeName(t)), r.pickedType == t, { onType(m.id, t) })
                        }
                    }
                },
                onIgnore = { onIgnore(m.id) },
                secondary = stringResource(R.string.edit), onSecondary = { onEdit(m.id) },
                primary = stringResource(R.string.yes_save), onPrimary = { if (r.canSave) onSave(m.id) }, primaryEnabled = r.canSave,
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.height(Space.block)) }
    }
}

/** The one line above the cards that says what to do (44, 116). */
@Composable
private fun Hint(text: String) {
    Text(
        text, style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.headingInset),
    )
}

/** The expense name ("Medicines") when the message had one, else the payee, else the sender. */
private fun ReviewItem.title(): String = capture.name ?: capture.payee?.let { tidyPayee(it) } ?: from

private fun ReviewItem.amountText(money: app.burrow.money.domain.MoneyFormat): String =
    if (isIncome) money.signed(capture.amount ?: 0) else money.signed(-(capture.amount ?: 0))

/** "8:05 AM" today, else "Yesterday" / the day. */
@Composable
private fun whenText(cap: CaptureEntity, dates: app.burrow.money.ui.DateText): String =
    dates.day(dates.date(cap.receivedAt)).let { d -> if (d == stringResource(R.string.today)) dates.time(cap.receivedAt) else d }
