package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarWallpaperRailLookTest {
    @Test public void defaultsMatchTheLauncher() {
        CarWallpaper.RailLook look = CarWallpaper.parseRailLook(null);
        assertTrue(look.isDefault());
        assertNull(look.color);
        assertEquals(0xFF252626, look.colorOrDefault());
        assertEquals(80, look.strength);
        assertEquals(0.8f, look.alpha(), 0.0001f);
        assertTrue(CarWallpaper.parseRailLook("").isDefault());
        assertNull(CarWallpaper.encodeRailLook(look));
        assertNull(CarWallpaper.encodeRailLook(null));
    }

    @Test public void roundTrip() {
        CarWallpaper.RailLook look = new CarWallpaper.RailLook(0xFF101820, 35);
        String text = CarWallpaper.encodeRailLook(look);
        assertEquals("color=FF101820;strength=35", text);
        CarWallpaper.RailLook parsed = CarWallpaper.parseRailLook(text);
        assertEquals(Integer.valueOf(0xFF101820), parsed.color);
        assertEquals(35, parsed.strength);
        assertEquals(0.35f, parsed.alpha(), 0.0001f);
    }

    @Test public void eachPartAlone() {
        assertEquals("color=FF000000", CarWallpaper.encodeRailLook(new CarWallpaper.RailLook(0xFF000000, 80)));
        assertEquals("strength=0", CarWallpaper.encodeRailLook(new CarWallpaper.RailLook(null, 0)));
        assertEquals(100, CarWallpaper.parseRailLook("strength=100").strength);
        assertNull(CarWallpaper.parseRailLook("strength=100").color);
        assertEquals(80, CarWallpaper.parseRailLook("color=FF000000").strength);
    }

    @Test public void malformedFallsBackToDefaults() {
        for (String bad : new String[] {"color", "color=FFF", "color=GGGGGGGG", "strength=101", "strength=-1",
                "strength=lots", "unknown=1", "=5", "color=FF000000;strength=x"}) {
            assertTrue(bad, CarWallpaper.parseRailLook(bad).isDefault());
        }
    }

    @Test public void constructorClamps() {
        assertEquals(100, new CarWallpaper.RailLook(null, 250).strength);
        assertEquals(0, new CarWallpaper.RailLook(null, -3).strength);
    }
}
