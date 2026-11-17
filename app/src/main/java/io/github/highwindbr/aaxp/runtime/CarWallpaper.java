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
