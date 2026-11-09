package com.example.playback

import com.example.data.models.Song
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class PlaybackQueueManagerTest {

    private val testScope = TestScope()
    private val syncListener = mockk<QueueMediaSyncListener>(relaxed = true)
    
    private fun createSong(id: String) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        album = "Album $id"
    )

    private fun createManager() = PlaybackQueueManager(
        coroutineScope = testScope,
        mediaItemBuilder = { _, _ -> mockk(relaxed = true) },
        syncListener = syncListener
    )

    @Test
    fun `updateQueueState preserves entry identities for existing songs`() = runTest {
        val manager = createManager()
        val songs1 = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs1, 0)
        
        val id1 = manager.queueState.value.entries[0].entryId
        val id2 = manager.queueState.value.entries[1].entryId
        
        val songs2 = listOf(createSong("2"), createSong("1"), createSong("3"))
        manager.updateQueueState(songs2, 1)
        
        val newState = manager.queueState.value
        assertEquals(id2, newState.entries[0].entryId)
        assertEquals(id1, newState.entries[1].entryId)
        assertNotEquals(id1, newState.entries[2].entryId)
        assertNotEquals(id2, newState.entries[2].entryId)
        assertEquals(id1, newState.currentEntryId)
    }

    @Test
    fun `updateQueueState handles duplicate song IDs distinguishably`() = runTest {
        val manager = createManager()
        val songs1 = listOf(createSong("1"), createSong("1"))
        manager.updateQueueState(songs1, 0)
        
        val id1a = manager.queueState.value.entries[0].entryId
        val id1b = manager.queueState.value.entries[1].entryId
        
        assertNotEquals(id1a, id1b)
        
        // Swap them
        val songs2 = listOf(createSong("1"), createSong("1"))
        manager.updateQueueState(songs2, 1)
        
        val newState = manager.queueState.value
        assertEquals(id1a, newState.entries[0].entryId)
        assertEquals(id1b, newState.entries[1].entryId)
        assertEquals(id1b, newState.currentEntryId)
    }

    @Test
    fun `updateQueueState no-op does not increment revision`() = runTest {
        val manager = createManager()
        val songs = listOf(createSong("1"))
        manager.updateQueueState(songs, 0)
        val rev1 = manager.queueState.value.revision
        
        manager.updateQueueState(songs, 0)
        val rev2 = manager.queueState.value.revision
        
        assertEquals(rev1, rev2)
    }

    @Test
    fun `getQueueSnapshot returns consistent state`() = runTest {
        val manager = createManager()
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 1)
        
        val (snapshotSongs, snapshotIndex) = manager.getQueueSnapshot()
        assertEquals(2, snapshotSongs.size)
        assertEquals(1, snapshotIndex)
        assertEquals("2", snapshotSongs[1].id)
    }

    @Test
    fun `restoreShuffleHistory maps indices to stable IDs`() = runTest {
        val manager = createManager()
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        
        val id1 = manager.queueState.value.entries[0].entryId
        val id3 = manager.queueState.value.entries[2].entryId
        
        manager.restoreShuffleHistory(listOf(0, 2, 5)) // 5 is out of bounds
        
        val history = manager.queueState.value.shuffleHistory
        assertEquals(2, history.size)
        assertEquals(id1, history[0])
        assertEquals(id3, history[1])
    }
}
