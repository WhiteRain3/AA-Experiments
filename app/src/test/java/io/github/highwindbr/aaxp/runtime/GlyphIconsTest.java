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
        assertTrue(GlyphIcons.isIconLoader("getDrawableForDensity", DRAWABLE));
        assertFalse(GlyphIcons.isIconLoader("getDrawable", DRAWABLE));
        assertFalse(GlyphIcons.isIconLoader("loadLabel", "java.lang.CharSequence"));
        assertFalse(GlyphIcons.isIconLoader("loadBanner", DRAWABLE));
        assertFalse(GlyphIcons.isIconLoader("getIcon", "int"));
        assertFalse(GlyphIcons.isIconLoader(null, DRAWABLE));
    }

    private static int[] square(int size, int fill) {
        int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, fill);
        return pixels;
    }

    private static void box(int[] pixels, int size, int from, int to, int color) {
        for (int y = from; y < to; y++) for (int x = from; x < to; x++) pixels[y * size + x] = color;
    }

    @Test public void transparentForegroundUsesItsOwnShape() {
        int size = 40;
        int[] pixels = square(size, 0x00000000);
        box(pixels, size, 12, 28, 0xFF3366FF);
        int[] mask = GlyphIcons.glyphAlpha(pixels, size);
        org.junit.Assert.assertEquals(255, mask[20 * size + 20]);
        org.junit.Assert.assertEquals(0, mask[2 * size + 2]);
    }

    @Test public void opaqueArtworkKeepsWhatDiffersFromTheBackdrop() {
        int size = 40;
        int[] pixels = square(size, 0xFF101010);       // dark backdrop filling the layer
        box(pixels, size, 12, 28, 0xFFF0F0F0);          // bright figure in the middle
        int[] mask = GlyphIcons.glyphAlpha(pixels, size);
        org.junit.Assert.assertEquals(255, mask[20 * size + 20]);
        org.junit.Assert.assertEquals(0, mask[2 * size + 2]);
    }

    @Test public void shapelessOrEmptyArtworkIsLeftAlone() {
        assertNull(GlyphIcons.glyphAlpha(square(40, 0xFF808080), 40)); // flat colour, no figure
        assertNull(GlyphIcons.glyphAlpha(square(40, 0x00000000), 40)); // nothing drawn
        assertNull(GlyphIcons.glyphAlpha(new int[10], 40));            // wrong size
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
