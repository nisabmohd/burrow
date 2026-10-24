package app.burrow.money.ui.components

import android.view.HapticFeedbackConstants as H
import androidx.compose.ui.hapticfeedback.HapticFeedbackType as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phones without vibration primitives play nothing for Android 14's types: they get classic ones. */
class HapticsTest {
    private val newTypes = setOf(H.TOGGLE_ON, H.TOGGLE_OFF, H.SEGMENT_TICK, H.SEGMENT_FREQUENT_TICK, H.GESTURE_THRESHOLD_ACTIVATE, H.CONFIRM, H.REJECT, H.GESTURE_END)
    private val all = listOf(T.ToggleOn, T.ToggleOff, T.SegmentTick, T.SegmentFrequentTick, T.GestureThresholdActivate, T.Confirm, T.Reject, T.GestureEnd, T.LongPress, T.TextHandleMove, T.ContextClick, T.VirtualKey)

    @Test fun withoutPrimitivesNoTypeMapsToAPrimitiveOnlyConstant() {
        for (t in all) assertTrue("$t played ${hapticConstant(t, rich = false)}", hapticConstant(t, rich = false) !in newTypes)
    }

    @Test fun withPrimitivesTheRichTypesAreKept() {
        assertEquals(H.TOGGLE_ON, hapticConstant(T.ToggleOn, true))
        assertEquals(H.TOGGLE_OFF, hapticConstant(T.ToggleOff, true))
        assertEquals(H.SEGMENT_TICK, hapticConstant(T.SegmentTick, true))
        assertEquals(H.SEGMENT_FREQUENT_TICK, hapticConstant(T.SegmentFrequentTick, true))
        assertEquals(H.GESTURE_THRESHOLD_ACTIVATE, hapticConstant(T.GestureThresholdActivate, true))
        assertEquals(H.CONFIRM, hapticConstant(T.Confirm, true))
    }

    @Test fun fallbacksStillFeelDifferent() {
        // A pick (tick) and a switch (toggle) shouldn't be the same buzz on old phones.
        assertEquals(H.KEYBOARD_TAP, hapticConstant(T.SegmentTick, false))
        assertEquals(H.VIRTUAL_KEY, hapticConstant(T.ToggleOn, false))
        assertEquals(H.CLOCK_TICK, hapticConstant(T.SegmentFrequentTick, false))
    }

    @Test fun longPressIsAlwaysLongPress() {
        assertEquals(H.LONG_PRESS, hapticConstant(T.LongPress, true))
        assertEquals(H.LONG_PRESS, hapticConstant(T.LongPress, false))
    }
}
