package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ResourceColorsTest {
    @Test public void roundTrip() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put(ResourceColors.MAP_FOCUS, 0xFFFF2D95);
        colors.put(ResourceColors.TEXT, 0xFFFFFFFF);
        String text = ResourceColors.encode(colors);
        assertEquals("text=FFFFFFFF;map_focus=FFFF2D95", text);
        assertEquals(colors, ResourceColors.parse(text));
    }

    @Test public void emptyIsEmptyAndMalformedIsNull() {
        assertTrue(ResourceColors.parse(null).isEmpty());
        assertTrue(ResourceColors.parse("").isEmpty());
        assertNull(ResourceColors.encode(new LinkedHashMap<>()));
        assertNull(ResourceColors.parse("text"));
        assertNull(ResourceColors.parse("unknown=FFFFFFFF"));
        assertNull(ResourceColors.parse("text=FFF"));
        assertNull(ResourceColors.parse("text=GGGGGGGG"));
    }

    @Test public void expandsGroupsToResourceNames() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put(ResourceColors.TEXT, 0xFF112233);
        Map<String, Integer> names = ResourceColors.byResourceName(colors);
        assertEquals(Integer.valueOf(0xFF112233), names.get("baseline_palette_n_95"));
        assertEquals(Integer.valueOf(0xFF112233), names.get("dynamic_palette_n_95"));
        assertEquals(2, names.size());
        assertTrue(ResourceColors.byResourceName(null).isEmpty());
    }

    @Test public void everyGroupHasResources() {
        for (String group : ResourceColors.groups()) {
            Map<String, Integer> one = new LinkedHashMap<>();
            one.put(group, 0xFF000000);
            assertTrue(group, !ResourceColors.byResourceName(one).isEmpty());
        }
    }
}
