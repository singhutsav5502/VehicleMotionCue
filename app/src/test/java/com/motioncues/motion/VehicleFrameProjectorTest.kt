package com.motioncues.motion

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VehicleFrameProjectorTest {

    private val g = 9.81f
    private val eps = 1e-3f

    /** Phone tilted back by [tiltDeg] from flat, portrait, top edge pointing forward. */
    private fun portraitGravity(tiltDeg: Double): Vec3 {
        val t = Math.toRadians(tiltDeg)
        // Gravity reading points "up" in device coords: +Z when flat, +Y when upright.
        return Vec3(0f, (g * sin(t)).toFloat(), (g * cos(t)).toFloat())
    }

    /** World forward (horizontal, away from the passenger) expressed in device coords. */
    private fun portraitForward(tiltDeg: Double): Vec3 {
        val t = Math.toRadians(tiltDeg)
        return Vec3(0f, cos(t).toFloat(), (-sin(t)).toFloat())
    }

    @Test
    fun flatPhone_forwardAccelerationIsLongitudinal() {
        val r = VehicleFrameProjector.project(Vec3(0f, 0f, g), Vec3(0f, 2f, 0f), Surface.ROTATION_0)!!
        assertEquals(2f, r.longitudinal, eps)
        assertEquals(0f, r.lateral, eps)
    }

    @Test
    fun flatPhone_rightwardAccelerationIsLateral() {
        val r = VehicleFrameProjector.project(Vec3(0f, 0f, g), Vec3(1.5f, 0f, 0f), Surface.ROTATION_0)!!
        assertEquals(1.5f, r.lateral, eps)
        assertEquals(0f, r.longitudinal, eps)
    }

    @Test
    fun anyTilt_forwardAccelerationStaysLongitudinal() {
        for (tilt in listOf(0.0, 20.0, 45.0, 70.0, 89.0)) {
            val fwd = portraitForward(tilt)
            val r = VehicleFrameProjector.project(portraitGravity(tilt), fwd * 3f, Surface.ROTATION_0)!!
            assertEquals("tilt $tilt", 3f, r.longitudinal, 1e-2f)
            assertEquals("tilt $tilt", 0f, r.lateral, 1e-2f)
        }
    }

    @Test
    fun verticalBumpsAreIgnored() {
        val tilt = 40.0
        val upInDevice = portraitGravity(tilt) * (1f / g)
        val r = VehicleFrameProjector.project(portraitGravity(tilt), upInDevice * 4f, Surface.ROTATION_0)!!
        assertEquals(0f, r.longitudinal, eps)
        assertEquals(0f, r.lateral, eps)
    }

    @Test
    fun landscapeRotation90_mapsAxes() {
        // Device turned CCW and lying flat: screen top is device −X, screen right is device +Y.
        val gravity = Vec3(0f, 0f, g)
        val forward = VehicleFrameProjector.project(gravity, Vec3(-2f, 0f, 0f), Surface.ROTATION_90)!!
        assertEquals(2f, forward.longitudinal, eps)
        assertEquals(0f, forward.lateral, eps)

        val right = VehicleFrameProjector.project(gravity, Vec3(0f, 1f, 0f), Surface.ROTATION_90)!!
        assertEquals(1f, right.lateral, eps)
        assertEquals(0f, right.longitudinal, eps)
    }

    @Test
    fun noGravity_returnsNull() {
        assertNull(VehicleFrameProjector.project(Vec3.ZERO, Vec3(1f, 1f, 1f), Surface.ROTATION_0))
    }
}
