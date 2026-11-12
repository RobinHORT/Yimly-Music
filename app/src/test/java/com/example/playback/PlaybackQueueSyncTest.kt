package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueSyncTest {

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
    fun `1 natural transition updates current queue entry metadata`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        val id2 = manager.queueState.value.entries[1].entryId

        val changed = manager.syncCurrentEntryFromPlayer(1, "2")
        
        assertTrue(changed)
        assertEquals(id2, manager.queueState.value.currentEntryId)
        assertEquals(1, manager.currentQueueIndex.value)
    }

    @Test
    fun `2 duplicate song IDs resolve through stable queue entry identity`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("1"))
        manager.updateQueueState(songs, 0)
        val id1a = manager.queueState.value.entries[0].entryId
        val id1b = manager.queueState.value.entries[2].entryId

        // Transition to the second instance of song "1" at index 2
        val changed = manager.syncCurrentEntryFromPlayer(2, "1")
        
        assertTrue(changed)
        assertEquals(id1b, manager.queueState.value.currentEntryId)
        assertEquals(2, manager.currentQueueIndex.value)
        
        // Transition back to the first instance at index 0
        val changed2 = manager.syncCurrentEntryFromPlayer(0, "1")
        assertTrue(changed2)
        assertEquals(id1a, manager.queueState.value.currentEntryId)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test
    fun `3 repeated notification for the same entry does not create redundant notifications`() = runTest {
        var notificationCount = 0
        val managerWithListener = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> MediaItem.Builder().setMediaId(song.id).build() },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            queueChangeListener = { notificationCount++ }
        )
        val songs = listOf(createSong("1"), createSong("2"))
        managerWithListener.updateQueueState(songs, 0)
        assertEquals(1, notificationCount) // Initial updateQueueState

        val initialRev = managerWithListener.queueState.value.revision

        // Repeated sync for already active entry (index 0, "1")
        val changed = managerWithListener.syncCurrentEntryFromPlayer(0, "1")
        assertFalse(changed)
        assertEquals(initialRev, managerWithListener.queueState.value.revision)
        assertEquals(1, notificationCount) // No new notification emitted
    }

    @Test
    fun `4 stale transition cannot overwrite a newer current entry`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        val id1 = manager.queueState.value.entries[0].entryId

        // Player reports index 1 but mediaId "3" (stale/mismatched item)
        val changed = manager.syncCurrentEntryFromPlayer(1, "3")
        
        assertFalse(changed)
        assertEquals(id1, manager.queueState.value.currentEntryId)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test
    fun `5 missing or ambiguous identity does not corrupt queue state`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 0)
        val id1 = manager.queueState.value.entries[0].entryId
        val rev = manager.queueState.value.revision

        // Out of bounds index
        assertFalse(manager.syncCurrentEntryFromPlayer(-1, "1"))
        assertFalse(manager.syncCurrentEntryFromPlayer(99, "1"))

        // Null mediaId
        assertFalse(manager.syncCurrentEntryFromPlayer(0, null))

        // State remains completely intact
        assertEquals(id1, manager.queueState.value.currentEntryId)
        assertEquals(rev, manager.queueState.value.revision)
        assertEquals(2, manager.queueState.value.entries.size)
    }

    @Test
    fun `6 synchronisation does not invoke next previous policy or mutate shuffle history`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        manager.addShuffleHistory(0)
        val initialHistory = manager.queueState.value.shuffleHistory

        // Natural transition to track 1
        val changed = manager.syncCurrentEntryFromPlayer(1, "2")
        assertTrue(changed)

        // Verify shuffle history was NOT mutated
        assertEquals(initialHistory, manager.queueState.value.shuffleHistory)
    }

    @Test
    fun `7 queue persistence notification occurs only when committed metadata changes`() = runTest {
        var notifiedState: QueueState? = null
        val managerWithListener = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> MediaItem.Builder().setMediaId(song.id).build() },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            queueChangeListener = { notifiedState = it }
        )
        val songs = listOf(createSong("1"), createSong("2"))
        managerWithListener.updateQueueState(songs, 0)
        val revAfterUpdate = managerWithListener.queueState.value.revision

        // Sync for changed entry: must trigger notification
        val changed = managerWithListener.syncCurrentEntryFromPlayer(1, "2")
        assertTrue(changed)
        assertNotNull(notifiedState)
        assertEquals(revAfterUpdate + 1, notifiedState?.revision)
        assertEquals(managerWithListener.queueState.value.entries[1].entryId, notifiedState?.currentEntryId)

        // Reset tracking and sync again for same entry: must NOT trigger notification
        notifiedState = null
        val changedAgain = managerWithListener.syncCurrentEntryFromPlayer(1, "2")
        assertFalse(changedAgain)
        assertTrue(notifiedState == null)
    }

    @Test
    fun `8 handles instrumental and android auto mediaId formats correctly`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        val id2 = manager.queueState.value.entries[1].entryId
        val id3 = manager.queueState.value.entries[2].entryId

        // Instrumental mediaId "2_inst"
        assertTrue(manager.syncCurrentEntryFromPlayer(1, "2_inst"))
        assertEquals(id2, manager.queueState.value.currentEntryId)

        // Android auto mediaId "all_song|3"
        assertTrue(manager.syncCurrentEntryFromPlayer(2, "all_song|3"))
        assertEquals(id3, manager.queueState.value.currentEntryId)

        // Playlist song mediaId "playlist_song|pl1|1"
        val id1 = manager.queueState.value.entries[0].entryId
        assertTrue(manager.syncCurrentEntryFromPlayer(0, "playlist_song|pl1|1"))
        assertEquals(id1, manager.queueState.value.currentEntryId)
    }
}
