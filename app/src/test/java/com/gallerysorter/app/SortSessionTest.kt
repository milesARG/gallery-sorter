package com.gallerysorter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortSessionTest {

    private fun session() = SortSession(listOf(1L, 2L, 3L, 4L, 5L))

    @Test
    fun swipesAdvanceAndCount() {
        val s = session()
        assertEquals(1L, s.current)
        s.decide(keep = true)
        s.decide(keep = false)
        assertEquals(3L, s.current)
        assertEquals(1, s.keptCount)
        assertEquals(1, s.markedCount)
        assertEquals(2, s.reviewed)
        assertEquals(3, s.remaining)
        assertEquals(listOf(4L, 5L), s.upNext(3))
    }

    @Test
    fun finishingLeavesNoCurrent() {
        val s = SortSession(listOf(1L, 2L))
        s.decide(true)
        s.decide(false)
        assertNull(s.current)
        assertFalse(s.decide(true))
        assertEquals(0, s.remaining)
    }

    @Test
    fun undoRestoresPreviousPhotoAndCounts() {
        val s = session()
        s.decide(false)
        s.decide(true)
        assertEquals(2L, s.undo())
        assertEquals(2L, s.current)
        assertEquals(0, s.keptCount)
        assertEquals(1, s.markedCount)
        assertEquals(1L, s.undo())
        assertEquals(1L, s.current)
        assertEquals(0, s.markedCount)
        assertNull(s.undo())
    }

    @Test
    fun undoAfterFinishing() {
        val s = SortSession(listOf(1L, 2L))
        s.decide(true)
        s.decide(false)
        assertEquals(2L, s.undo())
        assertEquals(2L, s.current)
    }

    @Test
    fun markedIdsAndUnmark() {
        val s = session()
        s.decide(false); s.decide(true); s.decide(false)
        assertEquals(listOf(1L, 3L), s.markedIds())
        assertTrue(s.unmark(1L))
        assertEquals(listOf(3L), s.markedIds())
        assertEquals(2, s.keptCount)
        assertEquals(1, s.markedCount)
        assertFalse(s.unmark(2L))
    }

    @Test
    fun trashingMovesMarkedToTrashedAndBlocksUndo() {
        val s = SortSession(listOf(1L, 2L, 3L))
        s.decide(false); s.decide(true); s.decide(false)
        s.markTrashed(s.markedIds())
        assertEquals(0, s.markedCount)
        assertEquals(2, s.trashedCount)
        assertEquals(1, s.keptCount)
        assertEquals(3, s.reviewed)
        assertTrue(s.markedIds().isEmpty())
        assertFalse(s.canUndo)
        assertNull(s.undo())
    }

    @Test
    fun encodeDecodeRoundTripRestoresPosition() {
        val s = session()
        s.decide(true); s.decide(false); s.decide(false)
        s.markTrashed(listOf(2L))
        val restored = SortSession(s.photos, SortSession.decode(s.encode()))
        assertEquals(4L, restored.current)
        assertEquals(1, restored.keptCount)
        assertEquals(1, restored.markedCount)
        assertEquals(1, restored.trashedCount)
        assertEquals(listOf(3L), restored.markedIds())
    }

    @Test
    fun restoreIgnoresPhotosThatNoLongerExist() {
        val saved = "1:k;99:d;2:d;"
        val s = SortSession(listOf(1L, 2L, 3L), SortSession.decode(saved))
        assertEquals(3L, s.current)
        assertEquals(1, s.keptCount)
        assertEquals(1, s.markedCount)
    }

    @Test
    fun decodeSkipsGarbage() {
        assertEquals(listOf(5L to Mark.KEEP), SortSession.decode("5:k;x:d;7:?;;"))
    }
}
