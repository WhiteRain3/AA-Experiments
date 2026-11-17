package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class CarPreviewColorsTest {
    @Test fun unsetRolesUseDefaultsAndAreFlaggedApproximate() {
        val colors = CarPreviewColors.resolve(emptyMap(), null)
        assertTrue(colors.usesApproximation)
        assertEquals(0xFF2A2B2C.toInt(), colors.surface)
        assertEquals(CarPreviewColors.PLACEHOLDER_ICON, colors.railIcon)
    }

    @Test fun setRolesAreUsed() {
        val colors = CarPreviewColors.resolve(mapOf("Surface" to 0xFF112233.toInt()), null)
        assertEquals(0xFF112233.toInt(), colors.surface)
        assertTrue(colors.usesApproximation)
    }

    @Test fun allSevenSetIsNotApproximate() {
        val roles = Themes.CONFIRMED_ROLES.associateWith { 0xFF123456.toInt() }
        assertFalse(CarPreviewColors.resolve(roles, null).usesApproximation)
    }

    @Test fun glyphColorsColourTheRailIcons() {
        val colors = CarPreviewColors.resolve(emptyMap(), "FFFF0000,FF00FF00")
        assertEquals(0xFFFF0000.toInt(), colors.railIcon)
        assertEquals(0xFF00FF00.toInt(), colors.railIconBackground)
    }

    @Test fun badGlyphTextFallsBackToPlaceholder() {
        assertEquals(CarPreviewColors.PLACEHOLDER_ICON, CarPreviewColors.resolve(emptyMap(), "nonsense").railIcon)
    }
}
