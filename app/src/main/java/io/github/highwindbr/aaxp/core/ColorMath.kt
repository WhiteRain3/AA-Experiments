package io.github.highwindbr.aaxp.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Plain ARGB colour arithmetic, free of Android classes so it can be unit-tested. */
object ColorMath {
    fun alpha(color: Int) = color ushr 24
    fun red(color: Int) = (color shr 16) and 0xFF
    fun green(color: Int) = (color shr 8) and 0xFF
    fun blue(color: Int) = color and 0xFF
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** Hue 0-360, saturation and value 0-1. */
    fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Int = 255): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f); val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** Returns hue (0-360), saturation (0-1) and value (0-1). */
    fun colorToHsv(color: Int): FloatArray {
        val r = red(color) / 255f; val g = green(color) / 255f; val b = blue(color) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
    }

    /** Returns hue (0-360), saturation (0-1) and lightness (0-1). */
    fun colorToHsl(color: Int): FloatArray {
        val r = red(color) / 255f; val g = green(color) / 255f; val b = blue(color) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
        val lightness = (max + min) / 2f
        val hue = colorToHsv(color)[0]
        val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness)
    }

    fun hslToColor(hue: Float, saturation: Float, lightness: Float, alpha: Int = 255): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f); val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = l - c / 2f
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** "RRGGBB" for opaque colours, otherwise "AARRGGBB". */
    fun toHex(color: Int): String =
        if (alpha(color) == 255) "%06X".format(color and 0xFFFFFF) else "%08X".format(color)

    /** Accepts RRGGBB or AARRGGBB with an optional '#'. */
    fun parseHex(text: String): Int? = WallpaperGeometry.parseColor(text)

    /** Black or white, whichever reads better on [color]. */
    fun readableOn(color: Int): Int {
        val luminance = (299 * red(color) + 587 * green(color) + 114 * blue(color)) / 1000
        return if (luminance > 150) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}
