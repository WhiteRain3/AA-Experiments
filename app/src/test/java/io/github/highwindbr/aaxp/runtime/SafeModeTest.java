package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SafeModeTest {
    @Test public void signatureIsNullWhenNoLookSettingIsSet() {
        assertNull(SafeMode.signature(null, "", null, "epoch"));
        assertNull(SafeMode.signature((String) null));
    }

    @Test public void signatureChangesWithSettingsAndEpoch() {
        String first = SafeMode.signature("Primary=FF000000", null, "1");
        assertEquals(first, SafeMode.signature("Primary=FF000000", null, "1"));
        assertNotEquals(first, SafeMode.signature("Primary=FF000001", null, "1"));
        assertNotEquals(first, SafeMode.signature("Primary=FF000000", null, "2"));
        assertNotEquals(SafeMode.signature("a", "", "1"), SafeMode.signature("", "a", "1"));
    }

    @Test public void oneCrashDoesNotTripTwoInTheWindowDo() {
        SafeMode.State state = SafeMode.atStart(null, "sig");
        assertFalse(state.tripped);
        state = SafeMode.afterCrash(state, 1_000L);
        assertFalse(state.tripped);
        state = SafeMode.afterCrash(state, 1_000L + SafeMode.WINDOW_MS);
        assertTrue(state.tripped);
    }

    @Test public void crashesFarApartDoNotTrip() {
        SafeMode.State state = SafeMode.afterCrash(SafeMode.atStart(null, "sig"), 1_000L);
        state = SafeMode.afterCrash(state, 2_000L + SafeMode.WINDOW_MS);
        assertFalse(state.tripped);
        assertEquals(1, state.crashes.length);
    }

    @Test public void trippedStateSurvivesRestartUntilSettingsChange() {
        SafeMode.State tripped = SafeMode.afterCrash(SafeMode.afterCrash(SafeMode.atStart(null, "sig"), 1L), 2L);
        SafeMode.State stored = SafeMode.parse(SafeMode.encode(tripped));
        assertTrue(SafeMode.atStart(stored, "sig").tripped);
        SafeMode.State changed = SafeMode.atStart(stored, "other");
        assertFalse(changed.tripped);
        assertEquals(0, changed.crashes.length);
    }

    @Test public void stateRoundTripsAndRejectsGarbage() {
        SafeMode.State state = SafeMode.parse(SafeMode.encode(new SafeMode.State("ab-3", new long[] {5L, 9L}, false)));
        assertEquals("ab-3", state.signature);
        assertEquals(2, state.crashes.length);
        assertEquals(9L, state.crashes[1]);
        assertFalse(state.tripped);
        assertNull(SafeMode.parse(null));
        assertNull(SafeMode.parse(""));
        assertNull(SafeMode.parse("aax-safe-1\nsig\nx,y\n0"));
        assertNull(SafeMode.parse("other\nsig\n\n0"));
    }

    @Test public void fileTokenIsSafe() {
        assertEquals("com.google.android.projection.gearhead_projection",
                SafeMode.fileToken("com.google.android.projection.gearhead:projection"));
        assertEquals("unknown", SafeMode.fileToken(null));
        assertEquals(".._.._x", SafeMode.fileToken("../../x"));
    }
}
