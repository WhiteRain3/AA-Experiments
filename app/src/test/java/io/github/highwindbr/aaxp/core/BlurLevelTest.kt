package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class BlurLevelTest {
    @Test fun oldValuesAreMultipliedByFour() {
        assertEquals(0, BlurLevel.fromStored(null, 0))
        assertEquals(12, BlurLevel.fromStored(null, 3))
        assertEquals(40, BlurLevel.fromStored(null, 10))
        assertEquals(40, BlurLevel.fromStored(null, 99))
    }

    @Test fun newValueWinsOverOld() {
        assertEquals(7, BlurLevel.fromStored(7, 10))
        assertEquals(40, BlurLevel.fromStored(500, null))
        assertEquals(0, BlurLevel.fromStored(-3, null))
    }

    @Test fun nothingStoredMeansOff() = assertEquals(0, BlurLevel.fromStored(null, null))

    @Test fun oldMaximumKeepsItsRadius() {
        // Old: level 10 -> 10 * edge / 200. New: level 40 -> 40 * edge / 800. Same radius.
        assertEquals(10 * 2000 / 200f, BlurLevel.radius(40, 2000), 0.001f)
        assertEquals(3 * 1000 / 200f, BlurLevel.radius(12, 1000), 0.001f)
    }

    @Test fun labelShowsOffAtZero() {
        assertEquals("Blur: off", BlurLevel.label(0))
        assertEquals("Blur: 50%", BlurLevel.label(20))
        assertEquals("Blur: 100%", BlurLevel.label(40))
    }
}
