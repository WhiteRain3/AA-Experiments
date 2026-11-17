package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarWallpaperTest {
    @Test public void enabledOnlyWithAVersion() {
        assertTrue(CarWallpaper.isEnabled("1700000000000"));
        assertFalse(CarWallpaper.isEnabled(null));
        assertFalse(CarWallpaper.isEnabled(""));
    }

    @Test public void onlyWallpaperReadsAreAnswered() {
        String drawable = "android.graphics.drawable.Drawable";
        assertEquals(CarWallpaper.Kind.DRAWABLE, CarWallpaper.kindOf("getDrawable", drawable));
        assertEquals(CarWallpaper.Kind.DRAWABLE, CarWallpaper.kindOf("peekFastDrawable", drawable));
        assertEquals(CarWallpaper.Kind.BITMAP, CarWallpaper.kindOf("getBitmap", "android.graphics.Bitmap"));
        assertEquals(CarWallpaper.Kind.FILE, CarWallpaper.kindOf("getWallpaperFile", "android.os.ParcelFileDescriptor"));
        // Unrelated drawables, setters and colour queries are left alone.
        assertEquals(CarWallpaper.Kind.NONE, CarWallpaper.kindOf("getBuiltInDrawable", drawable));
        assertEquals(CarWallpaper.Kind.NONE, CarWallpaper.kindOf("setBitmap", "int"));
        assertEquals(CarWallpaper.Kind.NONE, CarWallpaper.kindOf("getWallpaperColors", "android.app.WallpaperColors"));
        assertEquals(CarWallpaper.Kind.NONE, CarWallpaper.kindOf(null, drawable));
    }

    @Test public void sampleSizeKeepsLongestEdgeNearLimit() {
        assertEquals(1, CarWallpaper.sampleSize(1920, 1080, 2048));
        assertEquals(1, CarWallpaper.sampleSize(4000, 3000, 2048));
        assertEquals(2, CarWallpaper.sampleSize(4096, 3072, 2048));
        assertEquals(4, CarWallpaper.sampleSize(9000, 6000, 2048));
    }
}
