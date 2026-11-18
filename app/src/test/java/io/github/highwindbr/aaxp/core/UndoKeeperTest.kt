package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UndoKeeperTest {
    @get:Rule val folder = TemporaryFolder()
    private fun snap(label: String) = LookSnapshot(label, "Primary=FF000000", null, null, null, null, null, false)

    @Test fun capturedCopyIsLoadedWithItsLabel() {
        val keeper = UndoKeeper(folder.newFile())
        assertTrue(keeper.capture { snap("first") })
        assertEquals("first", keeper.load()!!.label)
    }

    @Test fun failedCaptureRemovesTheOldCopy() {
        val keeper = UndoKeeper(folder.newFile())
        keeper.capture { snap("first") }
        assertFalse(keeper.capture { error("disk full") })
        assertFalse(keeper.exists()); assertNull(keeper.load())
    }

    @Test fun missingOrUnreadableCopyLoadsAsNull() {
        val file = folder.newFile()
        val keeper = UndoKeeper(file)
        file.delete()
        assertNull(keeper.load())
        file.writeText("not json")
        assertNull(keeper.load())
    }

    @Test fun clearRemovesTheCopy() {
        val keeper = UndoKeeper(folder.newFile())
        keeper.capture { snap("x") }
        keeper.clear()
        assertFalse(keeper.exists())
    }
}
