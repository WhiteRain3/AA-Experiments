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

    @Test public void focusRingCoversBothPaletteResources() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put(ResourceColors.FOCUS_RING, 0xFF00E5FF);
        assertEquals("focus_ring=FF00E5FF", ResourceColors.encode(colors));
        assertEquals(colors, ResourceColors.parse("focus_ring=FF00E5FF"));
        Map<String, Integer> names = ResourceColors.byResourceName(colors);
        assertEquals(Integer.valueOf(0xFF00E5FF), names.get("baseline_palette_p_85"));
        assertEquals(Integer.valueOf(0xFF00E5FF), names.get("dynamic_palette_p_85"));
        assertEquals(2, names.size());
    }

    @Test public void focusRingIsStoredAfterTheOlderGroups() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put(ResourceColors.FOCUS_RING, 0xFF00E5FF);
        colors.put(ResourceColors.TEXT, 0xFFFFFFFF);
        assertEquals("text=FFFFFFFF;focus_ring=FF00E5FF", ResourceColors.encode(colors));
    }

    @Test public void noResourceBelongsToTwoGroups() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String group : ResourceColors.groups()) {
            Map<String, Integer> one = new LinkedHashMap<>();
            one.put(group, 0xFF000000);
            for (String name : ResourceColors.byResourceName(one).keySet()) assertTrue(name, seen.add(name));
        }
    }

    private static int[] table(int... typeDataId) {
        int[] data = new int[typeDataId.length / 3 * ResourceColors.ENTRY_SIZE];
        for (int i = 0; i < typeDataId.length / 3; i++) {
            data[i * 7] = typeDataId[i * 3];
            data[i * 7 + 1] = typeDataId[i * 3 + 1];
            data[i * 7 + 2] = 99; // asset cookie, must survive
            data[i * 7 + 3] = typeDataId[i * 3 + 2];
        }
        return data;
    }

    @Test public void rewritesOnlyPlainColoursFromConfiguredResources() {
        Map<Integer, Integer> byId = new LinkedHashMap<>();
        byId.put(0x7f060010, 0xFF00FF00);
        int[] data = table(
                0x1d, 0xFFF2F0F0, 0x7f060010,   // the configured colour: changed
                0x1d, 0xFFF2F0F0, 0x7f060011,   // same value from another resource: kept
                0x03, 0x00000123, 0x7f060010,   // a string (a colour file's path): kept
                0x01, 0x7f060010, 0x7f060010,   // an unresolved reference: kept
                0x1d, 0xFF111111, 0,            // written directly in the layout: kept
                0x1c, 0x80F2F0F0, 0x7f060010);  // ARGB form: changed
        assertEquals(2, ResourceColors.rewrite(data, byId, true));
        assertEquals(0xFF00FF00, data[1]);
        assertEquals(0x1c, data[0]);
        assertEquals(99, data[2]);
        assertEquals(0x7f060010, data[3]);
        assertEquals(0xFFF2F0F0, data[7 + 1]);
        assertEquals(0x00000123, data[14 + 1]);
        assertEquals(0x7f060010, data[21 + 1]);
        assertEquals(0xFF111111, data[28 + 1]);
        assertEquals(0xFF00FF00, data[35 + 1]);
    }

    @Test public void rewriteCanCountWithoutWriting() {
        Map<Integer, Integer> byId = new LinkedHashMap<>();
        byId.put(5, 0xFF00FF00);
        int[] data = table(0x1d, 0xFFF2F0F0, 5);
        assertEquals(1, ResourceColors.rewrite(data, byId, false));
        assertEquals(0xFFF2F0F0, data[1]);
    }

    @Test public void rewriteLeavesUnexpectedTablesAlone() {
        Map<Integer, Integer> byId = new LinkedHashMap<>();
        byId.put(5, 0xFF00FF00);
        assertEquals(0, ResourceColors.rewrite(null, byId, true));
        assertEquals(0, ResourceColors.rewrite(new int[0], byId, true));
        int[] odd = {0x1d, 0xFFF2F0F0, 0, 5, 0, 0, 0, 0x1d}; // not a multiple of seven
        assertEquals(0, ResourceColors.rewrite(odd, byId, true));
        assertEquals(0xFFF2F0F0, odd[1]);
        assertEquals(0, ResourceColors.rewrite(table(0x1d, 1, 5), new LinkedHashMap<>(), true));
        assertEquals(0, ResourceColors.rewrite(table(0x1d, 1, 5), null, true));
    }

    @Test public void tableLayoutOnlyFromAndroid10AndOnlyWithSevenEntries() {
        assertTrue(ResourceColors.tableLayoutSupported(29, 7));
        assertTrue(ResourceColors.tableLayoutSupported(36, 7));
        assertTrue(ResourceColors.tableLayoutSupported(36, null)); // count unreadable: the version decides
        assertTrue(!ResourceColors.tableLayoutSupported(28, 6));
        assertTrue(!ResourceColors.tableLayoutSupported(28, null));
        assertTrue(!ResourceColors.tableLayoutSupported(27, 7));
        assertTrue(!ResourceColors.tableLayoutSupported(36, 8)); // a future layout is left alone
        assertTrue(!ResourceColors.tableLayoutSupported(36, 6));
    }

    @Test public void colourTypes() {
        assertTrue(ResourceColors.isColorType(0x1c));
        assertTrue(ResourceColors.isColorType(0x1f));
        assertTrue(!ResourceColors.isColorType(0x1b));
        assertTrue(!ResourceColors.isColorType(0x03));
        assertTrue(!ResourceColors.isColorType(0x01));
    }

    @Test public void everyGroupHasResources() {
        for (String group : ResourceColors.groups()) {
            Map<String, Integer> one = new LinkedHashMap<>();
            one.put(group, 0xFF000000);
            assertTrue(group, !ResourceColors.byResourceName(one).isEmpty());
        }
    }
}
