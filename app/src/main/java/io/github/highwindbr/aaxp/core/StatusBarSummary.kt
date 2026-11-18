package io.github.highwindbr.aaxp.core

/** The one-line summaries shown on the Status bar page's folded sections: what is set, or "Default". */
object StatusBarSummary {
    private fun join(parts: List<String>) = if (parts.isEmpty()) "Default" else parts.joinToString(", ")

    fun clock(size: Int, defaultSize: Int, bold: Boolean, colour: Int?): String = join(buildList {
        if (size != defaultSize) add("$size%")
        if (bold) add("bold")
        if (colour != null) add(ColourLabel.of(colour))
    })

    fun battery(percent: Boolean, byLevel: Boolean, size: Int, defaultSize: Int): String = when {
        !percent -> "Icon only"
        else -> join(buildList {
            add("Number")
            if (size != defaultSize) add("$size%")
            if (byLevel) add("by level")
        }).replace("Default", "Number")
    }

    fun icons(size: Int, defaultSize: Int, colour: Int?): String = join(buildList {
        if (size != defaultSize) add("$size%")
        if (colour != null) add(ColourLabel.of(colour))
    })

    fun background(shown: Boolean, colour: Int?, radius: Int?): String = when {
        !shown -> "Hidden"
        else -> join(buildList {
            if (colour != null) add(ColourLabel.of(colour))
            if (radius != null) add("corners $radius")
        })
    }
}
