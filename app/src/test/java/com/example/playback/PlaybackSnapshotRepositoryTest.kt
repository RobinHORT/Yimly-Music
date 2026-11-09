package com.example.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackSnapshotRepositoryTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var repository: PlaybackSnapshotRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
        repository = PlaybackSnapshotRepository(preferencesManager)
    }

    @Test
    fun testSaveAndLoadSnapshot() = runBlocking {
        val song = Song(id = "1", title = "Test Song")
        val snapshot = PlaybackSnapshot(
            queue = listOf(song),
            currentQueueIndex = 0,
            currentPositionMs = 1000L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )
        repository.saveSnapshot(snapshot)
        val loaded = repository.loadSnapshot()
        assertEquals(snapshot, loaded)
    }

    @Test
    fun testLoadNullWhenNoSnapshot() = runBlocking {
        repository.clearSnapshot()
        assertNull(repository.loadSnapshot())
    }

    @Test
    fun testClearSnapshot() = runBlocking {
        val song = Song(id = "1", title = "Test Song")
        val snapshot = PlaybackSnapshot(
            queue = listOf(song),
            currentQueueIndex = 0,
            currentPositionMs = 1000L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )
        repository.saveSnapshot(snapshot)
        repository.clearSnapshot()
        assertNull(repository.loadSnapshot())
    }
}
