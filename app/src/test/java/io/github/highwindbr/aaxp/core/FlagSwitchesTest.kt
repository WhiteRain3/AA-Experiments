package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class FlagSwitchesTest {
    private val sw = FlagSwitches.PARKED_APPS
    private val a = "CradleFeature__all_app_launcher_enabled"
    private val b = "CradleFeature__allow_video_apps"

    @Test fun parkedAppsSetsBothCradleFlagsToTrue() {
        assertEquals(mapOf(a to "true", b to "true"), sw.flags)
    }

    @Test fun isOnNeedsEveryFlagAtItsValue() {
        assertFalse(FlagSwitches.isOn(sw, emptyMap()))
        assertFalse(FlagSwitches.isOn(sw, mapOf(a to "BOOLEAN:true")))
        assertFalse(FlagSwitches.isOn(sw, mapOf(a to "BOOLEAN:true", b to "BOOLEAN:false")))
        assertTrue(FlagSwitches.isOn(sw, mapOf(a to "BOOLEAN:true", b to "BOOLEAN:true")))
    }

    @Test fun beforeValuesRecordCurrentOverridesOrNone() {
        val before = FlagSwitches.beforeValues(sw, mapOf(a to "BOOLEAN:false"), null)
        assertEquals("BOOLEAN:false", before[a]); assertTrue(b in before); assertNull(before[b])
    }

    @Test fun earlierMemoryIsKeptWhenTurnedOnTwice() {
        val first = FlagSwitches.beforeValues(sw, mapOf(a to "BOOLEAN:false"), null)
        val second = FlagSwitches.beforeValues(sw, mapOf(a to "BOOLEAN:true", b to "BOOLEAN:true"), first)
        assertEquals(first, second)
    }

    @Test fun restoreGivesPlainValueOrRemoval() {
        val before = mapOf(a to "BOOLEAN:false", b to null)
        assertEquals("false", FlagSwitches.valueToRestore(before, a))
        assertNull(FlagSwitches.valueToRestore(before, b))
        assertNull(FlagSwitches.valueToRestore(null, a))
    }

    @Test fun beforeSurvivesEncoding() {
        val before = mapOf(a to "BOOLEAN:false", b to null)
        assertEquals(before, FlagSwitches.decodeBefore(FlagSwitches.encodeBefore(before)))
        assertNull(FlagSwitches.decodeBefore(null)); assertNull(FlagSwitches.decodeBefore("garbage"))
    }
}
