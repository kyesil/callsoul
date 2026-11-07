package com.bettercallsoul.island

import android.content.Context
import androidx.core.content.edit

/** How the floating call pill looks. Stored beside its position, read each time the pill appears. */
data class PillStyle(
    val vertical: Boolean = false,
    /** 1 is the normal size; the settings slider runs from [MIN_SCALE] to [MAX_SCALE]. */
    val scale: Float = 1f,
    /** Hang-up button first (left, or top when vertical) instead of the speaker / answer button. */
    val endFirst: Boolean = false,
    val opacity: Float = 1f,
) {
    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 1.6f
        const val MIN_OPACITY = 0.1f

        private const val FILE = "call_overlay"
        private const val KEY_VERTICAL = "style_vertical"
        private const val KEY_SCALE = "style_scale"
        private const val KEY_OLD_SIZE = "style_size"
        private const val KEY_END_FIRST = "style_end_first"
        private const val KEY_OPACITY = "style_opacity"

        fun load(context: Context): PillStyle {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            // The first version stored Small / Medium / Large.
            val old = when (prefs.getString(KEY_OLD_SIZE, null)) {
                "Small" -> 0.8f
                "Large" -> 1.25f
                else -> 1f
            }
            return PillStyle(
                vertical = prefs.getBoolean(KEY_VERTICAL, false),
                scale = prefs.getFloat(KEY_SCALE, old).coerceIn(MIN_SCALE, MAX_SCALE),
                endFirst = prefs.getBoolean(KEY_END_FIRST, false),
                opacity = prefs.getFloat(KEY_OPACITY, 1f).coerceIn(MIN_OPACITY, 1f),
            )
        }

        fun save(context: Context, style: PillStyle) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_VERTICAL, style.vertical)
                putFloat(KEY_SCALE, style.scale)
                remove(KEY_OLD_SIZE)
                putBoolean(KEY_END_FIRST, style.endFirst)
                putFloat(KEY_OPACITY, style.opacity)
            }
        }
    }
}
