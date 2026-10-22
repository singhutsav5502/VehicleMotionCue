package com.motioncues.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.motioncues.MotionCues
import com.motioncues.R
import com.motioncues.motion.CueDynamics
import com.motioncues.motion.VehicleMotionSensor
import com.motioncues.settings.MotionCuesSettings
import com.motioncues.ui.MainActivity

/**
 * Foreground service that owns the overlay window, the sensors and the dot physics.
 *
 * Lifecycle: started by [MotionCues.start], stopped by [MotionCues.stop], the notification's
 * "Turn off" action, or losing the overlay permission. Sensors pause whenever the screen is off.
 */
class MotionCuesService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: MotionCuesSettings
    private lateinit var windowManager: WindowManager
    private lateinit var dynamics: CueDynamics
    private lateinit var sensor: VehicleMotionSensor
    private var overlay: MotionCuesOverlayView? = null
    private var display: Display? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pauseSensing()
                Intent.ACTION_SCREEN_ON -> resumeSensing()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = MotionCuesSettings(this)
        windowManager = getSystemService(WindowManager::class.java)
        display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        dynamics = CueDynamics(dynamicsConfig())
        sensor = VehicleMotionSensor(
            context = this,
            displayRotation = { display?.rotation ?: Surface.ROTATION_0 },
            onAcceleration = { accel, dt ->
                dynamics.onAcceleration(accel, dt)
                overlay?.requestFrame()
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must promote to foreground promptly after startForegroundService(), even if we then stop.
        startInForeground()

        if (intent?.action == ACTION_STOP) {
            MotionCues.stop(this)
            return START_NOT_STICKY
        }
        if (!settings.enabled || !MotionCues.canDrawOverlays(this) || !sensor.isSupported) {
            settings.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlay == null) {
            addOverlay()
            settings.registerListener(this)
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            if (getSystemService(PowerManager::class.java).isInteractive) resumeSensing()
            MotionCues.setRunning(this, true)
        }
        // Re-created by the system after being killed: come back only if the user still wants it.
        return START_STICKY
    }

    override fun onDestroy() {
        pauseSensing()
        if (overlay != null) {
            runCatching { unregisterReceiver(screenReceiver) }
            settings.unregisterListener(this)
            overlay?.let { runCatching { windowManager.removeView(it) } }
            overlay = null
        }
        MotionCues.setRunning(this, false)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (!settings.enabled) {
            stopSelf()
            return
        }
        dynamics.config = dynamicsConfig()
        overlay?.apply {
            style = settings.style
            size = settings.size
            requestFrame()
        }
    }

    private fun addOverlay() {
        val view = MotionCuesOverlayView(this, dynamics).apply {
            style = settings.style
            size = settings.size
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "Vehicle motion cues"
            // Since Android 12, touches only pass through an untrusted overlay whose opacity is
            // at or below the system limit (0.8 by default). Above it, the phone becomes unusable.
            alpha = maxPassThroughAlpha()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        windowManager.addView(view, params)
        overlay = view
    }

    private fun maxPassThroughAlpha(): Float {
        val limit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        } else {
            1f
        }
        return minOf(DEFAULT_OVERLAY_ALPHA, limit)
    }

    private fun resumeSensing() {
        dynamics.reset()
        sensor.start()
        overlay?.startAnimating()
    }

    private fun pauseSensing() {
        sensor.stop()
        overlay?.stopAnimating()
    }

    private fun dynamicsConfig(): CueDynamics.Config {
        val density = resources.displayMetrics.density
        val intensity = settings.intensity
        return CueDynamics.Config(
            gainPxPerMps2 = GAIN_DP_PER_MPS2 * intensity * density,
            maxOffsetPx = MAX_OFFSET_DP * intensity * density,
        )
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MotionCuesService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_motion_cues)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.turn_off), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val ACTION_STOP = "com.motioncues.action.STOP"
        private const val CHANNEL_ID = "motion_cues"
        private const val NOTIFICATION_ID = 1

        /** Dot travel per m/s² at intensity 1.0; ~2 m/s² (firm acceleration) ≈ 28 dp. */
        private const val GAIN_DP_PER_MPS2 = 14f
        private const val MAX_OFFSET_DP = 44f
        private const val DEFAULT_OVERLAY_ALPHA = 0.8f
    }
}
