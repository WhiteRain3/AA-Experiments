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

    /** A layer of {@code size} pixels: a disc of the given radius, with an optional square hole in the middle. */
    private static int[] disc(int size, int radius, int hole) {
        int[] alpha = new int[size * size];
        int c = size / 2;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            boolean in = (x - c) * (x - c) + (y - c) * (y - c) <= radius * radius;
            boolean cut = Math.abs(x - c) <= hole && Math.abs(y - c) <= hole && hole > 0;
            alpha[y * size + x] = in && !cut ? 255 : 0;
        }
        return alpha;
    }

    @Test public void figureCutOutOfADiscBecomesTheGlyph() {
        int size = 96;
        int[] mask = GlyphIcons.figureInsidePlate(disc(size, 30, 10), size);
        org.junit.Assert.assertNotNull(mask);
        int c = size / 2;
        org.junit.Assert.assertEquals(255, mask[c * size + c]);          // the hole is the figure
        org.junit.Assert.assertEquals(0, mask[c * size + c + 20]);       // the plate is not
        org.junit.Assert.assertEquals(0, mask[0]);                       // nor the outside
        org.junit.Assert.assertEquals(0, mask[c * size + c + 30]);       // nor the plate's edge
    }

    @Test public void layersThatAreNotAPlateWithAFigureAreLeftAlone() {
        int size = 96;
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(disc(size, 30, 0), size));   // solid disc
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(disc(size, 30, 1), size));   // a pinhole
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(disc(size, 12, 4), size));   // too small to be a plate
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(new int[size * size], size)); // empty
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(null, size));
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(new int[5], size));
        // A ring: nearly all of it is hole, so it is an outline figure, not a plate.
        int[] ring = disc(size, 30, 0);
        int c = size / 2;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if ((x - c) * (x - c) + (y - c) * (y - c) <= 26 * 26) ring[y * size + x] = 0;
        }
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(ring, size));
        // A wide bar with a hole is a figure of its own, not a plate.
        int[] bar = new int[size * size];
        for (int y = 38; y < 58; y++) for (int x = 10; x < 86; x++) bar[y * size + x] = 255;
        for (int y = 44; y < 52; y++) for (int x = 40; x < 56; x++) bar[y * size + x] = 0;
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bar, size));
        // A notch open to the outside is not a hole.
        int[] notched = disc(size, 30, 0);
        for (int y = c - 4; y <= c + 4; y++) for (int x = c; x < size; x++) notched[y * size + x] = 0;
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(notched, size));
    }

    /** Opaque where the distance from the centre is within any of the given [from, to] bands. */
    private static int[] bands(int size, int... fromTo) {
        int[] alpha = new int[size * size];
        int c = size / 2;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int d2 = (x - c) * (x - c) + (y - c) * (y - c);
            for (int b = 0; b < fromTo.length; b += 2) {
                if (d2 >= fromTo[b] * fromTo[b] && d2 <= fromTo[b + 1] * fromTo[b + 1]) alpha[y * size + x] = 255;
            }
        }
        return alpha;
    }

    @Test public void anOutlineAroundASolidFigureIsNotFlipped() {
        // The reviewer's case: a ring (68 to 80) around a solid disc (40) on a 216 px layer.
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(216, 68, 80, 0, 40), 216));
        // The same drawing at the size the module renders at.
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 34, 40, 0, 20), 108));
        // A thin outline with a large figure close inside it: the gap is small, the rim is thin.
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 37, 40, 0, 33), 108));
        // Thick outlines around a solid disc (the reviewer's second set, on 108 px).
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 28, 40, 0, 20), 108));
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 30, 40, 0, 24), 108));
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 26, 40, 0, 22), 108));
        // A disc with a ring cut all the way round but a SOLID island: nothing says plate.
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 30, 40, 0, 26), 108));
        // A thick rim, but the gap is wide: still an outline with a dot.
        org.junit.Assert.assertNull(GlyphIcons.figureInsidePlate(bands(108, 30, 40, 0, 8), 108));
    }

    @Test public void aDiscWithARingCutRoundAnIslandThatHasItsOwnHoleIsAPlate() {
        // A disc of radius 40 with a thin ring (26 to 30) cut out, and a square cut out of the
        // island in the middle, as a play triangle would be.
        int size = 108;
        int[] alpha = bands(size, 30, 40, 0, 26);
        int c = size / 2;
        for (int y = c - 8; y <= c + 8; y++) for (int x = c - 8; x <= c + 8; x++) alpha[y * size + x] = 0;
        int[] mask = GlyphIcons.figureInsidePlate(alpha, size);
        org.junit.Assert.assertNotNull(mask);
        org.junit.Assert.assertEquals(255, mask[c * size + c + 28]);   // the cut ring is figure
        org.junit.Assert.assertEquals(255, mask[c * size + c]);        // so is the island's hole
        org.junit.Assert.assertEquals(0, mask[c * size + c + 15]);     // the island is plate
        org.junit.Assert.assertEquals(0, mask[c * size + c + 35]);     // so is the rim
    }
}
