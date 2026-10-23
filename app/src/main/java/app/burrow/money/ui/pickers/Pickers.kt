package app.burrow.money.ui.pickers

import androidx.compose.foundation.layout.fillMaxWidth
import app.burrow.money.ui.components.ChipRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import app.burrow.money.R
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.theme.Size
import java.time.LocalDate

/*
 * The pickers every form shares (docs/DESIGN.md rule 8: every picker is a bottom sheet).
 * Each is a BurrowSheet: header with title, one line and close; rows; selected row tinted with a check.
 */

/** The 40 dp circle of a category, in its own colours. */
@Composable
fun CategoryCircle(icon: String?, hue: Float?, size: androidx.compose.ui.unit.Dp = Size.iconCircle) =
    IconCircle(painterResource(duotoneIcon(icon)), hue = hue, size = size)

/** Category (19): every category of one kind, with what was spent on it this month. */
@Composable
fun CategorySheet(
    categories: List<CategoryEntity>,
    spent: Map<Long, Long>,
    selectedId: Long?,
    onPick: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.category), onDismiss, stringResource(R.string.category_sheet_line))
        SheetBody {
            ListGroup {
                categories.forEachIndexed { i, cat ->
                    val total = spent[cat.id] ?: 0L
                    ListRow(
                        headline = cat.name,
                        index = i,
                        count = categories.size,
                        supporting = if (total > 0) stringResource(R.string.spent_this_month, money.plain(total)) else stringResource(R.string.nothing_this_month),
                        selected = cat.id == selectedId,
                        lead = { CategoryCircle(cat.icon, cat.colorHue) },
                        trailing = { if (cat.id == selectedId) SelectedCheck() },
                        onClick = { haptics.tick(); onPick(cat) },
                    )
                }
            }
        }
    }
}

/** Account (20): accounts with their balance and kind. */
@Composable
fun AccountSheet(
    accounts: List<AccountWithBalance>,
    selectedId: Long?,
    onPick: (AccountWithBalance) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.account), onDismiss, stringResource(R.string.account_sheet_line))
        SheetBody {
            ListGroup {
                accounts.forEachIndexed { i, a ->
                    ListRow(
                        headline = a.account.name,
                        index = i,
                        count = accounts.size,
                        supporting = stringResource(R.string.dot_join, money.balance(a.balance), accountTypeLabel(a.account.type)),
                        selected = a.account.id == selectedId,
                        lead = { CategoryCircle(a.account.icon, a.account.colorHue) },
                        trailing = { if (a.account.id == selectedId) SelectedCheck() },
                        onClick = { haptics.tick(); onPick(a) },
                    )
                }
            }
        }
    }
}

@Composable
fun accountTypeLabel(type: String): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_cash
        AccountType.WALLET -> R.string.account_wallet
        AccountType.CARD -> R.string.account_card
        AccountType.LOAN -> R.string.account_loan
        AccountType.SAVINGS -> R.string.account_savings
        else -> R.string.account_bank
    },
)

@Composable
private fun SelectedCheck() =
    Icon(painterResource(R.drawable.ic_check_regular), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Size.icon))

/** One line of text (payee, note): a field that opens focused, and Done. */
@Composable
fun TextSheet(title: String, value: String, placeholder: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val done = { haptics.confirm(); onDone(text.text.trim()) }
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss)
        SheetBody {
            PillTextFieldValue(text, { text = it }, placeholder, Modifier.focusRequester(focus), onDone = done)
        }
        SheetFooter { BurrowButton(stringResource(R.string.done), done, Modifier.fillMaxWidth(), height = Size.buttonBottom) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PillTextFieldValue(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, placeholder: String, modifier: Modifier, onDone: () -> Unit) {
    // PillTextField works on String; keep the cursor at the end by re-wrapping.
    PillTextField(
        value = value.text,
        onValueChange = { onChange(TextFieldValue(it, TextRange(it.length))) },
        placeholder = placeholder,
        modifier = modifier,
        clearLabel = stringResource(R.string.clear),
        onDone = onDone,
    )
}

/**
 * The one date sheet: an optional Yesterday · Today · Tomorrow row (New expense), a month calendar
 * in equal columns, Done. Days on or before today (transaction dates), from today on with [future]
 * (goal dates), or any day with [anyDay] (a future one makes a transaction upcoming).
 */
@Composable
fun DateSheet(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    future: Boolean = false,
    title: String? = null,
    anyDay: Boolean = false,
    quick: Boolean = false,
) {
    val today = remember { LocalDate.now() }
    var picked by remember { mutableStateOf(date) }
    var month by remember { mutableStateOf(java.time.YearMonth.from(date)) }
    val haptics = LocalHapticFeedback.current
    val selectable: (LocalDate) -> Boolean = { d -> anyDay || if (future) !d.isBefore(today) else !d.isAfter(today) }
    BurrowSheet(onDismiss) {
        SheetHeader(title ?: stringResource(R.string.date), onDismiss, if (anyDay) stringResource(R.string.date_sheet_line) else null)
        SheetBody {
            if (quick) {
                ChipRow(
                    listOf(R.string.yesterday to today.minusDays(1), R.string.today to today, R.string.tomorrow to today.plusDays(1)),
                    { stringResource(it.first) }, { picked == it.second }, { haptics.confirm(); onPick(it.second) },
                )
            }
            app.burrow.money.ui.filters.MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
            app.burrow.money.ui.filters.MonthCalendar(month, today, app.burrow.money.domain.DayRange(picked, picked), selectable) { d ->
                haptics.tick(); picked = d
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.done), { haptics.confirm(); onPick(picked) }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
        }
    }
}

