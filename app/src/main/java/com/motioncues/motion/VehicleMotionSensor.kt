package com.motioncues.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Reads the motion sensors and reports vehicle acceleration along the screen.
 *
 * Prefers the platform's fused TYPE_GRAVITY + TYPE_LINEAR_ACCELERATION (gyro-assisted, so
 * gravity stays accurate during sustained turns). On devices without them it falls back to
 * splitting the raw accelerometer with a low-pass filter.
 *
 * Callbacks arrive on the main thread so they can feed [CueDynamics] without locking.
 */
class VehicleMotionSensor(
    context: Context,
    private val displayRotation: () -> Int,
    private val onAcceleration: (ScreenAcceleration, dtSeconds: Float) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linearSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val useFused = gravitySensor != null && linearSensor != null

    /** True if this device has enough hardware for motion cues at all. */
    val isSupported: Boolean get() = useFused || accelSensor != null

    private var gravity: Vec3? = null
    private var lastTimestampNs = 0L
    private var running = false

    fun start() {
        if (running || sensorManager == null) return
        running = true
        lastTimestampNs = 0L
        gravity = null
        if (useFused) {
            register(gravitySensor!!)
            register(linearSensor!!)
        } else if (accelSensor != null) {
            register(accelSensor)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
    }

    private fun register(sensor: Sensor) {
        // ~50 Hz is plenty for a ~1 Hz spring and stays under the 200 Hz cap that would
        // otherwise require HIGH_SAMPLING_RATE_SENSORS.
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, mainHandler)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> gravity = Vec3.of(event.values)
            Sensor.TYPE_LINEAR_ACCELERATION -> emit(Vec3.of(event.values), event.timestamp)
            Sensor.TYPE_ACCELEROMETER -> {
                val raw = Vec3.of(event.values)
                val dt = dtSeconds(event.timestamp, advance = false)
                val g = gravity
                gravity = if (g == null) raw else {
                    val a = CueDynamics.lowPassAlpha(dt, FALLBACK_GRAVITY_CUTOFF_HZ)
                    g + (raw - g) * a
                }
                emit(raw - gravity!!, event.timestamp)
            }
        }
    }

    private fun emit(linear: Vec3, timestampNs: Long) {
        val g = gravity ?: return
        val dt = dtSeconds(timestampNs, advance = true)
        val projected = VehicleFrameProjector.project(g, linear, displayRotation()) ?: return
        onAcceleration(projected, dt)
    }

    private fun dtSeconds(timestampNs: Long, advance: Boolean): Float {
        val dt = if (lastTimestampNs == 0L) DEFAULT_DT_S else (timestampNs - lastTimestampNs) / 1e9f
        if (advance) lastTimestampNs = timestampNs
        return if (dt <= 0f) DEFAULT_DT_S else dt
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val DEFAULT_DT_S = 0.02f
        const val FALLBACK_GRAVITY_CUTOFF_HZ = 0.3f
    }
}
