package com.motioncues.motion

import android.view.Surface

/**
 * Turns raw device-frame sensor vectors into the two accelerations a passenger feels,
 * expressed along the screen as the user currently sees it.
 *
 * The phone can be held at any tilt and in any display rotation, so we:
 *  1. drop the vertical part of the acceleration (bumps, not vehicle motion),
 *  2. work out which horizontal direction is "forward" for the person looking at the screen,
 *  3. read off the forward (longitudinal) and sideways (lateral) components.
 *
 * "Forward" blends the screen's top edge and the back of the phone, both flattened onto the
 * horizontal plane. Lying flat, the top edge points forward; held upright, the back of the
 * phone does; in between, both agree. Their sum never vanishes for a screen facing the user.
 *
 * Pure function of its inputs, so it is unit-tested on the JVM.
 */
object VehicleFrameProjector {

    /** Below this gravity magnitude (m/s²) the reading is unusable (free fall, bad sensor). */
    private const val MIN_GRAVITY = 1f

    /**
     * @param gravity gravity vector in device coordinates (TYPE_GRAVITY or low-passed accelerometer).
     * @param linear linear acceleration (gravity removed) in device coordinates.
     * @param displayRotation one of [Surface.ROTATION_0] .. [Surface.ROTATION_270].
     * @return accelerations along the screen, or null when the orientation is degenerate
     *   (e.g. screen facing straight down).
     */
    fun project(gravity: Vec3, linear: Vec3, displayRotation: Int): ScreenAcceleration? {
        val up = gravity.normalizedOrNull(MIN_GRAVITY) ?: return null

        fun horizontal(v: Vec3): Vec3 = v - up * (v dot up)

        val screenUp = screenUpAxis(displayRotation)
        val backOfPhone = -Vec3.Z

        // Unnormalised sum: each term's length is cos/sin of the tilt, so whichever axis lies
        // closer to horizontal naturally dominates and a near-vertical axis adds little noise.
        val forward = (horizontal(screenUp) + horizontal(backOfPhone)).normalizedOrNull(0.2f) ?: return null
        // Right-handed: forward × up points to the passenger's right.
        val right = forward cross up

        val horizontalAccel = horizontal(linear)
        return ScreenAcceleration(
            lateral = horizontalAccel dot right,
            longitudinal = horizontalAccel dot forward,
        )
    }

    /**
     * Device-frame direction of the screen's top edge for a given display rotation.
     * ROTATION_90 means the device is turned counter-clockwise, so the top of the screen
     * is the device's left side (−X), and so on around.
     */
    fun screenUpAxis(displayRotation: Int): Vec3 = when (displayRotation) {
        Surface.ROTATION_90 -> -Vec3.X
        Surface.ROTATION_180 -> -Vec3.Y
        Surface.ROTATION_270 -> Vec3.X
        else -> Vec3.Y
    }
}
