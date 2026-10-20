package app.burrow.money.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.burrow.money.MainActivity
import app.burrow.money.R

/**
 * Bill reminders and budget alerts (CLAUDE.md § Behaviour: app name + time, a title, one line, no
 * buttons). The small icon is always the Burrow mark. Tapping opens the bill or Plan.
 */
class Notifier(private val context: Context) {

    fun show(id: Int, title: String, line: String, open: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel()
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title)
            .setContentText(line)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_reminders_line)
            },
        )
    }

    companion object {
        const val CHANNEL = "reminders"

        fun openBill(context: Context, billId: Long) = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_BILL).putExtra(MainActivity.EXTRA_ID, billId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun openPlan(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
