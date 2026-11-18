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

    private static void disc(int[] pixels, int size, int radius, int color) {
        int c = size / 2;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            if ((x - c) * (x - c) + (y - c) * (y - c) <= radius * radius) pixels[y * size + x] = color;
    }

    @Test public void smallShapeOnTransparentLayerIsItsOwnGlyph() {
        int size = 60;
        int[] pixels = square(size, 0x00000000);
        box(pixels, size, 22, 38, 0xFFFFFFFF);          // small flat shape, like a logo
        int[] mask = GlyphIcons.glyphAlpha(pixels, size);
        org.junit.Assert.assertEquals(255, mask[30 * size + 30]);
        org.junit.Assert.assertEquals(0, mask[2 * size + 2]);
    }

    @Test public void figureInsideADiscIsExtractedInsteadOfTheDisc() {
        // Like a player icon: dark disc on a transparent layer with a bright figure in it.
        int size = 80;
        int[] pixels = square(size, 0x00000000);
        disc(pixels, size, 30, 0xFF101018);
        box(pixels, size, 34, 46, 0xFFFFFFFF);
        int[] mask = GlyphIcons.glyphAlpha(pixels, size);
        org.junit.Assert.assertEquals(255, mask[40 * size + 40]);   // the figure
        org.junit.Assert.assertEquals(0, mask[40 * size + 15]);     // the disc itself
        org.junit.Assert.assertEquals(0, mask[2 * size + 2]);       // outside
    }

    @Test public void figureOnAFullSquareIsExtracted() {
        // Like Android Auto's Exit entry: a filled square with a white figure.
        int size = 40;
        int[] pixels = square(size, 0xFF303034);
        box(pixels, size, 12, 28, 0xFFFFFFFF);
        int[] mask = GlyphIcons.glyphAlpha(pixels, size);
        org.junit.Assert.assertEquals(255, mask[20 * size + 20]);
        org.junit.Assert.assertEquals(0, mask[2 * size + 2]);
    }

    @Test public void largeFlatPlatesAndEmptyArtworkAreLeftAlone() {
        int size = 80;
        int[] flatDisc = square(size, 0x00000000);
        disc(flatDisc, size, 34, 0xFFFFFFFF);
        assertNull(GlyphIcons.glyphAlpha(flatDisc, size));              // would only be a blob
        assertNull(GlyphIcons.glyphAlpha(square(40, 0xFF808080), 40)); // flat square
        assertNull(GlyphIcons.glyphAlpha(square(40, 0x00000000), 40)); // nothing drawn
        assertNull(GlyphIcons.glyphAlpha(new int[10], 40));            // wrong size
    }

    @Test public void ownLauncherEntriesAreRecognisedByName() {
        assertTrue(GlyphIcons.isOwnLauncherIcon("ic_applauncher_exit_icon"));
        assertFalse(GlyphIcons.isOwnLauncherIcon("ic_exit_black"));
        assertFalse(GlyphIcons.isOwnLauncherIcon(null));
    }

    @Test public void coloursParseAsOpaquePairs() {
        assertArrayEquals(new int[] {0xFFFFFFFF, 0xFF000000}, GlyphIcons.parseColors(GlyphIcons.DEFAULT_COLORS));
        assertArrayEquals(new int[] {0xFFD7000F, 0xFF101010}, GlyphIcons.parseColors("#D7000F, #101010"));
        // AARRGGBB: half-transparent white glyph on a fully transparent background.
        assertArrayEquals(new int[] {0x80FFFFFF, 0x00000000}, GlyphIcons.parseColors("80FFFFFF,00000000"));
        assertArrayEquals(new int[] {0xFFFFFFFF, 0x66101010}, GlyphIcons.parseColors("#FFFFFF,#66101010"));
        assertNull(GlyphIcons.parseColors("FFFFFFF,000000"));
        assertNull(GlyphIcons.parseColors(null));
        assertNull(GlyphIcons.parseColors("FFFFFF"));
        assertNull(GlyphIcons.parseColors("FFFFFF,xyz123"));
        assertNull(GlyphIcons.parseColors("FFF,000"));
    }

    @Test public void figureSizeSetting() {
        org.junit.Assert.assertEquals(100, GlyphIcons.parseSize(null));
        org.junit.Assert.assertEquals(120, GlyphIcons.parseSize("120"));
        org.junit.Assert.assertEquals(50, GlyphIcons.parseSize(" 50 "));
        org.junit.Assert.assertEquals(150, GlyphIcons.parseSize("150"));
        org.junit.Assert.assertEquals(100, GlyphIcons.parseSize("49"));
        org.junit.Assert.assertEquals(100, GlyphIcons.parseSize("151"));
        org.junit.Assert.assertEquals(100, GlyphIcons.parseSize("big"));
        org.junit.Assert.assertEquals(100, GlyphIcons.parseSize(""));
    }

    @Test public void legacyInsetIsOneSixthAtTheDefaultSize() {
        org.junit.Assert.assertEquals(1f / 6f, GlyphIcons.insetFraction(GlyphIcons.LEGACY_FIGURE, 100), 0.0001f);
        // 150% fills the layer exactly; 50% leaves a third on each side.
        org.junit.Assert.assertEquals(0f, GlyphIcons.insetFraction(GlyphIcons.LEGACY_FIGURE, 150), 0.0001f);
        org.junit.Assert.assertEquals(1f / 3f, GlyphIcons.insetFraction(GlyphIcons.LEGACY_FIGURE, 50), 0.0001f);
    }

    @Test public void layerInsetIsZeroAtTheDefaultSizeAndNegativeWhenLarger() {
        org.junit.Assert.assertEquals(0f, GlyphIcons.insetFraction(GlyphIcons.LAYER_FIGURE, 100), 0.0001f);
        org.junit.Assert.assertEquals(0.25f, GlyphIcons.insetFraction(GlyphIcons.LAYER_FIGURE, 50), 0.0001f);
        org.junit.Assert.assertEquals(-0.25f, GlyphIcons.insetFraction(GlyphIcons.LAYER_FIGURE, 150), 0.0001f);
        org.junit.Assert.assertEquals(-0.1f, GlyphIcons.insetFraction(GlyphIcons.LAYER_FIGURE, 120), 0.0001f);
    }

    @Test public void insetStaysWithinWhatAnInsetCanExpress() {
        for (int percent = -100; percent <= 400; percent += 25) {
            for (float base : new float[] {GlyphIcons.LEGACY_FIGURE, GlyphIcons.LAYER_FIGURE}) {
                float inset = GlyphIcons.insetFraction(base, percent);
                org.junit.Assert.assertTrue(inset >= -0.25f && inset <= 0.45f);
            }
        }
        // A bigger percentage never gives a bigger inset.
        float previous = 1f;
        for (int percent = GlyphIcons.MIN_SIZE; percent <= GlyphIcons.MAX_SIZE; percent += 5) {
            float inset = GlyphIcons.insetFraction(GlyphIcons.LEGACY_FIGURE, percent);
            org.junit.Assert.assertTrue(inset <= previous);
            previous = inset;
        }
    }

    @Test public void perAppRoundTrip() {
        java.util.Map<String, String> choices = new java.util.LinkedHashMap<>();
        choices.put("com.spotify.music", GlyphIcons.ORIGINAL);
        choices.put("com.waze", "00E5FF,80000000");
        choices.put("org.example.app_1", GlyphIcons.GLYPH);
        String text = GlyphIcons.encodePerApp(choices);
        org.junit.Assert.assertEquals("com.spotify.music=original;com.waze=00E5FF,80000000;org.example.app_1=glyph", text);
        org.junit.Assert.assertEquals(choices, GlyphIcons.parsePerApp(text));
    }

    @Test public void perAppMalformedGivesNoChoices() {
        org.junit.Assert.assertTrue(GlyphIcons.parsePerApp(null).isEmpty());
        org.junit.Assert.assertTrue(GlyphIcons.parsePerApp("").isEmpty());
        for (String bad : new String[] {"com.waze", "com.waze=", "=original", "com.waze=blue", "com.waze=FFF,000000",
                "com waze=original", "com.waze=original;broken", "a;b=original"}) {
            org.junit.Assert.assertTrue(bad, GlyphIcons.parsePerApp(bad).isEmpty());
        }
        org.junit.Assert.assertNull(GlyphIcons.encodePerApp(null));
        org.junit.Assert.assertNull(GlyphIcons.encodePerApp(new java.util.LinkedHashMap<>()));
        java.util.Map<String, String> mixed = new java.util.LinkedHashMap<>();
        mixed.put("bad name", GlyphIcons.ORIGINAL);
        mixed.put("com.ok", "nonsense");
        mixed.put("com.fine", GlyphIcons.ORIGINAL);
        org.junit.Assert.assertEquals("com.fine=original", GlyphIcons.encodePerApp(mixed));
    }

    @Test public void effectiveColoursPerApp() {
        java.util.Map<String, String> choices = GlyphIcons.parsePerApp("a.orig=original;a.glyph=glyph;a.own=112233,445566");
        String main = "FFFFFF,000000";
        // Glyph icons on: an app follows the main setting unless it has a choice.
        org.junit.Assert.assertEquals(main, GlyphIcons.effectiveColors(main, choices, "a.other"));
        org.junit.Assert.assertEquals(main, GlyphIcons.effectiveColors(main, choices, null));
        org.junit.Assert.assertNull(GlyphIcons.effectiveColors(main, choices, "a.orig"));
        org.junit.Assert.assertEquals(main, GlyphIcons.effectiveColors(main, choices, "a.glyph"));
        org.junit.Assert.assertEquals("112233,445566", GlyphIcons.effectiveColors(main, choices, "a.own"));
        // Glyph icons off: only apps with a glyph choice get one.
        org.junit.Assert.assertNull(GlyphIcons.effectiveColors(null, choices, "a.other"));
        org.junit.Assert.assertNull(GlyphIcons.effectiveColors(null, choices, null));
        org.junit.Assert.assertNull(GlyphIcons.effectiveColors(null, choices, "a.orig"));
        org.junit.Assert.assertEquals(GlyphIcons.DEFAULT_COLORS, GlyphIcons.effectiveColors(null, choices, "a.glyph"));
        org.junit.Assert.assertEquals("112233,445566", GlyphIcons.effectiveColors(null, choices, "a.own"));
        org.junit.Assert.assertEquals(main, GlyphIcons.effectiveColors(main, null, "a.own"));
    }

    @Test public void packageNames() {
        org.junit.Assert.assertTrue(GlyphIcons.isPackageName("com.google.android.apps.maps"));
        org.junit.Assert.assertFalse(GlyphIcons.isPackageName(""));
        org.junit.Assert.assertFalse(GlyphIcons.isPackageName(null));
        org.junit.Assert.assertFalse(GlyphIcons.isPackageName("a=b"));
        org.junit.Assert.assertFalse(GlyphIcons.isPackageName("a;b"));
    }
}
