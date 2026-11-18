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

    @Test fun sharpPictureLowersBothWirelessQpValues() {
        val s = FlagSwitches.SHARP_PICTURE
        assertEquals(setOf("VideoEncoderParams__max_qp_wireless", "VideoEncoderParams__min_qp_wireless"), s.flags.keys)
        assertTrue(s.flags.getValue("VideoEncoderParams__max_qp_wireless").toInt() < 30)
        assertTrue(s.flags.getValue("VideoEncoderParams__min_qp_wireless").toInt() < 15)
        assertTrue(s.flags.getValue("VideoEncoderParams__min_qp_wireless").toInt() <= s.flags.getValue("VideoEncoderParams__max_qp_wireless").toInt())
    }

    @Test fun sharpPictureOffRestoresTheOwnersEarlierMaxQp() {
        val s = FlagSwitches.SHARP_PICTURE
        val max = "VideoEncoderParams__max_qp_wireless"; val min = "VideoEncoderParams__min_qp_wireless"
        val before = FlagSwitches.beforeValues(s, mapOf(max to "LONG:26"), null)
        // Turned on twice (for example after a failed attempt): the 26 must not be lost.
        val again = FlagSwitches.beforeValues(s, mapOf(max to "LONG:22", min to "LONG:10"), before)
        assertEquals("26", FlagSwitches.valueToRestore(again, max))
        assertNull(FlagSwitches.valueToRestore(again, min))
        assertTrue(FlagSwitches.isOn(s, mapOf(max to "LONG:22", min to "LONG:10")))
        assertFalse(FlagSwitches.isOn(s, mapOf(max to "LONG:26")))
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

    @Test fun offWithoutARecordNeverRemovesHandSetValues() {
        val on = mapOf(a to "BOOLEAN:true", b to "BOOLEAN:true")
        assertNull(FlagSwitches.turnOffPlan(sw, on, null))
    }

    @Test fun offOnlyTouchesFlagsStillAtTheSwitchValue() {
        val before = mapOf(a to "BOOLEAN:false", b to null)
        val plan = FlagSwitches.turnOffPlan(sw, mapOf(a to "BOOLEAN:true", b to "BOOLEAN:false"), before)!!
        assertEquals(mapOf<String, String?>(a to "false"), plan)
        assertEquals(mapOf<String, String?>(a to "false", b to null),
            FlagSwitches.turnOffPlan(sw, mapOf(a to "BOOLEAN:true", b to "BOOLEAN:true"), before))
    }

    @Test fun aHandEditSinceWinsOverTheOldMemory() {
        val old = mapOf(a to "BOOLEAN:false", b to null)
        // The owner changed b by hand to false, then turns the switch on again.
        val again = FlagSwitches.beforeValues(sw, mapOf(a to "BOOLEAN:true", b to "BOOLEAN:false"), old)
        assertEquals("BOOLEAN:false", again[a]); assertEquals("BOOLEAN:false", again[b])
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
