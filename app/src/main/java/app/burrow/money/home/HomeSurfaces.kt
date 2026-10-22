package app.burrow.money.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.burrow.money.AppContainer
import app.burrow.money.BurrowApp
import app.burrow.money.MainActivity
import app.burrow.money.R
import app.burrow.money.data.OccurrenceStatus
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.domain.safeToSpendToday
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * What the home screen shows outside the app (02, 03): the widgets, the long-press shortcuts and
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
    val billName: String?,
    val billAmount: Long?,
    val billDue: LocalDate?,
    val billId: Long?,
    val toReview: Int,
    val money: MoneyFormat,
)

object HomeSurfaces {
    /** Reads the numbers every surface shows. Same daily number as Today's hero and the Sunday review. */
    suspend fun numbers(app: AppContainer, today: LocalDate = LocalDate.now()): HomeNumbers {
        val s = app.settings.settings.first()
        val locale = java.util.Locale.getDefault()
        val money = MoneyFormat.of(s.mainCurrency, locale, s.numberFormat, s.currencyDisplay, s.decimals).withHidden(s.hideAmounts)
        val period = BudgetPeriod.containing(today, s.monthStartDay)
        val budget = app.money.overallBudget(period).first()?.amount
        val spent = app.money.spent(period).first()
        val spentToday = app.next.spentToday(today).first()
        val daysLeft = period.daysLeft(today)
        val left = (budget ?: 0) - spent
        val bill = app.plan.occurrences(today.minusDays(30), today.plusDays(62)).first()
            .filter { it.occurrence.status == OccurrenceStatus.UPCOMING }.minByOrNull { it.occurrence.dueDate }
        return HomeNumbers(
            budget = budget, spent = spent, left = left,
            safeToday = if (budget == null) 0 else safeToSpendToday(left, spentToday, daysLeft, money.digits),
            daysLeft = daysLeft,
            month = period.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
            progress = if (budget == null || budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f),
            billName = bill?.name, billAmount = bill?.amount,
            billDue = bill?.let { Instant.ofEpochMilli(it.occurrence.dueDate).atZone(ZoneId.systemDefault()).toLocalDate() },
            billId = bill?.occurrence?.billId,
            toReview = app.capture.reviewCount.first(),
            money = money,
        )
    }

    /** Redraws every widget and updates the shortcuts. Cheap; called after any change. */
    suspend fun refresh(app: AppContainer) {
        runCatching {
            val n = numbers(app)
            val ctx = app.context
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, SafeToSpendWidget::class.java)).takeIf { it.isNotEmpty() }?.let { manager.updateAppWidget(it, safeViews(ctx, n)) }
            manager.getAppWidgetIds(ComponentName(ctx, AddExpenseWidget::class.java)).takeIf { it.isNotEmpty() }?.let { manager.updateAppWidget(it, addViews(ctx)) }
            manager.getAppWidgetIds(ComponentName(ctx, NextBillWidget::class.java)).takeIf { it.isNotEmpty() }?.let { manager.updateAppWidget(it, billViews(ctx, n)) }
            shortcuts(ctx, n.toReview)
        }
    }

    fun open(context: Context, action: String?, request: Int, id: Long = 0L): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN)
            .putExtra(MainActivity.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun safeViews(ctx: Context, n: HomeNumbers) = RemoteViews(ctx.packageName, R.layout.widget_safe).apply {
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

    private fun addViews(ctx: Context) = RemoteViews(ctx.packageName, R.layout.widget_add).apply {
        setOnClickPendingIntent(R.id.widget_root, open(ctx, MainActivity.ACTION_NEW_EXPENSE, 103))
    }

    private fun billViews(ctx: Context, n: HomeNumbers) = RemoteViews(ctx.packageName, R.layout.widget_bill).apply {
        if (n.billName == null || n.billDue == null) {
            setTextViewText(R.id.widget_title, ctx.getString(R.string.no_bills_due))
            setTextViewText(R.id.widget_line, ctx.getString(R.string.next_bill))
            setOnClickPendingIntent(R.id.widget_root, open(ctx, null, 104))
        } else {
            setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_bill_title, n.billName, n.money.plain(n.billAmount ?: 0)))
            setTextViewText(R.id.widget_line, dueLine(ctx, n.billDue))
            setOnClickPendingIntent(R.id.widget_root, open(ctx, MainActivity.ACTION_OPEN_BILL, 105, n.billId ?: 0L))
        }
    }

    private fun dueLine(ctx: Context, due: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), due).toInt()
        return when {
            days < 0 -> ctx.getString(R.string.not_paid_yet)
            days == 0 -> ctx.getString(R.string.due_today)
            days == 1 -> ctx.getString(R.string.due_tomorrow)
            else -> ctx.resources.getQuantityString(R.plurals.due_in_days, days, days)
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

/** Every widget redraws the same way: read the numbers off the main thread, then draw. */
abstract class BurrowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = (context.applicationContext as BurrowApp).container
        val pending = goAsync()
        app.appScope.launch {
            try { HomeSurfaces.refresh(app) } finally { pending.finish() }
        }
    }
}

/** 4×2 "Safe to spend today" with the + button. */
class SafeToSpendWidget : BurrowWidget()

/** 2×2 "Add expense". */
class AddExpenseWidget : BurrowWidget()

/** 2×2 next bill. */
class NextBillWidget : BurrowWidget()
