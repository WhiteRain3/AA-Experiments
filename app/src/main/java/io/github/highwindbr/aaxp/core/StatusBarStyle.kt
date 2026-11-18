package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.StatusBarTweaks

/** Applying a style to the status bar without losing what the owner set up there. */
object StatusBarStyle {
    /**
     * [look]'s clock and icon colours and background setting laid over [current]. Everything else
     * of the current setting (hidden parts, battery number with its size, colour and colouring by
     * level, clock size and bold, plate colour and corners, icon size) is kept. A look without status bar changes leaves [current] as it is.
     */
    fun merge(current: String?, look: String?): String? {
        val style = StatusBarTweaks.parse(look) ?: return current
        val now = StatusBarTweaks.parse(current)
        // Start from everything the owner has, so settings added later are kept without a change here.
        val builder = now?.toBuilder() ?: StatusBarTweaks.Builder()
        builder.clockColor = style.clockColor ?: now?.clockColor
        builder.iconColor = style.iconColor ?: now?.iconColor
        builder.noPlate = style.noPlate
        return StatusBarTweaks.encode(builder.build())
    }
}
