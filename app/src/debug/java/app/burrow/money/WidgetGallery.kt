package app.burrow.money

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.burrow.money.data.BillOccurrenceEntity
import app.burrow.money.data.OccurrenceView
import app.burrow.money.home.HomeSurfaces
import app.burrow.money.ui.plan.ComingGroup
import app.burrow.money.ui.plan.ComingItem
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Debug only: every home-screen widget drawn from its real RemoteViews at the sizes launchers give
 * (the design's, a tall-row Pixel grid, the min and max resize sizes), to check them without resizing
 * on a launcher. `adb shell am start -n app.burrow.money/.WidgetGallery [--es which add]`.
 */
class WidgetGallery : Activity() {
    private val sizes = mapOf(
        "safe" to listOf(360 to 164, 318 to 278, 250 to 139, 200 to 120, 400 to 320),
        "add" to listOf(172 to 152, 146 to 278, 146 to 139, 110 to 110, 120 to 200, 250 to 250),
        "bill" to listOf(172 to 152, 146 to 278, 146 to 139, 120 to 110, 120 to 200, 250 to 250),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as BurrowApp).container
        val which = intent.getStringExtra("which")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(32), dp(16), dp(32)) }
        setContentView(ScrollView(this).apply { addView(list) })
        scope.launch { draw(app, which, list) }
    }

    private val scope = MainScope()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun draw(app: AppContainer, which: String?, list: LinearLayout) {
        val real = HomeSurfaces.numbers(app)
        // A bill due today with two more this week (Netflix in the design), whatever this phone has.
        val netflix = ComingItem(
            "o0", java.time.LocalDate.now(), "Netflix", 649_00, false, "movie", 275f, "HDFC Savings",
            ComingGroup.THIS_WEEK,
            occurrence = OccurrenceView(
                BillOccurrenceEntity(id = 0, billId = 0, dueDate = System.currentTimeMillis()),
                name = "Netflix", kind = "BILL", billAmount = 649_00, accountId = 1, accountName = "HDFC Savings",
                categoryId = null, categoryName = null, icon = "movie", hue = 275f, repeatRule = "MONTHLY:1", onDue = "ASK",
                remindersOn = false, remindDaysBefore = 0, remindAtMinutes = 0,
            ),
        )
        for ((kind, all) in sizes) {
            if (which != null && which != kind) continue
            val n = if (kind == "bill") real.copy(bill = netflix, moreThisWeek = 2) else real
            var row: LinearLayout? = null
            var used = 0
            for ((w, h) in all) {
                if (row == null || used + w + 12 > 380) {
                    row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    list.addView(row)
                    used = 0
                }
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), dp(12), 0) }
                cell.addView(TextView(this).apply { text = "$kind $w×$h"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) })
                val frame = FrameLayout(this)
                cell.addView(frame, LinearLayout.LayoutParams(dp(w), dp(h)))
                frame.addView(HomeSurfaces.viewsAt(this, n, kind, w, h).apply(this, frame))
                row.addView(cell)
                used += w + 12
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
