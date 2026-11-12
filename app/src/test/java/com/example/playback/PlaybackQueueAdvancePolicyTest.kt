package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueAdvancePolicyTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val syncListener = object : QueueMediaSyncListener {
        override fun onMediaItemAdded(index: Int, mediaItem: MediaItem) {}
        override fun onMediaItemRemoved(index: Int) {}
        override fun onMediaItemMoved(fromIndex: Int, toIndex: Int) {}
        override fun onQueueCleared() {}
    }

    private lateinit var manager: PlaybackQueueManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> MediaItem.Builder().setMediaId(song.id).build() },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSong(id: String) = Song(id = id, title = "Title $id", artist = "Artist $id")

    @Test
    fun `advanceNext normal sequential increment`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)

        val nextIndex = manager.advanceNext(manual = true, repeatMode = RepeatMode.OFF, isShuffle = false)
        assertEquals(1, nextIndex)
        assertEquals(1, manager.currentQueueIndex.value)
    }

    @Test
    fun `advanceNext returns null at end of queue when repeat is off`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 1)

        val nextIndex = manager.advanceNext(manual = true, repeatMode = RepeatMode.OFF, isShuffle = false)
        assertNull(nextIndex)
        assertEquals(1, manager.currentQueueIndex.value)
    }

    @Test
    fun `advanceNext repeat all wraps to start`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 1)

        val nextIndex = manager.advanceNext(manual = true, repeatMode = RepeatMode.ALL, isShuffle = false)
        assertEquals(0, nextIndex)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test
    fun `advanceNext repeat one stays on current when automatic`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 0)

        val nextIndex = manager.advanceNext(manual = false, repeatMode = RepeatMode.ONE, isShuffle = false)
        assertEquals(0, nextIndex)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test
    fun `advanceNext repeat one advances when manual`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 0)

        val nextIndex = manager.advanceNext(manual = true, repeatMode = RepeatMode.ONE, isShuffle = false)
        assertEquals(1, nextIndex)
        assertEquals(1, manager.currentQueueIndex.value)
    }

    @Test
    fun `advanceNext shuffle picks random index and updates history`() {
        val songs = (1..10).map { createSong(it.toString()) }
        manager.updateQueueState(songs, 0)
        val initialEntryId = manager.queueState.value.entries[0].entryId

        val nextIndex = manager.advanceNext(manual = true, repeatMode = RepeatMode.OFF, isShuffle = true)
        
        assertTrue(nextIndex in 1..9)
        assertEquals(1, manager.queueState.value.shuffleHistory.size)
        assertEquals(initialEntryId, manager.queueState.value.shuffleHistory[0])
    }

    @Test
    fun `advancePrevious normal sequential decrement`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 1)

        val prevIndex = manager.advancePrevious(isShuffle = false)
        assertEquals(0, prevIndex)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test
    fun `advancePrevious wraps to end when at start`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 0)

        val prevIndex = manager.advancePrevious(isShuffle = false)
        assertEquals(1, prevIndex)
        assertEquals(1, manager.currentQueueIndex.value)
    }

    @Test
    fun `advancePrevious shuffle history traversal`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        val id1 = manager.queueState.value.entries[0].entryId
        val id2 = manager.queueState.value.entries[1].entryId
        
        // Manual setup of history for testing
        manager.restoreShuffleHistory(listOf(0, 1))
        manager.setCurrentIndex(2)
        
        // Prev should go to id2 (index 1) and pop it
        val prev1 = manager.advancePrevious(isShuffle = true)
        assertEquals(1, prev1)
        assertEquals(1, manager.queueState.value.shuffleHistory.size)
        assertEquals(id1, manager.queueState.value.shuffleHistory[0])
        
        // Prev again should go to id1 (index 0) and pop it
        val prev2 = manager.advancePrevious(isShuffle = true)
        assertEquals(0, prev2)
        assertTrue(manager.queueState.value.shuffleHistory.isEmpty())
    }

    @Test
    fun `advanceNext and advancePrevious on empty queue`() {
        assertNull(manager.advanceNext(true, RepeatMode.OFF, false))
        assertNull(manager.advancePrevious(false))
    }
}
