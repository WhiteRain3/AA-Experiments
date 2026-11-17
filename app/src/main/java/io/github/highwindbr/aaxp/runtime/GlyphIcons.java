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
                return true;
            default:
                return false;
        }
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
