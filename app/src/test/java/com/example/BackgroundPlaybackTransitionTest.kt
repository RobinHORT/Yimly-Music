package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackManager
import com.example.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackgroundPlaybackTransitionTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    private val song1 = Song(
        id = "bg_song_1",
        title = "Song Alpha",
        artist = "Artist A",
        album = "Album Alpha",
        durationSeconds = 180L
    )

    private val song2 = Song(
        id = "bg_song_2",
        title = "Song Beta",
        artist = "Artist B",
        album = "Album Beta",
        durationSeconds = 200L
    )

    private val song3 = Song(
        id = "bg_song_3",
        title = "Song Gamma",
        artist = "Artist C",
        album = "Album Gamma",
        durationSeconds = 220L
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
    }

    @Test
    fun testSongProgressionAdvancesQueueCorrectly() {
        val playlist = listOf(song1, song2, song3)
        playbackManager.playSong(song1, playlist, startIndex = 0)

        assertEquals(0, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("bg_song_1", playbackManager.playbackInfo.value.currentSong?.id)

        // Advance to next song in background queue
        playbackManager.next()

        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("bg_song_2", playbackManager.playbackInfo.value.currentSong?.id)

        // Advance to third song
        playbackManager.next()

        assertEquals(2, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("bg_song_3", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRepeatAllLoopsQueueAtEnd() {
        val playlist = listOf(song1, song2)
        playbackManager.playSong(song1, playlist, startIndex = 0)
        playbackManager.setRepeatMode(RepeatMode.ALL)

        playbackManager.next()
        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)

        // Advancing at end of queue with RepeatMode.ALL wraps back to index 0
        playbackManager.next()
        assertEquals(0, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("bg_song_1", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRepeatOffStopsAtEndOfQueue() {
        val playlist = listOf(song1, song2)
        playbackManager.playSong(song1, playlist, startIndex = 0)
        playbackManager.setRepeatMode(RepeatMode.OFF)

        playbackManager.next()
        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)

        // Next at end with RepeatMode.OFF does not increment beyond bounds
        playbackManager.next()
        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)
    }

    @Test
    fun testTransitionWakeLockAcquisitionAndReleaseLifecycle() {
        // Initial state: not held
        assertFalse(playbackManager.isTransitionWakeLockHeld)

        // Acquire lock
        playbackManager.acquireTransitionWakeLock(10000L)
        assertTrue(playbackManager.isTransitionWakeLockHeld)

        // Pausing explicitly releases transition lock
        playbackManager.pause()
        assertFalse(playbackManager.isTransitionWakeLockHeld)
    }

    @Test
    fun testTransitionWakeLockNonReferenceCountedDuplicateSafety() {
        // Duplicate acquire calls should not require multiple releases
        playbackManager.acquireTransitionWakeLock(10000L)
        playbackManager.acquireTransitionWakeLock(10000L)
        assertTrue(playbackManager.isTransitionWakeLockHeld)

        // Single release call clears the lock cleanly
        playbackManager.releaseTransitionWakeLock()
        assertFalse(playbackManager.isTransitionWakeLockHeld)

        // Redundant release call should be a no-op without exception
        playbackManager.releaseTransitionWakeLock()
        assertFalse(playbackManager.isTransitionWakeLockHeld)
    }

    @Test
    fun testTransitionWakeLockReleasedOnDispose() {
        playbackManager.acquireTransitionWakeLock(10000L)
        assertTrue(playbackManager.isTransitionWakeLockHeld)

        // Disposing playback manager releases lock
        playbackManager.release()
        assertFalse(playbackManager.isTransitionWakeLockHeld)
    }
}
