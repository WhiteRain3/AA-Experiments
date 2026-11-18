package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.DiscoveredType
import org.junit.Assert.*
import org.junit.Test

class MapBorderFlagTest {
    @Test fun statusExplainsWhyTheControlCannotBeUsed() {
        assertEquals(MapBorderFlag.Status.UNKNOWN, MapBorderFlag.status(null, null))
        assertEquals(MapBorderFlag.Status.MISSING, MapBorderFlag.status(false, null))
        assertEquals(MapBorderFlag.Status.NOT_INTEGER, MapBorderFlag.status(true, DiscoveredType.BOOLEAN))
        assertEquals(MapBorderFlag.Status.NOT_INTEGER, MapBorderFlag.status(true, DiscoveredType.STRING))
        assertEquals(MapBorderFlag.Status.OK, MapBorderFlag.status(true, DiscoveredType.LONG))
        assertEquals(MapBorderFlag.Status.OK, MapBorderFlag.status(true, DiscoveredType.INT))
    }

    @Test fun currentReadsTheOverrideOrNone() {
        assertNull(MapBorderFlag.current(emptyMap()))
        assertEquals(3, MapBorderFlag.current(mapOf(MapBorderFlag.KEY to "LONG:3")))
        assertNull(MapBorderFlag.current(mapOf(MapBorderFlag.KEY to "garbage")))
    }

    @Test fun clampAndLabel() {
        assertEquals(0, MapBorderFlag.clamp(-4)); assertEquals(MapBorderFlag.MAX, MapBorderFlag.clamp(99))
        assertEquals("Android Auto's own", MapBorderFlag.label(null)); assertEquals("2", MapBorderFlag.label(2))
    }
}
