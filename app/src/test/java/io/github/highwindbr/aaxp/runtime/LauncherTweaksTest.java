package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherTweaksTest {
    @Test public void roundTrip() {
        String text = LauncherTweaks.encode(new LauncherTweaks.Config(true));
        assertEquals("names=off", text);
        LauncherTweaks.Config parsed = LauncherTweaks.parse(text);
        assertNotNull(parsed);
        assertTrue(parsed.hideNames);
    }

    @Test public void nothingToChangeIsNull() {
        assertNull(LauncherTweaks.encode(null));
        assertNull(LauncherTweaks.encode(new LauncherTweaks.Config(false)));
        assertNull(LauncherTweaks.parse(null));
        assertNull(LauncherTweaks.parse(""));
    }

    @Test public void malformedIsNull() {
        assertNull(LauncherTweaks.parse("names"));
        assertNull(LauncherTweaks.parse("names=on"));
        assertNull(LauncherTweaks.parse("=off"));
        assertNull(LauncherTweaks.parse("unknown=off"));
        assertNull(LauncherTweaks.parse("names=off;unknown=1"));
    }

    @Test public void emptyConfigSaysSo() {
        assertTrue(new LauncherTweaks.Config(false).isEmpty());
    }
}
