package io.github.highwindbr.aaxp.runtime;

/**
 * Thickness of the focus border Android Auto draws around the map on the dashboard.
 *
 * Android Auto 18.0 draws it as the foreground of the view with id {@code focus_frame}: a
 * rectangle whose stroke is 6dp when focused and 4dp when hovered. The flag
 * "Coolwalk__nav_app_border_width" is declared but never read in that version, so the module
 * replaces the foreground instead. The setting is the focused width in dp; absent means Android
 * Auto's own.
 */
public final class MapBorder {
    public static final String KEY = "look.map_border_width";
    /** The view that carries the border as its foreground. */
    public static final String FRAME = "focus_frame";
    /** Theme attributes the original border takes its colour and corner radius from. */
    public static final String COLOR_ATTR = "templateFocusAccentColorInverse";
    public static final String RADIUS_ATTR = "templateNoContentFocusCornerRadius";
    public static final int MIN = 0;
    public static final int MAX = 16;
    /** Android Auto's own widths, in dp. */
    public static final int OWN_FOCUSED = 6;
    public static final int OWN_HOVERED = 4;

    private MapBorder() { }

    /** The stored width in dp; null when it is missing, unreadable or out of range. Never throws. */
    public static Integer parse(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            int width = Integer.parseInt(text.trim());
            return width < MIN || width > MAX ? null : width;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The stored form of {@code width}; null (no setting) when it is null. Out of range is clamped. */
    public static String encode(Integer width) {
        return width == null ? null : String.valueOf(Math.max(MIN, Math.min(MAX, width)));
    }

    /** The hovered width that goes with a focused width, keeping Android Auto's 4 to 6 proportion. */
    public static int hovered(int focused) {
        if (focused <= 0) return 0;
        return Math.max(1, Math.round(focused * (float) OWN_HOVERED / OWN_FOCUSED));
    }

    /** A width in dp as whole pixels; anything above zero is at least one pixel. */
    public static int pixels(int dp, float density) {
        if (dp <= 0 || density <= 0f) return 0;
        return Math.max(1, Math.round(dp * density));
    }
}
