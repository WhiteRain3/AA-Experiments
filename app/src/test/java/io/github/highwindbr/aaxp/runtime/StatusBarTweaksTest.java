package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Test;

public class StatusBarTweaksTest {
    @Test public void roundTrip() {
        StatusBarTweaks.Config config = new StatusBarTweaks.Config(
                new LinkedHashSet<>(List.of("clock", "battery_level")), 0xFFFF2D95, 0x80FFFFFF, true);
        String text = StatusBarTweaks.encode(config);
        assertEquals("hide=clock,battery_level;clock=FFFF2D95;icons=80FFFFFF;plate=off", text);
        StatusBarTweaks.Config parsed = StatusBarTweaks.parse(text);
        assertEquals(config.hide, parsed.hide);
        assertEquals(config.clockColor, parsed.clockColor);
        assertEquals(config.iconColor, parsed.iconColor);
        assertTrue(parsed.noPlate);
    }

    @Test public void emptyOrMalformedIsNull() {
        assertNull(StatusBarTweaks.parse(null));
        assertNull(StatusBarTweaks.parse(""));
        assertNull(StatusBarTweaks.parse("hide=unknown_view"));
        assertNull(StatusBarTweaks.parse("clock=12345"));
        assertNull(StatusBarTweaks.parse("clock=GGGGGGGG"));
        assertNull(StatusBarTweaks.parse("plate=on"));
        assertNull(StatusBarTweaks.parse("nonsense"));
        assertNull(StatusBarTweaks.encode(new StatusBarTweaks.Config(new LinkedHashSet<>(), null, null, false)));
    }

    @Test public void singleParts() {
        assertTrue(StatusBarTweaks.parse("plate=off").noPlate);
        assertEquals(Integer.valueOf(0xFF00FF00), StatusBarTweaks.parse("icons=FF00FF00").iconColor);
        assertEquals(1, StatusBarTweaks.parse("hide=dnd_icon").hide.size());
    }

    @Test public void recognisesStatusBarLayouts() {
        assertTrue(StatusBarTweaks.isStatusBarLayout("rail_statusbar"));
        assertTrue(StatusBarTweaks.isStatusBarLayout("vertical_rail_statusbar"));
        assertFalse(StatusBarTweaks.isStatusBarLayout("adu_status_bar_view"));
        assertFalse(StatusBarTweaks.isStatusBarLayout(null));
    }
}
