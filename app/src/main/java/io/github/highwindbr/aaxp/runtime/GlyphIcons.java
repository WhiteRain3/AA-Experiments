package io.github.highwindbr.aaxp.runtime;

/**
 * Glyph-style app icons on the car screen.
 *
 * Most apps ship a single-colour ("monochrome") layer for Android's themed icons. When this
 * mode is on, the module replaces the icons Android Auto loads for apps with that layer, drawn
 * in one colour on a plain background. Apps without such a layer keep their normal icon.
 */
public final class GlyphIcons {
    /** Remote preference: "RRGGBB,RRGGBB" as glyph colour and background colour; absent = off. */
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
     * {@code argb} is the icon's foreground rendered on a transparent square of {@code size} pixels.
     * Returns one alpha value (0-255) per pixel, or null when no usable shape can be derived, in
     * which case the caller keeps the original icon.
     * <ul>
     *   <li>A foreground that is mostly transparent is already a shape: its alpha is the mask.</li>
     *   <li>A foreground that fills the square is treated as artwork on a backdrop: the backdrop
     *       brightness is taken from the outer ring, and pixels that differ from it form the
     *       glyph.</li>
     * </ul>
     */
    public static int[] glyphAlpha(int[] argb, int size) {
        if (argb == null || size <= 0 || argb.length != size * size) return null;
        int total = argb.length;
        int opaque = 0;
        for (int pixel : argb) if ((pixel >>> 24) >= 128) opaque++;
        if (opaque < total / 50) return null; // nothing drawn

        int[] mask = new int[total];
        if (opaque < total * 0.55f) {
            for (int i = 0; i < total; i++) mask[i] = argb[i] >>> 24;
            return mask;
        }

        // Backdrop brightness: average of the opaque pixels in the outer ring.
        int ring = Math.max(1, size / 12);
        long sum = 0;
        int count = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (x >= ring && x < size - ring && y >= ring && y < size - ring) continue;
                int pixel = argb[y * size + x];
                if ((pixel >>> 24) < 128) continue;
                sum += luma(pixel);
                count++;
            }
        }
        if (count == 0) return null;
        int backdrop = (int) (sum / count);

        int marked = 0;
        for (int i = 0; i < total; i++) {
            int pixel = argb[i];
            float difference = Math.abs(luma(pixel) - backdrop) / 255f;
            float strength = (difference - 0.12f) / 0.25f;
            if (strength < 0f) strength = 0f;
            if (strength > 1f) strength = 1f;
            mask[i] = (int) (strength * (pixel >>> 24));
            if (mask[i] >= 128) marked++;
        }
        // Too little or almost everything marked means the artwork has no clear figure.
        if (marked < total / 50 || marked > total * 0.85f) return null;
        return mask;
    }

    private static int luma(int pixel) {
        return (299 * ((pixel >> 16) & 0xff) + 587 * ((pixel >> 8) & 0xff) + 114 * (pixel & 0xff)) / 1000;
    }

    /** Parses "RRGGBB,RRGGBB" into opaque ARGB {glyph, background}; null when malformed. */
    public static int[] parseColors(String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length != 2) return null;
        int[] out = new int[2];
        for (int i = 0; i < 2; i++) {
            String hex = parts[i].trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() != 6) return null;
            try {
                out[i] = 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return out;
    }
}
