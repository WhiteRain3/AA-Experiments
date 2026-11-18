package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class StatusBarViewTest {
    @Test fun aPartIsShownUnlessItIsInTheHideList() {
        assertTrue(StatusBarView.isShown(emptySet(), "clock"))
        assertFalse(StatusBarView.isShown(setOf("clock"), "clock"))
        assertTrue(StatusBarView.isShown(setOf("signal_icon"), "clock"))
    }

    @Test fun switchingAPartStoresTheComplement() {
        assertEquals(setOf("clock"), StatusBarView.withShown(emptySet(), "clock", shown = false))
        assertEquals(emptySet<String>(), StatusBarView.withShown(setOf("clock"), "clock", shown = true))
        assertEquals(setOf("clock"), StatusBarView.withShown(setOf("clock"), "clock", shown = false))
        assertEquals(setOf("signal_icon"), StatusBarView.withShown(setOf("signal_icon"), "clock", shown = true))
    }

    @Test fun backgroundIsTheOppositeOfNoPlate() {
        assertTrue(StatusBarView.backgroundShown(noPlate = false))
        assertFalse(StatusBarView.backgroundShown(noPlate = true))
        assertTrue(StatusBarView.noPlateFor(backgroundShown = false))
        assertFalse(StatusBarView.noPlateFor(backgroundShown = true))
        listOf(true, false).forEach { assertEquals(it, StatusBarView.noPlateFor(StatusBarView.backgroundShown(it))) }
    }
}
