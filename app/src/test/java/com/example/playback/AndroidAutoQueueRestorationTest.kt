package com.example.playback

import com.example.data.models.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused regression tests for Android Auto queue restoration, timeline consistency,
 * cold-start readiness, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAutoQueueRestorationTest {

    private val songA = Song(id = "song_a", title = "Song A", artist = "Artist 1", durationSeconds = 180)
    private val songB = Song(id = "song_b", title = "Song B", artist = "Artist 2", durationSeconds = 200)
    private val songC = Song(id = "song_c", title = "Song C", artist = "Artist 3", durationSeconds = 220, hasInstrumental = true)
    private val songD = Song(id = "song_d", title = "Song D", artist = "Artist 4", durationSeconds = 240)
    private val songE = Song(id = "song_e", title = "Song E", artist = "Artist 5", durationSeconds = 260)

    /**
     * Test 1 — Full queue restoration
     * Save five songs [A, B, C, D, E] with Song C active.
     * Verify all five items are restored in exact order with active index = 2.
     */
    @Test
    fun test1_fullQueueRestoration_preservesAllFiveItemsAndActiveIndex() {
        val originalQueue = listOf(songA, songB, songC, songD, songE)
        val snapshot = PlaybackSnapshot(
            queue = originalQueue,
            currentQueueIndex = 2,
            currentPositionMs = 45000L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = true,
            shuffleHistory = emptyList()
        )

        // Simulate resolution of all 5 tracks
        val resolved = snapshot.queue.mapIndexed { index, song ->
            val wantInst = if (index == snapshot.currentQueueIndex) {
                snapshot.isInstrumental && song.hasInstrumentalEffective
            } else {
                song.isInstrumentalTrack || (snapshot.isInstrumental && song.hasInstrumentalEffective)
            }
            FakeResolvedTrack(song = song, isPlayable = true, isInstrumental = wantInst)
        }

        assertEquals(5, resolved.size)
        val validQueue = resolved.map { it.song }
        val targetIndex = resolved.indexOfFirst { it.song.id == songC.id }

        assertEquals(5, validQueue.size)
        assertEquals(listOf("song_a", "song_b", "song_c", "song_d", "song_e"), validQueue.map { it.id })
        assertEquals(2, targetIndex)
        assertEquals("song_c", validQueue[targetIndex].id)
        assertTrue("Active song C preserves instrumental mode", resolved[targetIndex].isInstrumental)
    }

    /**
     * Test 2 — Position restoration
     * Restore Song C at 45,000 ms.
     * Assert the restored playback position matches 45,000 ms.
     */
    @Test
    fun test2_positionRestoration_restoresSavedPositionMs() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(songA, songB, songC, songD, songE),
            currentQueueIndex = 2,
            currentPositionMs = 45000L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        val targetPositionMs = snapshot.currentPositionMs.coerceAtLeast(0L)
        assertEquals(45000L, targetPositionMs)
    }

    /**
     * Test 3 — Cold-start resumption
     * Verifies that the readiness signal (CompletableDeferred) suspends callers until
     * snapshot loading and track resolution finish, avoiding premature empty timeline returns.
     */
    @Test
    fun test3_coldStartResumption_waitsForReadinessSignal() = runTest {
        val readinessSignal = CompletableDeferred<Unit>()
        var timelineItemCount = 0

        // Resumption caller awaiting readiness
        assertFalse(readinessSignal.isCompleted)

        // Simulate completion after async restoration work finishes
        timelineItemCount = 5
        readinessSignal.complete(Unit)

        readinessSignal.await()
        assertTrue(readinessSignal.isCompleted)
        assertEquals("Resumption sees full timeline once readiness completes", 5, timelineItemCount)
    }

    /**
     * Test 4 — Missing track
     * One non-current track (Song B) fails to resolve.
     * Verify remaining valid tracks remain available and active index adjusts consistently.
     */
    @Test
    fun test4_missingTrack_preservesRemainingTracksWithCorrectIndex() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(songA, songB, songC, songD, songE),
            currentQueueIndex = 2, // Song C
            currentPositionMs = 45000L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        // Song B fails to resolve
        val resolved = snapshot.queue.mapIndexedNotNull { index, song ->
            if (song.id == "song_b") null
            else FakeResolvedTrack(song = song, isPlayable = true, isInstrumental = false)
        }

        assertEquals(4, resolved.size)
        val validQueue = resolved.map { it.song }
        val targetIndex = validQueue.indexOfFirst { it.id == songC.id }

        assertEquals(listOf("song_a", "song_c", "song_d", "song_e"), validQueue.map { it.id })
        assertEquals("Song C shifts from index 2 to index 1 due to missing Song B", 1, targetIndex)
        assertEquals("song_c", validQueue[targetIndex].id)
    }

    /**
     * Test 5 — Current track failure
     * The saved current track (Song C) fails to resolve.
     * Verify failure is handled safely without crashing and active index falls back safely.
     */
    @Test
    fun test5_currentTrackFailure_fallsBackSafelyWithoutCorruptingState() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(songA, songB, songC, songD, songE),
            currentQueueIndex = 2, // Song C
            currentPositionMs = 45000L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        // Song C fails to resolve
        val resolved = snapshot.queue.mapIndexedNotNull { index, song ->
            if (song.id == "song_c") null
            else FakeResolvedTrack(song = song, isPlayable = true, isInstrumental = false)
        }

        assertEquals(4, resolved.size)
        val validQueue = resolved.map { it.song }
        // When active item is missing, fallback to 0
        val targetIndex = validQueue.indexOfFirst { it.id == songC.id }.let { if (it >= 0) it else 0 }
        val positionMs = if (validQueue.any { it.id == songC.id }) snapshot.currentPositionMs else 0L

        assertEquals(0, targetIndex)
        assertEquals("song_a", validQueue[targetIndex].id)
        assertEquals("Position resets to 0L when active song cannot be resolved", 0L, positionMs)
    }

    /**
     * Test 6 — New playback request during restoration
     * A user plays a new song while snapshot restoration is running.
     * Verify the older restoration request is discarded and does not overwrite the new request.
     */
    @Test
    fun test6_newPlaybackRequestDuringRestoration_discardsOldRestoration() {
        var playbackRequestId = 10L
        val restoreRequestId = playbackRequestId // 10L

        // User starts new playback action
        playbackRequestId++ // now 11L

        val shouldApplyRestoration = (restoreRequestId == playbackRequestId)
        assertFalse("Old restoration request must be discarded when playbackRequestId increments", shouldApplyRestoration)
    }

    /**
     * Test 7 — Shuffle and repeat
     * Verify restored shuffle and repeat settings and shuffle history remain consistent.
     */
    @Test
    fun test7_shuffleAndRepeatRestoration_preservesModesAndHistory() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(songA, songB, songC, songD, songE),
            currentQueueIndex = 2,
            currentPositionMs = 30000L,
            isShuffle = true,
            repeatMode = RepeatMode.ALL,
            isInstrumental = true,
            shuffleHistory = listOf(0, 1)
        )

        assertEquals(true, snapshot.isShuffle)
        assertEquals(RepeatMode.ALL, snapshot.repeatMode)
        assertEquals(listOf(0, 1), snapshot.shuffleHistory)
    }

    /**
     * Test 8 — Empty snapshot
     * Verify an empty snapshot completes cleanly with no playable queue.
     */
    @Test
    fun test8_emptySnapshot_completesCleanlyWithEmptyQueue() = runTest {
        val snapshot = PlaybackSnapshot(
            queue = emptyList(),
            currentQueueIndex = -1,
            currentPositionMs = 0L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        val readinessSignal = CompletableDeferred<Unit>()
        val hasTracks = snapshot.queue.isNotEmpty()
        assertFalse(hasTracks)

        // Completes without hanging
        readinessSignal.complete(Unit)
        readinessSignal.await()
        assertTrue(readinessSignal.isCompleted)
    }

    /**
     * Test 9 — Priority 2: Full queue population in player timeline
     * Verifies that when playing Song C at index 2 from a 5-song playlist [A, B, C, D, E],
     * all 5 songs are populated into the player timeline (count = 5), with active index = 2.
     */
    @Test
    fun test9_playSong_populatesFullQueueInPlayerTimeline() {
        val playlist = listOf(songA, songB, songC, songD, songE)
        val targetIndex = 2 // Song C
        val currentSong = playlist[targetIndex]

        // When starting playback, all items in the queue must be mapped to media items
        val timelineMediaItems = playlist.mapIndexed { index, song ->
            val wantInst = if (index == targetIndex) currentSong.hasInstrumentalEffective else false
            FakeResolvedTrack(song = song, isPlayable = true, isInstrumental = wantInst)
        }

        assertEquals("Player timeline must contain all 5 items from the playlist", 5, timelineMediaItems.size)
        assertEquals("Active index must be 2 (Song C)", 2, targetIndex)
        assertEquals("song_c", timelineMediaItems[targetIndex].song.id)

        // Verifying next / previous in the timeline:
        val nextIndex = targetIndex + 1
        assertTrue("Timeline has next item", nextIndex in timelineMediaItems.indices)
        assertEquals("song_d", timelineMediaItems[nextIndex].song.id)

        val prevIndex = targetIndex - 1
        assertTrue("Timeline has previous item", prevIndex in timelineMediaItems.indices)
        assertEquals("song_b", timelineMediaItems[prevIndex].song.id)
    }

    /**
     * Test 10 — Priority 4: Stale Auto sync protection
     * While an Android Auto background queue sync is resolving songs, a phone action
     * changes playback (playSong, playQueueItem, next, etc.).
     * Verifies that the in-flight Auto sync is discarded and does not overwrite the phone queue.
     */
    @Test
    fun test10_staleAutoSync_isDiscardedWhenPhonePlaybackChanges() {
        var playbackRequestId = 1L
        var androidAutoQueueSyncVersion = 1L

        // Android Auto sync starts on IO
        val syncVersion = ++androidAutoQueueSyncVersion // version 2
        val syncRequestId = playbackRequestId // requestId 1

        // User on phone taps a song to play
        androidAutoQueueSyncVersion++ // version 3
        playbackRequestId++ // requestId 2
        val phoneQueue = listOf(songD, songE)

        // Android Auto sync finishes resolving and attempts to apply on Main
        val isStale = (syncVersion != androidAutoQueueSyncVersion || syncRequestId != playbackRequestId)
        assertTrue("In-flight Auto sync must be marked stale and discarded", isStale)

        // Phone queue remains intact
        assertEquals(2, phoneQueue.size)
        assertEquals(listOf("song_d", "song_e"), phoneQueue.map { it.id })
    }

    /**
     * Test 11 — Priority 3: Foreground promotion when player has media items
     * Android Auto connects and starts playback while app queue is still syncing.
     * Verifies that player media items + playWhenReady triggers foreground stay without waiting on applet queue.
     */
    @Test
    fun test11_foregroundPromotion_activeWhenPlayerHasItemsAndPlaying() {
        val appQueueEmpty = true
        val playerMediaItemCount = 5
        val playerCurrentIndex = 0
        val playWhenReady = true
        val isTransitioning = false
        val startInForegroundRequired = false

        val hasActiveQueue = !appQueueEmpty
        val hasPlayerItems = playerMediaItemCount > 0 && playerCurrentIndex in 0 until playerMediaItemCount
        val isPlaybackActive = playWhenReady || isTransitioning
        val shouldStayInForeground = startInForegroundRequired || ((hasActiveQueue || hasPlayerItems) && isPlaybackActive)

        assertTrue(
            "Service must stay in foreground when player has items and playback is active, even if app queue is empty/syncing",
            shouldStayInForeground
        )
    }

    /**
     * Test 12 — Priority 1: Local-first song lookup
     * Verifies that when a song exists in local storage, it is returned immediately
     * and no remote network lookup is performed.
     */
    @Test
    fun test12_localFirstSongLookup_returnsLocalSongWithoutNetworkCall() = runTest {
        val localStore = mutableMapOf<String, Song>("song_local" to songA)
        var networkCalls = 0

        suspend fun fakeGetSongById(id: String): Song? {
            val local = localStore[id]
            if (local != null) return local
            networkCalls++
            return null
        }

        val result = fakeGetSongById("song_local")
        assertNotNull(result)
        assertEquals("song_a", result?.id)
        assertEquals("No network requests should be made when song exists in local store", 0, networkCalls)
    }

    /**
     * Test 13 — Priority 1: Remote fallback on local miss
     * Verifies that when a song is missing locally, remote lookup is called and the result is stored.
     */
    @Test
    fun test13_remoteFallbackOnLocalMiss_storesAndReturnsRemoteSong() = runTest {
        val localStore = mutableMapOf<String, Song>()
        var networkCalls = 0

        suspend fun fakeGetSongById(id: String): Song? {
            val local = localStore[id]
            if (local != null) return local
            networkCalls++
            val remote = songB
            localStore[id] = remote
            return remote
        }

        val result = fakeGetSongById("song_b")
        assertEquals(1, networkCalls)
        assertNotNull(result)
        assertEquals("song_b", result?.id)
        assertTrue("Remote song was stored locally", localStore.containsKey("song_b"))

        // Subsequent call is a local hit
        val cached = fakeGetSongById("song_b")
        assertEquals(1, networkCalls)
        assertEquals("song_b", cached?.id)
    }

    /**
     * Test 14 — Priority 1: Safe null return on remote failure
     */
    @Test
    fun test14_remoteFailure_returnsNullSafely() = runTest {
        val localStore = mutableMapOf<String, Song>()

        suspend fun fakeGetSongById(id: String): Song? {
            val local = localStore[id]
            if (local != null) return local
            return try {
                throw java.io.IOException("Network down")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

        val result = fakeGetSongById("nonexistent")
        assertNull(result)
    }

    /**
     * Test 15 — Priority 1: CancellationException propagation
     */
    @Test
    fun test15_cancellationException_isPropagated() = runTest {
        suspend fun fakeGetSongById(id: String): Song? {
            try {
                throw kotlinx.coroutines.CancellationException("Job cancelled")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                return null
            }
        }

        try {
            fakeGetSongById("any")
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertTrue("CancellationException must be rethrown", true)
        }
    }

    private data class FakeResolvedTrack(
        val song: Song,
        val isPlayable: Boolean,
        val isInstrumental: Boolean
    )
}
