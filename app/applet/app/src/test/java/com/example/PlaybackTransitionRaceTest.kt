package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackManager
import com.example.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackTransitionRaceTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    private val songs = (1..10).map { i ->
        Song(
            id = "song_$i",
            title = "Track $i",
            artist = "Artist $i",
            album = "Album $i",
            durationSeconds = 180L
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
    }

    @Test
    fun testRapidNextPressesAdvanceToTargetSafely() {
        playbackManager.playSong(songs[0], songs, startIndex = 0)
        assertEquals(0, playbackManager.playbackInfo.value.currentQueueIndex)

        // Rapidly call next 5 times
        repeat(5) {
            playbackManager.next(manual = true)
        }

        assertEquals(5, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("song_6", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRapidPreviousPressesMoveBackSafely() {
        playbackManager.playSong(songs[5], songs, startIndex = 5)
        assertEquals(5, playbackManager.playbackInfo.value.currentQueueIndex)

        // Rapidly call previous 3 times
        repeat(3) {
            playbackManager.previous()
        }

        assertEquals(2, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("song_3", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRapidAlternatingNextAndPrevious() {
        playbackManager.playSong(songs[2], songs, startIndex = 2)

        playbackManager.next(manual = true)
        playbackManager.next(manual = true)
        playbackManager.previous()
        playbackManager.next(manual = true)

        assertEquals(3, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("song_4", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRapidNextExceedingQueueBoundsWithRepeatOff() {
        playbackManager.playSong(songs[8], songs, startIndex = 8)
        playbackManager.setRepeatMode(RepeatMode.OFF)

        // Press next 5 times when only 1 track remains
        repeat(5) {
            playbackManager.next(manual = true)
        }

        assertEquals(9, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("song_10", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRapidNextWithRepeatAllWrapsCorrectly() {
        playbackManager.playSong(songs[8], songs, startIndex = 8)
        playbackManager.setRepeatMode(RepeatMode.ALL)

        // Press next 3 times from index 8 (total 10 items: 8 -> 9 -> 0 -> 1)
        repeat(3) {
            playbackManager.next(manual = true)
        }

        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("song_2", playbackManager.playbackInfo.value.currentSong?.id)
    }
}
