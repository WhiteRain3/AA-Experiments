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
