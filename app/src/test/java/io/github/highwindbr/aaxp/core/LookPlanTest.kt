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

    @Test fun aStyleKeepsTheOwnersStatusBarSetup() {
        val mine = "hide=signal_icon,battery_level;clock=FF111111;battery=percent;battery_color=FF222222;battery_size=70;clock_size=130;clock_bold=on"
        val merged = StatusBarTweaks.parse(LookPlan.of(Themes.BUILT_IN.first { it.name == "Motorsport" }, mine).statusBar)!!
        assertEquals(setOf("signal_icon", "battery_level"), merged.hide)
        assertTrue(merged.batteryPercent); assertEquals(70, merged.batterySize); assertEquals(0xFF222222.toInt(), merged.batteryColor)
        assertEquals(130, merged.clockSize); assertTrue(merged.clockBold)
        // Only the colours and the background come from the style.
        assertEquals(0xFFFFFFFF.toInt(), merged.clockColor); assertEquals(0xFFC4C8D0.toInt(), merged.iconColor); assertTrue(merged.noPlate)
    }

    @Test fun aLookWithoutStatusBarLeavesTheCurrentOneAlone() {
        assertEquals("hide=clock", LookPlan.of(CarTheme("x", "", emptyMap()), "hide=clock").statusBar)
    }

    @Test fun railLookAndGlyphSizeAreOnlyChangedWhenTheStyleCarriesThem() {
        val plain = LookPlan.of(Themes.BUILT_IN.first())
        assertNull(plain.railLook); assertNull(plain.glyphSize)
        val look = CarTheme("x", "", emptyMap(), railLook = "strength=30", glyphSize = 400)
        val plan = LookPlan.of(look)
        assertEquals("strength=30", plan.railLook); assertEquals(150, plan.glyphSize)
        val back = Themes.decode(Themes.encode(listOf(look.copy(glyphSize = 120)))).single()
        assertEquals("strength=30", back.railLook); assertEquals(120, back.glyphSize)
        val bad = Themes.decode("{\"schemaVersion\":1,\"themes\":{\"B\":{\"roles\":{},\"railLook\":\"nonsense\",\"glyphSize\":9}}}").single()
        assertNull(bad.railLook); assertNull(bad.glyphSize)
    }

    @Test fun stylesNeverCarryPerAppIconChoices() {
        // Applying a style writes exactly the parts of LookPlan; none of them is the per-app choices, so the owner's stay.
        val names = (LookPlan::class.java.declaredFields + CarTheme::class.java.declaredFields).map { it.name.lowercase() }
        assertTrue(names.none { "perapp" in it || "per_app" in it })
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
