package com.example.playback

import androidx.media3.common.MediaItem
import com.example.data.models.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueManagerMedia3SyncTest {

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
            coroutineScope = this,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        assertEquals(3, manager.queueState.value.entries.size)
        assertEquals(1, manager.queueState.value.pendingAdds.size)
        assertEquals(2, syncListener.media3Timeline.size)

        manager.removeFromQueue(2)

        assertEquals(2, manager.queueState.value.entries.size)
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testRemovePendingEntryWithoutRemovingMedia3Item() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        manager.removeFromQueue(1)

        assertEquals(2, manager.queueState.value.entries.size)
        assertEquals(listOf("0", "2"), syncListener.media3Timeline)
    }

    @Test
    fun testBulkRemoveDuplicateSongIdsWithPendingAndResolvedMatches() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = this,
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
            coroutineScope = this,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        manager.reorderQueue(2, 0)

        assertEquals("2", manager.queueState.value.entries[0].song.id)
        assertEquals("0", manager.queueState.value.entries[1].song.id)
        assertEquals(listOf("2", "0"), syncListener.media3Timeline)
    }

    @Test
    fun testMovePendingEntryAndVerifyResolvedProjectionRemainsCorrect() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        manager.reorderQueue(1, 0)

        assertEquals("P", manager.queueState.value.entries[0].song.id)
        assertEquals(listOf("0", "2"), syncListener.media3Timeline)
    }

    @Test
    fun testMoveResolvedEntryAcrossPendingEntry() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ -> createMediaItem(song, song.id) },
            syncListener = syncListener
        )

        val song0 = createSong("0")
        val songPending = createSong("P")
        val song2 = createSong("2")

        manager.updateQueueState(listOf(song0, song2), 0)
        syncListener.media3Timeline.addAll(listOf("0", "2"))

        manager.playNext(songPending, isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }

        manager.reorderQueue(2, 1)

        assertEquals(listOf("0", "2"), syncListener.media3Timeline)
    }

    @Test
    fun testKeepOnlyCurrentWithSeveralResolvedUpcomingEntriesAndPendingAdditions() = runTest {
        val syncListener = FakeMediaSyncListener()
        val manager = PlaybackQueueManager(
            coroutineScope = this,
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

    // --- Deterministic Async Tests (Phase 3B.5B-R1) ---

    @Test
    fun testReverseCompletionProducesCorrectTimeline() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()

        val deferredY = CompletableDeferred<MediaItem>()
        val deferredX = CompletableDeferred<MediaItem>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                when (song.id) {
                    "Y" -> deferredY.await()
                    "X" -> deferredX.await()
                    else -> createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
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
        testScheduler.runCurrent()

        assertEquals(5, manager.queueState.value.entries.size)
        assertEquals(2, manager.queueState.value.pendingAdds.size)
        assertEquals(listOf("0", "1", "2"), syncListener.media3Timeline)

        // Complete X (second builder) first
        deferredX.complete(createMediaItem(songX))
        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.pendingAdds.size)
        assertEquals(listOf("0", "1", "X", "2"), syncListener.media3Timeline)

        // Complete Y (first builder) second
        deferredY.complete(createMediaItem(songY))
        testScheduler.runCurrent()

        assertEquals(0, manager.queueState.value.pendingAdds.size)
        assertEquals(listOf("0", "Y", "1", "X", "2"), syncListener.media3Timeline)
        assertEquals(
            manager.queueState.value.entries.map { it.song.id },
            syncListener.media3Timeline
        )
    }

    @Test
    fun testCancelBuilderBeforeCompletion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()
        val deferredCancel = CompletableDeferred<MediaItem>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "CANCELLED") {
                    deferredCancel.await()
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        val songCancelled = createSong("CANCELLED")
        manager.addToQueue(songCancelled, isCurrentSongNull = false) { _, _, _ -> }
        testScheduler.runCurrent()

        assertEquals(2, manager.queueState.value.entries.size)
        assertEquals(1, manager.queueState.value.pendingAdds.size)

        deferredCancel.cancel(CancellationException("Builder cancelled"))
        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testCancelBuilderAfterCompletionBeforeMainInsertion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()

        var capturedJob: Job? = null
        val builderCompletedGate = CompletableDeferred<Unit>()
        val releaseBuilderGate = CompletableDeferred<Unit>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "INTERRUPTED") {
                    capturedJob = coroutineContext[Job]
                    builderCompletedGate.complete(Unit)
                    releaseBuilderGate.await()
                    createMediaItem(song, song.id)
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        val songInterrupted = createSong("INTERRUPTED")
        manager.addToQueue(songInterrupted, isCurrentSongNull = false) { _, _, _ -> }
        testScheduler.runCurrent()

        builderCompletedGate.await()
        assertTrue(capturedJob != null)

        releaseBuilderGate.complete(Unit)
        capturedJob?.cancel(CancellationException("Cancel before main insertion"))

        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testStaleCompletionAfterQueueReplacementAndVerifyNoInsertion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()
        val deferredStale = CompletableDeferred<MediaItem>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "STALE") {
                    deferredStale.await()
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.addToQueue(createSong("STALE"), isCurrentSongNull = false) { _, _, _ -> }
        testScheduler.runCurrent()

        val newSong = createSong("NEW")
        manager.updateQueueState(listOf(newSong), 0)
        syncListener.media3Timeline.clear()
        syncListener.media3Timeline.add("NEW")

        deferredStale.complete(createMediaItem(createSong("STALE")))
        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("NEW", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("NEW"), syncListener.media3Timeline)
    }

    @Test
    fun testFailBuilderAndVerifyStateTimelineConsistency() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()
        val deferredFail = CompletableDeferred<MediaItem>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "FAILED") {
                    deferredFail.await()
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.addToQueue(createSong("FAILED"), isCurrentSongNull = false) { _, _, _ -> }
        testScheduler.runCurrent()

        deferredFail.completeExceptionally(RuntimeException("Network failure"))
        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testPlayNextCancellationBeforeCompletion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()
        val deferredCancel = CompletableDeferred<MediaItem>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "CANCELLED_PN") {
                    deferredCancel.await()
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.playNext(createSong("CANCELLED_PN"), isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }
        testScheduler.runCurrent()

        assertEquals(2, manager.queueState.value.entries.size)
        assertEquals(1, manager.queueState.value.pendingAdds.size)

        deferredCancel.cancel(CancellationException("PlayNext cancelled"))
        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testPlayNextCancellationAfterCompletionBeforeMainInsertion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()

        var capturedJob: Job? = null
        val builderCompletedGate = CompletableDeferred<Unit>()
        val releaseBuilderGate = CompletableDeferred<Unit>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "INTERRUPTED_PN") {
                    capturedJob = coroutineContext[Job]
                    builderCompletedGate.complete(Unit)
                    releaseBuilderGate.await()
                    createMediaItem(song, song.id)
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.playNext(createSong("INTERRUPTED_PN"), isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }
        testScheduler.runCurrent()

        builderCompletedGate.await()
        assertTrue(capturedJob != null)

        releaseBuilderGate.complete(Unit)
        capturedJob?.cancel(CancellationException("Cancel playNext before main insertion"))

        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }

    @Test
    fun testPlayNextBuilderFailure() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val syncListener = FakeMediaSyncListener()
        val deferredFail = CompletableDeferred<MediaItem>()

        val manager = PlaybackQueueManager(
            coroutineScope = this,
            mediaItemBuilder = { song, _ ->
                if (song.id == "FAILED_PN") {
                    deferredFail.await()
                } else {
                    createMediaItem(song, song.id)
                }
            },
            syncListener = syncListener,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        val song0 = createSong("0")
        manager.updateQueueState(listOf(song0), 0)
        syncListener.media3Timeline.add("0")

        manager.playNext(createSong("FAILED_PN"), isCurrentSongNull = false, currentQueueIndex = 0) { _, _, _ -> }
        testScheduler.runCurrent()

        deferredFail.completeExceptionally(RuntimeException("Network failure"))
        testScheduler.runCurrent()

        assertEquals(1, manager.queueState.value.entries.size)
        assertEquals("0", manager.queueState.value.entries[0].song.id)
        assertTrue(manager.queueState.value.pendingAdds.isEmpty())
        assertEquals(listOf("0"), syncListener.media3Timeline)
    }
}
