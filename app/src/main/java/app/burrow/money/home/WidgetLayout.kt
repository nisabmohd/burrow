package app.burrow.money.home

/**
 * How each home-screen widget lays itself out at the size the launcher gives it (dp), as Weft's
 * widgets do: what fits is shown whole, nothing is cut or squeezed (user, 15 Nov: "don't kill the
 * aesthetics"). Launchers differ a lot: a 2×2 is 172 × 152 dp in the design (89) but 146 × 278 on a
 * Pixel-style grid with tall rows. Pure, so `WidgetLayoutTest` covers every size between the min and
 * max resize sizes in `res/xml/widget_*_info.xml`. 0 = size unknown (an old launcher): the design's.
 */
object WidgetLayout {
    /** The design's 2×2 and 4×2 (89), used when the launcher doesn't say. */
    const val SMALL_W = 172
    const val SMALL_H = 152
    const val WIDE_W = 360
    const val WIDE_H = 164

    // The sizes the rules add up, from res/values/widget.xml.
    private const val PAD = 16
    private const val PAD_TIGHT = 12
    private const val CIRCLE = 40
    private const val GAP = 8
    private const val PILL = 36
    private const val TITLE_LINE = 22 // 16 sp
    private const val BODY_LINE = 20 // 14 sp
    /** "Add expense" at 16 sp medium (92 dp), and the 12 dp gap before it. */
    private const val ADD_LABEL = 12 + 92

    /** Safe to spend (4×2): the + and which lines show. The amount shrinks to the width by itself (autosize). */
    data class Safe(val showPlus: Boolean, val showLine: Boolean, val showBar: Boolean)

    fun safe(width: Int, height: Int): Safe {
        val w = width.takeIf { it > 0 } ?: WIDE_W
        val h = height.takeIf { it > 0 } ?: WIDE_H
        // Mark row 24 + amount 52 + line 20 + bar 12 + 8, inside 16 dp padding.
        return Safe(
            // The text column keeps "Safe to spend today" and "₹13.6k left in October · 13 days" whole.
            showPlus = w >= 290,
            showLine = h >= PAD + 24 + 52 + BODY_LINE + PAD,
            showBar = h >= PAD + 24 + 52 + BODY_LINE + 20 + PAD,
        )
    }

    /**
     * Add expense with favourites: "Add expense" beside the + when it fits on one line; on a narrow
     * widget it sits above the pills instead (as Next bill's title), when that costs no pill; and how
     * many pills fit whole.
     */
    data class Add(val labelBeside: Boolean, val labelBelow: Boolean, val pills: Int)

    fun add(width: Int, height: Int): Add {
        val w = width.takeIf { it > 0 } ?: SMALL_W
        val h = height.takeIf { it > 0 } ?: SMALL_H
        fun pills(extra: Int) = ((h - PAD_TIGHT - CIRCLE - extra - PAD_TIGHT) / (GAP + PILL)).coerceIn(0, 2)
        val beside = w >= PAD_TIGHT + CIRCLE + ADD_LABEL + PAD_TIGHT
        val below = !beside && pills(GAP + TITLE_LINE) == pills(0)
        return Add(beside, below, pills(if (below) GAP + TITLE_LINE else 0))
    }

    /**
     * Next bill: how many lines the title and the due line may take (0 = the due line is left out),
     * and whether "· 2 more this week" fits: only on a two-line due line wide enough for it, else
     * just "Due today", never "Due today · 2 m…".
     */
    data class Bill(val titleLines: Int, val lineLines: Int, val showMore: Boolean)

    fun bill(width: Int, height: Int): Bill {
        val w = width.takeIf { it > 0 } ?: SMALL_W
        val h = height.takeIf { it > 0 } ?: SMALL_H
        // The circles (40), at least 8 dp of air, then the text, inside 16 dp padding.
        val room = h - PAD - CIRCLE - GAP - PAD
        val (title, line) = when {
            room >= 2 * TITLE_LINE + 2 * BODY_LINE -> 2 to 2
            room >= TITLE_LINE + 2 * BODY_LINE -> 1 to 2
            room >= TITLE_LINE + BODY_LINE -> 1 to 1
            else -> 1 to 0
        }
        return Bill(title, line, showMore = line == 2 && w >= 140)
    }
}
