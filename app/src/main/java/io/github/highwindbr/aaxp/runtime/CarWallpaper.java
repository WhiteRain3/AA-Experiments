package io.github.highwindbr.aaxp.runtime;

/**
 * A wallpaper for the car screen that differs from the phone's own.
 *
 * Android Auto can show "the phone's wallpaper", which it obtains from the platform
 * WallpaperManager. When a custom image is configured, the module answers those platform calls
 * inside Android Auto with that image instead. Nothing on the phone itself changes.
 */
public final class CarWallpaper {
    /** Remote preference holding the version of the configured image; absent means disabled. */
    public static final String VERSION_KEY = "wallpaper.custom_version";
    /** Remote file holding the image (JPEG). */
    public static final String FILE_NAME = "car_wallpaper.jpg";
    /**
     * Set (to the wallpaper version) when a slice for the rail is stored too. The rail is a
     * separate window that Android Auto never paints the wallpaper into, so the module adds it.
     */
    public static final String RAIL_KEY = "wallpaper.rail_version";
    public static final String RAIL_FILE_NAME = "car_wallpaper_rail.jpg";
    /**
     * How the rail strip is painted, stored as "color=AARRGGBB;strength=80"; every part is
     * optional. The colour is the strip's background; the strength is how strongly the wallpaper
     * slice shows over it, in percent. The defaults match how Android Auto's launcher shows the
     * wallpaper: 80% over dark grey.
     */
    public static final String RAIL_LOOK_KEY = "wallpaper.rail_look";
    public static final int DEFAULT_RAIL_COLOR = 0xFF252626;
    public static final int DEFAULT_RAIL_STRENGTH = 80;

    public static final class RailLook {
        /** Background of the rail strip; null means the default (and nothing when there is no slice). */
        public final Integer color;
        /** 0 to 100: how strongly the wallpaper slice shows over the background. */
        public final int strength;

        public RailLook(Integer color, int strength) {
            this.color = color;
            this.strength = Math.max(0, Math.min(100, strength));
        }

        public boolean isDefault() {
            return color == null && strength == DEFAULT_RAIL_STRENGTH;
        }

        public int colorOrDefault() {
            return color == null ? DEFAULT_RAIL_COLOR : color;
        }

        /** The slice's opacity as a view alpha, 0 to 1. */
        public float alpha() {
            return strength / 100f;
        }
    }

    /** Parses the stored rail look. Missing, empty or malformed text gives the defaults. Never throws. */
    public static RailLook parseRailLook(String text) {
        RailLook defaults = new RailLook(null, DEFAULT_RAIL_STRENGTH);
        if (text == null || text.isEmpty()) return defaults;
        Integer color = null;
        int strength = DEFAULT_RAIL_STRENGTH;
        for (String part : text.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) return defaults;
            String name = part.substring(0, equals), value = part.substring(equals + 1);
            try {
                switch (name) {
                    case "color":
                        if (value.length() != 8) return defaults;
                        color = (int) Long.parseLong(value, 16);
                        break;
                    case "strength":
                        strength = Integer.parseInt(value);
                        if (strength < 0 || strength > 100) return defaults;
                        break;
                    default:
                        return defaults;
                }
            } catch (NumberFormatException e) {
                return defaults;
            }
        }
        return new RailLook(color, strength);
    }

    /** The stored form of {@code look}; null when it is the default. */
    public static String encodeRailLook(RailLook look) {
        if (look == null || look.isDefault()) return null;
        StringBuilder text = new StringBuilder();
        if (look.color != null) text.append("color=").append(String.format("%08X", look.color));
        if (look.strength != DEFAULT_RAIL_STRENGTH) {
            if (text.length() > 0) text.append(';');
            text.append("strength=").append(look.strength);
        }
        return text.toString();
    }

    /** Longest edge stored, in pixels. Car screens are far smaller than this. */
    public static final int MAX_EDGE = 2048;

    private CarWallpaper() { }

    public static boolean isEnabled(String version) {
        return version != null && !version.isEmpty();
    }

    /** How a WallpaperManager method should be answered, judged by its name and return type. */
    public enum Kind { DRAWABLE, BITMAP, FILE, NONE }

    public static Kind kindOf(String methodName, String returnTypeName) {
        if (methodName == null || returnTypeName == null) return Kind.NONE;
        switch (returnTypeName) {
            case "android.graphics.drawable.Drawable":
                return methodName.equals("getDrawable") || methodName.equals("peekDrawable")
                        || methodName.equals("getFastDrawable") || methodName.equals("peekFastDrawable")
                        ? Kind.DRAWABLE : Kind.NONE;
            case "android.graphics.Bitmap":
                return methodName.startsWith("getBitmap") || methodName.startsWith("peekBitmap")
                        ? Kind.BITMAP : Kind.NONE;
            case "android.os.ParcelFileDescriptor":
                return methodName.equals("getWallpaperFile") ? Kind.FILE : Kind.NONE;
            default:
                return Kind.NONE;
        }
    }

    /** Power-of-two sample size that brings the longest edge to at most {@code maxEdge}. */
    public static int sampleSize(int width, int height, int maxEdge) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (longest / (sample * 2) >= maxEdge) sample *= 2;
        return sample;
    }
}
