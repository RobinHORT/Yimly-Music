package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackInfo
import com.example.playback.PlaybackManager
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
class InstrumentalToggleClickDebugTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    private val adeleHello = Song(
        id = "adele_hello_1",
        title = "Hello",
        artist = "Adele",
        album = "25",
        instrumentalAudioPath = "/api/songs/adele_hello_1/audio?type=instrumental",
        hasInstrumental = true
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
    fun testAdeleHelloInstrumentalToggleImmediateSwitch() {
        // Step 1: Play Adele - Hello
        playbackManager.playSong(adeleHello)
        waitForCondition { it.currentSong?.id == "adele_hello_1" }

        assertEquals("adele_hello_1", playbackManager.playbackInfo.value.currentSong?.id)
        assertFalse("Initially should be playing normal vocal track", playbackManager.playbackInfo.value.isInstrumental)
        assertTrue("Adele - Hello has instrumental track available", playbackManager.playbackInfo.value.hasInstrumental)

        // Step 2: Tap Instrumental once -> switches immediately to instrumental
        playbackManager.toggleInstrumental()
        waitForCondition { it.isInstrumental }

        assertTrue("After 1st toggle, playback must be in instrumental mode", playbackManager.playbackInfo.value.isInstrumental)

        // Step 3: Tap Instrumental again -> switches immediately back to vocal track
        playbackManager.toggleInstrumental()
        waitForCondition { !it.isInstrumental }

        assertFalse("After 2nd toggle, playback must switch back to vocal mode", playbackManager.playbackInfo.value.isInstrumental)
    }

    @Test
    fun testAdeleHelloWithMetadataFalseSwitchesToInstrumentalEndpoint() {
        // Adele - Hello ID 1985 with hasInstrumental = false in metadata
        val song1985 = Song(
            id = "1985",
            title = "Hello",
            artist = "Adele",
            album = "25",
            explicitAudioUrl = "https://music.yimly.io/api/songs/1985/audio?type=main",
            instrumentalAudioPath = null,
            hasInstrumental = false
        )

        // 1. Initial playback
        playbackManager.playSong(song1985)
        waitForCondition { 
            it.currentSong?.id == "1985" && playbackManager.player?.currentMediaItem?.mediaId?.startsWith("1985") == true 
        }

        val player = playbackManager.player
        val initialUri = player?.currentMediaItem?.requestMetadata?.mediaUri
            ?: player?.currentMediaItem?.localConfiguration?.uri
        assertTrue("Initial player URI must be main vocal", initialUri.toString().contains("type=main"))
        assertFalse("Initial isInstrumental state must be false", playbackManager.playbackInfo.value.isInstrumental)

        // 2. Toggle Instrumental ON -> MUST resolve to type=instrumental even though hasInstrumental was false
        playbackManager.toggleInstrumental()
        waitForCondition { it.isInstrumental }

        val instrumentalUri = player?.currentMediaItem?.requestMetadata?.mediaUri
            ?: player?.currentMediaItem?.localConfiguration?.uri
        assertTrue("Instrumental URI must be requested directly", instrumentalUri.toString().contains("type=instrumental"))
        assertTrue("Instrumental isInstrumental state must be true", playbackManager.playbackInfo.value.isInstrumental)

        // 3. Toggle Instrumental OFF -> MUST switch back to type=main
        playbackManager.toggleInstrumental()
        waitForCondition { !it.isInstrumental }

        val backToVocalUri = player?.currentMediaItem?.requestMetadata?.mediaUri
            ?: player?.currentMediaItem?.localConfiguration?.uri
        assertTrue("Vocal URI must be restored", backToVocalUri.toString().contains("type=main"))
        assertFalse("Playback state isInstrumental must be false", playbackManager.playbackInfo.value.isInstrumental)
    }

    @Test
    fun testInstrumentalPlayerErrorFallsBackGracefully() {
        val song = Song(
            id = "test_no_inst_404",
            title = "No Instrumental Track",
            artist = "Artist",
            album = "Album",
            explicitAudioUrl = "https://music.yimly.io/api/songs/test_no_inst_404/audio?type=main",
            hasInstrumental = false
        )

        playbackManager.playSong(song)
        waitForCondition { it.currentSong?.id == "test_no_inst_404" }

        // Switch to instrumental
        playbackManager.toggleInstrumental()
        waitForCondition { it.isInstrumental }

        // Simulate an ExoPlayer error on the instrumental stream (e.g. 404 from server)
        val player = playbackManager.player as? androidx.media3.exoplayer.ExoPlayer
        val dummyException = androidx.media3.common.PlaybackException(
            "HTTP 404 Not Found",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        // Trigger player error handler (e.g., 404 stream error)
        playbackManager.handlePlayerError(dummyException)

        waitForCondition { !it.isInstrumental }

        assertFalse("After error, isInstrumental should fall back to false", playbackManager.playbackInfo.value.isInstrumental)
        assertFalse("After error, hasInstrumental should be false", playbackManager.playbackInfo.value.hasInstrumental)
        val fallbackUri = player?.currentMediaItem?.requestMetadata?.mediaUri
            ?: player?.currentMediaItem?.localConfiguration?.uri
        assertTrue("Fallback player URI must be main vocal", fallbackUri.toString().contains("type=main"))
    }
}
