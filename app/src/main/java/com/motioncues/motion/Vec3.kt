package com.motioncues.motion

import kotlin.math.sqrt

/** Minimal immutable 3D vector for sensor math. Allocation rate (~50 Hz) is negligible. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    val length: Float get() = sqrt(this dot this)

    /** Unit vector, or null when the vector is too short to have a meaningful direction. */
    fun normalizedOrNull(minLength: Float = 1e-3f): Vec3? {
        val len = length
        return if (len < minLength) null else this * (1f / len)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)

        fun of(values: FloatArray) = Vec3(values[0], values[1], values[2])
    }
}

/** Screen-frame vehicle acceleration in m/s². +lateral = toward screen right, +longitudinal = toward screen top. */
data class ScreenAcceleration(val lateral: Float, val longitudinal: Float) {
    companion object {
        val ZERO = ScreenAcceleration(0f, 0f)
    }
}
