package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class StatusBarSummaryTest {
    @Test fun unchangedSectionsSayDefault() {
        assertEquals("Default", StatusBarSummary.clock(100, 100, false, null))
        assertEquals("Default", StatusBarSummary.icons(100, 100, null))
        assertEquals("Default", StatusBarSummary.background(true, null, null))
    }

    @Test fun iconsMentionTheRedWarningOnlyWithAColour() {
        assertEquals("#00FFFF, red when low", StatusBarSummary.icons(100, 100, 0xFF00FFFF.toInt(), keepsLowRed = true))
        assertEquals("Default", StatusBarSummary.icons(100, 100, null, keepsLowRed = true))
        assertEquals("#00FFFF", StatusBarSummary.icons(100, 100, 0xFF00FFFF.toInt()))
    }

    @Test fun clockListsWhatIsSet() =
        assertEquals("140%, bold, #00FFFF", StatusBarSummary.clock(140, 100, true, 0xFF00FFFF.toInt()))

    @Test fun batteryIsIconOnlyUntilThePercentageIsOn() {
        assertEquals("Icon only", StatusBarSummary.battery(false, false, 100, 100))
        assertEquals("Number", StatusBarSummary.battery(true, false, 100, 100))
        assertEquals("Number, 80%, by level", StatusBarSummary.battery(true, true, 80, 100))
    }

    @Test fun backgroundSaysHiddenOrWhatIsSet() {
        assertEquals("Hidden", StatusBarSummary.background(false, 0xFF112233.toInt(), 8))
        assertEquals("#112233, corners 8", StatusBarSummary.background(true, 0xFF112233.toInt(), 8))
        assertEquals("Transparent", StatusBarSummary.background(true, 0x00FFFFFF, null))
    }
}
