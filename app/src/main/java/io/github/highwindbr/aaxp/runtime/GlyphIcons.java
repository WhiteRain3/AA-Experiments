package io.github.highwindbr.aaxp.runtime;

/**
 * Glyph-style app icons on the car screen.
 *
 * Most apps ship a single-colour ("monochrome") layer for Android's themed icons. When this
 * mode is on, the module replaces the icons Android Auto loads for apps with that layer, drawn
 * in one colour on a plain background. Apps without such a layer keep their normal icon.
 */
public final class GlyphIcons {
    /**
     * Remote preference: glyph colour and background colour, comma separated; absent = off.
     * Each is RRGGBB (opaque) or AARRGGBB, where AA is the opacity from 00 (invisible) to FF.
     */
    public static final String KEY = "icons.glyph_colors";
    public static final String DEFAULT_COLORS = "FFFFFF,000000";

    private GlyphIcons() { }

    /** True for the platform icon-loading methods worth intercepting: they return a Drawable. */
    public static boolean isIconLoader(String methodName, String returnTypeName) {
        if (!"android.graphics.drawable.Drawable".equals(returnTypeName) || methodName == null) return false;
        switch (methodName) {
            case "loadIcon":
            case "loadUnbadgedIcon":
            case "getIcon":
            case "getBadgedIcon":
            case "getApplicationIcon":
            case "getActivityIcon":
            // Android Auto's own icon class loads an app's icon resource through this call.
            case "getDrawableForDensity":
                return true;
            default:
                return false;
        }
    }

    /**
     * Builds a glyph mask for an icon that has no monochrome layer.
     *
     * {@code argb} is the icon artwork rendered on a transparent square of {@code size} pixels.
     * Returns one alpha value (0-255) per pixel, or null when no usable figure can be derived, in
     * which case the caller keeps the original icon.
     * <ul>
     *   <li>Artwork that is a solid "plate" (a disc, rounded square or the full square) usually
     *       carries its figure inside it. The plate's dominant brightness is taken as the
     *       backdrop and whatever differs from it becomes the glyph, e.g. a play triangle on a
     *       dark disc.</li>
     *   <li>Any other shape on a transparent layer is itself the glyph.</li>
     *   <li>A plate with nothing inside is kept as a glyph only when it is small; a large flat
     *       plate would just be a blob.</li>
     * </ul>
     */
    public static int[] glyphAlpha(int[] argb, int size) {
        if (argb == null || size <= 0 || argb.length != size * size) return null;
        int total = argb.length;
        int opaque = 0;
        int minX = size, minY = size, maxX = -1, maxY = -1;
        int[] histogram = new int[16];
        long[] histogramSum = new long[16];
        for (int i = 0; i < total; i++) {
            int pixel = argb[i];
            if ((pixel >>> 24) < 128) continue;
            opaque++;
            int x = i % size, y = i / size;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            int l = luma(pixel);
            histogram[l >> 4]++;
            histogramSum[l >> 4] += l;
        }
        if (opaque < total / 50) return null; // nothing drawn

        float cover = opaque / (float) total;
        float fill = opaque / (float) ((maxX - minX + 1) * (maxY - minY + 1));
        boolean plate = fill >= 0.70f && cover >= 0.20f;

        int[] mask = new int[total];
        if (plate) {
            int dominant = 0;
            for (int bin = 1; bin < histogram.length; bin++) if (histogram[bin] > histogram[dominant]) dominant = bin;
            int backdrop = (int) (histogramSum[dominant] / histogram[dominant]);
            int figure = 0;
            for (int i = 0; i < total; i++) {
                int pixel = argb[i];
                int alpha = pixel >>> 24;
                if (alpha < 128) continue;
                float strength = (Math.abs(luma(pixel) - backdrop) / 255f - 0.12f) / 0.25f;
                if (strength < 0f) strength = 0f;
                if (strength > 1f) strength = 1f;
                mask[i] = (int) (strength * alpha);
                if (mask[i] >= 128) figure++;
            }
            float figureShare = figure / (float) opaque;
            if (figureShare >= 0.02f && figureShare <= 0.60f) return mask;
            if (cover >= 0.35f) return null; // large flat plate, or busy artwork: no clear figure
        }
        for (int i = 0; i < total; i++) mask[i] = argb[i] >>> 24;
        return mask;
    }

    /** True for Android Auto's own launcher entries that are drawn as a full square with a figure. */
    public static boolean isOwnLauncherIcon(String resourceEntryName) {
        return resourceEntryName != null && resourceEntryName.startsWith("ic_applauncher_");
    }

    private static int luma(int pixel) {
        return (299 * ((pixel >> 16) & 0xff) + 587 * ((pixel >> 8) & 0xff) + 114 * (pixel & 0xff)) / 1000;
    }

    /** Parses "colour,colour" into ARGB {glyph, background}; null when malformed. */
    public static int[] parseColors(String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length != 2) return null;
        int[] out = new int[2];
        for (int i = 0; i < 2; i++) {
            String hex = parts[i].trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() != 6 && hex.length() != 8) return null;
            try {
                long parsed = Long.parseLong(hex, 16);
                out[i] = (int) (hex.length() == 6 ? 0xFF000000L | parsed : parsed);
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return out;
    }
}
