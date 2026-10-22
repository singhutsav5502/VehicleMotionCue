package com.motioncues.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CueDynamicsTest {

    private val config = CueDynamics.Config(gainPxPerMps2 = 30f, maxOffsetPx = 100f)

    /** Feeds a constant acceleration at 50 Hz sensor / 60 Hz frame rate for [seconds]. */
    private fun CueDynamics.run(a: ScreenAcceleration, seconds: Float) {
        var t = 0f
        var nextSensor = 0f
        while (t < seconds) {
            while (nextSensor <= t) { onAcceleration(a, 0.02f); nextSensor += 0.02f }
            step(1f / 60f)
            t += 1f / 60f
        }
    }

    @Test
    fun acceleratingForward_dotsMoveDown() {
        val d = CueDynamics(config)
        d.run(ScreenAcceleration(0f, 0f), 0.5f)
        d.run(ScreenAcceleration(0f, 2f), 1.5f)
        assertTrue("offsetY=${d.offsetY}", d.offsetY > 30f)
        assertEquals(0f, d.offsetX, 0.5f)
    }

    @Test
    fun braking_dotsMoveUp() {
        val d = CueDynamics(config)
        d.run(ScreenAcceleration(0f, 0f), 0.5f)
        d.run(ScreenAcceleration(0f, -2f), 1.5f)
        assertTrue(d.offsetY < -30f)
    }

    @Test
    fun leftTurn_dotsMoveRight() {
        val d = CueDynamics(config)
        d.run(ScreenAcceleration(0f, 0f), 0.5f)
        d.run(ScreenAcceleration(-2f, 0f), 1.5f)
        assertTrue(d.offsetX > 30f)
    }

    @Test
    fun offsetNeverExceedsLimitByMuch() {
        val d = CueDynamics(config)
        d.run(ScreenAcceleration(0f, 0f), 0.5f)
        var peak = 0f
        repeat(300) {
            d.onAcceleration(ScreenAcceleration(0f, 20f), 0.02f)
            d.step(1f / 60f)
            peak = maxOf(peak, abs(d.offsetY))
        }
        // Slightly underdamped spring may overshoot a little, never wildly.
        assertTrue("peak=$peak", peak < config.maxOffsetPx * 1.1f)
    }

    @Test
    fun returnsToRestAndStopsRequestingFrames() {
        val d = CueDynamics(config)
        d.run(ScreenAcceleration(0f, 0f), 0.5f)
        d.run(ScreenAcceleration(1f, 2f), 1f)
        d.run(ScreenAcceleration(0f, 0f), 6f)
        assertEquals(0f, d.offsetX, 0.5f)
        assertEquals(0f, d.offsetY, 0.5f)
        d.onAcceleration(ScreenAcceleration(0f, 0f), 0.02f)
        assertFalse(d.step(1f / 60f))
    }

    @Test
    fun tinyJitterIsIgnored() {
        val d = CueDynamics(config)
        var sign = 1f
        repeat(500) {
            d.onAcceleration(ScreenAcceleration(0.08f * sign, 0.08f * sign), 0.02f)
            d.step(1f / 60f)
            sign = -sign
        }
        assertEquals(0f, d.offsetX, 0.01f)
        assertEquals(0f, d.offsetY, 0.01f)
        assertEquals(0f, d.activity, 1e-3f)
    }

    @Test
    fun sustainedBiasIsSlowlyRemoved() {
        val d = CueDynamics(config)
        d.run(ScreenAcceleration(0f, 0.6f), 90f)
        assertEquals(0f, d.offsetY, 1f)
    }
}
