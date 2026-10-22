package com.motioncues.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class DotStyle {
    /** Dots always visible while the feature is on. */
    REGULAR,

    /** Dots fade in and grow only while the vehicle is accelerating, braking or turning. */
    DYNAMIC,
}

enum class DotSize(val radiusDp: Float) {
    SMALL(3.5f),
    MEDIUM(5f),
    LARGE(7f),
}

/** User-facing settings, persisted in SharedPreferences and observed live by the overlay. */
class MotionCuesSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Whether the user wants cues on. The service is the source of truth for whether they *are* on. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** Multiplier on dot travel; 0.5 = subtle, 2.0 = strong. */
    var intensity: Float
        get() = prefs.getFloat(KEY_INTENSITY, 1f).coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        set(value) = prefs.edit { putFloat(KEY_INTENSITY, value.coerceIn(MIN_INTENSITY, MAX_INTENSITY)) }

    var style: DotStyle
        get() = enumOr(prefs.getString(KEY_STYLE, null), DotStyle.REGULAR)
        set(value) = prefs.edit { putString(KEY_STYLE, value.name) }

    var size: DotSize
        get() = enumOr(prefs.getString(KEY_SIZE, null), DotSize.MEDIUM)
        set(value) = prefs.edit { putString(KEY_SIZE, value.name) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        const val MIN_INTENSITY = 0.5f
        const val MAX_INTENSITY = 2f

        private const val FILE = "motion_cues"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTENSITY = "intensity"
        private const val KEY_STYLE = "style"
        private const val KEY_SIZE = "size"
    }
}
