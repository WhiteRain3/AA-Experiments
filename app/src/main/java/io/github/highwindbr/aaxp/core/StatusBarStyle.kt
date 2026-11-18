package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.StatusBarTweaks

/** Applying a style to the status bar without losing what the owner set up there. */
object StatusBarStyle {
    /**
     * [look]'s clock and icon colours and background setting laid over [current]. Everything else
     * of the current setting (hidden parts, battery number with its size and colour, clock size
     * and bold) is kept. A look without status bar changes leaves [current] as it is.
     */
    fun merge(current: String?, look: String?): String? {
        val style = StatusBarTweaks.parse(look) ?: return current
        val now = StatusBarTweaks.parse(current)
        return StatusBarTweaks.encode(StatusBarTweaks.Config(
            now?.hide ?: LinkedHashSet<String>(), style.clockColor ?: now?.clockColor, style.iconColor ?: now?.iconColor, style.noPlate,
            now?.batteryPercent ?: false, now?.batteryColor, now?.batterySize ?: StatusBarTweaks.DEFAULT_BATTERY_SIZE,
            now?.clockSize ?: StatusBarTweaks.DEFAULT_CLOCK_SIZE, now?.clockBold ?: false))
    }
}
