package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class LauncherViewTest {
    @Test fun namesAreShownUnlessHidden() {
        assertTrue(LauncherView.namesShown(hideNames = false))
        assertFalse(LauncherView.namesShown(hideNames = true))
    }

    @Test fun switchingNamesOffStoresHideNames() {
        assertTrue(LauncherView.hideNamesFor(namesShown = false))
        assertFalse(LauncherView.hideNamesFor(namesShown = true))
        listOf(true, false).forEach { assertEquals(it, LauncherView.hideNamesFor(LauncherView.namesShown(it))) }
    }
}
