package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class ResetWatcherTest {
    @Test fun openingWithAnOldResetCountDoesNotReset() {
        // The menu was used twice earlier in this app session; the page is opened afterwards.
        val watcher = ResetWatcher(initial = 2)
        assertFalse(watcher.changed(2))
    }

    @Test fun aResetWhileOpenFiresOnce() {
        val watcher = ResetWatcher(initial = 2)
        assertTrue(watcher.changed(3))
        assertFalse(watcher.changed(3))
    }

    @Test fun eachFurtherResetFiresAgain() {
        val watcher = ResetWatcher(0)
        assertTrue(watcher.changed(1)); assertTrue(watcher.changed(2)); assertFalse(watcher.changed(2))
    }
}
