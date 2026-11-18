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

    private static StatusBarTweaks.Config byLevel() {
        return StatusBarTweaks.parse("battery=percent;battery_level=70,30");
    }

    @Test public void levelColourBoundaries() {
        StatusBarTweaks.Config config = byLevel();
        int base = 0xFFF2F0F0, green = StatusBarTweaks.DEFAULT_BATTERY_HIGH_COLOR, red = StatusBarTweaks.DEFAULT_BATTERY_LOW_COLOR;
        assertEquals(green, StatusBarTweaks.levelColor(100, false, config, base));
        assertEquals(green, StatusBarTweaks.levelColor(70, false, config, base));
        assertEquals(base, StatusBarTweaks.levelColor(69, false, config, base));
        assertEquals(base, StatusBarTweaks.levelColor(30, false, config, base));
        assertEquals(red, StatusBarTweaks.levelColor(29, false, config, base));
        assertEquals(red, StatusBarTweaks.levelColor(0, false, config, base));
        // Unknown level: the colour the number has anyway.
        assertEquals(base, StatusBarTweaks.levelColor(-1, false, config, base));
        // Charging is not special unless a colour for it was chosen.
        assertEquals(red, StatusBarTweaks.levelColor(10, true, config, base));
    }

    @Test public void levelColourOffIsTheBaseColour() {
        StatusBarTweaks.Config off = StatusBarTweaks.parse("battery=percent;battery_color=FF00FFFF");
        assertFalse(off.batteryByLevel);
        for (int percent : new int[] {-1, 0, 29, 30, 50, 70, 100}) {
            assertEquals(0xFF00FFFF, StatusBarTweaks.levelColor(percent, true, off, 0xFF00FFFF));
        }
        assertEquals(0xFF123456, StatusBarTweaks.levelColor(90, false, null, 0xFF123456));
    }

    @Test public void levelColourChosenColoursAndCharging() {
        StatusBarTweaks.Config config = StatusBarTweaks.parse("battery=percent;battery_level=80,20;"
                + "battery_high_color=FF00FF00;battery_mid_color=FFFFFF00;battery_low_color=FFFF0000;battery_charge_color=FF0000FF");
        assertEquals(80, config.batteryHigh);
        assertEquals(20, config.batteryLow);
        assertEquals(0xFF00FF00, StatusBarTweaks.levelColor(80, false, config, 0));
        assertEquals(0xFFFFFF00, StatusBarTweaks.levelColor(79, false, config, 0));
        assertEquals(0xFFFFFF00, StatusBarTweaks.levelColor(20, false, config, 0));
        assertEquals(0xFFFF0000, StatusBarTweaks.levelColor(19, false, config, 0));
        assertEquals(0xFF0000FF, StatusBarTweaks.levelColor(19, true, config, 0));
        assertEquals(0xFF0000FF, StatusBarTweaks.levelColor(95, true, config, 0));
        // Unknown level wins over charging: there is nothing to go by.
        assertEquals(0, StatusBarTweaks.levelColor(-1, true, config, 0));
        assertEquals("battery=percent;battery_level=80,20;battery_high_color=FF00FF00;battery_mid_color=FFFFFF00;"
                + "battery_low_color=FFFF0000;battery_charge_color=FF0000FF", StatusBarTweaks.encode(config));
    }

    @Test public void levelColourSetting() {
        assertEquals("battery=percent;battery_level=70,30", StatusBarTweaks.encode(byLevel()));
        assertNull(byLevel().batteryMidColor);
        assertNull(byLevel().batteryChargeColor);
        // Marks must be 0 <= low < high <= 100.
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=30,70"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=50,50"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=101,30"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=70,-1"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=70"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=high,low"));
        assertNull(StatusBarTweaks.parse("battery=percent;battery_level=70,30;battery_low_color=red"));
        // The builder falls back to the default marks when given unusable ones.
        StatusBarTweaks.Builder builder = new StatusBarTweaks.Builder();
        builder.batteryPercent = true;
        builder.batteryByLevel = true;
        builder.batteryHigh = 20;
        builder.batteryLow = 60;
        assertEquals(StatusBarTweaks.DEFAULT_BATTERY_HIGH, builder.build().batteryHigh);
        assertEquals(StatusBarTweaks.DEFAULT_BATTERY_LOW, builder.build().batteryLow);
        // Without the number there is nothing to colour, so nothing is stored.
        builder.batteryPercent = false;
        assertNull(StatusBarTweaks.encode(builder.build()));
        // Settings from before this existed are unchanged.
        assertFalse(StatusBarTweaks.parse("battery=percent").batteryByLevel);
        assertEquals("battery=percent", StatusBarTweaks.encode(StatusBarTweaks.parse("battery=percent")));
    }

    @Test public void toBuilderKeepsEverything() {
        String all = "hide=clock;clock=FFFF2D95;icons=80FFFFFF;battery=percent;battery_color=FF00FFFF;battery_size=110;"
                + "battery_level=80,20;battery_high_color=FF00FF00;battery_mid_color=FFFFFF00;battery_low_color=FFFF0000;"
                + "battery_charge_color=FF0000FF;clock_size=120;clock_bold=on;plate_color=80102030;plate_radius=12;icon_size=125";
        assertEquals(all, StatusBarTweaks.encode(StatusBarTweaks.parse(all)));
        assertEquals(all, StatusBarTweaks.encode(StatusBarTweaks.parse(all).toBuilder().build()));
    }

    @Test public void percentOfLevel() {
        assertEquals(74, StatusBarTweaks.percent(74, 100));
        assertEquals(50, StatusBarTweaks.percent(100, 200));
        assertEquals(100, StatusBarTweaks.percent(101, 100));
        assertEquals(-1, StatusBarTweaks.percent(-1, 100));
        assertEquals(-1, StatusBarTweaks.percent(50, 0));
    }

    @Test public void rowFitsAtTheWantedScaleWhenThereIsRoom() {
        // Two icons, 69 px wide, in a rail whose container can be 97 px: 140% (96.6 px) just fits.
        StatusBarTweaks.RowFit fit = StatusBarTweaks.fitRow(69, 1.4f, 97);
        assertEquals(1.4f, fit.scale, 0.0001f);
        assertEquals(97, fit.containerWidth);
        // At 100% the container only needs the row's own width.
        fit = StatusBarTweaks.fitRow(69, 1.0f, 97);
        assertEquals(1.0f, fit.scale, 0.0001f);
        assertEquals(69, fit.containerWidth);
    }

    @Test public void rowIsShrunkWhenIconsAndNumberDoNotFit() {
        // Two icons plus the number need 133 px; only 97 are there.
        StatusBarTweaks.RowFit fit = StatusBarTweaks.fitRow(133, 1.0f, 97);
        assertEquals(97f / 133f, fit.scale, 0.0001f);
        assertEquals(97, fit.containerWidth);
        assertTrue(133 * fit.scale <= 97.001f);
        // A larger wanted size cannot make it overflow either.
        assertEquals(97f / 133f, StatusBarTweaks.fitRow(133, 1.4f, 97).scale, 0.0001f);
        // A smaller wanted size is kept.
        assertEquals(0.7f, StatusBarTweaks.fitRow(133, 0.7f, 97).scale, 0.0001f);
    }

    @Test public void rowFitEdges() {
        // Never below the floor, and unusable measurements change nothing.
        assertEquals(StatusBarTweaks.MIN_ROW_SCALE, StatusBarTweaks.fitRow(1000, 1.0f, 97).scale, 0.0001f);
        assertEquals(1.2f, StatusBarTweaks.fitRow(0, 1.2f, 97).scale, 0.0001f);
        assertEquals(0, StatusBarTweaks.fitRow(0, 1.2f, 97).containerWidth);
        assertEquals(1.2f, StatusBarTweaks.fitRow(69, 1.2f, 0).scale, 0.0001f);
    }

    @Test public void scaledRowIsCentred() {
        assertEquals(0f, StatusBarTweaks.centredLeft(97, 133, 97f / 133f), 0.001f);
        assertEquals(8f, StatusBarTweaks.centredLeft(85, 69, 1.0f), 0.001f);
        assertEquals(-5.8f, StatusBarTweaks.centredLeft(85, 69, 1.4f), 0.001f);
    }

    @Test public void filteredIconsAreAmongTheTintableOnes() {
        assertTrue(StatusBarTweaks.TINTABLE_ICONS.containsAll(StatusBarTweaks.FILTERED_ICONS));
        assertTrue(StatusBarTweaks.FILTERED_ICONS.contains("battery_level"));
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
