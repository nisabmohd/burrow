package app.burrow.money.ui.txn

import app.burrow.money.ui.theme.Shapes
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.SplitEntity
import app.burrow.money.data.TxnView
import app.burrow.money.data.minorDigits
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.canSaveSplit
import app.burrow.money.domain.splitRemaining
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One part of the split being edited. */
@Immutable
data class Part(val categoryId: Long, val amount: Long)

@Immutable
data class SplitState(
    val txn: TxnView,
    val parts: List<Part>,
    val categories: List<CategoryEntity>,
    val spentByCategory: Map<Long, Long>,
    /** Already split before this screen opened (offers "Undo the split"). */
    val wasSplit: Boolean,
) {
    val total get() = txn.txn.amount
    val remaining get() = splitRemaining(total, parts.map { it.amount })
    val canSave get() = canSaveSplit(total, parts.map { it.amount })
    fun category(id: Long) = categories.firstOrNull { it.id == id }
}

/** Split payment (08): parts with a category and an amount each; Save once they add up. */
class SplitViewModel(private val app: AppContainer, private val txnId: Long, monthStartDay: Int) : ViewModel() {
    private val parts = MutableStateFlow<List<Part>?>(null)
    private var wasSplit = false

    init {
        viewModelScope.launch {
            val t = app.money.txn(txnId).first() ?: return@launch
            val existing = app.next.splitsOf(txnId).first()
            wasSplit = existing.isNotEmpty()
            // A new split starts with the whole payment in its category; adding a part takes half.
            parts.value = existing.map { Part(it.categoryId, it.amount) }.ifEmpty { listOfNotNull(t.txn.categoryId?.let { Part(it, t.txn.amount) }) }
        }
    }

    val state: StateFlow<SplitState?> = combine(
        app.money.txn(txnId), parts, app.money.categories,
        app.money.spentByCategory(BudgetPeriod.containing(LocalDate.now(), monthStartDay)),
    ) { t, p, cats, spent ->
        if (t == null || p == null) null else SplitState(t, p, cats.filter { it.kind == CategoryKind.EXPENSE }, spent, wasSplit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun edit(change: (List<Part>) -> List<Part>) = parts.update { it?.let(change) }

    fun setAmount(i: Int, amount: Long) = edit { l -> l.mapIndexed { j, p -> if (j == i) p.copy(amount = amount.coerceAtLeast(0)) else p } }

    fun setCategory(i: Int, categoryId: Long) = edit { l -> l.mapIndexed { j, p -> if (j == i) p.copy(categoryId = categoryId) else p } }

    /** A new part: what is still unplaced, or else half of the biggest part (whole units). */
    fun addPart(categoryId: Long, unit: Long) = edit { l ->
        val total = state.value?.total ?: 0
        val left = splitRemaining(total, l.map { it.amount })
        if (left > 0 || l.isEmpty()) return@edit l + Part(categoryId, left.coerceAtLeast(0))
        val big = l.indices.maxByOrNull { l[it].amount } ?: return@edit l + Part(categoryId, 0)
        val half = (l[big].amount / 2 / unit * unit).takeIf { it > 0 } ?: (l[big].amount / 2)
        l.mapIndexed { j, p -> if (j == big) p.copy(amount = p.amount - half) else p } + Part(categoryId, half)
    }

    fun remove(i: Int) = edit { l -> l.filterIndexed { j, _ -> j != i } }

    /** "Put ₹560 in Rent & home": the last part takes what is left (or gives back what is too much). */
    fun settle() = edit { l ->
        val total = state.value?.total ?: return@edit l
        val left = splitRemaining(total, l.map { it.amount })
        val i = l.indices.lastOrNull { left > 0 || l[it].amount >= -left } ?: return@edit l
        l.mapIndexed { j, p -> if (j == i) p.copy(amount = p.amount + left) else p }
    }

    fun save(onDone: (suspend () -> Unit) -> Unit) {
        val s = state.value ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val merged = s.parts.filter { it.amount > 0 }.groupBy { it.categoryId }.map { (id, l) -> SplitEntity(txnId = txnId, categoryId = id, amount = l.sumOf { it.amount }) }
            onDone(app.next.saveSplits(txnId, merged))
        }
    }

    /** Back to one category (the biggest part's). */
    fun unsplit(onDone: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch { onDone(app.next.saveSplits(txnId, emptyList())) }
    }
}

private sealed interface SplitPicker {
    data class Category(val index: Int) : SplitPicker
    data object NewPart : SplitPicker
    data class Amount(val index: Int) : SplitPicker
}

/**
 * Split payment (08): close · "Split payment" · Save; the payment in a centred header with what is
 * still to put in a category; a row per part (category, − amount +; tap the amount to type it);
 * Add a part; the "Put ₹X in …" chip; one line on what a split does.
 */
@Composable
fun SplitScreen(vm: SplitViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    val c = MaterialTheme.colorScheme
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var picker by androidx.compose.runtime.remember { mutableStateOf<SplitPicker?>(null) }
    val digits = minorDigits(s.txn.txn.currency)
    var unit = 1L
    repeat(digits) { unit *= 10 }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose, title = stringResource(R.string.split_payment)) {
            BurrowButton(stringResource(R.string.save), {
                if (s.canSave) {
                    haptics.confirm()
                    vm.save { undo -> snackbar.show(context.getString(R.string.split_saved), undo); onClose() }
                }
            }, kind = if (s.canSave) ButtonKind.FILLED else ButtonKind.OFF)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            val left = s.remaining
            CenterHeader(
                icon = { CategoryCircle(s.txn.categoryIcon, s.txn.categoryHue, Size.iconCircleBig) },
                title = s.txn.title(),
                value = money.plain(s.total),
                line = when {
                    left > 0 -> stringResource(R.string.still_to_place, money.plain(left))
                    left < 0 -> stringResource(R.string.too_much_placed, money.plain(-left))
                    s.parts.count { it.amount > 0 } < 2 -> stringResource(R.string.split_add_part_line)
                    else -> stringResource(R.string.split_adds_up)
                },
                bad = left < 0,
            )
            ListGroup {
                val n = s.parts.size + 1
                s.parts.forEachIndexed { i, p ->
                    val cat = s.category(p.categoryId)
                    ListRow(
                        cat?.name.orEmpty(), i, n, supporting = stringResource(R.string.tap_to_change),
                        lead = { CategoryCircle(cat?.icon, cat?.colorHue) },
                        onClick = { picker = SplitPicker.Category(i) },
                        // The part's amount: a tappable value that opens the value sheet (95, no − / +).
                        trailing = { app.burrow.money.ui.components.ValuePill(money.plain(p.amount), { picker = SplitPicker.Amount(i) }, stringResource(R.string.type_amount)) },
                    )
                }
                ListRow(
                    stringResource(R.string.add_a_part), s.parts.size, n,
                    lead = { IconCircle(painterResource(R.drawable.ic_add_regular)) },
                    onClick = { picker = SplitPicker.NewPart },
                )
            }
            val last = s.parts.lastOrNull()?.let { s.category(it.categoryId) }
            if (left != 0L && last != null && s.parts.size > 1) {
                Chip(
                    if (left > 0) stringResource(R.string.split_put_in, money.plain(left), last.name) else stringResource(R.string.take_from, money.plain(-left), last.name),
                    false, vm::settle, icon = painterResource(R.drawable.ic_call_split_regular),
                )
            }
            Text(stringResource(R.string.split_explain), style = BurrowType.body, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.small))
            if (s.wasSplit) LinkButton(stringResource(R.string.unsplit), {
                vm.unsplit { undo -> snackbar.show(context.getString(R.string.unsplit_done), undo); onClose() }
            })
        }
    }
    when (val p = picker) {
        is SplitPicker.Category -> CategorySheet(s.categories, s.spentByCategory, s.parts.getOrNull(p.index)?.categoryId, { vm.setCategory(p.index, it.id); picker = null }, { picker = null })
        SplitPicker.NewPart -> CategorySheet(s.categories.filter { cat -> s.parts.none { it.categoryId == cat.id } }, s.spentByCategory, null, { vm.addPart(it.id, unit); picker = null }, { picker = null })
        is SplitPicker.Amount -> s.parts.getOrNull(p.index)?.let { part ->
            app.burrow.money.ui.components.AmountSheet(
                title = s.category(part.categoryId)?.name.orEmpty(), line = stringResource(R.string.split_part_line, money.plain(s.total)),
                amount = part.amount, digits = digits, max = s.total,
                onDone = { vm.setAmount(p.index, it); picker = null }, onDismiss = { picker = null },
                // Removing a part lives here now that there is no − to take it to zero.
                secondary = if (s.parts.size > 1) stringResource(R.string.remove_part) to { vm.remove(p.index); picker = null } else null,
            )
        }
        null -> Unit
    }
}
