package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.ResourceColors
import io.github.highwindbr.aaxp.runtime.ThemeColors

/**
 * Exactly what applying a look writes. A null text means "switch that part off"; a null wallpaper
 * value means "leave the wallpaper settings as they are".
 */
class LookPlan(
    val themeColors: String?,
    val glyphColors: String?,
    val statusBar: String?,
    val resourceColors: String?,
    val wallpaperDim: Int?,
    val wallpaperBlur: Int?
) {
    companion object {
        /** [currentStatusBar] is the owner's present status bar setting; a look only changes its colours and background. */
        fun of(look: CarTheme, currentStatusBar: String? = null) = LookPlan(
            ThemeColors.encode(look.roles), look.glyphColors, StatusBarStyle.merge(currentStatusBar, look.statusBar),
            ResourceColors.encode(look.resourceColors), look.wallpaperDim?.coerceIn(0, 80),
            look.wallpaperBlur?.coerceIn(0, BlurLevel.MAX))
    }
}
