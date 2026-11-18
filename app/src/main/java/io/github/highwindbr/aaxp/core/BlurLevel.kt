package io.github.highwindbr.aaxp.core

/**
 * The wallpaper blur setting. Stored as a fine level of 0..[MAX]; before wr28 it was stored as
 * 0..10, and an old value is read as four times itself so the picture looks the same.
 */
object BlurLevel {
    const val MAX = 40
    private const val OLD_MAX = 10

    /** The level to use given the new stored value, if any, and the old one, if any. */
    fun fromStored(fine: Int?, old: Int?): Int = when {
        fine != null -> fine.coerceIn(0, MAX)
        old != null -> (old.coerceIn(0, OLD_MAX) * 4)
        else -> 0
    }

    /** Blur radius in pixels for an image whose longest edge is [longestEdge] pixels. */
    fun radius(level: Int, longestEdge: Int): Float = level.coerceIn(0, MAX) * longestEdge / 800f

    fun label(level: Int): String = if (level <= 0) "Blur: off" else "Blur: ${level * 100 / MAX}%"
}
