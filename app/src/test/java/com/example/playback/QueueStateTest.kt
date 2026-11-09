package com.example.playback

import com.example.data.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStateTest {

    private fun createSong(id: String) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        album = "Album $id",
        isFavorite = false,
        hasInstrumental = false
    )

    @Test
    fun testStableIdentityAfterReorder() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("1")) // rev 1
        state = QueueReducer.addEntry(state, createSong("2")) // rev 2
        
        val id1Before = state.entries[0].entryId
        val id2Before = state.entries[1].entryId
        
        state = QueueReducer.reorder(state, 0, 1) // rev 3
        
        assertEquals(id2Before, state.entries[0].entryId)
        assertEquals(id1Before, state.entries[1].entryId)
        assertEquals(3, state.revision)
    }

    @Test
    fun testStableIdentityAfterRemoval() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("1"))
        state = QueueReducer.addEntry(state, createSong("2"))
        state = QueueReducer.addEntry(state, createSong("3")) // rev 3
        
        val id3Before = state.entries[2].entryId
        
        state = QueueReducer.removeEntry(state, state.entries[0].entryId) // rev 4
        
        assertEquals(2, state.entries.size)
        assertEquals(id3Before, state.entries[1].entryId)
        assertEquals(4, state.revision)
    }

    @Test
    fun testDuplicateSongsHaveUniqueEntryIds() {
        var state = QueueState()
        val song = createSong("1")
        state = QueueReducer.addEntry(state, song)
        state = QueueReducer.addEntry(state, song)
        
        assertEquals(2, state.entries.size)
        assertNotEquals(state.entries[0].entryId, state.entries[1].entryId)
    }

    @Test
    fun testCurrentEntryPreservationAfterReorder() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("1"))
        state = QueueReducer.addEntry(state, createSong("2"))
        val id2 = state.entries[1].entryId
        state = QueueReducer.setCurrentEntry(state, id2)
        
        state = QueueReducer.reorder(state, 0, 1)
        
        assertEquals(id2, state.currentEntryId)
    }

    @Test
    fun testCurrentEntryRemovalSelection() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("1"))
        state = QueueReducer.addEntry(state, createSong("2"))
        state = QueueReducer.addEntry(state, createSong("3"))
        
        val id1 = state.entries[0].entryId
        val id2 = state.entries[1].entryId
        val id3 = state.entries[2].entryId
        
        // Remove current (middle)
        state = QueueReducer.setCurrentEntry(state, id2)
        state = QueueReducer.removeEntry(state, id2)
        assertEquals(id3, state.currentEntryId) // Selects next item
        
        // Remove last current
        state = QueueReducer.removeEntry(state, id3)
        assertEquals(id1, state.currentEntryId) // Selects previous if last was removed
        
        // Remove last remaining
        state = QueueReducer.removeEntry(state, id1)
        assertEquals(null, state.currentEntryId)
    }

    @Test
    fun testRevisionIncrementsOnlyOnActualChange() {
        val state = QueueState()
        val song = createSong("1")
        
        val state1 = QueueReducer.addEntry(state, song)
        assertEquals(1, state1.revision)
        
        val state2 = QueueReducer.setCurrentEntry(state1, state1.entries[0].entryId)
        assertEquals(2, state2.revision)
        
        val state3 = QueueReducer.setCurrentEntry(state2, state2.entries[0].entryId)
        assertEquals(2, state3.revision) // No-op
        
        val state4 = QueueReducer.removeEntry(state3, "non-existent")
        assertEquals(2, state4.revision) // No-op
    }

    @Test
    fun testImmutablePendingAddAdjustments() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("1")) // Index 0
        state = QueueReducer.addEntry(state, createSong("2")) // Index 1
        
        val token = "token1"
        val pendingAdd = PendingQueueAdd(token = token, song = createSong("P"), targetIndex = 1)
        state = QueueReducer.registerPendingAdd(state, pendingAdd)
        
        // Insert entry at 0, pending add should shift to 2
        state = QueueReducer.insertEntry(state, 0, createSong("0"))
        assertEquals(2, state.pendingAdds[0].targetIndex)
        
        // Reorder 0 to 1, pending add at 2 shouldn't change
        state = QueueReducer.reorder(state, 0, 1)
        assertEquals(2, state.pendingAdds[0].targetIndex)
        
        // Remove at index 0, pending add should shift to 1
        state = QueueReducer.removeEntry(state, state.entries[0].entryId)
        assertEquals(1, state.pendingAdds[0].targetIndex)
        
        state = QueueReducer.invalidatePendingAdd(state, token)
        assertTrue(state.pendingAdds.isEmpty())
    }

    @Test
    fun testShuffleHistoryCleanup() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("1"))
        state = QueueReducer.addEntry(state, createSong("2"))
        val id1 = state.entries[0].entryId
        val id2 = state.entries[1].entryId
        
        state = state.copy(shuffleHistory = listOf(id1, id2))
        
        state = QueueReducer.removeEntry(state, id1)
        assertEquals(1, state.shuffleHistory.size)
        assertEquals(id2, state.shuffleHistory[0])
        
        state = QueueReducer.clear(state)
        assertTrue(state.shuffleHistory.isEmpty())
    }
}
