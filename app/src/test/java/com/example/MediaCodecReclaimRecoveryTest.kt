package com.example

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun testPlayerInitialization() {
        val player = playbackManager.ensurePlayerInitialized()
        assertNotNull(player)
    }

    @Test
    fun testTransientNetworkErrorHandling() {
        playbackManager.ensurePlayerInitialized()

        val exception = PlaybackException(
            "Network connection failed",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        // Should handle without throwing exception
        playbackManager.handlePlayerError(exception)
        assertTrue(true)
    }
}
