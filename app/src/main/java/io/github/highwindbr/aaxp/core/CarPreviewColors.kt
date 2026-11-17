package io.github.highwindbr.aaxp.core

/**
 * The colours the theme editor's car screen drawing needs, worked out from the theme's colour
 * slots. Slots with no colour get a neutral dark stand-in (Android Auto's own colour is not
 * known exactly). Things the slots do not paint are fixed, as seen on the car.
 */
data class CarPreviewColors(
    val outlineVariant: Int, val surface: Int, val surfaceVariant: Int, val surfaceHigh: Int,
    val onSurface: Int, val onSurfaceVariant: Int, val textPrimary: Int,
    val railIcon: Int, val railIconBackground: Int,
    val usesApproximation: Boolean
) {
    companion object {
        const val RAIL_BACKGROUND = 0xFF000000.toInt()
        const val RAIL_FIXED_ICON = 0xFFF2F0F0.toInt()
        const val MAP_BACKGROUND = 0xFF14301F.toInt()
        const val MEDIA_BACKGROUND = 0xFF3A3839.toInt()
        const val PLAY_BUTTON = 0xFFDDD1DD.toInt()
        const val PLACEHOLDER_ICON = 0xFF808080.toInt()

        private val DEFAULTS = mapOf(
            "OutlineVariant" to 0xFF4A4A4A.toInt(), "Surface" to 0xFF2A2B2C.toInt(),
            "SurfaceVariant" to 0xFF3C3D3E.toInt(), "SurfaceHigh" to 0xFF343536.toInt(),
            "OnSurface" to 0xFFE6E6E6.toInt(), "OnSurfaceVariant" to 0xFFB8B8B8.toInt(),
            "TextPrimary" to 0xFFFFFFFF.toInt()
        )

        /** [glyphColors] is the "glyph,background" text of the icon feature, or null when it is off. */
        fun resolve(roles: Map<String, Int>, glyphColors: String?): CarPreviewColors {
            fun c(role: String) = roles[role] ?: DEFAULTS.getValue(role)
            val glyph = glyphColors?.let { io.github.highwindbr.aaxp.runtime.GlyphIcons.parseColors(it) }
            return CarPreviewColors(
                c("OutlineVariant"), c("Surface"), c("SurfaceVariant"), c("SurfaceHigh"),
                c("OnSurface"), c("OnSurfaceVariant"), c("TextPrimary"),
                glyph?.get(0) ?: PLACEHOLDER_ICON, glyph?.get(1) ?: 0xFF242424.toInt(),
                usesApproximation = Themes.CONFIRMED_ROLES.any { it !in roles }
            )
        }
    }
}
