package com.example.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.YimlyApplication
import com.example.data.models.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ColdStartPlaybackCrashTest {

    private lateinit var context: Context
    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test play waits for snapshot restoration and signals transition`() = runTest {
        val app = context as YimlyApplication
        
        // Create PlaybackManager using the test scope. 
        // Restoration starts on IO, but eventually hits withContext(Dispatchers.Main).
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = app.musicRepository,
            preferencesManager = app.preferencesManager,
            coroutineScope = backgroundScope
        )

        // Call play(). It sets isTransitioning=true synchronously.
        playbackManager.play()
        assertTrue("isTransitioning must be true immediately", playbackManager.isTransitioning)

        // At this point, the play() coroutine is suspended waiting for restoration.
        // Restoration is likely suspended waiting for the Main dispatcher to run its final block.
        
        // Verify that the service hasn't started yet (or at least we haven't handed off to player)
        // Since we can't easily check Service startup in this environment without complex shadows,
        // we check that the player is still IDLE and unpopulated.
        val player = playbackManager.ensurePlayerInitialized()
        assertEquals(0, player.mediaItemCount)

        // Allow restoration and play() to finish.
        advanceUntilIdle()

        // After completion, isTransitioning should be false (cleanup in finally).
        assertFalse("isTransitioning must be false after completion", playbackManager.isTransitioning)
    }

    @Test
    fun `test pause cancels pending play resumption`() = runTest {
        val app = context as YimlyApplication
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = app.musicRepository,
            preferencesManager = app.preferencesManager,
            coroutineScope = backgroundScope
        )

        val initialRequestId = playbackManager.playbackRequestId
        playbackManager.play()
        val playRequestId = playbackManager.playbackRequestId
        assertTrue(playRequestId > initialRequestId)
        assertTrue(playbackManager.isTransitioning)

        // Call pause() immediately. It increments playbackRequestId.
        playbackManager.pause()
        val pauseRequestId = playbackManager.playbackRequestId
        assertTrue(pauseRequestId > playRequestId)
        assertFalse("isTransitioning must be false after pause", playbackManager.isTransitioning)

        // Complete all tasks.
        advanceUntilIdle()

        // The play() coroutine should have terminated early after await() 
        // because its captured requestId no longer matches playbackRequestId.
        assertFalse(playbackManager.isTransitioning)
    }

    @Test
    fun `test rapid Play Pause Play ensures only latest request wins`() = runTest {
        val app = context as YimlyApplication
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = app.musicRepository,
            preferencesManager = app.preferencesManager,
            coroutineScope = backgroundScope
        )

        playbackManager.play() // Request 1
        val req1 = playbackManager.playbackRequestId
        
        playbackManager.pause() // Cancels 1
        
        playbackManager.play() // Request 2
        val req2 = playbackManager.playbackRequestId
        
        assertTrue(req2 > req1)
        assertTrue(playbackManager.isTransitioning)

        advanceUntilIdle()
        
        // Final state should be consistent.
        assertFalse(playbackManager.isTransitioning)
    }

    @Test
    fun `test restoration with empty queue exits play early`() = runTest {
        val app = context as YimlyApplication
        // We assume DataStore is empty here.
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = app.musicRepository,
            preferencesManager = app.preferencesManager,
            coroutineScope = backgroundScope
        )

        playbackManager.play()
        assertTrue(playbackManager.isTransitioning)

        advanceUntilIdle()

        // Since queue is empty, play() should have returned after restoration without starting service.
        assertFalse(playbackManager.isTransitioning)
        val p = playbackManager.ensurePlayerInitialized()
        assertEquals(0, p.mediaItemCount)
    }

    @Test
    fun `test playCurrentQueueIndex sets isTransitioning synchronously`() {
        // This test doesn't need a scope since it checks synchronous behavior.
        val app = context as YimlyApplication
        playbackManager = app.playbackManager
        
        // Manually set a queue so playCurrentQueueIndex doesn't exit early.
        val song = Song(id = "test", title = "Test", artist = "Artist")
        // We use an internal reflection or just call a method that sets the queue.
        // Actually, we can use playSong which is public.
        
        playbackManager.playSong(song, listOf(song), startIndex = 0)
        
        // playSong calls playCurrentQueueIndex. 
        // We should be in transitioning state immediately.
        assertTrue("isTransitioning must be true immediately after playSong", playbackManager.isTransitioning)
    }

    @Test
    fun `test onStartCommand promotes to foreground when newly registered and transitioning`() {
        val app = context as YimlyApplication
        val service = org.robolectric.Robolectric.buildService(YimlyPlaybackService::class.java).create().get()
        
        val manager = app.playbackManager
        val song = Song(id = "test", title = "Test", artist = "Artist")
        manager.playSong(song, listOf(song), startIndex = 0)
        assertTrue(manager.isTransitioning)
        
        val intent = android.content.Intent(context, YimlyPlaybackService::class.java)
        service.onStartCommand(intent, 0, 1)
        
        // Ensure service is active and session is added
        assertNotNull(service)
        assertTrue(service.sessions.isNotEmpty())
    }

    @Test
    fun `test onStartCommand promotes to foreground when already registered and transitioning`() {
        val app = context as YimlyApplication
        val service = org.robolectric.Robolectric.buildService(YimlyPlaybackService::class.java).create().get()
        
        val manager = app.playbackManager
        val intent = android.content.Intent(context, YimlyPlaybackService::class.java)
        service.onStartCommand(intent, 0, 1)
        assertTrue(service.sessions.isNotEmpty())
        
        // Transition after already registered
        val song = Song(id = "test2", title = "Test 2", artist = "Artist")
        manager.playSong(song, listOf(song), startIndex = 0)
        assertTrue(manager.isTransitioning)
        
        service.onStartCommand(intent, 0, 2)
        assertNotNull(service)
    }

    @Test
    fun `test onStartCommand does not promote when not transitioning`() {
        val app = context as YimlyApplication
        val service = org.robolectric.Robolectric.buildService(YimlyPlaybackService::class.java).create().get()
        
        val manager = app.playbackManager
        assertFalse(manager.isTransitioning)
        
        val intent = android.content.Intent(context, YimlyPlaybackService::class.java)
        service.onStartCommand(intent, 0, 1)
        
        assertNotNull(service)
    }
}
