package app.burrow.money.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/*
 * Spacing, sizes and the ONE SHAPE RULE (docs/TOKENS.md).
 * Two shapes only: 24 dp surfaces, and circles/pills for everything small or tappable.
 * Never write a raw dp for these in a screen: use the names below.
 */
object Space {
    val grid = 4.dp
    val screen = 16.dp        // side margin of every screen
    val block = 12.dp         // between blocks/cards on a page
    val row = 2.dp            // between rows of one group
    val sectionTop = 16.dp    // above a section header
    val sectionBottom = 8.dp  // header → its group
    val card = 16.dp          // inner padding of cards, widgets, notes
    val sheet = 24.dp         // sheet side padding
    val inline = 8.dp         // between buttons or chips in one row (header buttons, chip rows)
    val barStart = 24.dp      // tab title's start inset in the top bar
    val group = 4.dp          // between the options of a connected button group
    val small = 8.dp          // icon → its label inside a widget, note or button row
    val hero = 20.dp          // inside Today's hero
    val amountTop = 20.dp     // above the keypad amount
    val fabLift = 56.dp       // the FAB moves up this much while a snackbar shows
}

object Size {
    val row = 72.dp
    val rowOneLine = 56.dp
    val iconCircle = 40.dp
    val iconCircleBig = 64.dp
    val icon = 24.dp
    val iconInCircle = 22.dp
    val button = 48.dp
    val buttonBottom = 56.dp
    val iconButton = 48.dp
    val chip = 36.dp
    val keypadKey = 60.dp
    val topBar = 64.dp
    val nav = 80.dp
    val search = 56.dp
    val note = 72.dp
    val widgetHalf = 184.dp
    val heatmapDay = 38.dp
    val chartBar = 16.dp
    val chartHeight = 104.dp
    val fab = 56.dp
    /** Space a list leaves under its last item so the FAB never covers it. */
    val fabClearance = 88.dp
    val iconSmall = 18.dp     // icons in chips, widget labels, a button group's check
    val iconButton20 = 20.dp  // icons inside buttons and the hero label
    val emptyShape = 128.dp
    val wavyHeight = 14.dp
    val thinBar = 8.dp
}

object Shapes {
    val surface = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val pill = CircleShape // RoundedCornerShape(50) for wide pills
    /** Grouped list: 24 dp on the outside of the group, 6 dp between its rows. */
    fun grouped(index: Int, count: Int): RoundedCornerShape {
        val top = if (index == 0) 24.dp else 6.dp
        val bottom = if (index == count - 1) 24.dp else 6.dp
        return RoundedCornerShape(top, top, bottom, bottom)
    }
}
