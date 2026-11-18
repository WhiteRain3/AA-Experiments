package io.github.highwindbr.aaxp.core

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookBundleTest {
    @Test fun roundTripsEveryPart() {
        val themes = Themes.encode(listOf(CarTheme("Mine", "", mapOf("Primary" to 0xFFFF0000.toInt()), "FFFFFF,00000000")))
        val image = byteArrayOf(1, 2, 3, -4)
        val text = LookBundles.encode(LookBundle("Primary=FFFF0000", "FFFFFF,00000000", "hide=clock;plate=off",
            themes, null, JSONObject().put("dim", 20), image))
        val bundle = LookBundles.decode(text)
        assertEquals("Primary=FFFF0000", bundle.themeColors)
        assertEquals("FFFFFF,00000000", bundle.glyphColors)
        assertEquals("hide=clock;plate=off", bundle.statusBar)
        assertEquals(1, Themes.decode(bundle.themesJson!!).size)
        assertNull(bundle.presetsJson)
        assertEquals(20, bundle.wallpaperSettings!!.getInt("dim"))
        assertArrayEquals(image, bundle.wallpaperImage)
    }

    @Test fun carriesTheExtraResourceColours() {
        val bundle = LookBundles.decode(LookBundles.encode(LookBundle(resourceColors = "text=FFFFFFFF;map_focus=FFFF2D95")))
        assertEquals("text=FFFFFFFF;map_focus=FFFF2D95", bundle.resourceColors)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(resourceColors = "nonsense"))) }.isFailure)
        assertNull(LookBundles.decode(LookBundles.encode(LookBundle())).resourceColors)
    }

    @Test fun carriesTheLauncherSetting() {
        val bundle = LookBundles.decode(LookBundles.encode(LookBundle(launcherConfig = "names=off")))
        assertEquals("names=off", bundle.launcherConfig)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(launcherConfig = "nonsense"))) }.isFailure)
        assertNull(LookBundles.decode(LookBundles.encode(LookBundle())).launcherConfig)
    }

    @Test fun carriesTheRailLookAndGlyphSize() {
        val bundle = LookBundles.decode(LookBundles.encode(LookBundle(railLook = "color=FF112233;strength=40", glyphSize = "120")))
        assertEquals("color=FF112233;strength=40", bundle.railLook); assertEquals("120", bundle.glyphSize)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(railLook = "nonsense"))) }.isFailure)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(glyphSize = "900"))) }.isFailure)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(glyphSize = "big"))) }.isFailure)
        val none = LookBundles.decode(LookBundles.encode(LookBundle()))
        assertNull(none.railLook); assertNull(none.glyphSize)
    }

    @Test fun carriesThePerAppIconChoices() {
        val text = "com.example.maps=glyph;com.example.radio=FF0000,00000000;com.example.news=original"
        assertEquals(text, LookBundles.decode(LookBundles.encode(LookBundle(perAppIcons = text))).perAppIcons)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(perAppIcons = "nonsense"))) }.isFailure)
        assertNull(LookBundles.decode(LookBundles.encode(LookBundle())).perAppIcons)
    }

    @Test fun carriesTheMapBorderWidth() {
        assertEquals("8", LookBundles.decode(LookBundles.encode(LookBundle(mapBorder = "8"))).mapBorder)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(mapBorder = "wide"))) }.isFailure)
        assertTrue(runCatching { LookBundles.decode(LookBundles.encode(LookBundle(mapBorder = "40"))) }.isFailure)
        assertNull(LookBundles.decode(LookBundles.encode(LookBundle())).mapBorder)
    }

    @Test fun emptyBundleIsValidAndHasNoParts() {
        val bundle = LookBundles.decode(LookBundles.encode(LookBundle()))
        assertNull(bundle.themeColors); assertNull(bundle.glyphColors); assertNull(bundle.statusBar)
        assertNull(bundle.wallpaperImage); assertNull(bundle.wallpaperSettings)
    }

    @Test fun wallpaperSettingsWithoutAnImageAreDropped() {
        val bundle = LookBundles.decode(LookBundles.encode(LookBundle(wallpaperSettings = JSONObject().put("dim", 5))))
        assertNull(bundle.wallpaperSettings)
    }

    @Test fun rejectsOtherFilesAndBadParts() {
        fun fails(text: String) = assertTrue(runCatching { LookBundles.decode(text) }.isFailure)
        fails("{}")
        fails("not json")
        fails(Themes.encode(emptyList()))
        val base = JSONObject().put("schemaVersion", 1).put("kind", "aa-experiments-look")
        fails(JSONObject(base.toString()).put("glyphColors", "nope").toString())
        fails(JSONObject(base.toString()).put("statusBar", "hide=everything").toString())
        fails(JSONObject(base.toString()).put("themes", JSONObject().put("schemaVersion", 9)).toString())
        fails(JSONObject(base.toString()).put("wallpaperImage", "***").toString())
    }
}
