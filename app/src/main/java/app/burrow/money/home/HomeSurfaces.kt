package app.burrow.money.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.burrow.money.AppContainer
import app.burrow.money.BurrowApp
import app.burrow.money.MainActivity
import app.burrow.money.R
import app.burrow.money.data.FavouriteView
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.safeToSpendToday
import app.burrow.money.ui.DateText
import app.burrow.money.ui.components.duotoneIcon
import app.burrow.money.ui.plan.ComingGroup
import app.burrow.money.ui.plan.ComingItem
import app.burrow.money.ui.plan.comingUpDue
import app.burrow.money.ui.plan.comingUpFlow
import app.burrow.money.ui.theme.categoryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.concurrent.ConcurrentHashMap

/**
 * What the home screen shows outside the app (02, 03, 89): the widgets, the long-press shortcuts and
 * the quick settings tile. All of it is read on the phone; nothing here goes online.
 */
data class HomeNumbers(
    val budget: Long?,
    val spent: Long,
    val left: Long,
    val safeToday: Long,
    val daysLeft: Int,
    val month: String,
    val progress: Float,
    /** Next bill (89): the soonest unpaid bill due this week (or overdue); null = "Nothing due". */
    val bill: ComingItem?,
    /** The other bills due this week: "· 2 more this week". */
    val moreThisWeek: Int,
    /** Add expense (89): the first two favourites, one tap saves. */
    val favourites: List<FavouriteView>,
    val toReview: Int,
    val money: MoneyFormat,
    /** Customise › Region's date style, for "Due Sat, 24 Oct". */
    val dateStyle: String,
)

/**
 * The Next bill widget's bill (89): unpaid bills (not income, not one-off payments) that are overdue
 * or due this week, the same groups as Coming up. The soonest shows; the rest are "N more this week".
 */
fun widgetBill(items: List<ComingItem>): Pair<ComingItem?, Int> {
    val due = items.filter { it.occurrence != null && !it.income && (it.group == ComingGroup.OVERDUE || it.group == ComingGroup.THIS_WEEK) }
        .sortedBy { it.date }
    return due.firstOrNull() to (due.size - 1).coerceAtLeast(0)
}

object HomeSurfaces {
    /** Favourite id → the payment it just saved, while its pill reads "Saved · tap to undo" (4 s). */
    private val justSaved = ConcurrentHashMap<Long, Long>()

    /** How long a saved favourite's pill offers Undo; the same as the app's undo snackbar. */
    private const val UNDO_MS = 4_000L

    /** Reads the numbers every surface shows. Same daily number as Today's hero and the Sunday review. */
    suspend fun numbers(app: AppContainer, today: LocalDate = LocalDate.now()): HomeNumbers {
        val s = app.settings.settings.first()
        val locale = java.util.Locale.getDefault()
        val money = MoneyFormat.of(s.mainCurrency, locale, s.numberFormat, s.currencyDisplay, s.decimals).withHidden(s.hideAmounts)
        val period = BudgetPeriod.containing(today, s.monthStartDay)
        // With carry-over, like Today's hero and the Sunday review.
        val budget = app.plan.budgets(period).first().first?.amount
        val spent = app.money.spent(period).first()
        val spentToday = app.next.spentToday(today).first()
        val daysLeft = period.daysLeft(today)
        val left = (budget ?: 0) - spent
        val (bill, more) = widgetBill(app.comingUpFlow(today).first())
        return HomeNumbers(
            budget = budget, spent = spent, left = left,
            safeToday = if (budget == null) 0 else safeToSpendToday(left, spentToday, daysLeft, money.digits, app.comingUpDue(period, today)),
            daysLeft = daysLeft,
            month = period.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
            progress = if (budget == null || budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f),
            bill = bill, moreThisWeek = more,
            favourites = app.next.favourites.first().take(2),
            toReview = app.capture.reviewCount.first(),
            money = money,
            dateStyle = s.dateFormat,
        )
    }

    /** Redraws every widget and updates the shortcuts. Cheap; called after any change. */
    suspend fun refresh(app: AppContainer) {
        runCatching {
            val n = numbers(app)
            val ctx = app.context
            val manager = AppWidgetManager.getInstance(ctx)
            // Each widget is drawn for its own size (WidgetLayout): what fits shows whole.
            for (id in manager.getAppWidgetIds(ComponentName(ctx, SafeToSpendWidget::class.java))) {
                val (w, h) = size(manager, id)
                manager.updateAppWidget(id, safeViews(ctx, n, WidgetLayout.safe(w, h)))
            }
            for (id in manager.getAppWidgetIds(ComponentName(ctx, AddExpenseWidget::class.java))) {
                val (w, h) = size(manager, id)
                manager.updateAppWidget(id, addViews(ctx, n, WidgetLayout.add(w, h)))
            }
            for (id in manager.getAppWidgetIds(ComponentName(ctx, NextBillWidget::class.java))) {
                val (w, h) = size(manager, id)
                manager.updateAppWidget(id, billViews(ctx, n, WidgetLayout.bill(w, h)))
            }
            shortcuts(ctx, n.toReview)
        }
    }

    fun open(context: Context, action: String?, request: Int, id: Long = 0L): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN)
            .putExtra(MainActivity.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A tap that the widget itself handles (a favourite, Mark paid): a broadcast to [widget]. */
    private fun act(context: Context, widget: Class<out BurrowWidget>, action: String, request: Int, id: Long): PendingIntent {
        val intent = Intent(context, widget).setAction(action).putExtra(MainActivity.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A favourite pill tapped: saves it now (as on New expense); tapped again within 4 s, undoes it. */
    suspend fun spendFavourite(app: AppContainer, favouriteId: Long) {
        val saved = justSaved.remove(favouriteId)
        if (saved != null) {
            app.money.delete(saved)
        } else {
            val f = app.next.favourites.first().firstOrNull { it.favourite.id == favouriteId } ?: return
            val txnId = app.next.spendFavourite(f.favourite)
            justSaved[favouriteId] = txnId
            app.appScope.launch {
                delay(UNDO_MS)
                if (justSaved.remove(favouriteId, txnId)) refresh(app)
            }
        }
        refresh(app)
    }

    /** ✓ on the Next bill widget: the same as swiping right on Coming up, then a toast. */
    suspend fun markPaid(app: AppContainer, occurrenceId: Long) {
        val name = app.comingUpFlow(LocalDate.now()).first().firstOrNull { it.occurrence?.occurrence?.id == occurrenceId }?.name
        if (app.plan.markPaid(occurrenceId) != null && name != null) {
            val ctx = app.context
            withContext(Dispatchers.Main) { Toast.makeText(ctx, ctx.getString(R.string.widget_marked_paid, name), Toast.LENGTH_SHORT).show() }
        }
        refresh(app)
    }

    private fun safeViews(ctx: Context, n: HomeNumbers, fit: WidgetLayout.Safe) = RemoteViews(ctx.packageName, R.layout.widget_safe).apply {
        setViewVisibility(R.id.widget_add, if (fit.showPlus) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_line, if (fit.showLine) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_bar, if (fit.showBar) View.VISIBLE else View.GONE)
        val money = n.money
        if (n.budget == null) {
            // No budget yet: what was spent this month; the + still works.
            setTextViewText(R.id.widget_label, ctx.getString(R.string.hero_label_no_budget, n.month))
            setTextViewText(R.id.widget_amount, money.plain(n.spent))
            setTextViewText(R.id.widget_line, ctx.getString(R.string.widget_no_budget))
            setProgressBar(R.id.widget_bar, 1000, 0, false)
        } else {
            setTextViewText(R.id.widget_label, ctx.getString(R.string.safe_to_spend_today))
            setTextViewText(R.id.widget_amount, money.plain(n.safeToday))
            val days = ctx.resources.getQuantityString(R.plurals.n_days, n.daysLeft, n.daysLeft)
            setTextViewText(
                R.id.widget_line,
                if (n.left < 0) ctx.getString(R.string.widget_over_line, money.short(-n.left), n.month)
                else ctx.getString(R.string.widget_left_line, money.short(n.left), n.month, days),
            )
            setProgressBar(R.id.widget_bar, 1000, (n.progress * 1000).toInt(), false)
        }
        setOnClickPendingIntent(R.id.widget_root, open(ctx, null, 101))
        setOnClickPendingIntent(R.id.widget_add, open(ctx, MainActivity.ACTION_NEW_EXPENSE, 102))
    }

    /** One widget ("safe", "add", "bill") drawn for a [w] × [h] dp size: the debug widget gallery uses it. */
    fun viewsAt(ctx: Context, n: HomeNumbers, which: String, w: Int, h: Int): RemoteViews = when (which) {
        "safe" -> safeViews(ctx, n, WidgetLayout.safe(w, h))
        "add" -> addViews(ctx, n, WidgetLayout.add(w, h))
        else -> billViews(ctx, n, WidgetLayout.bill(w, h))
    }

    /** A widget's size in portrait (dp): the min width and the max height; 0 when the launcher doesn't say. */
    private fun size(manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = manager.getAppWidgetOptions(id)
        return o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) to o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    }

    /**
     * Add expense (89): the + row at the top, the favourites that fit as pills at the bottom (a tall
     * widget has one even gap between, as Next bill); none = the + and the label only.
     */
    private fun addViews(ctx: Context, n: HomeNumbers, fit: WidgetLayout.Add): RemoteViews {
        val favourites = n.favourites.take(fit.pills)
        if (favourites.isEmpty()) return RemoteViews(ctx.packageName, R.layout.widget_add).apply {
            setOnClickPendingIntent(R.id.widget_root, open(ctx, MainActivity.ACTION_NEW_EXPENSE, 103))
        }
        return RemoteViews(ctx.packageName, R.layout.widget_add_favourites).apply {
            setOnClickPendingIntent(R.id.widget_root, open(ctx, MainActivity.ACTION_NEW_EXPENSE, 103))
            // Narrow: the label above the pills or left out, never "Add ex…" or on two lines.
            setViewVisibility(R.id.widget_add_label, if (fit.labelBeside) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_add_label_below, if (fit.labelBelow) View.VISIBLE else View.GONE)
            val pills = listOf(
                Triple(R.id.widget_fav_1, R.id.widget_fav_1_amount, R.id.widget_fav_1_name),
                Triple(R.id.widget_fav_2, R.id.widget_fav_2_amount, R.id.widget_fav_2_name),
            )
            pills.forEachIndexed { i, (pill, amount, name) ->
                val f = favourites.getOrNull(i)
                if (f == null) { setViewVisibility(pill, View.GONE); return@forEachIndexed }
                setViewVisibility(pill, View.VISIBLE)
                if (justSaved.containsKey(f.favourite.id)) {
                    setTextViewText(amount, ctx.getString(R.string.widget_saved_undo))
                    setViewVisibility(name, View.GONE)
                } else {
                    setTextViewText(amount, n.money.plain(f.favourite.amount))
                    setTextViewText(name, f.favourite.name)
                    setViewVisibility(name, View.VISIBLE)
                }
                setOnClickPendingIntent(pill, act(ctx, AddExpenseWidget::class.java, ACTION_SPEND_FAVOURITE, 110 + i, f.favourite.id))
            }
        }
    }

    /** Next bill (89): its circle, ✓ Mark paid, "Netflix ₹649", "Due today · 2 more this week". */
    private fun billViews(ctx: Context, n: HomeNumbers, fit: WidgetLayout.Bill) = RemoteViews(ctx.packageName, R.layout.widget_bill).apply {
        setInt(R.id.widget_title, "setMaxLines", fit.titleLines)
        if (fit.lineLines > 0) setInt(R.id.widget_line, "setMaxLines", fit.lineLines)
        setViewVisibility(R.id.widget_line, if (fit.lineLines > 0) View.VISIBLE else View.GONE)
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bill = n.bill
        val occurrence = bill?.occurrence
        if (bill == null || occurrence == null) {
            setImageViewResource(R.id.widget_icon, R.drawable.ic_event_available_duotone)
            setInt(R.id.widget_circle, "setColorFilter", ContextCompat.getColor(ctx, R.color.widget_circle))
            setInt(R.id.widget_icon, "setColorFilter", ContextCompat.getColor(ctx, R.color.widget_text_muted))
            setViewVisibility(R.id.widget_paid, View.GONE)
            setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_nothing_due))
            setTextViewText(R.id.widget_line, ctx.getString(R.string.this_week))
            setOnClickPendingIntent(R.id.widget_root, open(ctx, MainActivity.ACTION_OPEN_COMING_UP, 104))
        } else {
            // The bill's own circle (its category's colours), never a generic icon.
            setImageViewResource(R.id.widget_icon, duotoneIcon(bill.icon))
            val colours = bill.hue?.let { categoryColors(it, dark) }
            setInt(R.id.widget_circle, "setColorFilter", colours?.container?.toArgb() ?: ContextCompat.getColor(ctx, R.color.widget_circle))
            setInt(R.id.widget_icon, "setColorFilter", colours?.onContainer?.toArgb() ?: ContextCompat.getColor(ctx, R.color.widget_text_muted))
            setViewVisibility(R.id.widget_paid, View.VISIBLE)
            setOnClickPendingIntent(R.id.widget_paid, act(ctx, NextBillWidget::class.java, ACTION_MARK_PAID, 106, occurrence.occurrence.id))
            setContentDescription(R.id.widget_paid, ctx.getString(R.string.mark_paid))
            setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_bill_title, bill.name, n.money.plain(bill.amount)))
            val due = dueLine(ctx, bill.date, n.dateStyle)
            setTextViewText(
                R.id.widget_line,
                if (n.moreThisWeek == 0 || !fit.showMore) due
                else due + " · " + ctx.resources.getQuantityString(R.plurals.widget_more_this_week, n.moreThisWeek, n.moreThisWeek),
            )
            setOnClickPendingIntent(R.id.widget_root, open(ctx, MainActivity.ACTION_OPEN_BILL, 105, occurrence.occurrence.billId))
        }
    }

    /** "Due today", "Due Sat, 24 Oct"; a past one stays "Not paid yet" until marked. */
    private fun dueLine(ctx: Context, due: LocalDate, style: String): String {
        val today = LocalDate.now()
        return when {
            due.isBefore(today) -> ctx.getString(R.string.not_paid_yet)
            due == today -> ctx.getString(R.string.due_today)
            else -> ctx.getString(R.string.due_on, DateText(ctx, java.util.Locale.getDefault(), style).weekdayDay(due))
        }
    }

    /**
     * Long-press the icon (02): New expense, New income, Review N payments (only while there are
     * some), Search. All dynamic, so they work in every build flavour.
     */
    private fun shortcuts(ctx: Context, toReview: Int) {
        fun shortcut(id: String, label: String, icon: Int, action: String) = ShortcutInfoCompat.Builder(ctx, id)
            .setShortLabel(label).setLongLabel(label)
            .setIcon(IconCompat.createWithResource(ctx, icon))
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(action))
            .build()
        val list = listOfNotNull(
            shortcut("new_expense", ctx.getString(R.string.new_expense), R.drawable.ic_shortcut_add, MainActivity.ACTION_NEW_EXPENSE),
            shortcut("new_income", ctx.getString(R.string.new_income), R.drawable.ic_shortcut_payments, MainActivity.ACTION_NEW_INCOME),
            if (toReview > 0) shortcut("review", ctx.resources.getQuantityString(R.plurals.review_n_payments, toReview, toReview), R.drawable.ic_shortcut_review, MainActivity.ACTION_OPEN_REVIEW) else null,
            shortcut("search", ctx.getString(R.string.search), R.drawable.ic_shortcut_search, MainActivity.ACTION_SEARCH),
        ).take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtLeast(1))
        ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
    }
}

/** Taps the widgets handle themselves (89): a favourite pill, the bill's ✓. */
const val ACTION_SPEND_FAVOURITE = "app.burrow.money.widget.SPEND_FAVOURITE"
const val ACTION_MARK_PAID = "app.burrow.money.widget.MARK_PAID"

/** Every widget redraws the same way: read the numbers off the main thread, then draw. */
abstract class BurrowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = work(context) { HomeSurfaces.refresh(it) }

    /** Resized: Add expense shows as many favourites as now fit. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        work(context) { HomeSurfaces.refresh(it) }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MainActivity.EXTRA_ID, 0L)
        when (intent.action) {
            ACTION_SPEND_FAVOURITE -> work(context) { HomeSurfaces.spendFavourite(it, id) }
            ACTION_MARK_PAID -> work(context) { HomeSurfaces.markPaid(it, id) }
            else -> super.onReceive(context, intent)
        }
    }

    private fun work(context: Context, block: suspend (AppContainer) -> Unit) {
        val app = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        app.appScope.launch {
            try { block(app) } finally { pending.finish() }
        }
    }
}

/** 4×2 "Safe to spend today" with the + button. */
class SafeToSpendWidget : BurrowWidget()

/** 2×2 "Add expense", with the first two favourites (one tap saves). */
class AddExpenseWidget : BurrowWidget()

/** 2×2 next bill, with ✓ Mark paid. */
class NextBillWidget : BurrowWidget()
