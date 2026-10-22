package app.burrow.money.home

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.burrow.money.MainActivity
import app.burrow.money.R

/**
 * Quick settings tile (03): "Add expense · Burrow" with the Burrow mark; opens New expense directly
 * (behind the app lock when it is on). On a locked phone it asks to unlock first.
 */
class AddExpenseTile : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.add_expense)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val go = {
            val intent = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_EXPENSE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 110, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(go) else go()
    }
}
