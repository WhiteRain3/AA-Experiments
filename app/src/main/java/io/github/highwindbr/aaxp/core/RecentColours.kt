package io.github.highwindbr.aaxp.core

/** The colours picked most recently, newest first, kept so they can be picked again. */
object RecentColours {
    const val MAX = 8

    /** [colours] with [colour] moved or added at the front; no repeats, at most [MAX]. */
    fun push(colours: List<Int>, colour: Int): List<Int> = (listOf(colour) + colours.filterNot { it == colour }).take(MAX)

    fun encode(colours: List<Int>): String = colours.take(MAX).joinToString(",") { "%08X".format(it) }

    /** Reads the stored text; anything unreadable is skipped. */
    fun decode(text: String?): List<Int> = text.orEmpty().split(',').mapNotNull {
        if (it.length == 8) it.toLongOrNull(16)?.toInt() else null
    }.distinct().take(MAX)
}
