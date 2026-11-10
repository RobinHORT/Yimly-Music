package com.example.playback

import android.content.Context
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = musicRepository,
            preferencesManager = preferencesManager,
            snapshotRepository = snapshotRepository,
            coroutineScope = testScope
        )
    }

    @After
    fun tearDown() {
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
}
