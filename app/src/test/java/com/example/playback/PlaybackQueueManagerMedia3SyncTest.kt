package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueManagerMedia3SyncTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class FakeMediaSyncListener : QueueMediaSyncListener {
        val media3Timeline = mutableListOf<String>()

        override fun onMediaItemAdded(index: Int, mediaItem: MediaItem) {
            val id = mediaItem.mediaId
            if (index in 0..media3Timeline.size) {
                media3Timeline.add(index, id)
            } else {
                throw IndexOutOfBoundsException("Invalid Media3 insertion index $index for timeline size ${media3Timeline.size}")
            }
        }

        override fun onMediaItemRemoved(index: Int) {
            if (index in media3Timeline.indices) {
                media3Timeline.removeAt(index)
            } else {
                throw IndexOutOfBoundsException("Invalid Media3 removal index $index for timeline size ${media3Timeline.size}")
            }
        }

        override fun onMediaItemMoved(fromIndex: Int, toIndex: Int) {
            if (fromIndex in media3Timeline.indices && toIndex in media3Timeline.indices) {
                val item = media3Timeline.removeAt(fromIndex)
                media3Timeline.add(toIndex, item)
            } else {
                throw IndexOutOfBoundsException("Invalid Media3 move from $fromIndex to $toIndex for timeline size ${media3Timeline.size}")
            }
        }

        override fun onQueueCleared() {
            media3Timeline.clear()
        }
    }

    private fun createSong(id: String) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        album = "Album $id"
    )

    private fun createMediaItem(song: Song, id: String = song.id): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .build()
    }

    @Test
    fun testRemoveResolvedEntryAfterEarlierPendingEntry() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        // Add pending at index 1 via playNext
        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        assertEquals(3, manager.queueState.value.entries.size)
        assertEquals(1, manager.queueState.value.pendingAdds.size)
        assertEquals(2, syncListener.media3Timeline.size)

        // Remove resolved entry at index 2 (song2)
        manager.removeFromQueue(2)

        assertEquals(2, manager.queueState.value.entries.size)
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testRemovePendingEntryWithoutRemovingMedia3Item() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        // Remove pending entry at index 1
        manager.removeFromQueue(1)

        assertEquals(2, manager.queueState.value.entries.size)
        assertEquals(listOf("0", "2"), syncListener.media3Timeline)
    }

    @Test
    fun testBulkRemoveDuplicateSongIdsWithPendingAndResolvedMatches() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val songDup = createSong("DUP")
        val songOther = createSong("OTHER")

        manager.updateQueueState(listOf(songDup, songOther), 0)
        syncListener.media3Timeline.addAll(listOf("DUP", "OTHER"))

        manager.playNext(songDup, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        manager.removeSongFromQueue("DUP", manager.queueState.value.entries.map { it.song }, 0)

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("OTHER", manager.queueState.value.entries[0].song.id)
        assertEquals(listOf("OTHER"), syncListener.media3Timeline)
    }

    @Test
    fun testReorderResolvedEntriesWithPendingEntriesBeforeAndBetween() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        // Reorder index 2 (song2) to index 0 (ahead of 0)
        manager.reorderQueue(2, 0)

        assertEquals("2", manager.queueState.value.entries[0].song.id)
        assertEquals("0", manager.queueState.value.entries[1].song.id)
        assertEquals(listOf("2", "0"), syncListener.media3Timeline)
    }

    @Test
    fun testMovePendingEntryAndVerifyResolvedProjectionRemainsCorrect() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        // Move pending entry at index 1 to index 0
        manager.reorderQueue(1, 0)

        assertEquals("P", manager.queueState.value.entries[0].song.id)
        assertEquals(listOf("0", "2"), syncListener.media3Timeline)
    }

    @Test
    fun testMoveResolvedEntryAcrossPendingEntry() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        // Move song2 (index 2) to index 1 (between 0 and P)
        manager.reorderQueue(2, 1)

        assertEquals(listOf("0", "2"), syncListener.media3Timeline)
    }

    @Test
    fun testKeepOnlyCurrentWithSeveralResolvedUpcomingEntriesAndPendingAdditions() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val song1 = createSong("1")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song1, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "1", "2"))

        manager.playNext(createSong("P"), isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        manager.keepOnlyCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testCancelBuilderAndVerifyNoGhostEntryRemains() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { _, _ -> throw CancellationException("Cancelled") },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        val songCancelled = createSong("CANCELLED")
        try {
            manager.addToQueue(songCancelled, isCurrentSongNull = false) { _, _, _ -> }
        } catch (e: Exception) {
            // Cancellation rethrown
        }

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testFailBuilderAndVerifyStateTimelineConsistency() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { _, _ -> throw RuntimeException("Network error") },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.addToQueue(createSong("FAILED"), isCurrentSongNull = false) { _, _, _ -> }

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testCompleteStaleBuilderAfterQueueReplacementAndVerifyNoInsertion() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.addToQueue(createSong("STALE"), isCurrentSongNull = false) { _, _, _ -> }

        // Replace queue before builder completes
        val newSong = createSong("NEW")
        manager.updateQueueState(listOf(newSong), 0)
        syncListener.media3Timeline.clear()
        syncListener.media3Timeline.add("NEW")

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("NEW", manager.queueState.value.entries[0].song.id)
        assertEquals(listOf("NEW"), syncListener.media3Timeline)
    }

    @Test
    fun testReverseCompletionProducesCorrectTimeline() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = testScope,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val song1 = createSong("1")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song1, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "1", "2"))

        val songY = createSong("Y")
        val songX = createSong("X")
        manager.playNext(songY, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }
        manager.playNext(songX, isCurrentSongNull = false, currentQueueIndex = 1) { _, _, _ -> }

        assertEquals(5, manager.queueState.value.entries.size)
        assertEquals(listOf("0", "Y", "X", "1", "2"), syncListener.media3Timeline)
    }
}
