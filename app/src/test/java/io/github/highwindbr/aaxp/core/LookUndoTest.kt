package io.github.highwindbr.aaxp.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LookUndoTest {
    private fun snap(colors: String? = null, glyph: String? = null, wallpaper: String? = null) =
        LookSnapshot("x", colors, glyph, null, null, if (wallpaper != null) JSONObject().put("dim", 1) else null, wallpaper)

    @Test fun snapshotRoundTrips() {
        val s = LookSnapshot("theme", "a=1", "g", "sb", "rc", JSONObject().put("dim", 20), "5-9")
        val back = LookUndo.decode(LookUndo.encode(s))!!
        assertEquals("theme", back.label); assertEquals("a=1", back.themeColors); assertEquals("g", back.glyphColors)
        assertEquals("sb", back.statusBar); assertEquals("rc", back.resourceColors)
        assertEquals("5-9", back.wallpaperStamp); assertEquals(20, back.wallpaperSettings!!.getInt("dim"))
    }

    @Test fun emptySnapshotRoundTripsAndGarbageIsRejected() {
        val back = LookUndo.decode(LookUndo.encode(snap()))!!
        assertNull(back.themeColors); assertNull(back.wallpaperStamp); assertNull(back.wallpaperSettings)
        assertNull(LookUndo.decode("not json"))
    }

    @Test fun noWallpaperMeansNothingToKeep() {
        assertNull(LookUndo.wallpaperPlan(false, "1-1", "1-1", null).snapshotStamp)
        assertNull(LookUndo.wallpaperPlan(true, null, null, null).snapshotStamp)
    }

    @Test fun unchangedImageIsNotCopiedAgain() {
        val plan = LookUndo.wallpaperPlan(true, "5-9", "5-9", "5-9")
        assertFalse(plan.copyImage); assertEquals("5-9", plan.snapshotStamp)
    }

    @Test fun firstTimeCopiesTheImage() {
        val plan = LookUndo.wallpaperPlan(true, "5-9", "5-9", null)
        assertTrue(plan.copyImage); assertEquals("5-9", plan.snapshotStamp)
    }

    @Test fun unknownAppliedStampAssumesCurrentFile() {
        assertTrue(LookUndo.wallpaperPlan(true, "5-9", null, null).copyImage)
    }

    @Test fun newlyPickedImageDoesNotReplaceTheKeptOne() {
        val plan = LookUndo.wallpaperPlan(true, "new", "old", "old")
        assertFalse(plan.copyImage); assertEquals("old", plan.snapshotStamp)
    }

    @Test fun newlyPickedImageWithNoKeptCopyCannotBeRestored() {
        val plan = LookUndo.wallpaperPlan(true, "new", "old", null)
        assertFalse(plan.copyImage); assertNull(plan.snapshotStamp)
    }

    @Test fun differencesNameTheParts() {
        assertEquals(listOf("colours", "single-colour icons"), LookUndo.differences(snap("a", "g"), snap("b", null)))
        assertTrue(LookUndo.differences(snap("a"), snap("a")).isEmpty())
        assertEquals(listOf("wallpaper"), LookUndo.differences(snap(wallpaper = "1-1"), snap()))
    }

    @Test fun wallpaperIsRemovedOnlyWhenTheCopyRecordsNone() {
        fun kept(on: Boolean?) = LookSnapshot("x", null, null, null, null, null, null, on)
        assertEquals(LookUndo.WallpaperAction.REMOVE, LookUndo.wallpaperAction(kept(false), false))
        assertEquals(LookUndo.WallpaperAction.LEAVE, LookUndo.wallpaperAction(kept(true), false))
        assertEquals(LookUndo.WallpaperAction.LEAVE, LookUndo.wallpaperAction(kept(null), false))
        assertEquals(LookUndo.WallpaperAction.RESTORE, LookUndo.wallpaperAction(kept(true), true))
        assertEquals(LookUndo.WallpaperAction.RESTORE, LookUndo.wallpaperAction(kept(null), true))
    }

    @Test fun wallpaperOnSurvivesEncoding() {
        assertEquals(false, LookUndo.decode(LookUndo.encode(LookSnapshot("x", null, null, null, null, null, null, false)))!!.wallpaperOn)
        assertNull(LookUndo.decode(LookUndo.encode(snap()))!!.wallpaperOn)
    }

    @Test fun messages() {
        assertTrue(LookUndo.restoredMessage(listOf("colours"), "saving a theme").contains("colours"))
        assertTrue(LookUndo.restoredMessage(emptyList(), "saving a theme").startsWith("Nothing to undo"))
    }
}
