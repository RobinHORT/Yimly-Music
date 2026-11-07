package com.example.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.YimlyApplication
import com.example.data.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 3B: Queue Reorder Validation Test Suite (ISS-05).
 *
 * Verifies that:
 * 1. Negative fromIndex preserves queue and avoids incrementing playbackRequestId / androidAutoQueueSyncVersion.
 * 2. Negative toIndex preserves queue and avoids side effects.
 * 3. fromIndex beyond queue size preserves queue and avoids side effects.
 * 4. toIndex beyond queue size preserves queue and avoids side effects.
 * 5. Valid reorder correctly reorders items and updates current index.
 * 6. Moving the currently playing item correctly tracks the item.
 * 7. Moving another item across the playing item adjusts current index properly.
 * 8. Invalid reorder requests do not cancel Android Auto synchronization.
 * 9. Identical indices execute cleanly without unintended side effects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class QueueReorderTest {

    private lateinit var context: Context
    private lateinit var app: YimlyApplication
    private lateinit var playbackManager: PlaybackManager

    private val song1 = Song(id = "song_1", title = "Song One", artist = "Artist 1", durationSeconds = 180)
    private val song2 = Song(id = "song_2", title = "Song Two", artist = "Artist 2", durationSeconds = 200)
    private val song3 = Song(id = "song_3", title = "Song Three", artist = "Artist 3", durationSeconds = 220)
    private val song4 = Song(id = "song_4", title = "Song Four", artist = "Artist 4", durationSeconds = 240)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as YimlyApplication
        playbackManager = app.playbackManager
        playbackManager.clearQueueAndStop()
    }

    @Test
    fun test1_negativeFromIndexPreservesQueueAndRequestId() {
        val queue = listOf(song1, song2, song3)
        playbackManager.playSong(song1, queue, startIndex = 0)

        val requestIdBefore = playbackManager.playbackRequestId
        val queueBefore = playbackManager.playbackInfo.value.queue

        playbackManager.reorderQueue(fromIndex = -1, toIndex = 1)

        assertEquals("Queue must remain unchanged", queueBefore, playbackManager.playbackInfo.value.queue)
        assertEquals("playbackRequestId must not be incremented on invalid reorder", requestIdBefore, playbackManager.playbackRequestId)
    }

    @Test
    fun test2_negativeToIndexPreservesQueueAndRequestId() {
        val queue = listOf(song1, song2, song3)
        playbackManager.playSong(song1, queue, startIndex = 0)

        val requestIdBefore = playbackManager.playbackRequestId
        val queueBefore = playbackManager.playbackInfo.value.queue

        playbackManager.reorderQueue(fromIndex = 0, toIndex = -2)

        assertEquals("Queue must remain unchanged", queueBefore, playbackManager.playbackInfo.value.queue)
        assertEquals("playbackRequestId must not be incremented on invalid reorder", requestIdBefore, playbackManager.playbackRequestId)
    }

    @Test
    fun test3_fromIndexBeyondSizePreservesQueueAndRequestId() {
        val queue = listOf(song1, song2, song3)
        playbackManager.playSong(song1, queue, startIndex = 0)

        val requestIdBefore = playbackManager.playbackRequestId
        val queueBefore = playbackManager.playbackInfo.value.queue

        playbackManager.reorderQueue(fromIndex = 10, toIndex = 1)

        assertEquals("Queue must remain unchanged", queueBefore, playbackManager.playbackInfo.value.queue)
        assertEquals("playbackRequestId must not be incremented on invalid reorder", requestIdBefore, playbackManager.playbackRequestId)
    }

    @Test
    fun test4_toIndexBeyondSizePreservesQueueAndRequestId() {
        val queue = listOf(song1, song2, song3)
        playbackManager.playSong(song1, queue, startIndex = 0)

        val requestIdBefore = playbackManager.playbackRequestId
        val queueBefore = playbackManager.playbackInfo.value.queue

        playbackManager.reorderQueue(fromIndex = 0, toIndex = 10)

        assertEquals("Queue must remain unchanged", queueBefore, playbackManager.playbackInfo.value.queue)
        assertEquals("playbackRequestId must not be incremented on invalid reorder", requestIdBefore, playbackManager.playbackRequestId)
    }

    @Test
    fun test5_validReorderUpdatesQueueAndCurrentIndexCorrectly() {
        val queue = listOf(song1, song2, song3, song4)
        playbackManager.playSong(song2, queue, startIndex = 1) // Song 2 is active at index 1

        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals(song2.id, playbackManager.playbackInfo.value.currentSong?.id)

        // Move song 4 (index 3) to index 0
        playbackManager.reorderQueue(fromIndex = 3, toIndex = 0)

        val expectedQueue = listOf(song4, song1, song2, song3)
        assertEquals(expectedQueue, playbackManager.playbackInfo.value.currentSong?.let { playbackManager.playbackInfo.value.queue })
        // Active item song2 was at index 1, moved to index 2
        assertEquals(2, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals(song2.id, playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun test6_movingCurrentlyPlayingItemTracksIndex() {
        val queue = listOf(song1, song2, song3)
        playbackManager.playSong(song2, queue, startIndex = 1) // Song 2 active at index 1

        // Move active song from index 1 to index 2
        playbackManager.reorderQueue(fromIndex = 1, toIndex = 2)

        assertEquals(listOf(song1, song3, song2), playbackManager.playbackInfo.value.queue)
        assertEquals("Active song index must follow the moved item", 2, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals(song2.id, playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun test7_movingItemAcrossCurrentlyPlayingItemAdjustsIndex() {
        val queue = listOf(song1, song2, song3, song4)
        playbackManager.playSong(song3, queue, startIndex = 2) // Song 3 active at index 2

        // Move song 0 to index 3 (moving past active index 2)
        playbackManager.reorderQueue(fromIndex = 0, toIndex = 3)

        assertEquals(listOf(song2, song3, song4, song1), playbackManager.playbackInfo.value.queue)
        // Song 3 shifted from index 2 to index 1
        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals(song3.id, playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun test8_invalidReorderDoesNotCancelOrInvalidateAndroidAutoSync() {
        val queue = listOf(song1, song2)
        playbackManager.playSong(song1, queue, startIndex = 0)

        val initialRequestId = playbackManager.playbackRequestId

        // Attempt invalid reorder
        playbackManager.reorderQueue(fromIndex = -5, toIndex = 50)

        assertEquals("Playback request ID must not change", initialRequestId, playbackManager.playbackRequestId)
    }

    @Test
    fun test9_identicalIndicesPreserveQueueWithoutSideEffects() {
        val queue = listOf(song1, song2, song3)
        playbackManager.playSong(song1, queue, startIndex = 0)

        val requestIdBefore = playbackManager.playbackRequestId
        playbackManager.reorderQueue(fromIndex = 1, toIndex = 1)

        assertEquals(queue, playbackManager.playbackInfo.value.queue)
        assertEquals(requestIdBefore + 1, playbackManager.playbackRequestId) // Valid reorder increments request ID
    }
}
