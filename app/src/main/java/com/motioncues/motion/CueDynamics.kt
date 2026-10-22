package com.motioncues.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Converts a stream of vehicle accelerations into a smooth on-screen offset for the cue dots.
 *
 * The dots behave like a light object on a spring inside the car: when the car speeds up they
 * slide toward the bottom of the screen, when it brakes they slide up, and in a left turn they
 * drift right. That matches what the inner ear feels, which is what reduces the sensory
 * conflict behind motion sickness.
 *
 * Pipeline (all per axis):
 *   raw → low-pass (removes hand tremor and road buzz)
 *       → minus slow bias estimate (removes sensor drift and a tilted mount)
 *       → soft dead zone (dots stay still at a red light)
 *       → gain + soft clamp → spring-damper target
 *
 * Contains no Android types so it is fully unit-testable.
 */
class CueDynamics(config: Config) {

    data class Config(
        /** Pixels of dot travel per m/s² of acceleration. */
        val gainPxPerMps2: Float,
        /** Soft upper bound on dot travel in pixels. */
        val maxOffsetPx: Float,
        val naturalFrequencyHz: Float = 1.1f,
        val dampingRatio: Float = 0.8f,
        /** Accelerations below this (m/s²) are treated as zero. */
        val deadZoneMps2: Float = 0.12f,
        val inputCutoffHz: Float = 2.5f,
        /** How slowly the bias estimate follows the signal; must be far longer than a turn. */
        val biasTimeConstantS: Float = 30f,
    )

    /** Can be swapped live (e.g. the user drags the intensity slider); state is kept. */
    var config: Config = config

    // Filtered input, m/s².
    private var lpLat = 0f
    private var lpLon = 0f
    private var biasLat = 0f
    private var biasLon = 0f
    private var conditionedLat = 0f
    private var conditionedLon = 0f
    private var hasInput = false

    /** Current dot offset in pixels; +x is screen right, +y is screen down. */
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    private var velX = 0f
    private var velY = 0f

    /** 0..1 measure of how much motion is happening, used by the "dynamic" dot style. */
    var activity = 0f
        private set

    /** Feed one sensor sample. [dtSeconds] is the time since the previous sample. */
    fun onAcceleration(a: ScreenAcceleration, dtSeconds: Float) {
        if (!hasInput) {
            // Start the filters at rest rather than ramping from zero over several seconds.
            lpLat = a.lateral; lpLon = a.longitudinal
            biasLat = 0f; biasLon = 0f
            hasInput = true
        }
        val dt = dtSeconds.coerceIn(0f, 0.25f)
        val lpAlpha = lowPassAlpha(dt, config.inputCutoffHz)
        lpLat += (a.lateral - lpLat) * lpAlpha
        lpLon += (a.longitudinal - lpLon) * lpAlpha

        val biasAlpha = 1f - exp(-dt / config.biasTimeConstantS)
        biasLat += (lpLat - biasLat) * biasAlpha
        biasLon += (lpLon - biasLon) * biasAlpha

        conditionedLat = deadZone(lpLat - biasLat, config.deadZoneMps2)
        conditionedLon = deadZone(lpLon - biasLon, config.deadZoneMps2)
    }

    /** Forget all motion, e.g. when the screen turns back on after being off for a while. */
    fun reset() {
        hasInput = false
        lpLat = 0f; lpLon = 0f; biasLat = 0f; biasLon = 0f
        conditionedLat = 0f; conditionedLon = 0f
        offsetX = 0f; offsetY = 0f; velX = 0f; velY = 0f
        activity = 0f
    }

    /**
     * Advance the spring simulation by [dtSeconds] (one display frame).
     * @return true if anything visible changed and the view should redraw.
     */
    fun step(dtSeconds: Float): Boolean {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        if (dt == 0f) return false

        val before = Snapshot(offsetX, offsetY, activity)

        val targetX = softClamp(-conditionedLat * config.gainPxPerMps2, config.maxOffsetPx)
        val targetY = softClamp(conditionedLon * config.gainPxPerMps2, config.maxOffsetPx)

        val omega = (2.0 * PI * config.naturalFrequencyHz).toFloat()
        val k = omega * omega
        val c = 2f * config.dampingRatio * omega

        // Semi-implicit Euler with small sub-steps stays stable for any frame rate.
        var remaining = dt
        while (remaining > 0f) {
            val h = min(remaining, MAX_SUBSTEP_S)
            velX += (k * (targetX - offsetX) - c * velX) * h
            velY += (k * (targetY - offsetY) - c * velY) * h
            offsetX += velX * h
            offsetY += velY * h
            remaining -= h
        }

        val magnitude = hypot(conditionedLat, conditionedLon)
        val targetActivity = smoothstep(ACTIVITY_LOW_MPS2, ACTIVITY_HIGH_MPS2, magnitude)
        val tau = if (targetActivity > activity) ACTIVITY_ATTACK_S else ACTIVITY_RELEASE_S
        activity += (targetActivity - activity) * (1f - exp(-dt / tau))
        if (activity < 1e-3f && targetActivity == 0f) activity = 0f

        if (isAtRest(targetX, targetY)) {
            offsetX = targetX; offsetY = targetY; velX = 0f; velY = 0f
        }

        return before.differsFrom(offsetX, offsetY, activity)
    }

    private fun isAtRest(targetX: Float, targetY: Float) =
        abs(offsetX - targetX) < REST_POS_PX && abs(offsetY - targetY) < REST_POS_PX &&
            abs(velX) < REST_VEL_PX && abs(velY) < REST_VEL_PX

    private data class Snapshot(val x: Float, val y: Float, val activity: Float) {
        fun differsFrom(nx: Float, ny: Float, na: Float) =
            abs(nx - x) > REDRAW_EPS_PX || abs(ny - y) > REDRAW_EPS_PX || abs(na - activity) > 1e-3f
    }

    companion object {
        private const val MAX_SUBSTEP_S = 0.004f
        private const val REST_POS_PX = 0.05f
        private const val REST_VEL_PX = 0.5f
        private const val REDRAW_EPS_PX = 0.01f
        private const val ACTIVITY_LOW_MPS2 = 0.15f
        private const val ACTIVITY_HIGH_MPS2 = 1.2f
        private const val ACTIVITY_ATTACK_S = 0.15f
        private const val ACTIVITY_RELEASE_S = 1.5f

        fun lowPassAlpha(dt: Float, cutoffHz: Float): Float {
            val rc = 1f / (2f * PI.toFloat() * cutoffHz)
            return dt / (rc + dt)
        }

        fun deadZone(v: Float, zone: Float): Float = sign(v) * max(0f, abs(v) - zone)

        /** Linear near zero, approaches ±[limit] asymptotically. */
        fun softClamp(v: Float, limit: Float): Float =
            if (limit <= 0f) 0f else limit * tanh(v / limit)

        fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
