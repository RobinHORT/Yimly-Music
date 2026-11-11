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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSnapshotPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
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
        hasInstrumental = false
    )

    @Test
    fun `Rapid consecutive queue mutations trigger trailing persistence and save latest state`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        val song2 = createSong("2")
        val song3 = createSong("3")

        // First action: playSong persists immediately
        playbackManager.playSong(song1)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { snapshotRepository.saveSnapshot(any()) }

        // Rapid consecutive mutations within the 5s debounce window
        playbackManager.addToQueue(song2)
        playbackManager.addToQueue(song3)
        playbackManager.reorderQueue(0, 2)
        testScheduler.runCurrent()

        // Within debounce window, no additional saves yet
        coVerify(exactly = 1) { snapshotRepository.saveSnapshot(any()) }

        // Advance past debounce window to allow trailing persistence to run
        testScheduler.advanceTimeBy(5001L)
        testScheduler.advanceUntilIdle()

        // Latest state is eventually persisted
        coVerify(atLeast = 2) { snapshotRepository.saveSnapshot(any()) }
        val savedSnapshots = mutableListOf<PlaybackSnapshot>()
        coVerify { snapshotRepository.saveSnapshot(capture(savedSnapshots)) }
        val lastSnapshot = savedSnapshots.last()
        assertEquals(3, lastSnapshot.queue.size)
    }

    @Test
    fun `Paused queue mutations are persisted via trailing persistence`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        val song2 = createSong("2")

        playbackManager.playSong(song1)
        testScheduler.advanceUntilIdle()

        playbackManager.pause()
        testScheduler.advanceUntilIdle()
        val initialSaves = mutableListOf<PlaybackSnapshot>()
        coVerify { snapshotRepository.saveSnapshot(capture(initialSaves)) }
        val countAfterPause = initialSaves.size

        // Mutate queue while paused
        playbackManager.addToQueue(song2)
        testScheduler.runCurrent()

        // Advance past debounce window
        testScheduler.advanceTimeBy(5001L)
        testScheduler.advanceUntilIdle()

        val afterSaves = mutableListOf<PlaybackSnapshot>()
        coVerify { snapshotRepository.saveSnapshot(capture(afterSaves)) }
        assertTrue(afterSaves.size > countAfterPause)
        assertEquals(2, afterSaves.last().queue.size)
    }

    @Test
    fun `Queue clearing persists empty snapshot via clearSnapshot`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        playbackManager.playSong(song1)
        testScheduler.advanceUntilIdle()

        playbackManager.clearQueueAndStop()
        testScheduler.advanceUntilIdle()

        coVerify(atLeast = 1) { snapshotRepository.clearSnapshot() }
    }

    @Test
    fun `Priority queue cleanup keepOnlyCurrent persists single active track`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        val song2 = createSong("2")
        val song3 = createSong("3")

        playbackManager.playSong(song1, listOf(song1, song2, song3), 0)
        testScheduler.advanceUntilIdle()

        // Wait out throttle window so next action is immediate
        testScheduler.advanceTimeBy(5001L)
        testScheduler.advanceUntilIdle()

        playbackManager.clearUpcomingQueue()
        testScheduler.advanceUntilIdle()

        val savedSnapshots = mutableListOf<PlaybackSnapshot>()
        coVerify { snapshotRepository.saveSnapshot(capture(savedSnapshots)) }
        val lastSnapshot = savedSnapshots.last()
        assertEquals(1, lastSnapshot.queue.size)
        assertEquals(song1.id, lastSnapshot.queue[0].id)
    }

    @Test
    fun `Restoration does not prematurely overwrite the saved snapshot`() = runTest(testDispatcher) {
        val songA = createSong("restored_a")
        val songB = createSong("restored_b")
        val snapshot = PlaybackSnapshot(
            queue = listOf(songA, songB),
            currentQueueIndex = 0,
            currentPositionMs = 1200L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        clearMocks(snapshotRepository)

        // Restore snapshot
        val result = playbackManager.restoreFromSnapshot(snapshot)
        testScheduler.advanceUntilIdle()

        assertTrue(result)
        // During restoration, intermediate writes to snapshotRepository are blocked
        coVerify(exactly = 0) { snapshotRepository.saveSnapshot(any()) }
    }

    @Test
    fun `Progress tracker ticks do not cause repeated snapshot writes`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        playbackManager.playSong(song1)
        testScheduler.advanceUntilIdle()

        clearMocks(snapshotRepository)

        // Advance time representing playback progress ticks (e.g. 10 seconds of playback)
        testScheduler.advanceTimeBy(10000L)
        testScheduler.advanceUntilIdle()

        // Progress updates must not cause repeated snapshot writes
        coVerify(exactly = 0) { snapshotRepository.saveSnapshot(any()) }
    }
}
