package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class RecentColoursTest {
    @Test fun newestFirstWithoutRepeats() {
        val list = RecentColours.push(RecentColours.push(emptyList(), 0xFF111111.toInt()), 0xFF222222.toInt())
        assertEquals(listOf(0xFF222222.toInt(), 0xFF111111.toInt()), list)
        assertEquals(listOf(0xFF111111.toInt(), 0xFF222222.toInt()), RecentColours.push(list, 0xFF111111.toInt()))
    }

    @Test fun keepsOnlyTheLatestEight() {
        var list = emptyList<Int>()
        for (i in 1..12) list = RecentColours.push(list, 0xFF000000.toInt() or i)
        assertEquals(RecentColours.MAX, list.size)
        assertEquals(0xFF00000C.toInt(), list.first())
    }

    @Test fun survivesEncodingAndSkipsJunk() {
        val list = listOf(0xFFFF0000.toInt(), 0x80123456.toInt())
        assertEquals(list, RecentColours.decode(RecentColours.encode(list)))
        assertEquals(listOf(0xFF00FF00.toInt()), RecentColours.decode("zz,FF00FF00,12,FF00FF00"))
        assertTrue(RecentColours.decode(null).isEmpty())
    }
}
