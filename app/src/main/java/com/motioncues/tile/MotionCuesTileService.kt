package com.motioncues.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.motioncues.MotionCues
import com.motioncues.R
import com.motioncues.ui.MainActivity

/** Quick Settings tile: one tap to turn cues on as you get in the car. */
class MotionCuesTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (MotionCues.running.value) {
            MotionCues.stop(this)
            refresh(forceRunning = false)
            return
        }
        if (!MotionCues.canDrawOverlays(this)) {
            openApp()
            return
        }
        val started = runCatching { MotionCues.start(this) }.isSuccess
        if (started) refresh(forceRunning = true) else openApp()
    }

    private fun refresh(forceRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val running = forceRunning ?: MotionCues.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    /** Opens the app (to grant permission, or to start from the foreground) and closes the shade. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_START_REQUESTED, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
