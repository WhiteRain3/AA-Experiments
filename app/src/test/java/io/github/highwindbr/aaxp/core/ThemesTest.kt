package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.GlyphIcons
import io.github.highwindbr.aaxp.runtime.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {
    @Test fun hsvAndHslRoundTrip() {
        for (color in listOf(0xFFFF2D95.toInt(), 0xFF00B4D8.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())) {
            val hsv = ColorMath.colorToHsv(color)
            assertEquals(color, ColorMath.hsvToColor(hsv[0], hsv[1], hsv[2]))
            val hsl = ColorMath.colorToHsl(color)
            val back = ColorMath.hslToColor(hsl[0], hsl[1], hsl[2])
            assertTrue(kotlin.math.abs(ColorMath.red(back) - ColorMath.red(color)) <= 1)
            assertTrue(kotlin.math.abs(ColorMath.green(back) - ColorMath.green(color)) <= 1)
            assertTrue(kotlin.math.abs(ColorMath.blue(back) - ColorMath.blue(color)) <= 1)
        }
    }

    @Test fun hexFormatting() {
        assertEquals("FF2D95", ColorMath.toHex(0xFFFF2D95.toInt()))
        assertEquals("66FF2D95", ColorMath.toHex(0x66FF2D95))
        assertEquals(0x66FF2D95, ColorMath.parseHex("#66FF2D95"))
        assertEquals(0xFF000000.toInt(), ColorMath.readableOn(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.readableOn(0xFF101010.toInt()))
    }

    @Test fun generatedDarkThemeHasDarkBackgroundAndLightText() {
        val roles = Themes.generate(0xFFFF2D95.toInt(), dark = true)
        assertTrue(ColorMath.colorToHsl(roles.getValue("Background"))[2] < 0.15f)
        assertTrue(ColorMath.colorToHsl(roles.getValue("OnSurface"))[2] > 0.85f)
        assertTrue(roles.keys.all { it in Themes.ALL_ROLES })
        val light = Themes.generate(0xFFFF2D95.toInt(), dark = false)
        assertTrue(ColorMath.colorToHsl(light.getValue("Background"))[2] > 0.85f)
    }

    @Test fun builtInThemesAreStorableAsIs() {
        assertTrue(Themes.BUILT_IN.size >= 5)
        Themes.BUILT_IN.forEach { theme ->
            assertTrue("${theme.name} uses only known roles", theme.roles.keys.all { it in Themes.ALL_ROLES })
            // What the module will read back must be exactly what the theme defines.
            assertEquals(theme.roles, ThemeColors.parse(ThemeColors.encode(theme.roles)))
            assertNotNull("${theme.name} glyph colours", GlyphIcons.parseColors(theme.glyphColors))
        }
        assertEquals(Themes.BUILT_IN.size, Themes.BUILT_IN.map { it.name }.toSet().size)
    }

    @Test fun themesFileRoundTripAndUnknownRolesAreDropped() {
        val theme = CarTheme("Mine", "test", mapOf("Primary" to 0xFFFF2D95.toInt(), "OutlineVariant" to 0), "FFFFFF,00000000")
        assertEquals(listOf(theme), Themes.decode(Themes.encode(listOf(theme))))
        val decoded = Themes.decode("""{"schemaVersion":1,"themes":{"X":{"roles":{"Primary":"FF0000","NotARole":"00FF00","Surface":"zz"}}}}""")
        assertEquals(mapOf("Primary" to 0xFFFF0000.toInt()), decoded.single().roles)
    }

    @Test fun everyEditorRoleIsAValidRoleName() {
        assertEquals(46, Themes.ALL_ROLES.size)
        assertEquals(Themes.ALL_ROLES.size, Themes.ALL_ROLES.toSet().size)
        assertTrue(Themes.ALL_ROLES.all { it.matches(Regex("[A-Za-z]+")) })
    }
}
