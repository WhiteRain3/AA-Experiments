package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.MapBorder

/**
 * The map's focus border width as the app shows it. The module setting is [MapBorder.KEY], a
 * plain number of dp; the old Coolwalk flag is dead in Android Auto 18.0 and is only ever removed.
 */
object MapBorderSetting {
    /** The flag this app used before the module had a setting of its own; it is never read by Android Auto 18.0. */
    const val DEAD_FLAG_KEY = "Coolwalk__nav_app_border_width"

    /** The stored width, or null when none is set (Android Auto's own). */
    fun read(text: String?): Int? = MapBorder.parse(text)

    /** The text to store for [width]; null removes the setting. Out of range is clamped. */
    fun write(width: Int?): String? = MapBorder.encode(width)

    /** Where the slider starts when nothing is set: Android Auto's own width. */
    fun sliderStart(width: Int?): Int = width ?: MapBorder.OWN_FOCUSED

    fun label(width: Int?): String = if (width == null) "Default" else "$width dp"
}
