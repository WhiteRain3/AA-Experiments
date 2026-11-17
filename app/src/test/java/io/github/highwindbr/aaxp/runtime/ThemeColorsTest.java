package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

public class ThemeColorsTest {
    @Test public void parsesLinesAndSemicolonsWithOptionalAlpha() {
        Map<String, Integer> colors = ThemeColors.parse("OutlineVariant = 00000000\nPrimary=#FF2D95; Surface=CC2A0845");
        assertEquals(3, colors.size());
        assertEquals(0x00000000, (int) colors.get("OutlineVariant"));
        assertEquals(0xFFFF2D95, (int) colors.get("Primary"));
        assertEquals(0xCC2A0845, (int) colors.get("Surface"));
        assertTrue(ThemeColors.parse("").isEmpty());
        assertTrue(ThemeColors.parse(null).isEmpty());
    }

    @Test public void malformedInputIsRejectedAsAWhole() {
        assertNull(ThemeColors.parse("Primary"));
        assertNull(ThemeColors.parse("Primary=12345"));
        assertNull(ThemeColors.parse("Primary=GGGGGG"));
        assertNull(ThemeColors.parse("Not A Role=FFFFFF"));
    }

    @Test public void encodeRoundTrips() {
        Map<String, Integer> colors = ThemeColors.parse("Primary=FF2D95;OutlineVariant=00000000");
        assertEquals("Primary=FFFF2D95;OutlineVariant=00000000", ThemeColors.encode(colors));
        assertEquals(colors, ThemeColors.parse(ThemeColors.encode(colors)));
        assertNull(ThemeColors.encode(ThemeColors.parse("")));
    }

    @Test public void roleEnumIsRecognisedByItsRoleNames() {
        assertTrue(ThemeColors.looksLikeRoleEnum(Arrays.asList(
                "Primary", "OnPrimaryContainer", "OutlineVariant", "SurfaceContainerHighest", "InverseOnSurface", "x")));
        assertFalse(ThemeColors.looksLikeRoleEnum(Arrays.asList("OnPrimaryContainer", "OutlineVariant")));
        assertFalse(ThemeColors.looksLikeRoleEnum(null));
    }
}
