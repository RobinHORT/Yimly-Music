package com.example.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ApplicationProvider
import com.example.YimlyApplication
import com.example.data.api.NetworkModule
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerInitialisationThreadTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
    }

    private fun createPlaybackManager(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    ): PlaybackManager {
        val app = context as YimlyApplication
        return PlaybackManager(
            context = context,
            musicRepository = app.musicRepository,
            preferencesManager = preferencesManager,
            snapshotRepository = com.example.playback.PlaybackSnapshotRepository(preferencesManager),
            coroutineScope = scope
        )
    }

    @Test
    fun testDirectOffMainSynchronousInitThrowsAssertion() {
        val playbackManager = createPlaybackManager()
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<Throwable?> {
            try {
                playbackManager.ensurePlayerInitialized()
                null
            } catch (t: Throwable) {
                t
            }
        }
        val thrown = future.get()
        executor.shutdown()

        assertNotNull("Direct off-main synchronous ensurePlayerInitialized() must throw an exception", thrown)
        assertTrue(
            "Expected IllegalStateException from thread check, but was $thrown",
            thrown is IllegalStateException
        )
        assertTrue(
            "Message should specify main thread requirement, got: ${thrown?.message}",
            thrown?.message?.contains("Player initialization must occur on the main thread") == true
        )
    }

    @Test
    fun testMainThreadConstructionConfiguresMainLooper() {
        val playbackManager = createPlaybackManager()
        val player = playbackManager.ensurePlayerInitialized()

        assertNotNull(player)
        assertEquals("Player applicationLooper must be main looper", Looper.getMainLooper(), player.applicationLooper)
        assertSame("player property must match initialized player", player, playbackManager.player)
        assertTrue("isPlayerInitialized must be true", playbackManager.isPlayerInitialized)
        playbackManager.release()
    }

    @Test
    fun testBackgroundSuspendCallerCorrectlyMarshalsToMain() = runBlocking {
        val playbackManager = createPlaybackManager()

        val player = withContext(Dispatchers.Default) {
            playbackManager.getOrInitializePlayerOnMain()
        }

        assertNotNull(player)
        assertEquals("Player applicationLooper must be main looper", Looper.getMainLooper(), player.applicationLooper)
        assertSame(player, playbackManager.player)
        playbackManager.release()
    }

    @Test
    fun testConstructorParityBothConstructorsUseMainLooper() = runBlocking {
        // Constructor 1: Secondary constructor PlaybackManager(context)
        val pm1 = PlaybackManager(context)
        val p1 = pm1.ensurePlayerInitialized()
        assertEquals(Looper.getMainLooper(), p1.applicationLooper)
        pm1.release()

        // Constructor 2: Full constructor with Default dispatcher scope
        val pm2 = PlaybackManager(
            context = context,
            musicRepository = (context as YimlyApplication).musicRepository,
            preferencesManager = preferencesManager,
            snapshotRepository = com.example.playback.PlaybackSnapshotRepository(preferencesManager)
        )
        val p2 = pm2.ensurePlayerInitialized()
        assertEquals(Looper.getMainLooper(), p2.applicationLooper)
        pm2.release()
    }

    @Test
    fun testConcurrentInitialisationReturnsSameInstanceWithoutDeadlock() = runBlocking {
        val playbackManager = createPlaybackManager()

        val deferreds = (1..10).map {
            async(Dispatchers.Default) {
                playbackManager.getOrInitializePlayerOnMain()
            }
        }

        val players = deferreds.awaitAll()
        val firstPlayer = players.first()

        for (p in players) {
            assertSame("All concurrent requests must return the exact same ExoPlayer instance", firstPlayer, p)
        }
        assertEquals(Looper.getMainLooper(), firstPlayer.applicationLooper)
        playbackManager.release()
    }

    @Test
    fun testDecoderRecoveryReleasesAndRecreatesOnMain() = runBlocking {
        val playbackManager = createPlaybackManager(
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
        val initialPlayer = playbackManager.ensurePlayerInitialized()
        assertNotNull(initialPlayer)

        val testSong = Song(
            id = "decoder_test_song",
            title = "Decoder Test Song",
            artist = "Artist",
            album = "Album",
            durationSeconds = 200L
        )

        playbackManager.playSong(testSong, listOf(testSong), 0)

        val codecError = PlaybackException(
            "MediaCodec error: decoder allocation failed",
            null,
            3001
        )

        playbackManager.handlePlayerError(codecError)

        // Allow background recovery delay (300ms) to trigger and dispatch onto Main
        ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(600L)
        ShadowLooper.idleMainLooper()

        val recoveredPlayer = playbackManager.player
        assertNotNull("Recovered player must not be null", recoveredPlayer)
        assertEquals("Recovered player must have main looper", Looper.getMainLooper(), recoveredPlayer?.applicationLooper)

        playbackManager.release()
    }

    @Test
    fun testPlaybackControlsFromBackgroundDispatcherExecuteOnMain() = runBlocking {
        val playbackManager = createPlaybackManager(
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
        playbackManager.ensurePlayerInitialized()

        val testSong = Song(
            id = "control_test_song",
            title = "Control Test Song",
            artist = "Artist",
            album = "Album",
            durationSeconds = 180L
        )
        playbackManager.playSong(testSong, listOf(testSong), 0)

        withContext(Dispatchers.Default) {
            playbackManager.pause()
            playbackManager.seekTo(1000L)
            playbackManager.play()
            playbackManager.togglePlayPause()
            playbackManager.next(manual = true)
            playbackManager.previous()
            playbackManager.playQueueItem(0)
        }

        ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(200L)
        ShadowLooper.idleMainLooper()

        assertNotNull(playbackManager.player)
        assertEquals(Looper.getMainLooper(), playbackManager.player?.applicationLooper)

        playbackManager.release()
    }

    @Test
    fun testQueueMutationsSynchroniseMedia3Timeline() = runBlocking {
        val playbackManager = createPlaybackManager()
        val player = playbackManager.ensurePlayerInitialized()

        val s1 = Song(id = "q1", title = "Track 1")
        val s2 = Song(id = "q2", title = "Track 2")
        val s3 = Song(id = "q3", title = "Track 3")

        playbackManager.playSong(s1, listOf(s1), 0)
        ShadowLooper.idleMainLooper()

        playbackManager.addToQueue(s2)
        ShadowLooper.idleMainLooper()
        assertEquals(2, player.mediaItemCount)

        playbackManager.playNext(s3)
        ShadowLooper.idleMainLooper()
        assertEquals(3, player.mediaItemCount)

        playbackManager.reorderQueue(2, 0)
        ShadowLooper.idleMainLooper()
        assertEquals(3, player.mediaItemCount)

        playbackManager.removeFromQueue(0)
        ShadowLooper.idleMainLooper()
        assertEquals(2, player.mediaItemCount)

        playbackManager.release()
    }
}
