package io.github.highwindbr.aaxp

import io.github.highwindbr.aaxp.core.BlurLevel
import io.github.highwindbr.aaxp.core.CarScreen
import io.github.highwindbr.aaxp.core.LookBundle
import io.github.highwindbr.aaxp.core.LookBundles
import io.github.highwindbr.aaxp.core.RailSide
import io.github.highwindbr.aaxp.core.WallpaperCoverage
import io.github.highwindbr.aaxp.core.WallpaperPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wallpaper editor's settings must come back exactly from a look file. */
class WallpaperSettingsRoundTripTest {
    private val saved = CarWallpaperStudio.Saved(
        CarScreen(1200, 723, RailSide.LEFT, 107), WallpaperCoverage.WHOLE_SCREEN, WallpaperPlacement(1.5f, 12f, -7f, 1),
        separatorThickness = 3, separatorColor = "#D7000F", dim = 25, blurLevel = BlurLevel.MAX / 4 * 1 + 6, railShiftX = 2, railShiftY = -3)

    @Test fun settingsSurviveTheirJson() {
        assertEquals(saved, CarWallpaperStudio.settingsFrom(CarWallpaperStudio.settingsJson(saved)))
    }

    @Test fun settingsSurviveAWholeLookFile() {
        val bundle = LookBundle(wallpaperSettings = CarWallpaperStudio.settingsJson(saved), wallpaperImage = byteArrayOf(1, 2, 3))
        val back = LookBundles.decode(LookBundles.encode(bundle))
        assertEquals(saved, CarWallpaperStudio.settingsFrom(back.wallpaperSettings))
    }

    @Test fun darkenAndBlurOfEveryBuiltInStyleSurvive() {
        io.github.highwindbr.aaxp.core.Themes.BUILT_IN.forEach { look ->
            val s = saved.copy(dim = look.wallpaperDim!!, blurLevel = look.wallpaperBlur!!)
            val bundle = LookBundle(wallpaperSettings = CarWallpaperStudio.settingsJson(s), wallpaperImage = byteArrayOf(1))
            val back = CarWallpaperStudio.settingsFrom(LookBundles.decode(LookBundles.encode(bundle)).wallpaperSettings)
            assertEquals(look.name, s.dim, back.dim); assertEquals(look.name, s.blurLevel, back.blurLevel)
        }
    }

    @Test fun anOldLookFileStillReadsItsBlurAsFourTimesTheOldLevel() {
        val old = org.json.JSONObject().put("dim", 20).put("blur", 3)
        val read = CarWallpaperStudio.settingsFrom(old)
        assertEquals(20, read.dim); assertEquals(12, read.blurLevel)
    }
}
