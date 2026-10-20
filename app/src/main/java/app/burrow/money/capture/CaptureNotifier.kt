package app.burrow.money.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.burrow.money.MainActivity
import app.burrow.money.R
import app.burrow.money.data.CaptureEntity
import app.burrow.money.data.CategoryEntity
import app.burrow.money.domain.MoneyFormat

/**
 * The capture notification (design 43): "₹240 to Third Wave Coffee", "HDFC ••1234 · which
 * category?", two category pills and Ignore under them (res/layout/notification_capture.xml). One tap saves. Tapping the notification opens
 * To review. The only notification in the app with buttons (CLAUDE.md § Behaviour).
 */
class CaptureNotifier(private val context: Context) {

    fun show(capture: CaptureEntity, categories: List<CategoryEntity>, from: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel()
        val money = MoneyFormat.of(capture.currency ?: "INR")
        val amount = money.plain(capture.amount ?: 0)
        val incoming = capture.direction == Direction.IN.name
        val title = when {
            capture.payee == null -> context.getString(if (incoming) R.string.notif_received else R.string.notif_spent, amount)
            incoming -> context.getString(R.string.notif_from, amount, app.burrow.money.data.tidyPayee(capture.payee))
            else -> context.getString(R.string.notif_to, amount, app.burrow.money.data.tidyPayee(capture.payee))
        }
        val line = context.getString(R.string.notif_which_category, from ?: context.getString(R.string.app_name))
        val id = notificationId(capture.id)
        // Design 43: two category pills, then Ignore below, in our own layout (Android's action
        // buttons can't look like this). Collapsed: title and line. Heads-up: + pills (Android caps its
        // height). Expanded in the shade: the full design with Ignore under the pills.
        fun withButtons(layout: Int, ignore: Boolean) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.line, line)
            val pills = listOf(R.id.category1, R.id.category2)
            pills.forEachIndexed { i, viewId ->
                val c = categories.getOrNull(i)
                if (c == null) {
                    setViewVisibility(viewId, View.GONE)
                } else {
                    setTextViewText(viewId, c.name)
                    setOnClickPendingIntent(viewId, action(CaptureActionReceiver.ACTION_SAVE, capture.id, c.id))
                }
            }
            if (ignore) {
                setTextViewText(R.id.ignore, context.getString(R.string.ignore))
                setOnClickPendingIntent(R.id.ignore, action(CaptureActionReceiver.ACTION_IGNORE, capture.id, 0))
            }
        }
        val body = withButtons(R.layout.notification_capture, ignore = true)
        val headsUp = withButtons(R.layout.notification_capture_headsup, ignore = false)
        val small = RemoteViews(context.packageName, R.layout.notification_capture_small).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.line, line)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(body)
            .setCustomHeadsUpContentView(headsUp)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(openReview(context))
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun cancel(captureId: Long) = NotificationManagerCompat.from(context).cancel(notificationId(captureId))

    private fun action(action: String, captureId: Long, categoryId: Long): PendingIntent {
        val intent = Intent(context, CaptureActionReceiver::class.java)
            .setAction(action)
            .putExtra(CaptureActionReceiver.EXTRA_CAPTURE, captureId)
            .putExtra(CaptureActionReceiver.EXTRA_CATEGORY, categoryId)
        // Unique per capture and button, so each button keeps its own extras.
        val request = (captureId * 31 + categoryId + action.hashCode()).toInt()
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_capture), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_capture_line)
            },
        )
    }

    companion object {
        const val CHANNEL = "capture"
        private const val ID_BASE = 10_000

        fun notificationId(captureId: Long) = ID_BASE + (captureId % 1_000_000).toInt()

        /** Opens the app on To review. */
        fun openReview(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_REVIEW).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
