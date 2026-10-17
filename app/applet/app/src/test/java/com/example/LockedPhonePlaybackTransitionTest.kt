package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import com.example.playback.PlaybackManager
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockedPhonePlaybackTransitionTest {
    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager
    private val song1 = Song(id = "s1", title = "Song 1", artist = "A", album = "Al", durationSeconds = 180L)
    private val song2 = Song(id = "s2", title = "Song 2", artist = "A", album = "Al", durationSeconds = 200L)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
    }

    @Test
    fun testWakeLockHeldDuringTransition() {
        playbackManager.playSong(song1, listOf(song1, song2), startIndex = 0)
        
        // Simulate "locking" the device
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val shadowPowerManager = org.robolectric.Shadows.shadowOf(powerManager)
        shadowPowerManager.setIsInteractive(false)

        // Trigger track ending
        playbackManager.next()
        
        // Verify lock is held immediately
        assertTrue(\"WakeLock should be held after next() called\", playbackManager.isTransitionWakeLockHeld)
    }
}
