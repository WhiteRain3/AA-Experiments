package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.GlyphIcons
import io.github.highwindbr.aaxp.runtime.ResourceColors
import io.github.highwindbr.aaxp.runtime.StatusBarTweaks
import io.github.highwindbr.aaxp.runtime.ThemeColors
import org.junit.Assert.*
import org.junit.Test

class LookPlanTest {
    @Test fun everyBuiltInLookIsComplete() {
        Themes.BUILT_IN.forEach { look ->
            val plan = LookPlan.of(look)
            assertNotNull(look.name, plan.themeColors)
            assertEquals(look.roles, ThemeColors.parse(plan.themeColors))
            assertNotNull(look.name, GlyphIcons.parseColors(plan.glyphColors))
            assertEquals(look.name, ResourceColors.groups().toSet(), ResourceColors.parse(plan.resourceColors)!!.keys)
            assertNotNull(look.name, StatusBarTweaks.parse(plan.statusBar))
            assertTrue(look.name, plan.wallpaperDim in 0..80)
            assertTrue(look.name, plan.wallpaperBlur in 0..BlurLevel.MAX)
            assertTrue(look.name, look.roles.keys.all { it in Themes.ALL_ROLES })
        }
    }

    @Test fun namesAreUnique() = assertEquals(Themes.BUILT_IN.size, Themes.BUILT_IN.map { it.name }.toSet().size)

    @Test fun planWritesExactlyTheLooksSettings() {
        val look = Themes.BUILT_IN.first { it.name == "Motorsport" }
        val plan = LookPlan.of(look)
        assertEquals("FFFFFF,00000000", plan.glyphColors)
        assertEquals("clock=FFFFFFFF;icons=FFC4C8D0;plate=off", plan.statusBar)
        assertEquals("text=FFF2F2F2;launcher_bg=FF0B0B0C;map_focus=FFE10600;focus_ring=FFE10600", plan.resourceColors)
        assertEquals(35, plan.wallpaperDim); assertEquals(4, plan.wallpaperBlur)
    }

    @Test fun aLookWithoutExtrasSwitchesThemOff() {
        val plan = LookPlan.of(CarTheme("Mine", "", mapOf("Primary" to 0xFF112233.toInt())))
        assertNull(plan.glyphColors); assertNull(plan.statusBar); assertNull(plan.resourceColors)
        assertNull(plan.wallpaperDim); assertNull(plan.wallpaperBlur)
        assertEquals("Primary=FF112233", plan.themeColors)
    }

    @Test fun wallpaperValuesAreClamped() {
        val plan = LookPlan.of(CarTheme("x", "", emptyMap(), wallpaperDim = 500, wallpaperBlur = -3))
        assertEquals(80, plan.wallpaperDim); assertEquals(0, plan.wallpaperBlur)
    }

    @Test fun extendedLooksSurviveEncodingAndOldFilesStillRead() {
        val look = Themes.BUILT_IN.first { it.name == "Forest" }.copy(builtIn = false)
        val back = Themes.decode(Themes.encode(listOf(look))).single()
        assertEquals(look.resourceColors, back.resourceColors); assertEquals(look.statusBar, back.statusBar)
        assertEquals(look.wallpaperDim, back.wallpaperDim); assertEquals(look.wallpaperBlur, back.wallpaperBlur)
        val old = """{"schemaVersion":1,"themes":{"Old":{"description":"","roles":{"Primary":"FF112233"}}}}"""
        val oldLook = Themes.decode(old).single()
        assertTrue(oldLook.resourceColors.isEmpty()); assertNull(oldLook.statusBar); assertNull(oldLook.wallpaperDim)
    }
}
