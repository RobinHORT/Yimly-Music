package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.playback.PlaybackSnapshot
import com.example.playback.RepeatMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackSnapshotTest {
    @Test
    fun playbackSnapshotRoundTripsQueuePositionModesAndShuffleHistory() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(
                Song(id = "snapshot_1", title = "Track One"),
                Song(id = "snapshot_2", title = "Track Two")
            ),
            currentQueueIndex = 1,
            currentPositionMs = 42_500L,
            isShuffle = true,
            repeatMode = RepeatMode.ALL,
            isInstrumental = true,
            shuffleHistory = listOf(0)
        )
        val adapter = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter(PlaybackSnapshot::class.java)

        val restored = adapter.fromJson(adapter.toJson(snapshot))

        assertNotNull(restored)
        assertEquals(snapshot, restored)
    }
    @Test
    fun preferencesManagerPersistsAndClearsPlaybackSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = PreferencesManager(context)
        val snapshot = PlaybackSnapshot(
            queue = listOf(Song(id = "persisted_song", title = "Persisted Track")),
            currentQueueIndex = 0,
            currentPositionMs = 18_250L,
            isShuffle = false,
            repeatMode = RepeatMode.OFF,
            isInstrumental = false
        )

        preferences.clearPlaybackSnapshot()
        preferences.savePlaybackSnapshot(snapshot)
        assertEquals(snapshot, preferences.loadPlaybackSnapshot())

        preferences.clearPlaybackSnapshot()
        assertEquals(null, preferences.loadPlaybackSnapshot())
    }

}
