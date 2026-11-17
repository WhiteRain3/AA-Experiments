package io.github.highwindbr.aaxp.core

import org.json.JSONObject

/**
 * A look for the car screen: colours for Android Auto's palette roles and, optionally, the
 * glyph icon colours ("glyph,background" as stored by the glyph icon feature).
 */
data class CarTheme(
    val name: String,
    val description: String,
    val roles: Map<String, Int>,
    val glyphColors: String? = null,
    val builtIn: Boolean = false
)

object Themes {
    /** Palette roles in the order and grouping shown in the editor. */
    val ROLE_GROUPS: List<Pair<String, List<String>>> = listOf(
        "Accent" to listOf("Primary", "OnPrimary", "PrimaryContainer", "OnPrimaryContainer"),
        "Second accent" to listOf("Secondary", "OnSecondary", "SecondaryContainer", "OnSecondaryContainer"),
        "Third accent" to listOf("Tertiary", "OnTertiary", "TertiaryContainer", "OnTertiaryContainer"),
        "Backgrounds" to listOf("Background", "Surface", "SurfaceDim", "SurfaceHigh", "SurfaceVariant",
            "SurfaceContainerLowest", "SurfaceContainerLow", "SurfaceContainer", "SurfaceContainerHigh",
            "SurfaceContainerHighest", "InverseSurface"),
        "Text and icons" to listOf("OnSurface", "OnSurfaceVariant", "InverseOnSurface", "TextPrimary", "TextSecondary"),
        "Lines and plates" to listOf("Outline", "OutlineVariant"),
        "Status colours" to listOf("Red", "OnRed", "RedContainer", "OnRedContainer", "Green", "OnGreen",
            "GreenContainer", "OnGreenContainer", "Blue", "OnBlue", "BlueContainer", "OnBlueContainer",
            "Yellow", "OnYellow", "YellowContainer", "OnYellowContainer")
    )

    val ALL_ROLES: List<String> = ROLE_GROUPS.flatMap { it.second }

    /** What is known about a role on screen; empty when it has not been identified yet. */
    fun hint(role: String): String = when (role) {
        "OutlineVariant" -> "Plate behind the rail's app icons"
        "Primary" -> "Main accent"
        "Background", "Surface" -> "Main background"
        "OnSurface", "TextPrimary" -> "Main text"
        "OnSurfaceVariant", "TextSecondary" -> "Secondary text"
        else -> ""
    }

    /**
     * Derives a full set of role colours from one seed colour. Accents keep the seed's hue at
     * fixed lightness steps; backgrounds and text use a low-saturation tint of [neutralHue].
     */
    fun generate(
        seed: Int, dark: Boolean = true,
        neutralHue: Float = ColorMath.colorToHsl(seed)[0], neutralSaturation: Float = 0.10f
    ): Map<String, Int> {
        val (hue, rawSaturation, _) = ColorMath.colorToHsl(seed).let { Triple(it[0], it[1], it[2]) }
        val saturation = rawSaturation.coerceAtLeast(0.05f)
        fun accent(h: Float, s: Float, l: Float) = ColorMath.hslToColor(h, s, if (dark) l else 1f - l)
        fun neutral(l: Float) = ColorMath.hslToColor(neutralHue, neutralSaturation, if (dark) l else 1f - l)
        val result = LinkedHashMap<String, Int>()
        fun family(prefix: String, h: Float, s: Float) {
            result[prefix] = accent(h, s, 0.70f)
            result["On$prefix"] = accent(h, s, 0.14f)
            result["${prefix}Container"] = accent(h, s, 0.30f)
            result["On${prefix}Container"] = accent(h, s, 0.88f)
        }
        family("Primary", hue, saturation)
        family("Secondary", hue, saturation * 0.45f)
        family("Tertiary", hue + 60f, saturation * 0.8f)
        result["Background"] = neutral(0.06f)
        result["Surface"] = neutral(0.06f)
        result["SurfaceDim"] = neutral(0.05f)
        result["SurfaceHigh"] = neutral(0.16f)
        result["SurfaceVariant"] = neutral(0.24f)
        result["SurfaceContainerLowest"] = neutral(0.04f)
        result["SurfaceContainerLow"] = neutral(0.09f)
        result["SurfaceContainer"] = neutral(0.11f)
        result["SurfaceContainerHigh"] = neutral(0.15f)
        result["SurfaceContainerHighest"] = neutral(0.19f)
        result["InverseSurface"] = neutral(0.90f)
        result["OnSurface"] = neutral(0.92f)
        result["OnSurfaceVariant"] = neutral(0.78f)
        result["InverseOnSurface"] = neutral(0.18f)
        result["TextPrimary"] = neutral(0.95f)
        result["TextSecondary"] = neutral(0.76f)
        result["Outline"] = neutral(0.58f)
        result["OutlineVariant"] = neutral(0.28f)
        return result
    }

    private fun c(hex: Long): Int = hex.toInt()

    /** Looks shipped with the app. They are original palettes; no third-party artwork is included. */
    val BUILT_IN: List<CarTheme> = listOf(
        CarTheme("Neon Sunset", "Hot pink and orange on deep purple, with teal highlights",
            generate(c(0xFFFF2D95), neutralHue = 268f, neutralSaturation = 0.42f) + mapOf(
                "Secondary" to c(0xFFFF8A3D), "SecondaryContainer" to c(0xFF7A3A12), "OnSecondaryContainer" to c(0xFFFFD9BF),
                "Tertiary" to c(0xFF2DE2E6), "TertiaryContainer" to c(0xFF0B5C5E), "OnTertiaryContainer" to c(0xFFBDF7F8),
                "OutlineVariant" to c(0x66FF2D95)),
            glyphColors = "FF8AC6,00000000", builtIn = true),
        CarTheme("Motorsport", "Racing red on near-black with silver accents",
            generate(c(0xFFE10600), neutralSaturation = 0.03f) + mapOf(
                "Secondary" to c(0xFFC4C8D0), "SecondaryContainer" to c(0xFF3A3D42), "OnSecondaryContainer" to c(0xFFE6E8EC),
                "Tertiary" to c(0xFFFFFFFF), "OutlineVariant" to c(0x80E10600)),
            glyphColors = "FFFFFF,00000000", builtIn = true),
        CarTheme("Minimal Mono", "Black, white and grey, nothing else",
            generate(c(0xFFFFFFFF), neutralSaturation = 0f) + mapOf(
                "Primary" to c(0xFFFFFFFF), "OnPrimary" to c(0xFF000000), "PrimaryContainer" to c(0xFF2A2A2A),
                "OnPrimaryContainer" to c(0xFFFFFFFF), "Secondary" to c(0xFFBDBDBD), "Tertiary" to c(0xFF8E8E8E),
                "Background" to c(0xFF000000), "Surface" to c(0xFF000000), "OutlineVariant" to c(0x00000000)),
            glyphColors = "FFFFFF,00000000", builtIn = true),
        CarTheme("Deep Ocean", "Cyan and aqua on dark navy",
            generate(c(0xFF00B4D8), neutralHue = 212f, neutralSaturation = 0.38f) + mapOf(
                "OutlineVariant" to c(0x6600B4D8)),
            glyphColors = "8EE9FF,00000000", builtIn = true),
        CarTheme("Amber Night", "Warm amber on black, like classic instrument lighting",
            generate(c(0xFFFFB000), neutralHue = 38f, neutralSaturation = 0.12f) + mapOf(
                "Background" to c(0xFF050402), "Surface" to c(0xFF050402), "OutlineVariant" to c(0x55FFB000)),
            glyphColors = "FFB000,00000000", builtIn = true),
        CarTheme("Forest", "Soft green on dark moss",
            generate(c(0xFF5BD68A), neutralHue = 140f, neutralSaturation = 0.22f) + mapOf(
                "OutlineVariant" to c(0x555BD68A)),
            glyphColors = "A8F0C2,00000000", builtIn = true)
    )

    fun encode(themes: Collection<CarTheme>): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("themes", JSONObject().apply {
            themes.forEach { theme ->
                put(theme.name, JSONObject().apply {
                    put("description", theme.description)
                    theme.glyphColors?.let { put("glyphColors", it) }
                    put("roles", JSONObject().apply {
                        theme.roles.forEach { (role, color) -> put(role, "%08X".format(color)) }
                    })
                })
            }
        })
    }.toString(1)

    /** Parses themes, keeping only known roles with readable colours. Throws on an unusable file. */
    fun decode(text: String): List<CarTheme> {
        val root = JSONObject(text)
        require(root.optInt("schemaVersion") == 1) { "Unsupported themes file" }
        val themes = root.getJSONObject("themes")
        return buildList {
            themes.keys().forEach { rawName ->
                val name = Presets.cleanName(rawName) ?: return@forEach
                val entry = themes.optJSONObject(rawName) ?: return@forEach
                val roles = entry.optJSONObject("roles") ?: return@forEach
                val parsed = LinkedHashMap<String, Int>()
                roles.keys().forEach { role ->
                    val color = ColorMath.parseHex(roles.optString(role))
                    if (role in ALL_ROLES && color != null) parsed[role] = color
                }
                val glyph = entry.optString("glyphColors").takeIf {
                    it.isNotEmpty() && io.github.highwindbr.aaxp.runtime.GlyphIcons.parseColors(it) != null
                }
                add(CarTheme(name, entry.optString("description"), parsed, glyph))
            }
        }.take(Presets.MAX_PRESETS)
    }
}
