package com.example

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackManager
import com.example.playback.RepeatMode
import com.example.playback.YimlyForwardingPlayer
import com.example.playback.YimlySessionCallback
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
class MediaControlsIntegrationTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    private val song1 = Song(
        id = "test_song_1",
        title = "Track One",
        artist = "Artist A",
        album = "Album Alpha",
        durationSeconds = 180L,
        hasInstrumental = true
    )

    private val song2 = Song(
        id = "test_song_2",
        title = "Track Two",
        artist = "Artist B",
        album = "Album Alpha",
        durationSeconds = 210L,
        hasInstrumental = false
    )

    private val song3 = Song(
        id = "test_song_3",
        title = "Track Three",
        artist = "Artist C",
        album = "Album Beta",
        durationSeconds = 150L,
        hasInstrumental = true
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
    }

    @Test
    fun testMediaSessionInitializationAndForwardingPlayer() {
        val session = playbackManager.mediaSession
        assertNotNull("MediaSession must be initialized", session)

        val player = session?.player
        assertNotNull("Session player must be initialized", player)
        assertTrue(
            "Player should be instance of YimlyForwardingPlayer",
            player is YimlyForwardingPlayer
        )
    }

    @Test
    fun testQueueNavigationCommandsAndAvailableCommands() {
        playbackManager.playSong(song1, listOf(song1, song2, song3), startIndex = 0)

        val session = playbackManager.mediaSession!!
        val player = session.player

        // At index 0, has next should be true, has previous should be false (at 0ms)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))

        // Trigger seekToNext via media controller / forwarding player
        player.seekToNext()
        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("test_song_2", playbackManager.playbackInfo.value.currentSong?.id)

        // At index 1, both next and previous should be available
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))

        // Trigger seekToPrevious
        player.seekToPrevious()
        assertEquals(0, playbackManager.playbackInfo.value.currentQueueIndex)
        assertEquals("test_song_1", playbackManager.playbackInfo.value.currentSong?.id)
    }

    @Test
    fun testRepeatAndShuffleModeMapping() {
        val session = playbackManager.mediaSession!!
        val player = session.player

        // Default repeat OFF
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        assertFalse(player.shuffleModeEnabled)

        // Set repeat ALL via player
        player.repeatMode = Player.REPEAT_MODE_ALL
        assertEquals(RepeatMode.ALL, playbackManager.playbackInfo.value.repeatMode)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)

        // Set repeat ONE
        player.repeatMode = Player.REPEAT_MODE_ONE
        assertEquals(RepeatMode.ONE, playbackManager.playbackInfo.value.repeatMode)

        // Toggle shuffle
        player.shuffleModeEnabled = true
        assertTrue(playbackManager.playbackInfo.value.isShuffle)
        assertTrue(player.shuffleModeEnabled)

        player.shuffleModeEnabled = false
        assertFalse(playbackManager.playbackInfo.value.isShuffle)
    }

    @Test
    fun testBluetoothMediaKeyEventsHandled() {
        val session = playbackManager.mediaSession!!
        val callback = YimlySessionCallback(playbackManager)
        playbackManager.playSong(song1, listOf(song1, song2, song3), startIndex = 0)

        val controller = createDummyControllerInfo()

        // Test KEYCODE_MEDIA_NEXT
        val nextIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        }
        val consumedNext = callback.onMediaButtonEvent(session, controller, nextIntent)
        assertTrue("KEYCODE_MEDIA_NEXT should be consumed", consumedNext)
        assertEquals(1, playbackManager.playbackInfo.value.currentQueueIndex)

        // Test KEYCODE_MEDIA_PREVIOUS
        val prevIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        }
        val consumedPrev = callback.onMediaButtonEvent(session, controller, prevIntent)
        assertTrue("KEYCODE_MEDIA_PREVIOUS should be consumed", consumedPrev)
        assertEquals(0, playbackManager.playbackInfo.value.currentQueueIndex)
    }

    @Test
    fun testInstrumentalPlaybackMetadataPreserved() {
        playbackManager.playSong(song1, listOf(song1, song2), startIndex = 0)

        val currentSong = playbackManager.playbackInfo.value.currentSong
        assertNotNull(currentSong)
        assertEquals("Track One", currentSong!!.title)
        assertEquals("Artist A", currentSong.artist)

        // Toggle instrumental
        playbackManager.toggleInstrumental()

        // Metadata still corresponds to Song 1
        assertEquals("Track One", playbackManager.playbackInfo.value.currentSong?.title)
        assertEquals("Artist A", playbackManager.playbackInfo.value.currentSong?.artist)
    }

    private fun createDummyControllerInfo(): androidx.media3.session.MediaSession.ControllerInfo {
        val constructors = androidx.media3.session.MediaSession.ControllerInfo::class.java.declaredConstructors
        for (c in constructors) {
            c.isAccessible = true
            val paramTypes = c.parameterTypes
            val args = arrayOfNulls<Any>(paramTypes.size)
            for (i in paramTypes.indices) {
                when {
                    paramTypes[i] == Int::class.javaPrimitiveType -> args[i] = 0
                    paramTypes[i] == Boolean::class.javaPrimitiveType -> args[i] = false
                    paramTypes[i] == String::class.java -> args[i] = "test.package"
                    paramTypes[i] == android.os.Bundle::class.java -> args[i] = android.os.Bundle()
                    else -> args[i] = null
                }
            }
            try {
                return c.newInstance(*args) as androidx.media3.session.MediaSession.ControllerInfo
            } catch (_: Exception) {}
        }
        throw IllegalStateException("Could not create ControllerInfo")
    }
}
