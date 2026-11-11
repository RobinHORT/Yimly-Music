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
        val pendingAdd = PendingQueueAdd(token = token, entryId = state.entries[1].entryId, song = createSong("P"), targetIndex = 1)
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

    @Test
    fun testAtomicAppendWithPendingRegistration() {
        val song = createSong("1")
        val (state, pending) = QueueReducer.addEntryWithPending(QueueState(), song, "tok1")
        assertEquals(1, state.entries.size)
        assertEquals(1, state.pendingAdds.size)
        assertEquals(0, pending.targetIndex)
        assertEquals(state.entries[0].entryId, pending.entryId)
        assertEquals("tok1", pending.token)
        assertEquals(1, state.revision)
    }

    @Test
    fun testAtomicPlayNextInsertionWithPendingRegistration() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("0"))
        val (state2, pending) = QueueReducer.insertEntryWithPending(state, 1, createSong("1"), "tok1")
        assertEquals(2, state2.entries.size)
        assertEquals("1", state2.entries[1].song.id)
        assertEquals(1, pending.targetIndex)
        assertEquals(state2.entries[1].entryId, pending.entryId)
        assertEquals(2, state2.revision)
    }

    @Test
    fun testStableEntryIdBinding() {
        val (state, pending) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"))
        assertEquals(pending.entryId, state.entries[0].entryId)
        assertNotNull(pending.entryId)
    }

    @Test
    fun testDuplicateSongIdsWithIndependentTokens() {
        val song = createSong("same_id")
        val (state1, p1) = QueueReducer.addEntryWithPending(QueueState(), song, "tok1")
        val (state2, p2) = QueueReducer.addEntryWithPending(state1, song, "tok2")
        assertEquals(2, state2.entries.size)
        assertEquals(2, state2.pendingAdds.size)
        assertNotEquals(p1.token, p2.token)
        assertNotEquals(p1.entryId, p2.entryId)
        assertNotEquals(state2.entries[0].entryId, state2.entries[1].entryId)
    }

    @Test
    fun testPendingIndexAdjustmentAfterInsertion() {
        val (state1, _) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"), "tokA")
        val (state2, p2) = QueueReducer.insertEntryWithPending(state1, 0, createSong("B"), "tokB")
        assertEquals(0, p2.targetIndex)
        val pAAfter = state2.pendingAdds.find { it.token == "tokA" }!!
        assertEquals(1, pAAfter.targetIndex)
    }

    @Test
    fun testPendingIndexAdjustmentAfterRemoval() {
        val (state1, _) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"), "tokA")
        val (state2, _) = QueueReducer.addEntryWithPending(state1, createSong("B"), "tokB")
        val state3 = QueueReducer.removeEntry(state2, state2.entries[0].entryId)
        assertEquals(1, state3.pendingAdds.size)
        assertEquals("tokB", state3.pendingAdds[0].token)
        assertEquals(0, state3.pendingAdds[0].targetIndex)
    }

    @Test
    fun testPendingIndexAdjustmentAfterReorder() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("0"))
        state = QueueReducer.addEntry(state, createSong("1"))
        val pending = PendingQueueAdd(token = "tok", entryId = state.entries[1].entryId, song = createSong("P"), targetIndex = 1)
        state = QueueReducer.registerPendingAdd(state, pending)
        state = QueueReducer.reorder(state, 1, 0)
        assertEquals(0, state.pendingAdds[0].targetIndex)
    }

    @Test
    fun testCancellationWhenAssociatedEntryIsRemoved() {
        val (state1, _) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"), "tokA")
        val state2 = QueueReducer.removeEntry(state1, state1.entries[0].entryId)
        assertTrue(state2.pendingAdds.isEmpty())
        val consume = QueueReducer.consumePendingAdd(state2, "tokA")
        assertEquals(null, consume.media3InsertionIndex)
    }

    @Test
    fun testCancellationAfterClear() {
        val (state1, _) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"), "tokA")
        val state2 = QueueReducer.clear(state1)
        assertTrue(state2.pendingAdds.isEmpty())
        val consume = QueueReducer.consumePendingAdd(state2, "tokA")
        assertEquals(null, consume.media3InsertionIndex)
    }

    @Test
    fun testCancellationAfterKeepOnlyCurrent() {
        var state = QueueState()
        val (state1, _) = QueueReducer.addEntryWithPending(state, createSong("current"), "tokCurrent")
        state = QueueReducer.setCurrentEntry(state1, state1.entries[0].entryId)
        val (state2, _) = QueueReducer.addEntryWithPending(state, createSong("upcoming"), "tokUpcoming")
        val state3 = QueueReducer.keepOnlyCurrent(state2)
        assertEquals(1, state3.entries.size)
        assertEquals("current", state3.entries[0].song.id)
        assertTrue(state3.pendingAdds.isEmpty())
        val consume = QueueReducer.consumePendingAdd(state3, "tokUpcoming")
        assertEquals(null, consume.media3InsertionIndex)
    }

    @Test
    fun testCancellationAfterUpdateQueueState() {
        val (state1, _) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"), "tokA")
        val state2 = state1.copy(entries = listOf(QueueEntry(song = createSong("X"))), pendingAdds = emptyList(), revision = state1.revision + 1)
        assertTrue(state2.pendingAdds.isEmpty())
        val consume = QueueReducer.consumePendingAdd(state2, "tokA")
        assertEquals(null, consume.media3InsertionIndex)
    }

    @Test
    fun testInvalidOrAlreadyConsumedToken() {
        val state = QueueState()
        val consume = QueueReducer.consumePendingAdd(state, "non_existent")
        assertEquals(null, consume.media3InsertionIndex)
        assertEquals(state.revision, consume.newState.revision)
    }

    @Test
    fun testDoubleConsumption() {
        val (state1, _) = QueueReducer.addEntryWithPending(QueueState(), createSong("A"), "tokA")
        val res1 = QueueReducer.consumePendingAdd(state1, "tokA")
        assertNotNull(res1.media3InsertionIndex)
        val res2 = QueueReducer.consumePendingAdd(res1.newState, "tokA")
        assertEquals(null, res2.media3InsertionIndex)
        assertEquals(res1.newState.revision, res2.newState.revision)
    }

    @Test
    fun testMedia3InsertionIndexWhenEarlierPendingItemRemainsUnresolved() {
        // Initial timeline: [0, 1, 2]
        // Play Next Y at 1, Play Next X at 2: entries = [0, Y, X, 1, 2]
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("0"))
        state = QueueReducer.addEntry(state, createSong("1"))
        state = QueueReducer.addEntry(state, createSong("2"))
        val (stateWithY, _) = QueueReducer.insertEntryWithPending(state, 1, createSong("Y"), "tokY")
        val (stateWithBoth, _) = QueueReducer.insertEntryWithPending(stateWithY, 2, createSong("X"), "tokX")
        
        // X resolves first: queueIndex is 2, unresolved preceding pending is Y (count 1)
        val resX = QueueReducer.consumePendingAdd(stateWithBoth, "tokX")
        assertEquals(1, resX.media3InsertionIndex) // 2 - 1 = 1
    }

    @Test
    fun testCorrectInsertionIndexForReverseCompletionOrder() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("0"))
        state = QueueReducer.addEntry(state, createSong("1"))
        state = QueueReducer.addEntry(state, createSong("2"))
        val (stateWithY, _) = QueueReducer.insertEntryWithPending(state, 1, createSong("Y"), "tokY")
        val (stateWithBoth, _) = QueueReducer.insertEntryWithPending(stateWithY, 2, createSong("X"), "tokX")
        
        // X resolves first: inserted into timeline at index 1 -> timeline [0, X, 1, 2]
        val resX = QueueReducer.consumePendingAdd(stateWithBoth, "tokX")
        assertEquals(1, resX.media3InsertionIndex)
        
        // Y resolves second: inserted into timeline at index 1 -> timeline [0, Y, X, 1, 2]
        val resY = QueueReducer.consumePendingAdd(resX.newState, "tokY")
        assertEquals(1, resY.media3InsertionIndex)
    }

    @Test
    fun testMultiplePendingAdditionsResolvingInArbitraryOrders() {
        var state = QueueState()
        state = QueueReducer.addEntry(state, createSong("0"))
        state = QueueReducer.addEntry(state, createSong("4"))
        val (s1, _) = QueueReducer.insertEntryWithPending(state, 1, createSong("A"), "tokA")
        val (s2, _) = QueueReducer.insertEntryWithPending(s1, 2, createSong("B"), "tokB")
        val (s3, _) = QueueReducer.insertEntryWithPending(s2, 3, createSong("C"), "tokC")
        
        // Resolve C first: A and B precede C (unresolved count = 2) -> 3 - 2 = 1
        val resC = QueueReducer.consumePendingAdd(s3, "tokC")
        assertEquals(1, resC.media3InsertionIndex)
        
        // Resolve A second: No unresolved precede A (unresolved count = 0) -> 1 - 0 = 1
        val resA = QueueReducer.consumePendingAdd(resC.newState, "tokA")
        assertEquals(1, resA.media3InsertionIndex)
        
        // Resolve B third: A and C resolved, so unresolved before B = 0 -> 2 - 0 = 2
        val resB = QueueReducer.consumePendingAdd(resA.newState, "tokB")
        assertEquals(2, resB.media3InsertionIndex)
    }

    @Test
    fun testRevisionIncrementsExactlyOnceForAtomicOperations() {
        val state0 = QueueState()
        val (state1, _) = QueueReducer.addEntryWithPending(state0, createSong("1"))
        assertEquals(1, state1.revision)
        val (state2, _) = QueueReducer.insertEntryWithPending(state1, 0, createSong("0"))
        assertEquals(2, state2.revision)
        val res = QueueReducer.consumePendingAdd(state2, state2.pendingAdds[0].token)
        assertEquals(3, res.newState.revision)
    }

    @Test
    fun testNoOpOperationsPreserveRevision() {
        val state0 = QueueState()
        val res = QueueReducer.consumePendingAdd(state0, "invalid")
        assertEquals(0, res.newState.revision)
        val state1 = QueueReducer.removeEntry(state0, "invalid")
        assertEquals(0, state1.revision)
        val state2 = QueueReducer.reorder(state0, 0, 0)
        assertEquals(0, state2.revision)
    }
}
