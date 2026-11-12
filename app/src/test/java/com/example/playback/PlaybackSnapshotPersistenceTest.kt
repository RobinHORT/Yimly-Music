package com.example.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackSnapshotPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var fakeSnapshotRepository: FakePlaybackSnapshotRepository

    private var simulatedTimeMs = 1000L
    private val testClock: () -> Long = { simulatedTimeMs }

    private lateinit var playbackManager: PlaybackManager

    class FakePlaybackSnapshotRepository(
        preferencesManager: PreferencesManager
    ) : PlaybackSnapshotRepository(preferencesManager) {
        val savedSnapshots = mutableListOf<PlaybackSnapshot>()
        var clearCount = 0

        override suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
            savedSnapshots.add(snapshot)
        }

        override suspend fun loadSnapshot(): PlaybackSnapshot? {
            return savedSnapshots.lastOrNull()
        }

        override suspend fun clearSnapshot() {
            clearCount++
            savedSnapshots.clear()
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
        fakeSnapshotRepository = FakePlaybackSnapshotRepository(preferencesManager)
        simulatedTimeMs = 1000L

        playbackManager = PlaybackManager(
            context = context,
            musicRepository = MusicRepository(
                musicDao = com.example.data.db.YimlyDatabase.getDatabase(context).musicDao(),
                apiService = com.example.data.api.NetworkModule(
                    preferencesManager = preferencesManager,
                    scope = testScope
                ).apiService,
                coroutineScope = testScope,
                context = context,
                preferencesManager = preferencesManager
            ),
            preferencesManager = preferencesManager,
            snapshotRepository = fakeSnapshotRepository,
            coroutineScope = testScope.backgroundScope,
            clock = testClock
        )
    }

    @After
    fun tearDown() {
        playbackManager.release()
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
        assertEquals(1, fakeSnapshotRepository.savedSnapshots.size)

        // Rapid consecutive mutations within the 5s debounce window
        playbackManager.addToQueue(song2)
        playbackManager.addToQueue(song3)
        playbackManager.reorderQueue(0, 2)
        testScheduler.runCurrent()

        // Within debounce window, no additional saves yet
        assertEquals(1, fakeSnapshotRepository.savedSnapshots.size)

        // Advance simulated time past debounce window (5000ms)
        simulatedTimeMs += 5001L
        testScheduler.advanceTimeBy(5001L)
        testScheduler.advanceUntilIdle()

        // Latest state is eventually persisted
        assertTrue(fakeSnapshotRepository.savedSnapshots.size >= 2)
        val lastSnapshot = fakeSnapshotRepository.savedSnapshots.last()
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
        val countAfterPause = fakeSnapshotRepository.savedSnapshots.size

        // Mutate queue while paused
        playbackManager.addToQueue(song2)
        testScheduler.runCurrent()

        // Advance simulated time past debounce window
        simulatedTimeMs += 5001L
        testScheduler.advanceTimeBy(5001L)
        testScheduler.advanceUntilIdle()

        assertTrue(fakeSnapshotRepository.savedSnapshots.size > countAfterPause)
        assertEquals(2, fakeSnapshotRepository.savedSnapshots.last().queue.size)
    }

    @Test
    fun `Queue clearing persists empty snapshot via clearSnapshot`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        playbackManager.playSong(song1)
        testScheduler.advanceUntilIdle()

        playbackManager.clearQueueAndStop()
        testScheduler.advanceUntilIdle()

        assertTrue(fakeSnapshotRepository.clearCount >= 1)
    }

    @Test
    fun `Priority queue cleanup keepOnlyCurrent persists single active track`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        val song2 = createSong("2")
        val song3 = createSong("3")

        playbackManager.playSong(song1, listOf(song1, song2, song3), 0)
        testScheduler.advanceUntilIdle()

        // Wait out throttle window so next action is immediate
        simulatedTimeMs += 5001L
        testScheduler.advanceTimeBy(5001L)
        testScheduler.advanceUntilIdle()

        playbackManager.clearUpcomingQueue()
        testScheduler.advanceUntilIdle()

        val lastSnapshot = fakeSnapshotRepository.savedSnapshots.last()
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

        fakeSnapshotRepository.savedSnapshots.clear()

        // Restore snapshot
        val result = playbackManager.restoreFromSnapshot(snapshot)
        testScheduler.advanceUntilIdle()

        assertTrue(result)
        // During restoration, intermediate writes to snapshotRepository are blocked
        assertEquals(0, fakeSnapshotRepository.savedSnapshots.size)
    }

    @Test
    fun `Progress tracker ticks do not cause repeated snapshot writes`() = runTest(testDispatcher) {
        val song1 = createSong("1")
        playbackManager.playSong(song1)
        testScheduler.advanceUntilIdle()

        fakeSnapshotRepository.savedSnapshots.clear()

        // Advance time representing playback progress ticks (e.g. 10 seconds of playback)
        simulatedTimeMs += 10000L
        testScheduler.advanceTimeBy(10000L)
        testScheduler.advanceUntilIdle()

        // Progress updates must not cause repeated snapshot writes
        assertEquals(0, fakeSnapshotRepository.savedSnapshots.size)
    }
}
