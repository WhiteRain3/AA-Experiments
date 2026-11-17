package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlRequestsTest {
    @Test public void freshRequestIsAccepted() {
        String request = ControlRequests.make(1_000_000L, "n");
        assertTrue(ControlRequests.isFresh(request, 1_000_000L));
        assertTrue(ControlRequests.isFresh(request, 1_000_000L + ControlRequests.MAX_AGE_MS));
    }

    @Test public void staleFutureAndMalformedRequestsAreRejected() {
        String request = ControlRequests.make(1_000_000L, "n");
        assertFalse(ControlRequests.isFresh(request, 1_000_000L + ControlRequests.MAX_AGE_MS + 1));
        assertFalse("a request from the future is never honoured", ControlRequests.isFresh(request, 999_999L));
        assertFalse(ControlRequests.isFresh(null, 1L));
        assertFalse(ControlRequests.isFresh("", 1L));
        assertFalse(ControlRequests.isFresh("abc|n", 1L));
        assertFalse(ControlRequests.isFresh("1000", 1000L));
        assertFalse(ControlRequests.isFresh("1000|", 1000L));
    }

    @Test public void headUnitServicePrefersDeveloperVariant() {
        assertEquals("a.b.DeveloperHeadUnitNetworkService", ControlRequests.pickHeadUnitService(new String[] {
                "a.b.SomethingElse", "a.b.HeadUnitHelper", "a.b.DeveloperHeadUnitNetworkService" }));
        assertEquals("a.b.HeadUnitHelper", ControlRequests.pickHeadUnitService(new String[] {
                "a.b.SomethingElse", "a.b.HeadUnitHelper" }));
        assertNull(ControlRequests.pickHeadUnitService(new String[] { "a.b.SomethingElse", null }));
        assertNull(ControlRequests.pickHeadUnitService(null));
    }
}
