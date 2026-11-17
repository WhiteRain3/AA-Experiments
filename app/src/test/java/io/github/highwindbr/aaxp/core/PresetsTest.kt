package io.github.highwindbr.aaxp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    @Test fun diffReplacesTheCurrentSet() {
        val current = mapOf("A__keep" to "BOOLEAN:true", "A__change" to "LONG:450", "A__drop" to "BOOLEAN:true")
        val target = mapOf("A__keep" to "BOOLEAN:true", "A__change" to "LONG:900", "A__new" to "STRING:system_theme")
        val diff = Presets.diff(current, target)
        assertEquals(mapOf("A__new" to "STRING:system_theme"), diff.added)
        assertEquals(mapOf("A__change" to ("LONG:450" to "LONG:900")), diff.changed)
        assertEquals(mapOf("A__drop" to "BOOLEAN:true"), diff.removed)
        assertEquals(3, diff.size)
        assertTrue(Presets.diff(current, current).isEmpty)
    }

    @Test fun namesAreTrimmedAndBounded() {
        assertEquals("Daily drive", Presets.cleanName("  Daily   drive "))
        assertNull(Presets.cleanName("   "))
        assertEquals(Presets.MAX_NAME_LENGTH, Presets.cleanName("x".repeat(200))!!.length)
    }

    @Test fun encodeDecodeRoundTripAndBadValuesAreDropped() {
        val presets = listOf(Preset("Daily", mapOf("A__b" to "BOOLEAN:true", "A__c" to "LONG:900"), "18.0"))
        assertEquals(presets, Presets.decode(Presets.encode(presets)))
        val decoded = Presets.decode("""{"schemaVersion":1,"presets":{"X":{"overrides":{"A__ok":"BOOLEAN:false","A__bad":"BOOLEAN:maybe","A__junk":"nonsense"}}}}""")
        assertEquals(mapOf("A__ok" to "BOOLEAN:false"), decoded.single().overrides)
    }

    @Test fun displayValueHidesTheType() {
        assertEquals("system_theme", Presets.displayValue("STRING:system_theme"))
        assertEquals("a:b", Presets.displayValue("STRING:a:b"))
    }
}
