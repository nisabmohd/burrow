package app.burrow.money.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.burrow.money.R
import app.burrow.money.domain.AmountInput
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.caretBlinkSpec
import java.time.LocalTime

/*
 * The value sheet (COMPONENTS § Value sheet, user 2026-10-24): ONE pattern for every number —
 * amount, time, day of month, rate, budget, add money, refund, fix balance, pay in. Title + muted
 * line · the value big with a blinking caret · optional chips / rows · the same 3 × 4 keypad ·
 * footer Done, or Cancel + Save. No − / + steppers anywhere.
 */

/** The typed value's caret: 3 dp, primary, blinking (Motion.kt). Also New expense's amount. */
@Composable
fun Caret(modifier: Modifier = Modifier) {
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(1f, 0f, caretBlinkSpec(), label = "caretAlpha")
    Box(modifier.padding(start = Space.grid).size(Size.caretWidth, Size.caretHeight).alpha(blink).clip(Shapes.pill).background(MaterialTheme.colorScheme.primary))
}

/**
 * The sheet itself. [value] is drawn as given ("₹9,000", "10:··", "1st"); keys go to [onKey].
 * [label] sits above the value ("Your bank shows"), [below] under the chips ("₹320 less than the
 * app…", "1 Oct – 31 Oct"). [chips] and [rows] are optional. With [cancel] the footer is Cancel +
 * [confirm], else one full-width [confirm]. [confirmEnabled] false = the button looks off (still taps).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ValueSheet(
    title: String,
    value: String,
    onKey: (Key) -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    line: String? = null,
    label: String? = null,
    below: String? = null,
    valueError: Boolean = false,
    showDot: Boolean = true,
    cancel: Boolean = false,
    confirmEnabled: Boolean = true,
    chips: (@Composable () -> Unit)? = null,
    rows: (@Composable () -> Unit)? = null,
    /** A tonal button beside [confirm] other than Cancel (a saved rate: Remove). */
    secondary: Pair<String, () -> Unit>? = null,
) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    BurrowSheet(onDismiss) {
        SheetHeader(title, onDismiss, line)
        SheetBody {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.small)) {
                if (label != null) Text(label, style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        value, style = BurrowType.display.copy(color = if (valueError) c.error else c.onSurface), maxLines = 1,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = BurrowType.title.fontSize, maxFontSize = BurrowType.display.fontSize),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Caret()
                }
                if (chips != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.small)) { chips() }
                }
                if (below != null) Text(below, style = BurrowType.body, color = if (valueError) c.error else c.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            rows?.invoke()
            NumberPad(onKey, showDot = showDot)
        }
        SheetFooter {
            if (secondary != null) BurrowButton(secondary.first, secondary.second, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            else if (cancel) BurrowButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            BurrowButton(
                confirm, { if (confirmEnabled) haptics.confirm(); onConfirm() }, Modifier.weight(1f),
                kind = if (confirmEnabled) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom,
            )
        }
    }
}

/** A quick value under the big number: a [Chip] with a check when it is the value (32, 99, 141, 96). */
@Composable
fun ValueChip(label: String, selected: Boolean, onClick: () -> Unit) = Chip(label, selected, onClick)

/**
 * Money in a [ValueSheet] (budget, add money, refund, fix balance, pay in, the Welcome budget, a
 * split part, a typed amount). Typing replaces the start value; a chip sets it. Over [max] the value
 * turns red and Save looks off. [below] reads the value as it is typed ("₹320 less than the app").
 */
@Composable
fun AmountSheet(
    title: String,
    amount: Long,
    onDone: (Long) -> Unit,
    onDismiss: () -> Unit,
    line: String? = null,
    label: String? = null,
    chips: List<Pair<String, Long>> = emptyList(),
    below: (@Composable (Long) -> String?)? = null,
    confirm: String = stringResource(R.string.done),
    cancel: Boolean = false,
    max: Long? = null,
    min: Long = 0,
    digits: Int = LocalMoney.current.digits,
    rows: (@Composable () -> Unit)? = null,
    /** Every change as it is typed, for a sheet that steps away and back (Add money → account picker). */
    onChange: ((Long) -> Unit)? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    val money = LocalMoney.current
    // Starts showing the current amount; the first key typed replaces it (fresh = not typed yet).
    var input by rememberSaveable(stateSaver = AmountTypedSaver) { mutableStateOf(AmountTyped(AmountInput.of(amount, digits), fresh = true)) }
    val value = input.input.minor()
    val ok = value >= min && (max == null || value <= max)
    ValueSheet(
        title = title, value = money.plain(value), line = line, label = label,
        onKey = { key -> input = input.press(key, digits); onChange?.invoke(input.input.minor()) },
        below = when {
            max != null && value > max -> stringResource(R.string.at_most, money.plain(max))
            else -> below?.invoke(value)
        },
        valueError = max != null && value > max,
        showDot = digits > 0, cancel = cancel, confirm = confirm, confirmEnabled = ok,
        onConfirm = { if (ok) onDone(value) }, onDismiss = onDismiss,
        chips = if (chips.isEmpty()) null else ({ chips.forEach { (l, v) -> ValueChip(l, v == value) { input = AmountTyped(AmountInput.of(v, digits), fresh = true); onChange?.invoke(v) } } }),
        rows = rows, secondary = secondary,
    )
}

/**
 * A value on a screen that opens its [ValueSheet] when tapped (Split parts 95): a tonal pill with the
 * amount in `titleSmall`, 48 dp tall to tap.
 */
@Composable
fun ValuePill(text: String, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    // 48 dp to tap, the ripple stays on the pill (as FormChip).
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.minimumInteractiveComponentSize()
            .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = Size.chip).clip(Shapes.pill).background(c.secondaryContainer)
                .indication(interaction, androidx.compose.material3.ripple()).padding(horizontal = Space.block),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = BurrowType.titleSmall, color = c.onSecondaryContainer, maxLines = 1) }
    }
}

/** A big value on a screen that opens its [ValueSheet] (Welcome budget 99): `display`, centred, tappable. */
@Composable
fun ValueText(text: String, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text, style = BurrowType.display.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center), maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = BurrowType.title.fontSize, maxFontSize = BurrowType.display.fontSize),
        modifier = modifier.fillMaxWidth().clip(Shapes.pill).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick).padding(vertical = Space.small),
    )
}

/** What has been typed into an [AmountSheet]; [fresh] until the first key, which then starts over. */
data class AmountTyped(val input: AmountInput, val fresh: Boolean) {
    fun press(key: Key, digits: Int): AmountTyped {
        val base = if (fresh && key != Key.Backspace) AmountInput(digits = digits) else input
        val next = when (key) {
            is Key.Digit -> base.press(key.char)
            Key.Dot -> base.press(AmountInput.DOT)
            Key.Backspace -> if (fresh) AmountInput(digits = digits) else base.backspace()
            Key.Clear -> AmountInput(digits = digits)
            else -> base
        }
        return AmountTyped(next, fresh = false)
    }
}

private val AmountTypedSaver = Saver<AmountTyped, List<Any>>(
    save = { listOf(it.input.text, it.input.digits, it.fresh) },
    restore = { AmountTyped(AmountInput(text = it[0] as String, digits = it[1] as Int), it[2] as Boolean) },
)

/**
 * A day of the month in a [ValueSheet] (month start 68, due day 28): typed 1–[max], quick chips,
 * [result] under them ("1 Oct – 31 Oct"). No dot key.
 */
@Composable
fun DaySheet(
    title: String,
    day: Int,
    onDone: (Int) -> Unit,
    onDismiss: () -> Unit,
    ordinal: (Int) -> String,
    line: String? = null,
    quick: List<Int> = emptyList(),
    result: (@Composable (Int) -> String)? = null,
    max: Int = 28,
) {
    var typed by rememberSaveable { mutableStateOf(day.toString()) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val d = typed.toIntOrNull()
    val ok = d != null && d in 1..max
    ValueSheet(
        title = title, value = d?.let(ordinal) ?: "–", line = line,
        onKey = { key ->
            typed = typeDay(if (fresh && key != Key.Backspace) "" else typed, key, max)
            fresh = false
        },
        below = if (ok) result?.invoke(d!!) else null,
        showDot = false, confirm = stringResource(R.string.done), confirmEnabled = ok,
        onConfirm = { if (ok) onDone(d!!) }, onDismiss = onDismiss,
        chips = if (quick.isEmpty()) null else ({ quick.forEach { q -> ValueChip(ordinal(q), q == d) { typed = q.toString(); fresh = true } } }),
    )
}

/** A key on a day being typed: a second digit that would pass [max] starts a new day instead. */
fun typeDay(typed: String, key: Key, max: Int): String = when (key) {
    is Key.Digit -> {
        val next = typed + key.char
        when {
            next.toInt() in 1..max -> next.trimStart('0').ifEmpty { "0" }
            key.char == '0' && typed.isEmpty() -> typed
            else -> key.char.toString().takeIf { it.toInt() in 1..max } ?: typed
        }
    }
    Key.Backspace -> typed.dropLast(1)
    Key.Clear -> ""
    else -> typed
}

/** A key on a rate being typed ("84.10"): digits, one dot, at most 4 decimals and 12 characters. */
fun typeRate(typed: String, key: Key): String = when (key) {
    is Key.Digit -> when {
        typed.length >= 12 -> typed
        typed.contains('.') && typed.substringAfter('.').length >= 4 -> typed
        typed == "0" -> key.char.toString()
        else -> typed + key.char
    }
    Key.Dot -> if (typed.contains('.')) typed else (typed.ifEmpty { "0" } + ".")
    Key.Backspace -> typed.dropLast(1)
    Key.Clear -> ""
    else -> typed
}
