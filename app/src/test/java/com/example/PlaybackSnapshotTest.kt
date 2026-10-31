package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Song
import com.example.data.models.LyricsFormatMode
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
    fun preferencesManagerPersistsLrcAndElrcOffsetsPerSong() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = PreferencesManager(context)
        preferences.clearSongLyricsOffsets("offset_song_1")

        preferences.setSongLyricsOffset("offset_song_1", LyricsFormatMode.LRC, 1250L)
        preferences.setSongLyricsOffset("offset_song_1", LyricsFormatMode.ELRC, -375L)

        assertEquals(1250L, preferences.getSongLyricsOffset("offset_song_1", LyricsFormatMode.LRC))
        assertEquals(-375L, preferences.getSongLyricsOffset("offset_song_1", LyricsFormatMode.ELRC))
        assertEquals(null, preferences.getSongLyricsOffset("offset_song_2", LyricsFormatMode.LRC))

        preferences.clearSongLyricsOffsets("offset_song_1")
        assertEquals(null, preferences.getSongLyricsOffset("offset_song_1", LyricsFormatMode.LRC))
        assertEquals(null, preferences.getSongLyricsOffset("offset_song_1", LyricsFormatMode.ELRC))
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
