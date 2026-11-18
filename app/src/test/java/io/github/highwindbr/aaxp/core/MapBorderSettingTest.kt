package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.MapBorder
import org.junit.Assert.*
import org.junit.Test

class MapBorderSettingTest {
    @Test fun readsAPlainNumberOrNothing() {
        assertEquals(8, MapBorderSetting.read("8")); assertEquals(0, MapBorderSetting.read(" 0 "))
        assertNull(MapBorderSetting.read(null)); assertNull(MapBorderSetting.read("")); assertNull(MapBorderSetting.read("wide"))
        assertNull(MapBorderSetting.read("17")); assertNull(MapBorderSetting.read("-1"))
    }

    @Test fun writesAClampedNumberOrRemoves() {
        assertEquals("8", MapBorderSetting.write(8)); assertEquals("16", MapBorderSetting.write(99)); assertEquals("0", MapBorderSetting.write(-4))
        assertNull(MapBorderSetting.write(null))
    }

    @Test fun sliderStartsAtAndroidAutosOwnWidth() {
        assertEquals(MapBorder.OWN_FOCUSED, MapBorderSetting.sliderStart(null)); assertEquals(3, MapBorderSetting.sliderStart(3))
    }

    @Test fun labelsAreDefaultOrDp() {
        assertEquals("Default", MapBorderSetting.label(null)); assertEquals("12 dp", MapBorderSetting.label(12))
    }

    @Test fun theDeadFlagKeyIsTheOldCoolwalkFlag() = assertEquals("Coolwalk__nav_app_border_width", MapBorderSetting.DEAD_FLAG_KEY)
}
