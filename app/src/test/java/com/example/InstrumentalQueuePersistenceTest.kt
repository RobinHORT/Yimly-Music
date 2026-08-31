package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackInfo
import com.example.playback.PlaybackManager
import com.example.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InstrumentalQueuePersistenceTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    private val song1 = Song(
        id = "inst_song_1",
        title = "Song One",
        artist = "Artist A",
        hasInstrumental = true
    )
    private val song2 = Song(
        id = "inst_song_2",
        title = "Song Two",
        artist = "Artist B",
        hasInstrumental = true
    )
    private val song3 = Song(
        id = "inst_song_3",
        title = "Song Three",
        artist = "Artist C",
        hasInstrumental = true
    )
    private val songNoInst = Song(
        id = "norm_song_4",
        title = "Acoustic Solo",
        artist = "Artist D",
        hasInstrumental = false
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
    }

    private fun waitForCondition(timeoutMs: Long = 3000L, condition: (PlaybackInfo) -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            ShadowLooper.idleMainLooper()
            if (condition(playbackManager.playbackInfo.value)) {
                return
            }
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
        assertTrue("Condition was not met within ${timeoutMs}ms", condition(playbackManager.playbackInfo.value))
    }

    @Test
    fun testInstrumentalPersistenceThroughQueueAdvancementAndToggles() {
        val queue = listOf(song1, song2, song3, songNoInst)

        // 1. Play normal song
        playbackManager.playSong(song1, queue, startIndex = 0)
        waitForCondition { it.currentSong?.id == "inst_song_1" && it.hasInstrumental }

        assertEquals("inst_song_1", playbackManager.playbackInfo.value.currentSong?.id)
        assertFalse("Initially should be playing normal vocal track", playbackManager.playbackInfo.value.isInstrumental)
        assertTrue("Track 1 has instrumental available", playbackManager.playbackInfo.value.hasInstrumental)

        // 2. Tap Instrumental ON
        playbackManager.toggleInstrumental()
        waitForCondition { it.isInstrumental }

        assertTrue("After toggle ON, song 1 should be in instrumental mode", playbackManager.playbackInfo.value.isInstrumental)

        // 3. Tap Next -> Song 2
        playbackManager.next()
        waitForCondition { it.currentSong?.id == "inst_song_2" && it.isInstrumental }

        assertEquals("inst_song_2", playbackManager.playbackInfo.value.currentSong?.id)
        assertTrue("Song 2 should automatically play in instrumental mode", playbackManager.playbackInfo.value.isInstrumental)

        // 4. Tap Next -> Song 3
        playbackManager.next()
        waitForCondition { it.currentSong?.id == "inst_song_3" && it.isInstrumental }

        assertEquals("inst_song_3", playbackManager.playbackInfo.value.currentSong?.id)
        assertTrue("Song 3 should automatically play in instrumental mode", playbackManager.playbackInfo.value.isInstrumental)

        // 5. Tap Instrumental OFF
        playbackManager.toggleInstrumental()
        waitForCondition { !it.isInstrumental }

        assertFalse("After toggle OFF, song 3 should be back in vocal mode", playbackManager.playbackInfo.value.isInstrumental)

        // 6. Tap Previous -> Song 2
        playbackManager.previous()
        waitForCondition { it.currentSong?.id == "inst_song_2" && !it.isInstrumental }

        assertEquals("inst_song_2", playbackManager.playbackInfo.value.currentSong?.id)
        assertFalse("Song 2 should now play vocal since mode was turned off", playbackManager.playbackInfo.value.isInstrumental)

        // 7. Turn Instrumental back ON and test fallback when skipping to song with no instrumental
        playbackManager.toggleInstrumental()
        waitForCondition { it.isInstrumental }
        assertTrue(playbackManager.playbackInfo.value.isInstrumental)

        playbackManager.toggleShuffle()
        ShadowLooper.idleMainLooper()
        assertTrue("Shuffle must not reset instrumental mode", playbackManager.playbackInfo.value.isInstrumental)

        playbackManager.cycleRepeatMode()
        ShadowLooper.idleMainLooper()
        assertEquals(RepeatMode.ALL, playbackManager.playbackInfo.value.repeatMode)
        assertTrue("Repeat mode change must not reset instrumental mode", playbackManager.playbackInfo.value.isInstrumental)
    }
}
