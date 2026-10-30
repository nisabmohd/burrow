package app.burrow.money.ui.review

import app.burrow.money.ui.components.MessageCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.data.tidyPayee
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.MessageBox
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.marksFor
import app.burrow.money.ui.components.threshold
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.shortName
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * To review (44): each payment the app found as a card (circle, payee, account · time, amount, ✕),
 * its message with the parts it read highlighted, category chips and More, and its own Edit and Save
 * buttons (user, 2026-10-24: no swiping here, one tap per card instead; 2026-10-28: Edit opens it in
 * New expense to change the name, amount or account before saving). "Save N payments" saves them all.
 * Empty: All caught up.
 */
@Composable
fun ReviewScreen(vm: ReviewViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit = {}, onMissed: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val missed by vm.notUnderstood.collectAsStateWithLifecycle()
    val items = state ?: return
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var moreFor by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = stringResource(R.string.to_review))
        if (items.isEmpty()) {
            EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_task_alt_regular)) },
                title = stringResource(R.string.all_caught_up),
                line = stringResource(R.string.all_caught_up_line),
                // Back to Today, and under it the messages it missed (170) when there are any.
                button = {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        app.burrow.money.ui.components.BurrowButton(stringResource(R.string.back_to_today), onBack, kind = app.burrow.money.ui.components.ButtonKind.TONAL)
                        if (missed > 0) app.burrow.money.ui.components.LinkButton(pluralStringResource(R.plurals.teach_n_missed, missed, missed), onMissed, Modifier.padding(top = Space.small))
                    }
                },
            )
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.block),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            if (missed > 0) item(key = "missed") {
                app.burrow.money.ui.components.ListGroup {
                    app.burrow.money.ui.components.ListRow(
                        pluralStringResource(R.plurals.n_not_understood, missed, missed), 0, 1,
                        supporting = stringResource(R.string.teach_once_short),
                        lead = { app.burrow.money.ui.components.IconCircle(painterResource(R.drawable.ic_help_duotone)) },
                        trailing = { app.burrow.money.ui.components.Chevron() },
                        onClick = onMissed,
                    )
                }
            }
            items(items, key = { it.capture.id }) { item ->
                val save = {
                    vm.save(item.capture.id) { undo -> snackbar.show(context.getString(R.string.saved), undo) }
                }
                val ignore = {
                    vm.ignore(item.capture.id) { undo -> snackbar.show(context.getString(R.string.ignored), undo) }
                }
                ReviewCard(
                    item, Modifier.animateItem(), onIgnore = ignore, onChoose = { vm.choose(item.capture.id, it) }, onMore = { moreFor = item.capture.id },
                    onSave = { haptics.confirm(); save() }, onEdit = { onEdit(item.capture.id) },
                )
            }
        }
        BurrowButton(
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

    val item = items.firstOrNull { it.capture.id == moreFor }
    if (item != null) {
        CategorySheet(item.allCategories, emptyMap(), item.category?.id, { vm.choose(item.capture.id, it.id); moreFor = null }, { moreFor = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(item: ReviewItem, modifier: Modifier, onIgnore: () -> Unit, onChoose: (Long) -> Unit, onMore: () -> Unit, onSave: () -> Unit, onEdit: () -> Unit) {
    val money = LocalMoney.current
    val dates = rememberDateText()
    val cap = item.capture
    val amount = if (item.isIncome) money.signed(cap.amount ?: 0) else money.signed(-(cap.amount ?: 0))
    val when_ = dates.day(dates.date(cap.receivedAt)).let { d -> if (d == stringResource(R.string.today)) dates.time(cap.receivedAt) else d }
    MessageCard(
        lead = { CategoryCircle(item.category?.icon, item.category?.colorHue) },
        title = cap.payee?.let { tidyPayee(it) } ?: item.from,
        line = listOfNotNull(item.accountName, when_).joinToString(" · "),
        amount = amount,
        ignoreLabel = stringResource(R.string.ignore), onIgnore = onIgnore,
        button = stringResource(R.string.save), buttonIcon = painterResource(R.drawable.ic_check_regular), onButton = onSave,
        second = stringResource(R.string.edit), secondIcon = painterResource(R.drawable.ic_edit_regular), onSecond = onEdit,
        modifier = modifier,
        extra = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                item.chips.forEach { cat -> Chip(cat.name, cat.id == item.category?.id, { onChoose(cat.id) }) }
                Chip(stringResource(R.string.more), false, onMore, icon = painterResource(R.drawable.ic_more_horiz_regular))
            }
        },
    ) { if (cap.body.isNotEmpty()) MessageBox(item.from, cap.body, marksFor(item.spans)) }
}
