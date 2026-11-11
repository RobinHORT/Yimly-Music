package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerQueueConsistencyTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context = mockk<Context>(relaxed = true)
    private val musicRepository = mockk<MusicRepository>(relaxed = true)
    private val preferencesManager = mockk<PreferencesManager>(relaxed = true)
    private val snapshotRepository = mockk<PlaybackSnapshotRepository>(relaxed = true)
    private val mockPlayer = mockk<ExoPlayer>(relaxed = true)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper
        every { Looper.myLooper() } returns mockLooper

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        playbackManager = PlaybackManager(
            context = context,
            musicRepository = musicRepository,
            preferencesManager = preferencesManager,
            snapshotRepository = snapshotRepository,
            coroutineScope = testScope.backgroundScope
        )

        // Inject mock ExoPlayer so ensurePlayerInitialized() returns immediately
        val playerField = PlaybackManager::class.java.getDeclaredField("_player")
        playerField.isAccessible = true
        playerField.set(playbackManager, mockPlayer)
    }

    @After
    fun tearDown() {
        playbackManager.release()
        unmockkStatic(Looper::class)
        unmockkStatic(Log::class)
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun createSong(id: String) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        album = "Album $id",
        hasInstrumental = true
    )

    @Test
    fun `Empty-queue consistency ensured in PlaybackInfo`() = runTest {
        val song = createSong("1")

        // Add a song
        playbackManager.playSong(song)

        var info = playbackManager.playbackInfo.value
        assertEquals(1, info.queue.size)
        assertEquals(song.id, info.currentSong?.id)
        assertEquals(0, info.currentQueueIndex)

        // Toggle instrumental
        playbackManager.toggleInstrumental()
        info = playbackManager.playbackInfo.value
        assertEquals(true, info.isInstrumental)

        // Clear queue
        playbackManager.clearQueueAndStop()

        info = playbackManager.playbackInfo.value
        assertEquals(0, info.queue.size)
        assertNull(info.currentSong)
        assertEquals(-1, info.currentQueueIndex)
        assertFalse(info.hasInstrumental)
        assertFalse(info.isInstrumental) // Reset where appropriate (empty queue)
    }

    @Test
    fun `Rapid queue mutations maintain snapshot consistency`() = runTest {
        val song1 = createSong("1")
        val song2 = createSong("2")
        val song3 = createSong("3")

        playbackManager.playSong(song1, listOf(song1, song2, song3), 0)

        // Rapidly remove and check
        playbackManager.removeFromQueue(1) // Remove song2
        playbackManager.removeFromQueue(1) // Remove song3 (which is now at index 1)

        val info = playbackManager.playbackInfo.value
        assertEquals(1, info.queue.size)
        assertEquals(song1.id, info.currentSong?.id)
        assertEquals(0, info.currentQueueIndex)
    }

    @Test
    fun `Duplicate song identities remain stable during snapshot logic`() = runTest {
        val song1 = createSong("1")
        // Queue: [1 (a), 1 (b)]
        playbackManager.playSong(song1, listOf(song1, song1), 0)

        val infoBefore = playbackManager.playbackInfo.value
        assertEquals(2, infoBefore.queue.size)
        assertEquals(0, infoBefore.currentQueueIndex)

        playbackManager.next(manual = true)

        val infoAfter = playbackManager.playbackInfo.value
        assertEquals(2, infoAfter.queue.size)
        assertEquals(1, infoAfter.currentQueueIndex)
    }

    @Test
    fun `handlePlayerError releases transition WakeLock immediately`() = runTest {
        // Mock a WakeLock being held
        playbackManager.acquireTransitionWakeLock()

        // Call handlePlayerError
        val error = mockk<androidx.media3.common.PlaybackException>(relaxed = true)
        playbackManager.handlePlayerError(error)

        // Verify WakeLock is released
        assertFalse(playbackManager.isTransitionWakeLockHeld)
    }

    @Test
    fun `restoreFromSnapshot returns false when authoritative queue is already populated`() = runTest {
        val song = createSong("existing_1")
        playbackManager.playSong(song)

        val snapshot = PlaybackSnapshot(
            queue = listOf(createSong("snapshot_1"), createSong("snapshot_2")),
            currentQueueIndex = 0,
            currentPositionMs = 0,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        // Attempt to restore when queue is already populated
        val result = playbackManager.restoreFromSnapshot(snapshot)

        // Must return false and not overwrite
        assertFalse(result)
        assertEquals("existing_1", playbackManager.playbackInfo.value.currentSong?.id)
        assertEquals(1, playbackManager.playbackInfo.value.queue.size)
        assertEquals("existing_1", playbackManager.playbackInfo.value.queue[0].id)
    }

    @Test
    fun `restoreFromSnapshot returns true when queue is empty and successfully applies restored state`() = runTest {
        playbackManager.clearQueueAndStop()
        assertEquals(0, playbackManager.playbackInfo.value.queue.size)

        val songA = createSong("restored_a")
        val songB = createSong("restored_b")
        val snapshot = PlaybackSnapshot(
            queue = listOf(songA, songB),
            currentQueueIndex = 1,
            currentPositionMs = 4500L,
            isShuffle = true,
            repeatMode = RepeatMode.ALL,
            isInstrumental = true,
            shuffleHistory = listOf(0)
        )

        // Valid restoration on empty queue
        val result = playbackManager.restoreFromSnapshot(snapshot)

        // Must return true only after successfully applying the restored state
        assertTrue(result)
        val info = playbackManager.playbackInfo.value
        assertEquals(2, info.queue.size)
        assertEquals(1, info.currentQueueIndex)
        assertEquals("restored_b", info.currentSong?.id)
        assertEquals(true, info.isShuffle)
        assertEquals(RepeatMode.ALL, info.repeatMode)
        assertEquals(true, info.isInstrumental)
        assertEquals(4500L, info.currentPositionMs)
    }
}
