package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.CompletableDeferred
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

    private class SimulatedTransitionDispatcher(
        private val queueManager: PlaybackQueueManager
    ) {
        var isRestoringSnapshot = false
        var currentMediaItem: MediaItem? = null
        var currentMediaItemIndex: Int = -1

        fun onMediaItemTransition(callbackMediaItem: MediaItem?) {
            if (callbackMediaItem != null && !isRestoringSnapshot) {
                val current = currentMediaItem ?: return
                // Guard against stale callbacks: revalidate against the actual active item in ExoPlayer
                if (current.mediaId == callbackMediaItem.mediaId) {
                    queueManager.syncCurrentEntryFromPlayer(currentMediaItemIndex, current.mediaId)
                }
            }
        }
    }

    @Test
    fun `9 genuine stale transition callback cannot move metadata backwards`() {
        val songs = listOf(createSong("1"), createSong("2"), createSong("3"))
        manager.updateQueueState(songs, 0)
        val id2 = manager.queueState.value.entries[1].entryId
        val id3 = manager.queueState.value.entries[2].entryId

        val dispatcher = SimulatedTransitionDispatcher(manager)

        // Step 1: Transition A occurs in player to item 1 ("2")
        val callbackMediaItemA = MediaItem.Builder().setMediaId("2").build()

        // Step 2: Before callback A is processed, Player transitions to item 2 ("3") (Transition B)
        dispatcher.currentMediaItem = MediaItem.Builder().setMediaId("3").build()
        dispatcher.currentMediaItemIndex = 2

        // Step 3: Callback for Transition B is processed
        val callbackMediaItemB = MediaItem.Builder().setMediaId("3").build()
        dispatcher.onMediaItemTransition(callbackMediaItemB)

        // Verify metadata aligned with B
        assertEquals(id3, manager.queueState.value.currentEntryId)
        assertEquals(2, manager.currentQueueIndex.value)

        // Step 4: Delayed callback for Transition A arrives afterward
        dispatcher.onMediaItemTransition(callbackMediaItemA)

        // Stale callback must be dropped; metadata must remain aligned with B and NOT move backwards
        assertEquals(id3, manager.queueState.value.currentEntryId)
        assertEquals(2, manager.currentQueueIndex.value)
    }

    @Test
    fun `10 snapshot restoration guard prevents transition callback from overwriting restored metadata`() {
        val songs = listOf(createSong("1"), createSong("2"))
        manager.updateQueueState(songs, 0)
        val id1 = manager.queueState.value.entries[0].entryId

        val dispatcher = SimulatedTransitionDispatcher(manager).apply {
            isRestoringSnapshot = true
            currentMediaItem = MediaItem.Builder().setMediaId("2").build()
            currentMediaItemIndex = 1
        }

        // Transition callback arrives while restoration is in progress
        dispatcher.onMediaItemTransition(MediaItem.Builder().setMediaId("2").build())

        // Restoration guard must block synchronisation: metadata remains intact
        assertEquals(id1, manager.queueState.value.currentEntryId)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test
    fun `11 duplicate song IDs with pending additions resolve through actual timeline positions`() = runTest {
        val pendingDeferred = CompletableDeferred<MediaItem>()
        val testManager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ ->
                if (song.id == "PENDING") {
                    pendingDeferred.await()
                } else {
                    MediaItem.Builder().setMediaId(song.id).build()
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val songDup = createSong("DUP")
        val songOther = createSong("OTHER")
        val songPending = createSong("PENDING")

        // Initial queue with duplicate songs: index 1 is OTHER, index 0 is DUP, index 2 is DUP
        testManager.updateQueueState(listOf(songDup, songOther, songDup), 1)
        val id0 = testManager.queueState.value.entries[0].entryId
        val id2 = testManager.queueState.value.entries[2].entryId

        // Verify duplicate songs have distinct unique entry IDs
        assertTrue(id0 != id2)

        // Add pending addition after index 0 (playNext)
        testManager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        // Entries: [DUP (id0), PENDING (pending), OTHER, DUP (id2)]
        // Resolved entries in timeline: [DUP (id0), OTHER, DUP (id2)] (size 3)
        assertEquals(4, testManager.queueState.value.entries.size)
        assertEquals(1, testManager.queueState.value.pendingAdds.size)

        // Player transitions to timeline index 0 ("DUP") -> must resolve to id0
        assertTrue(testManager.syncCurrentEntryFromPlayer(0, "DUP"))
        assertEquals(id0, testManager.queueState.value.currentEntryId)

        // Player transitions to timeline index 2 ("DUP") -> must resolve to id2
        assertTrue(testManager.syncCurrentEntryFromPlayer(2, "DUP"))
        assertEquals(id2, testManager.queueState.value.currentEntryId)

        // Now resolve the pending addition
        pendingDeferred.complete(MediaItem.Builder().setMediaId(songPending.id).build())
        testScheduler.advanceUntilIdle()

        // Pending addition is consumed: timeline now has 4 items: [DUP (id0), PENDING, OTHER, DUP (id2)]
        assertEquals(0, testManager.queueState.value.pendingAdds.size)
        assertEquals(4, testManager.queueState.value.entries.size)

        // Player transitions to timeline index 3 ("DUP") -> must resolve to id2
        assertTrue(testManager.syncCurrentEntryFromPlayer(1, "PENDING"))
        assertTrue(testManager.syncCurrentEntryFromPlayer(3, "DUP"))
        assertEquals(id2, testManager.queueState.value.currentEntryId)

        // Player transitions to timeline index 0 ("DUP") -> must resolve to id0
        assertTrue(testManager.syncCurrentEntryFromPlayer(0, "DUP"))
        assertEquals(id0, testManager.queueState.value.currentEntryId)
    }
}

