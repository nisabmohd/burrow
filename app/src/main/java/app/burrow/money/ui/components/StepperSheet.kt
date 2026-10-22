package app.burrow.money.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.burrow.money.R
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * An amount picked with − / + and quick chips (budget edit 32, Add money 35, Prepay): the same
 * shape as [DayStepper], for money. [extra] sits under the chips (carry-over switch, From account).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountStepperSheet(
    title: String,
    line: String?,
    amount: Long,
    onAmount: (Long) -> Unit,
    step: Long,
    chips: List<Long>,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val money = LocalMoney.current
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss, line)
        SheetBody {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.small), verticalAlignment = Alignment.CenterVertically) {
                TonalIconButton(painterResource(R.drawable.ic_remove_regular), stringResource(R.string.key_minus), { haptics.tick(); onAmount((amount - step).coerceAtLeast(step)) })
                Text(money.plain(amount), style = BurrowType.display, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                TonalIconButton(painterResource(R.drawable.ic_add_regular), stringResource(R.string.key_plus), { haptics.tick(); onAmount(amount + step) })
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                chips.forEach { v -> Chip(money.short(v), v == amount, { onAmount(v) }) }
            }
            extra?.invoke()
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(confirm, { haptics.confirm(); onConfirm() }, Modifier.weight(1f), height = Size.buttonBottom)
        }
    }
}

/** A form field with its label above (New bill: "Name", "Amount"). */
@Composable
fun LabeledField(label: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Text(label, style = BurrowType.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.grid))
        content()
    }
}

/**
 * Type an exact amount (a split part, a refund, the Welcome budget): the amount big, the keypad, ✓.
 * [max] caps it (a refund can't be more than was paid); over it, ✓ looks off.
 */
@Composable
fun AmountKeypadSheet(title: String, amount: Long, digits: Int, onDone: (Long) -> Unit, onDismiss: () -> Unit, max: Long? = null) {
    val money = LocalMoney.current
    var input by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = AmountInputSaver) {
        androidx.compose.runtime.mutableStateOf(app.burrow.money.domain.AmountInput.of(amount, digits))
    }
    val value = input.minor()
    val ok = value >= 0 && (max == null || value <= max)
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss, max?.let { stringResource(R.string.at_most, money.plain(it)) })
        SheetBody {
            Text(
                money.plain(value), style = BurrowType.display, textAlign = TextAlign.Center, maxLines = 1,
                color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
            Keypad(
                onKey = { key ->
                    when {
                        key != Key.Done -> input = input.apply(key)
                        input.hasOperator -> input = input.evaluated()
                        ok -> onDone(value)
                    }
                },
                doneEnabled = ok, showDot = digits > 0,
            )
        }
    }
}

/** Keeps what was typed across rotation. */
private val AmountInputSaver = androidx.compose.runtime.saveable.Saver<app.burrow.money.domain.AmountInput, List<Any>>(
    save = { listOf(it.text, it.digits) },
    restore = { app.burrow.money.domain.AmountInput(text = it[0] as String, digits = it[1] as Int) },
)
