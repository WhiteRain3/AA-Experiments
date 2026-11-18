package io.github.highwindbr.aaxp.core

/** How a colour is written on a row: "Default", "Transparent" or "#RRGGBB" (with alpha only when it is not opaque). */
object ColourLabel {
    fun of(colour: Int?): String = when {
        colour == null -> "Default"
        ColorMath.alpha(colour) == 0 -> "Transparent"
        else -> "#" + ColorMath.toHex(colour)
    }
}
