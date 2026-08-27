package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Media3SourceReplacementTest {

    private lateinit var context: Context
    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = ExoPlayer.Builder(context).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun testMedia3SourceReplacementAndPositionPreservation() {
        // 1. Setup initial vocal MediaItem
        val vocalUri = Uri.parse("https://yimly.robinhort.link/api/songs/song_101/audio")
        val initialMediaItem = MediaItem.Builder()
            .setMediaId("song_101")
            .setUri(vocalUri)
            .build()

        player.setMediaItem(initialMediaItem)
        player.prepare()
        
        // Simulate playback progress and seek to target position (45,000 ms)
        val targetPositionMs = 45000L
        player.seekTo(targetPositionMs)
        
        val capturedPosition = player.currentPosition.coerceAtLeast(0L)
        assertEquals(targetPositionMs, capturedPosition)

        // 2. Perform Instrumental Toggle source replacement logic (as implemented in PlaybackManager)
        val instrumentalUri = Uri.parse("https://yimly.robinhort.link/api/songs/song_101_inst/audio")
        val metadata = MediaMetadata.Builder()
            .setTitle("All of the Lights")
            .setArtist("Kanye West")
            .build()

        val currentItem = player.currentMediaItem
        val requestMetadata = (currentItem?.requestMetadata ?: MediaItem.RequestMetadata.EMPTY)
            .buildUpon()
            .setMediaUri(instrumentalUri)
            .build()

        val newItem = (currentItem?.buildUpon() ?: MediaItem.Builder())
            .setMediaId("song_101_inst")
            .setUri(instrumentalUri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)
            .build()

        val activeIndex = player.currentMediaItemIndex
        if (activeIndex in 0 until player.mediaItemCount) {
            player.replaceMediaItem(activeIndex, newItem)
            player.seekTo(activeIndex, capturedPosition)
        } else {
            player.setMediaItem(newItem, capturedPosition)
        }
        player.prepare()

        // 3. Assertions
        val updatedItem = player.currentMediaItem
        assertNotNull(updatedItem)
        assertEquals(instrumentalUri.toString(), updatedItem?.localConfiguration?.uri?.toString())
        assertEquals(targetPositionMs, player.currentPosition)
    }
}
