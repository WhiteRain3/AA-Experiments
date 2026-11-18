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

    @Test public void batteryPercent() {
        StatusBarTweaks.Config config = StatusBarTweaks.parse("hide=battery_level;battery=percent");
        assertTrue(config.batteryPercent);
        assertEquals("hide=battery_level;battery=percent", StatusBarTweaks.encode(config));
        assertTrue(StatusBarTweaks.parse("battery=percent").batteryPercent);
        assertNull(StatusBarTweaks.parse("battery=icon"));
        assertFalse(StatusBarTweaks.parse("plate=off").batteryPercent);
        assertEquals("74%", StatusBarTweaks.percentText(74, 100));
        assertEquals("50%", StatusBarTweaks.percentText(100, 200));
        assertEquals("100%", StatusBarTweaks.percentText(101, 100));
        assertEquals("", StatusBarTweaks.percentText(-1, 100));
        assertEquals("", StatusBarTweaks.percentText(50, 0));
    }

    @Test public void batteryNumberColourAndSize() {
        StatusBarTweaks.Config config = StatusBarTweaks.parse("battery=percent;battery_color=FFFF2D95;battery_size=110");
        assertEquals(Integer.valueOf(0xFFFF2D95), config.batteryColor);
        assertEquals(110, config.batterySize);
        assertEquals("battery=percent;battery_color=FFFF2D95;battery_size=110", StatusBarTweaks.encode(config));
        // Defaults: follows the clock, default size, and neither is written out.
        StatusBarTweaks.Config plain = StatusBarTweaks.parse("battery=percent");
        assertNull(plain.batteryColor);
        assertEquals(StatusBarTweaks.DEFAULT_BATTERY_SIZE, plain.batterySize);
        assertEquals("battery=percent", StatusBarTweaks.encode(plain));
        // Out of range or unreadable values are rejected; the constructor clamps.
        assertNull(StatusBarTweaks.parse("battery=percent;battery_size=10"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_size=big"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_color=red"));
        assertEquals(StatusBarTweaks.MAX_BATTERY_SIZE,
                new StatusBarTweaks.Config(new LinkedHashSet<>(), null, null, false, true, null, 999).batterySize);
        // Without the number, its colour and size are not stored.
        assertNull(StatusBarTweaks.encode(new StatusBarTweaks.Config(new LinkedHashSet<>(), null, null, false, false, 0xFF000000, 120)));
    }

    @Test public void clockSizeAndBold() {
        StatusBarTweaks.Config config = StatusBarTweaks.parse("clock_size=120;clock_bold=on");
        assertEquals(120, config.clockSize);
        assertTrue(config.clockBold);
        assertEquals("clock_size=120;clock_bold=on", StatusBarTweaks.encode(config));
        // Each alone is a setting of its own.
        assertEquals(70, StatusBarTweaks.parse("clock_size=70").clockSize);
        assertFalse(StatusBarTweaks.parse("clock_size=70").clockBold);
        assertTrue(StatusBarTweaks.parse("clock_bold=on").clockBold);
        assertEquals(StatusBarTweaks.DEFAULT_CLOCK_SIZE, StatusBarTweaks.parse("clock_bold=on").clockSize);
        // Defaults are not written out and settings from before this existed still parse.
        StatusBarTweaks.Config old = StatusBarTweaks.parse("clock=FFFF2D95");
        assertEquals(StatusBarTweaks.DEFAULT_CLOCK_SIZE, old.clockSize);
        assertFalse(old.clockBold);
        assertEquals("clock=FFFF2D95", StatusBarTweaks.encode(old));
        assertNull(StatusBarTweaks.encode(new StatusBarTweaks.Config(
                new LinkedHashSet<>(), null, null, false, false, null, 70, StatusBarTweaks.DEFAULT_CLOCK_SIZE, false)));
        // Out of range or unreadable values are rejected; the constructor clamps.
        assertNull(StatusBarTweaks.parse("clock_size=69"));
        assertNull(StatusBarTweaks.parse("clock_size=141"));
        assertNull(StatusBarTweaks.parse("clock_size=big"));
        assertNull(StatusBarTweaks.parse("clock_bold=off"));
        assertEquals(StatusBarTweaks.MIN_CLOCK_SIZE, new StatusBarTweaks.Config(
                new LinkedHashSet<>(), null, null, false, false, null, 70, 5, false).clockSize);
    }

    @Test public void scaledSize() {
        assertEquals(32f, StatusBarTweaks.scaledSize(32f, 100), 0.001f);
        assertEquals(44.8f, StatusBarTweaks.scaledSize(32f, 140), 0.001f);
        assertEquals(22.4f, StatusBarTweaks.scaledSize(32f, 70), 0.001f);
        assertEquals(1f, StatusBarTweaks.scaledSize(0f, 70), 0.001f);
    }

    @Test public void plateColourAndCorners() {
        StatusBarTweaks.Config config = StatusBarTweaks.parse("plate_color=80102030;plate_radius=12");
        assertEquals(Integer.valueOf(0x80102030), config.plateColor);
        assertEquals(Integer.valueOf(12), config.plateRadius);
        assertFalse(config.noPlate);
        assertEquals("plate_color=80102030;plate_radius=12", StatusBarTweaks.encode(config));
        // Each alone is a setting; a radius of 0 (square corners) is not "no setting".
        assertEquals(Integer.valueOf(0), StatusBarTweaks.parse("plate_radius=0").plateRadius);
        assertNull(StatusBarTweaks.parse("plate_radius=0").plateColor);
        assertNull(StatusBarTweaks.parse("plate_color=FF000000").plateRadius);
        // Settings from before this existed keep Android Auto's plate.
        assertNull(StatusBarTweaks.parse("clock=FFFF2D95").plateColor);
        assertNull(StatusBarTweaks.parse("clock=FFFF2D95").plateRadius);
        assertNull(StatusBarTweaks.parse("plate_radius=49"));
        assertNull(StatusBarTweaks.parse("plate_radius=-1"));
        assertNull(StatusBarTweaks.parse("plate_radius=round"));
        assertNull(StatusBarTweaks.parse("plate_color=red"));
        // A removed plate drops its colour and corners from the stored text.
        StatusBarTweaks.Builder builder = new StatusBarTweaks.Builder();
        builder.noPlate = true;
        builder.plateColor = 0xFF000000;
        builder.plateRadius = 8;
        assertEquals("plate=off", StatusBarTweaks.encode(builder.build()));
        builder.noPlate = false;
        builder.plateRadius = 999;
        assertEquals(Integer.valueOf(StatusBarTweaks.MAX_PLATE_RADIUS), builder.build().plateRadius);
    }

    @Test public void builderDefaultsChangeNothing() {
        StatusBarTweaks.Config config = new StatusBarTweaks.Builder().build();
        assertTrue(config.isEmpty());
        assertNull(StatusBarTweaks.encode(config));
        StatusBarTweaks.Builder nullHide = new StatusBarTweaks.Builder();
        nullHide.hide = null;
        assertTrue(nullHide.build().hide.isEmpty());
    }

    @Test public void radiusInPixels() {
        assertEquals(15.975f, StatusBarTweaks.radiusPx(12, 1.33125f), 0.001f);
        assertEquals(0f, StatusBarTweaks.radiusPx(0, 2f), 0.001f);
        assertEquals(0f, StatusBarTweaks.radiusPx(-4, 2f), 0.001f);
    }

    @Test public void iconSize() {
        StatusBarTweaks.Config config = StatusBarTweaks.parse("icon_size=125");
        assertEquals(125, config.iconSize);
        assertEquals("icon_size=125", StatusBarTweaks.encode(config));
        assertEquals(StatusBarTweaks.DEFAULT_ICON_SIZE, StatusBarTweaks.parse("clock=FFFF2D95").iconSize);
        assertEquals("clock=FFFF2D95", StatusBarTweaks.encode(StatusBarTweaks.parse("clock=FFFF2D95")));
        assertNull(StatusBarTweaks.parse("icon_size=100")); // the default alone changes nothing
        assertNull(StatusBarTweaks.parse("icon_size=69"));
        assertNull(StatusBarTweaks.parse("icon_size=141"));
        assertNull(StatusBarTweaks.parse("icon_size=huge"));
        StatusBarTweaks.Builder builder = new StatusBarTweaks.Builder();
        builder.iconSize = 500;
        assertEquals(StatusBarTweaks.MAX_ICON_SIZE, builder.build().iconSize);
        assertEquals(1.25f, StatusBarTweaks.iconScale(125), 0.0001f);
        assertEquals(0.7f, StatusBarTweaks.iconScale(10), 0.0001f);
        assertEquals(1.4f, StatusBarTweaks.iconScale(900), 0.0001f);
    }

    @Test public void batteryNumberKeepsItsSizeInsideAScaledRow() {
        // Drawn at the row's scale, the number comes out at the size that was wanted.
        for (int percent : new int[] {70, 100, 125, 140}) {
            float inside = StatusBarTweaks.sizeInsideScaledRow(22.4f, percent);
            assertEquals(22.4f, inside * StatusBarTweaks.iconScale(percent), 0.001f);
        }
        assertEquals(22.4f, StatusBarTweaks.sizeInsideScaledRow(22.4f, 100), 0.001f);
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
