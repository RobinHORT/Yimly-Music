package com.example

import com.example.data.models.Song
import com.example.playback.PlaybackSnapshot
import com.example.playback.RepeatMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

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
}
