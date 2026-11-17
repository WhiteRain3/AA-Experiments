package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlyphIconsTest {
    private static final String DRAWABLE = "android.graphics.drawable.Drawable";

    @Test public void onlyDrawableIconLoadersAreHooked() {
        assertTrue(GlyphIcons.isIconLoader("loadIcon", DRAWABLE));
        assertTrue(GlyphIcons.isIconLoader("getApplicationIcon", DRAWABLE));
        assertTrue(GlyphIcons.isIconLoader("getBadgedIcon", DRAWABLE));
        assertFalse(GlyphIcons.isIconLoader("loadLabel", "java.lang.CharSequence"));
        assertFalse(GlyphIcons.isIconLoader("loadBanner", DRAWABLE));
        assertFalse(GlyphIcons.isIconLoader("getIcon", "int"));
        assertFalse(GlyphIcons.isIconLoader(null, DRAWABLE));
    }

    @Test public void coloursParseAsOpaquePairs() {
        assertArrayEquals(new int[] {0xFFFFFFFF, 0xFF000000}, GlyphIcons.parseColors(GlyphIcons.DEFAULT_COLORS));
        assertArrayEquals(new int[] {0xFFD7000F, 0xFF101010}, GlyphIcons.parseColors("#D7000F, #101010"));
        assertNull(GlyphIcons.parseColors(null));
        assertNull(GlyphIcons.parseColors("FFFFFF"));
        assertNull(GlyphIcons.parseColors("FFFFFF,xyz123"));
        assertNull(GlyphIcons.parseColors("FFF,000"));
    }
}
