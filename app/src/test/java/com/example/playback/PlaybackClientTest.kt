package com.example.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackClientTest {

    private lateinit var context: Context
    private lateinit var playbackClient: PlaybackClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        playbackClient = PlaybackClient(context)
    }

    @After
    fun tearDown() {
        playbackClient.release()
    }

    @Test
    fun test1_initialState_isNotNullAndDefaults() = runTest {
        val state = playbackClient.playbackState.value
        assertNotNull(state)
        assertTrue(state.queue.isEmpty())
        assertEquals(false, state.isPlaying)
    }

    @Test
    fun test2_progressState_initialDefaults() = runTest {
        val progress = playbackClient.progressState.value
        assertNotNull(progress)
        assertEquals(0L, progress.currentPositionMs)
        assertEquals(0L, progress.durationMs)
    }

    @Test
    fun test3_standardPlaybackCommands_doNotThrow() {
        playbackClient.play()
        playbackClient.pause()
        playbackClient.togglePlayPause()
        playbackClient.next()
        playbackClient.previous()
        playbackClient.seekTo(1000L)
        playbackClient.setRepeatMode(RepeatMode.ALL)
        playbackClient.setShuffle(true)
        playbackClient.toggleShuffle()
    }

    @Test
    fun test4_customCommands_doNotThrow() {
        playbackClient.toggleInstrumental()
        playbackClient.shuffleCurrentQueue()
    }

    @Test
    fun test5_release_cleansUpController() {
        playbackClient.release()
        playbackClient.pause()
    }

    @Test
    fun test6_repeatModeCycling() {
        val mode = RepeatMode.OFF
        val nextMode = mode.next()
        assertEquals(RepeatMode.ALL, nextMode)
        assertEquals(RepeatMode.ONE, nextMode.next())
        assertEquals(RepeatMode.OFF, nextMode.next().next())
    }

    @Test
    fun test7_repeatedRelease_isSafeAndIdempotent() {
        playbackClient.release()
        playbackClient.release()
        playbackClient.release()
        playbackClient.play()
    }

    @Test
    fun test8_customQueueCommands_doNotThrowWhenDisconnected() {
        val testSong = Song(id = "s1", title = "Title", artist = "Artist")
        playbackClient.release()

        playbackClient.playSong(testSong, listOf(testSong), 0)
        playbackClient.addToQueue(testSong)
        playbackClient.playNext(testSong)
        playbackClient.removeFromQueue(0)
        playbackClient.removeSongFromQueue("s1")
        playbackClient.reorderQueue(0, 1)
        playbackClient.clearUpcomingQueue()
        playbackClient.clearQueueAndStop()
        playbackClient.playQueueItem(0)
        playbackClient.updateFavoriteStatus("s1", true)
        playbackClient.setLyricsActive(true)
    }

    @Test
    fun test9_reconnect_isNoOpWhenReleased() {
        playbackClient.release()
        playbackClient.connect() // Must be no-op when released
        assertEquals(emptyList<Song>(), playbackClient.playbackState.value.queue)
    }
}
