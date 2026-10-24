package app.burrow.money.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BarMathTest {
    @Test fun emptyDayHasNoBar() {
        assertEquals(0f, pillBarHeight(0, 1000, 120f, 16f), 0f)
        assertEquals("refunds net below zero: still no bar", 0f, pillBarHeight(-50, 1000, 120f, 16f), 0f)
        assertEquals(0f, pillBarHeight(10, 0, 120f, 16f), 0f)
    }

    @Test fun smallDayIsARoundDotNotAFlatBlob() {
        // ₹10 against a ₹5,000 day: proportional height 0.24 px, drawn as tall as it is wide.
        assertEquals(16f, pillBarHeight(10, 5000, 120f, 16f), 0f)
    }

    @Test fun biggestDayFillsTheChart() {
        assertEquals(120f, pillBarHeight(5000, 5000, 120f, 16f), 0f)
    }

    @Test fun neverTallerThanTheChart() {
        assertEquals("tiny widget chart, wide bar", 10f, pillBarHeight(1, 1000, 10f, 16f), 0f)
        assertEquals(120f, pillBarHeight(9000, 5000, 120f, 16f), 0f)
    }

    @Test fun proportionalInBetweenAndGrowsIn() {
        assertEquals(60f, pillBarHeight(2500, 5000, 120f, 16f), 0.001f)
        assertEquals("half-way through the grow animation", 30f, pillBarHeight(2500, 5000, 120f, 16f, grow = 0.5f), 0.001f)
        assertEquals("start of the animation is still a dot", 16f, pillBarHeight(2500, 5000, 120f, 16f, grow = 0f), 0f)
    }

    @Test fun tapPicksTheBarUnderTheFinger() {
        assertEquals(0, barIndexAt(0f, 140f, 14))
        assertEquals(0, barIndexAt(9.9f, 140f, 14))
        assertEquals(1, barIndexAt(10f, 140f, 14))
        assertEquals(13, barIndexAt(139.9f, 140f, 14))
        assertEquals("past the right edge", 13, barIndexAt(500f, 140f, 14))
        assertEquals("past the left edge", 0, barIndexAt(-20f, 140f, 14))
        assertEquals("no bars", 0, barIndexAt(50f, 140f, 0))
        assertEquals("not laid out yet", 0, barIndexAt(50f, 0f, 14))
    }
}
