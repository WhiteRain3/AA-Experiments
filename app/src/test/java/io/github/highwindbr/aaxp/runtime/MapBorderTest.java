package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MapBorderTest {
    @Test public void parseAndEncode() {
        assertEquals(Integer.valueOf(12), MapBorder.parse("12"));
        assertEquals(Integer.valueOf(0), MapBorder.parse("0"));
        assertEquals(Integer.valueOf(16), MapBorder.parse(" 16 "));
        assertNull(MapBorder.parse(null));
        assertNull(MapBorder.parse(""));
        assertNull(MapBorder.parse("17"));
        assertNull(MapBorder.parse("-1"));
        assertNull(MapBorder.parse("thick"));
        assertNull(MapBorder.parse("LONG:12"));
        assertEquals("12", MapBorder.encode(12));
        assertEquals("16", MapBorder.encode(99));
        assertEquals("0", MapBorder.encode(-5));
        assertNull(MapBorder.encode(null));
    }

    @Test public void hoveredKeepsAndroidAutosProportion() {
        assertEquals(MapBorder.OWN_HOVERED, MapBorder.hovered(MapBorder.OWN_FOCUSED));
        assertEquals(8, MapBorder.hovered(12));
        assertEquals(1, MapBorder.hovered(1));
        assertEquals(1, MapBorder.hovered(2));
        assertEquals(0, MapBorder.hovered(0));
    }

    @Test public void pixelsOnTheOwnersScreen() {
        float density = 213f / 160f;
        assertEquals(8, MapBorder.pixels(6, density));   // what the tester measured: about 8 px
        assertEquals(3, MapBorder.pixels(2, density));
        assertEquals(16, MapBorder.pixels(12, density));
        assertEquals(0, MapBorder.pixels(0, density));
        assertEquals(1, MapBorder.pixels(1, 0.5f));
        assertEquals(0, MapBorder.pixels(4, 0f));
    }
}
