package app.burrow.money.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/*
 * The app's haptics, so the same kind of action always feels the same:
 * - toggle: something turns on or off (a switch, a day circle, a checkbox, marking a bill paid).
 * - tick: one choice in a row is picked (chips, segmented buttons, swatches), or − / + steps once.
 * - stepTick: each step a slider passes, lighter than tick because it repeats quickly.
 * - threshold: a swipe has gone far enough to count; letting go now logs it (once, going out).
 * - confirm: a change is saved (swipe released past the threshold, Save in a sheet).
 * Long-press keeps HapticFeedbackType.LongPress.
 */

fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

fun HapticFeedback.stepTick() = performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

fun HapticFeedback.threshold() = performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.Confirm)

/**
 * The app's [HapticFeedback], provided at the root (MainActivity). Android 14's richer types
 * (toggle on/off, segment tick, threshold) are built from vibration "primitives"; phones whose motor
 * lacks them (many Vivo, Oppo, Realme, older Samsung) play nothing at all. There each type falls back
 * to a classic constant every phone plays, so a tap always answers.
 */
class BurrowHapticFeedback(private val view: android.view.View) : HapticFeedback {
    private val rich: Boolean = android.os.Build.VERSION.SDK_INT >= 34 && runCatching {
        val vibrator = view.context.getSystemService(android.os.Vibrator::class.java)
        vibrator.areAllPrimitivesSupported(
            android.os.VibrationEffect.Composition.PRIMITIVE_TICK,
            android.os.VibrationEffect.Composition.PRIMITIVE_CLICK,
            android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        )
    }.getOrDefault(false)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        view.performHapticFeedback(constantFor(hapticFeedbackType))
    }
    private fun constantFor(type: HapticFeedbackType): Int = hapticConstant(type, rich)
}

/** [type] → the View constant to play; without [rich] (no vibration primitives) a classic one every phone plays. */
@android.annotation.SuppressLint("InlinedApi")
internal fun hapticConstant(type: HapticFeedbackType, rich: Boolean): Int {
    val k = android.view.HapticFeedbackConstants.VIRTUAL_KEY
    return when (type) {
        HapticFeedbackType.ToggleOn -> if (rich) android.view.HapticFeedbackConstants.TOGGLE_ON else k
        HapticFeedbackType.ToggleOff -> if (rich) android.view.HapticFeedbackConstants.TOGGLE_OFF else k
        HapticFeedbackType.SegmentTick -> if (rich) android.view.HapticFeedbackConstants.SEGMENT_TICK else android.view.HapticFeedbackConstants.KEYBOARD_TAP
        HapticFeedbackType.SegmentFrequentTick -> if (rich) android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else android.view.HapticFeedbackConstants.CLOCK_TICK
        HapticFeedbackType.GestureThresholdActivate -> if (rich) android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else k
        HapticFeedbackType.Confirm -> if (rich) android.view.HapticFeedbackConstants.CONFIRM else k
        HapticFeedbackType.Reject -> if (rich) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS
        HapticFeedbackType.GestureEnd -> if (rich) android.view.HapticFeedbackConstants.GESTURE_END else android.view.HapticFeedbackConstants.CONTEXT_CLICK
        HapticFeedbackType.LongPress -> android.view.HapticFeedbackConstants.LONG_PRESS
        HapticFeedbackType.TextHandleMove -> android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE
        HapticFeedbackType.ContextClick -> android.view.HapticFeedbackConstants.CONTEXT_CLICK
        else -> k
    }
}
