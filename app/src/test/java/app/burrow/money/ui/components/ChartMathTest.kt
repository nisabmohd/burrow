package app.burrow.money.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    @Test fun stackWidthsAlwaysFillTheWidth() {
        val w = stackWidths(listOf(7600, 4000, 2400, 1300, 600, 500), 300f, 3f)
        assertEquals(300f, w.sum(), 0.01f)
        assertTrue(w.all { it >= 3f })
    }

    @Test fun tinySharesGetASliverTakenFromTheBigOnes() {
        val w = stackWidths(listOf(1_000_000, 1, 1, 1), 100f, 3f)
        assertEquals(100f, w.sum(), 0.01f)
        assertEquals(3f, w[1], 0.001f)
        assertEquals(91f, w[0], 0.01f)
    }

    @Test fun sharesAboveTheMinimumStayProportional() {
        val w = stackWidths(listOf(1, 1), 100f, 3f)
        assertEquals(50f, w[0], 0.001f)
    }

    @Test fun ringGapCoversBothCapsPlusSpace() {
        // 4 dp stroke at radius 24 + 3 dp clear = 7/24 rad ≈ 16.7°
        assertEquals(16.71f, ringGapDegrees(4f, 24f, 3f), 0.05f)
        assertEquals(0f, ringGapDegrees(4f, 0f, 3f), 0f)
        assertEquals(60f, ringGapDegrees(40f, 10f, 3f), 0f)
    }
}
