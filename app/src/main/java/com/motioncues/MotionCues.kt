package com.motioncues

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.motioncues.overlay.MotionCuesService
import com.motioncues.settings.MotionCuesSettings
import com.motioncues.tile.MotionCuesTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single entry point for turning the feature on and off from any UI surface. */
object MotionCues {

    private val _running = MutableStateFlow(false)

    /** Whether the overlay service is currently showing cues. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Turns cues on. Caller must have checked [canDrawOverlays]; otherwise the service
     * shuts itself down immediately.
     * @throws IllegalStateException (ForegroundServiceStartNotAllowedException on API 31+)
     *   if called while the app is in the background without an exemption.
     */
    fun start(context: Context) {
        val settings = MotionCuesSettings(context)
        settings.enabled = true
        try {
            ContextCompat.startForegroundService(context, Intent(context, MotionCuesService::class.java))
        } catch (e: IllegalStateException) {
            settings.enabled = false
            throw e
        }
    }

    fun stop(context: Context) {
        // The running service observes this setting and stops itself.
        MotionCuesSettings(context).enabled = false
        context.stopService(Intent(context, MotionCuesService::class.java))
    }

    internal fun setRunning(context: Context, value: Boolean) {
        _running.value = value
        TileService.requestListeningState(
            context,
            ComponentName(context, MotionCuesTileService::class.java),
        )
    }
}
