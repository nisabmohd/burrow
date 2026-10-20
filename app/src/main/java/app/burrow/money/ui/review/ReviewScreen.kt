package app.burrow.money.ui.review

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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
 * its message with the parts it read highlighted, category chips and More. Swipe right saves,
 * left ignores; "Save N payments" saves them all. Empty: All caught up.
 */
@Composable
fun ReviewScreen(vm: ReviewViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
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
            )
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.block),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            items(items, key = { it.capture.id }) { item ->
                val save = {
                    vm.save(item.capture.id) { undo -> snackbar.show(context.getString(R.string.saved), undo) }
                }
                val ignore = {
                    vm.ignore(item.capture.id) { undo -> snackbar.show(context.getString(R.string.ignored), undo) }
                }
                val swipe = rememberSwipeToDismissBoxState(
                    confirmValueChange = { v ->
                        when (v) {
                            SwipeToDismissBoxValue.StartToEnd -> { haptics.confirm(); save(); true }
                            SwipeToDismissBoxValue.EndToStart -> { haptics.threshold(); ignore(); true }
                            else -> false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = swipe,
                    modifier = Modifier.animateItem(),
                    backgroundContent = { SwipeBackground(swipe.dismissDirection) },
                ) {
                    ReviewCard(item, onIgnore = ignore, onChoose = { vm.choose(item.capture.id, it) }, onMore = { moreFor = item.capture.id }, onSave = save)
                }
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
private fun ReviewCard(item: ReviewItem, onIgnore: () -> Unit, onChoose: (Long) -> Unit, onMore: () -> Unit, onSave: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    val cap = item.capture
    val amount = if (item.isIncome) money.signed(cap.amount ?: 0) else money.signed(-(cap.amount ?: 0))
    val saveLabel = stringResource(R.string.save)
    val ignoreLabel = stringResource(R.string.ignore)
    val when_ = dates.day(dates.date(cap.receivedAt)).let { d -> if (d == stringResource(R.string.today)) dates.time(cap.receivedAt) else d }
    Column(
        Modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(Space.card)
            .semantics { customActions = listOf(CustomAccessibilityAction(saveLabel) { onSave(); true }, CustomAccessibilityAction(ignoreLabel) { onIgnore(); true }) },
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.card)) {
            CategoryCircle(item.category?.icon, item.category?.colorHue)
            Column(Modifier.weight(1f)) {
                Text(cap.payee?.let { tidyPayee(it) } ?: item.from, style = BurrowType.bodyLarge, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.accountName, when_).joinToString(" · "),
                    style = BurrowType.body, color = c.onSurfaceVariant, maxLines = 2,
                )
            }
            Text(amount, style = BurrowType.titleSmall, color = c.onSurface)
            TonalIconButton(painterResource(R.drawable.ic_close_regular), ignoreLabel, onIgnore)
        }
        if (cap.body.isNotEmpty()) MessageBox(item.from, cap.body, marksFor(item.spans))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            item.chips.forEach { cat -> Chip(cat.name, cat.id == item.category?.id, { onChoose(cat.id) }) }
            Chip(stringResource(R.string.more), false, onMore, icon = painterResource(R.drawable.ic_more_horiz_regular))
        }
    }
}

/** What shows under a card being swiped: save (right) or ignore (left). */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val c = MaterialTheme.colorScheme
    val saving = direction == SwipeToDismissBoxValue.StartToEnd
    Box(
        Modifier.fillMaxSize().clip(Shapes.surface).background(if (saving) c.primaryContainer else c.surfaceContainerHigh).padding(horizontal = Space.sheet),
        contentAlignment = if (saving) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            painterResource(if (saving) R.drawable.ic_check_regular else R.drawable.ic_close_regular),
            null,
            tint = if (saving) c.onPrimaryContainer else c.onSurfaceVariant,
        )
    }
}
