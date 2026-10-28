package com.example

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaCodecReclaimRecoveryTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    private val testSong = Song(
        id = "codec_test_1",
        title = "Codec Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationSeconds = 150L
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
    }

    @Test
    fun testVideoTrackSelectionIsDisabledForAudioOnlyPlayback() {
        val player = playbackManager.ensurePlayerInitialized()
        val isVideoDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)
        assertTrue("Video track selection must be disabled to prevent allocating video MediaCodec instances", isVideoDisabled)
    }

    @Test
    fun testMediaCodecReclaimIncrementsRetryCounter() {
        playbackManager.ensurePlayerInitialized()
        assertEquals(0, playbackManager.codecRecoveryRetries)

        val exception = PlaybackException(
            "Released by resource manager",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        playbackManager.handlePlayerError(exception)

        assertEquals("Codec recovery retries should be incremented", 1, playbackManager.codecRecoveryRetries)
    }

    @Test
    fun testMediaCodecReclaimIsBoundedByMaxRetries() {
        playbackManager.ensurePlayerInitialized()

        val exception = PlaybackException(
            "MediaCodec error: Released by resource manager",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )

        for (i in 1..5) {
            playbackManager.handlePlayerError(exception)
        }

        assertTrue("Codec recovery retries must be bounded and not exceed 3", playbackManager.codecRecoveryRetries <= 3)
    }
}
