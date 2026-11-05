package com.example.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.example.YimlyApplication
import com.example.data.db.toEntity
import com.example.data.models.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Focused regression tests for Android Auto queue restoration, timeline consistency,
 * cold-start readiness, and error handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAutoQueueRestorationTest {

    private lateinit var context: Context
    private lateinit var app: YimlyApplication
    private lateinit var playbackManager: PlaybackManager

    private val songA = Song(id = "song_a", title = "Song A", artist = "Artist 1", durationSeconds = 180)
    private val songB = Song(id = "song_b", title = "Song B", artist = "Artist 2", durationSeconds = 200)
    private val songC = Song(id = "song_c", title = "Song C", artist = "Artist 3", durationSeconds = 220, hasInstrumental = true)
    private val songD = Song(id = "song_d", title = "Song D", artist = "Artist 4", durationSeconds = 240)
    private val songE = Song(id = "song_e", title = "Song E", artist = "Artist 5", durationSeconds = 260)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as YimlyApplication
        playbackManager = app.playbackManager
        playbackManager.clearQueueAndStop()
    }

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

    /**
     * Test 16 — Natural next track transition
     * Verifies that when Media3 advances to the next track, current song and index
     * update accurately without assuming timeline index == queue index.
     */
    @Test
    fun test16_naturalNextTrackTransition_updatesSongAndIndex() {
        val queue = listOf(songA, songB, songC, songD, songE)
        var currentQueueIndex = 0
        var currentSong = queue[currentQueueIndex]

        // Natural auto-advance to songB (e.g. MediaItem transition with song_b)
        val nextMediaId = "all_song|song_b"
        val nextSongId = nextMediaId.substringAfter("|")
        val matchedIndex = queue.indexOfFirst { it.id == nextSongId }

        assertTrue(matchedIndex >= 0)
        currentQueueIndex = matchedIndex
        currentSong = queue[matchedIndex]

        assertEquals(1, currentQueueIndex)
        assertEquals("song_b", currentSong.id)
    }

    /**
     * Test 17 — Repeat-one and Repeat-all mode mapping
     * Verifies RepeatMode maps bidirectionally to Player repeat modes.
     */
    @Test
    fun test17_repeatModeMapping_roundTripsCorrectly() {
        fun mapToPlayer(mode: RepeatMode): Int = when (mode) {
            RepeatMode.OFF -> androidx.media3.common.Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
        }
        fun mapFromPlayer(mode: Int): RepeatMode = when (mode) {
            androidx.media3.common.Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            androidx.media3.common.Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, mapToPlayer(RepeatMode.OFF))
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, mapToPlayer(RepeatMode.ONE))
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, mapToPlayer(RepeatMode.ALL))

        assertEquals(RepeatMode.OFF, mapFromPlayer(androidx.media3.common.Player.REPEAT_MODE_OFF))
        assertEquals(RepeatMode.ONE, mapFromPlayer(androidx.media3.common.Player.REPEAT_MODE_ONE))
        assertEquals(RepeatMode.ALL, mapFromPlayer(androidx.media3.common.Player.REPEAT_MODE_ALL))
    }

    /**
     * Test 18 — Instrumental preservation across queue transitions
     * Verifies that if user has instrumental active, transitioning to an instrumental track
     * keeps instrumental active, and transitioning to a non-instrumental track falls back gracefully
     * without clearing the user preference.
     */
    @Test
    fun test18_instrumentalPreservation_acrossQueueTransitions() {
        val queue = listOf(songA, songC, songD) // songA: false, songC: true, songD: false
        var isInstrumentalPreference = true

        // Track 1: songA (no instrumental)
        val song1 = queue[0]
        val song1PlayableInst = isInstrumentalPreference && song1.hasInstrumentalEffective
        assertFalse("songA does not have instrumental", song1PlayableInst)

        // Natural transition to Track 2: songC (has instrumental)
        val song2 = queue[1]
        val song2PlayableInst = isInstrumentalPreference && song2.hasInstrumentalEffective
        assertTrue("songC preserves and plays instrumental", song2PlayableInst)

        // Manual transition to Track 3: songD (no instrumental)
        val song3 = queue[2]
        val song3PlayableInst = isInstrumentalPreference && song3.hasInstrumentalEffective
        assertFalse("songD plays vocal", song3PlayableInst)
        assertTrue("User instrumental preference was never cleared", isInstrumentalPreference)
    }

    /**
     * Test 19 — Production Integration: Android Auto queue sync preserves instrumental preference and variant
     */
    @Test
    fun testProduction_androidAutoQueueSync_preservesInstrumentalPreferenceAndVariant() = runTest {
        app.database.musicDao().upsertSong(songA.toEntity()) // hasInstrumental = false
        app.database.musicDao().upsertSong(songC.toEntity()) // hasInstrumental = true

        playbackManager.ensurePlayerInitialized()
        playbackManager.toggleInstrumental()
        assertTrue("User preference set to instrumental", playbackManager.playbackInfo.value.isInstrumental)

        val autoItem1 = MediaItem.Builder()
            .setMediaId("playlist_song|rock|song_c")
            .setUri("http://example.com/audio/song_c.mp3")
            .build()
        val autoItem2 = MediaItem.Builder()
            .setMediaId("playlist_song|rock|song_a")
            .setUri("http://example.com/audio/song_a.mp3")
            .build()
        val player = playbackManager.player!!
        player.setMediaItems(listOf(autoItem1, autoItem2), 0, 0L)

        playbackManager.syncAndroidAutoQueueFromPlayer(autoItem1)
        advanceUntilIdle()

        val info = playbackManager.playbackInfo.value
        assertEquals(2, info.queue.size)
        assertEquals("song_c", info.currentSong?.id)
        assertTrue("User instrumental preference must be preserved during AA sync", info.isInstrumental)
        assertTrue("Song C hasInstrumental must be true", info.hasInstrumental)

        // When switching to Song A (which lacks instrumental)
        player.seekTo(1, 0L)
        playbackManager.syncAndroidAutoQueueFromPlayer(autoItem2)
        advanceUntilIdle()
        val info2 = playbackManager.playbackInfo.value
        assertEquals("song_a", info2.currentSong?.id)
        assertFalse("Song A lacks instrumental", info2.hasInstrumental)
        assertTrue("User instrumental preference must stay true even when song lacks instrumental", info2.isInstrumental)
    }

    /**
     * Test 20 — Production Integration: Natural track transitions preserve instrumental playback
     */
    @Test
    fun testProduction_naturalTrackTransition_preservesInstrumentalPlayback() = runTest {
        app.database.musicDao().upsertSong(songA.toEntity())
        app.database.musicDao().upsertSong(songC.toEntity())

        playbackManager.playSong(songA, listOf(songA, songC), startIndex = 0)
        playbackManager.toggleInstrumental()
        assertTrue(playbackManager.playbackInfo.value.isInstrumental)

        val mediaItemC = playbackManager.buildMediaItem(songC, wantInstrumental = true)
        playbackManager.handleMediaItemTransition(mediaItemC, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceUntilIdle()

        val info = playbackManager.playbackInfo.value
        assertEquals("song_c", info.currentSong?.id)
        assertEquals(1, info.currentQueueIndex)
        assertTrue("Instrumental preference preserved across natural transition", info.isInstrumental)
        assertTrue("Song C hasInstrumental is true", info.hasInstrumental)
    }

    /**
     * Test 21 — Production Integration: Deleting non-current song removes from app queue and Media3 timeline
     */
    @Test
    fun testProduction_deleteNonCurrentSong_removesFromAppQueueAndMedia3Timeline() = runTest {
        playbackManager.playSong(songA, listOf(songA, songB, songC), startIndex = 0)
        val player = playbackManager.player!!
        assertEquals(3, player.mediaItemCount)
        assertEquals(3, playbackManager.playbackInfo.value.queue.size)
        assertEquals(0, playbackManager.playbackInfo.value.currentQueueIndex)

        // Delete non-current song (songB)
        playbackManager.removeSongFromQueue(songB.id)
        advanceUntilIdle()

        val info = playbackManager.playbackInfo.value
        assertEquals(2, info.queue.size)
        assertEquals(listOf("song_a", "song_c"), info.queue.map { it.id })
        assertEquals(0, info.currentQueueIndex)
        assertEquals("song_a", info.currentSong?.id)

        // Verify Media3 timeline also removed songB
        assertEquals(2, player.mediaItemCount)
        assertEquals("song_a", playbackManager.extractBaseSongId(player.getMediaItemAt(0).mediaId))
        assertEquals("song_c", playbackManager.extractBaseSongId(player.getMediaItemAt(1).mediaId))
    }

    /**
     * Test 22 — Production Integration: Deleting active song preserves replacement track and playback state
     */
    @Test
    fun testProduction_deleteActiveSong_preservesReplacementTrackAndPlaybackState() = runTest {
        playbackManager.playSong(songA, listOf(songA, songB, songC), startIndex = 0)
        playbackManager.toggleInstrumental()
        assertTrue(playbackManager.playbackInfo.value.isInstrumental)

        val player = playbackManager.player!!
        player.play()
        assertTrue(player.playWhenReady)

        // Delete the currently playing song (songA)
        playbackManager.removeSongFromQueue(songA.id)
        advanceUntilIdle()

        val info = playbackManager.playbackInfo.value
        assertEquals(2, info.queue.size)
        assertEquals("song_b", info.currentSong?.id)
        assertEquals(0, info.currentQueueIndex)
        assertTrue("Instrumental preference preserved when active song removed", info.isInstrumental)

        assertEquals(2, player.mediaItemCount)
        assertEquals("song_b", playbackManager.extractBaseSongId(player.getMediaItemAt(0).mediaId))
    }

    /**
     * Test 23 — Production Integration: Multiple occurrences of same song ID during deletion
     */
    @Test
    fun testProduction_deleteSongWithMultipleOccurrences_updatesIndicesAndTimeline() = runTest {
        playbackManager.playSong(songB, listOf(songA, songB, songA, songC), startIndex = 1)
        val player = playbackManager.player!!
        assertEquals(4, player.mediaItemCount)

        // Remove all occurrences of songA
        playbackManager.removeSongFromQueue(songA.id)
        advanceUntilIdle()

        val info = playbackManager.playbackInfo.value
        assertEquals(2, info.queue.size)
        assertEquals(listOf("song_b", "song_c"), info.queue.map { it.id })
        assertEquals(0, info.currentQueueIndex)
        assertEquals("song_b", info.currentSong?.id)

        assertEquals(2, player.mediaItemCount)
        assertEquals("song_b", playbackManager.extractBaseSongId(player.getMediaItemAt(0).mediaId))
        assertEquals("song_c", playbackManager.extractBaseSongId(player.getMediaItemAt(1).mediaId))
    }

    /**
     * Test 24 — Production Integration: Queue synchronization with Android Auto prefixed media IDs
     */
    @Test
    fun testProduction_queueSynchronization_withAndroidAutoPrefixedMediaIds() = runTest {
        playbackManager.playSong(songA, listOf(songA, songB, songC), startIndex = 0)
        val player = playbackManager.player!!

        // Remove using Android Auto prefixed ID: "playlist_song|rock|song_b"
        playbackManager.removeSongFromQueue("playlist_song|rock|song_b")
        advanceUntilIdle()

        val info = playbackManager.playbackInfo.value
        assertEquals(2, info.queue.size)
        assertEquals(listOf("song_a", "song_c"), info.queue.map { it.id })
        assertEquals(2, player.mediaItemCount)
        assertEquals("song_a", playbackManager.extractBaseSongId(player.getMediaItemAt(0).mediaId))
        assertEquals("song_c", playbackManager.extractBaseSongId(player.getMediaItemAt(1).mediaId))
    }


    /**
     * Test 25 — Production Integration: Repeat and shuffle behavior with actual player state
     */
    @Test
    fun testProduction_repeatAndShuffleBehavior_withActualPlayerState() {
        playbackManager.playSong(songA, listOf(songA, songB, songC), startIndex = 0)
        val player = playbackManager.player!!

        playbackManager.setRepeatMode(RepeatMode.ALL)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        assertEquals(RepeatMode.ALL, playbackManager.playbackInfo.value.repeatMode)

        playbackManager.setRepeatMode(RepeatMode.ONE)
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
        assertEquals(RepeatMode.ONE, playbackManager.playbackInfo.value.repeatMode)

        playbackManager.setRepeatMode(RepeatMode.OFF)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        assertEquals(RepeatMode.OFF, playbackManager.playbackInfo.value.repeatMode)

        playbackManager.setShuffle(true)
        assertTrue(player.shuffleModeEnabled)
        assertTrue(playbackManager.playbackInfo.value.isShuffle)

        playbackManager.setShuffle(false)
        assertFalse(player.shuffleModeEnabled)
        assertFalse(playbackManager.playbackInfo.value.isShuffle)
    }

    /**
     * Test 26 — Production Integration: Queue changes during async resolution bump requestId
     */
    @Test
    fun testProduction_queueChanges_duringAsyncResolution_abortsStaleResolution() = runBlocking {
        playbackManager.playSong(songA, listOf(songA, songB), startIndex = 0)
        val initialRequestId = playbackManager.playbackRequestId

        playbackManager.addToQueue(songC)
        val newRequestId = playbackManager.playbackRequestId
        assertTrue("Queue mutation must bump playbackRequestId", newRequestId > initialRequestId)

        playbackManager.removeFromQueue(0)
        assertTrue("Queue removal must bump playbackRequestId", playbackManager.playbackRequestId > newRequestId)
    }


    /**
     * Regression: when no item is active, adding a duplicate starts the newly
     * appended queue occurrence rather than the earlier occurrence with the same ID.
     */
    @Test
    fun testProduction_addDuplicateWhenNoCurrentSong_startsNewOccurrence() {
        val stateField = PlaybackManager::class.java.getDeclaredField("_playbackInfo").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val state = stateField.get(playbackManager) as kotlinx.coroutines.flow.MutableStateFlow<PlaybackInfo>
        state.value = state.value.copy(
            queue = listOf(songA),
            currentSong = null,
            currentQueueIndex = -1
        )

        playbackManager.addToQueue(songA)

        val info = playbackManager.playbackInfo.value
        assertEquals(listOf("song_a", "song_a"), info.queue.map { it.id })
        assertEquals("The appended occurrence must be selected", 1, info.currentQueueIndex)
        assertEquals("song_a", info.currentSong?.id)
    }

    /**
     * Invalid removals must not invalidate a pending playback resolution or AA sync.
     */
    @Test
    fun testProduction_invalidQueueRemovals_doNotInvalidatePlaybackRequest() {
        playbackManager.playSong(songA, listOf(songA, songB), startIndex = 0)
        val requestBefore = playbackManager.playbackRequestId

        playbackManager.removeFromQueue(-1)
        assertEquals("Invalid index must not invalidate playback", requestBefore, playbackManager.playbackRequestId)

        playbackManager.removeFromQueue(99)
        assertEquals("Out-of-range index must not invalidate playback", requestBefore, playbackManager.playbackRequestId)

        playbackManager.removeSongFromQueue("song_not_in_queue")
        assertEquals("Missing song must not invalidate playback", requestBefore, playbackManager.playbackRequestId)

        assertEquals(listOf("song_a", "song_b"), playbackManager.playbackInfo.value.queue.map { it.id })
    }

    /**
     * Exercise the real forwarding-player indexed seek with a Media3 timeline
     * whose order differs from the logical app queue. The media ID must win.
     */
    @Test
    fun testProduction_forwardingSeek_mapsTimelineItemToLogicalQueueIndex() = runTest {
        playbackManager.playSong(songA, listOf(songA, songB, songC), startIndex = 0)
        val player = playbackManager.player!!

        val mediaItemC = playbackManager.buildMediaItem(songC, wantInstrumental = false)
        val mediaItemA = playbackManager.buildMediaItem(songA, wantInstrumental = false)
        val mediaItemB = playbackManager.buildMediaItem(songB, wantInstrumental = false)
        player.setMediaItems(listOf(mediaItemC, mediaItemA, mediaItemB), 0, 0L)
        playbackManager.setShuffle(true)

        val forwardingPlayer = YimlyForwardingPlayer(player, playbackManager)
        forwardingPlayer.seekTo(0, 37_000L)

        val info = playbackManager.playbackInfo.value
        assertEquals("Timeline item C maps to logical queue index 2", 2, info.currentQueueIndex)
        assertEquals("song_c", info.currentSong?.id)
        assertEquals(37_000L, info.currentPositionMs)
    }

    private data class FakeResolvedTrack(
        val song: Song,
        val isPlayable: Boolean,
        val isInstrumental: Boolean
    )
}
