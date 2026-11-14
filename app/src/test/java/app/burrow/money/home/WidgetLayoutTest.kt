package app.burrow.money.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every widget size between the min and max resize sizes (res/xml/widget_*_info.xml): what shows
 * fits whole, and the design's sizes (89) show everything.
 */
class WidgetLayoutTest {
    @Test fun theDesignsSizesShowEverything() {
        assertEquals(WidgetLayout.Safe(showPlus = true, showLine = true, showBar = true), WidgetLayout.safe(360, 164))
        assertEquals(WidgetLayout.Add(labelBeside = true, labelBelow = false, pills = 2), WidgetLayout.add(172, 152))
        assertEquals(WidgetLayout.Bill(titleLines = 1, lineLines = 2, showMore = true), WidgetLayout.bill(172, 152))
        // Size unknown: the design's.
        assertEquals(WidgetLayout.add(172, 152), WidgetLayout.add(0, 0))
        assertEquals(WidgetLayout.safe(360, 164), WidgetLayout.safe(0, 0))
    }

    @Test fun aTallLauncherCellKeepsEverythingAndWrapsTheTitle() {
        // A Pixel-style 2×2: narrow and tall.
        assertEquals(WidgetLayout.Add(labelBeside = false, labelBelow = true, pills = 2), WidgetLayout.add(146, 278))
        assertEquals(WidgetLayout.Bill(titleLines = 2, lineLines = 2, showMore = true), WidgetLayout.bill(146, 278))
    }

    @Test fun addNeverCutsAPill() {
        for (w in listOf(110, 146, 172, 300)) for (h in 110..320) {
            val a = WidgetLayout.add(w, h)
            // 12 dp padding, the 40 dp row, the label below (8 + 22) if shown, then 8 + 36 dp a pill, 12 dp padding.
            val label = if (a.labelBelow) 30 else 0
            assertTrue("w=$w h=$h $a", 12 + 40 + label + a.pills * 44 + 12 <= h)
            assertTrue("w=$w h=$h room for one more pill", a.pills == 2 || 12 + 40 + label + (a.pills + 1) * 44 + 12 > h)
            assertFalse("w=$w h=$h label twice", a.labelBeside && a.labelBelow)
        }
    }

    @Test fun addLabelOnlyWhereItFitsOnOneLine() {
        for (w in 110..300) {
            val label = WidgetLayout.add(w, 152).labelBeside
            assertEquals("w=$w", 12 + 40 + 12 + 92 + 12 <= w, label)
        }
    }

    @Test fun billTextNeverOverflows() {
        for (h in 110..320) {
            val b = WidgetLayout.bill(172, h)
            val used = 16 + 40 + 8 + b.titleLines * 22 + b.lineLines * 20 + 16
            assertTrue("h=$h $b", used <= h)
        }
        assertEquals(0, WidgetLayout.bill(172, 110).lineLines)
        // "· 2 more this week" only where it fits whole.
        assertFalse(WidgetLayout.bill(146, 139).showMore)
        assertFalse(WidgetLayout.bill(120, 200).showMore)
    }

    @Test fun safeDropsThePlusWhenNarrowAndTheLinesWhenShort() {
        assertFalse(WidgetLayout.safe(180, 164).showPlus)
        assertFalse(WidgetLayout.safe(250, 164).showPlus)
        assertTrue(WidgetLayout.safe(290, 164).showPlus)
        WidgetLayout.safe(360, 120).let { assertFalse(it.showLine); assertFalse(it.showBar) }
        WidgetLayout.safe(360, 130).let { assertTrue(it.showLine); assertFalse(it.showBar) }
        for (h in 120..320) {
            val s = WidgetLayout.safe(360, h)
            val used = 16 + 24 + 52 + (if (s.showLine) 20 else 0) + (if (s.showBar) 20 else 0) + 16
            assertTrue("h=$h $s", used <= h)
        }
    }
}
