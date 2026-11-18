package io.github.highwindbr.aaxp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusBarStyleTest {
    private val owners = "hide=dnd_icon;clock=FFFFFFFF;battery=percent;battery_color=FF00FFFF;battery_size=110;" +
        "battery_level=80,20;battery_mid_color=FFFFFF00;battery_charge_color=FF0000FF;clock_size=120;clock_bold=on;" +
        "plate_color=80102030;plate_radius=12;icon_size=125"

    @Test fun styleChangesOnlyClockAndIconColours() {
        val merged = StatusBarStyle.merge(owners, "clock=FFFF2D95;icons=FF00FF00")
        assertEquals("hide=dnd_icon;clock=FFFF2D95;icons=FF00FF00;battery=percent;battery_color=FF00FFFF;battery_size=110;" +
            "battery_level=80,20;battery_mid_color=FFFFFF00;battery_charge_color=FF0000FF;clock_size=120;clock_bold=on;" +
            "plate_color=80102030;plate_radius=12;icon_size=125", merged)
    }

    @Test fun styleWithoutStatusBarChangesLeavesEverything() {
        assertEquals(owners, StatusBarStyle.merge(owners, null))
        assertNull(StatusBarStyle.merge(null, null))
    }

    @Test fun styleOnAnUntouchedStatusBar() {
        assertEquals("clock=FFFF2D95;plate=off", StatusBarStyle.merge(null, "clock=FFFF2D95;plate=off"))
    }
}
