package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class ColourLabelTest {
    @Test fun noColourIsDefault() = assertEquals("Default", ColourLabel.of(null))

    @Test fun fullyTransparentIsSaidInWords() {
        assertEquals("Transparent", ColourLabel.of(0x00FFFFFF)); assertEquals("Transparent", ColourLabel.of(0x00000000))
    }

    @Test fun opaqueColoursShowThreeBytes() = assertEquals("#FF2D95", ColourLabel.of(0xFFFF2D95.toInt()))

    @Test fun partlyTransparentKeepsItsAlpha() = assertEquals("#80FF2D95", ColourLabel.of(0x80FF2D95.toInt()))
}
