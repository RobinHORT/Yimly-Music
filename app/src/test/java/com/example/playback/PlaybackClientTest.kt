package com.example.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackClientTest {

    private lateinit var context: Context
    private lateinit var playbackClient: PlaybackClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        playbackClient = PlaybackClient(context)
    }

    @After
    fun tearDown() {
        playbackClient.release()
    }

    @Test
    fun test1_initialState_isNotNullAndDefaults() = runTest {
        val state = playbackClient.playbackState.value
        assertNotNull(state)
        assertTrue(state.queue.isEmpty())
        assertEquals(false, state.isPlaying)
    }

    @Test
    fun test2_progressState_initialDefaults() = runTest {
        val progress = playbackClient.progressState.value
        assertNotNull(progress)
        assertEquals(0L, progress.currentPositionMs)
        assertEquals(0L, progress.durationMs)
    }

    @Test
    fun test3_standardPlaybackCommands_doNotThrow() {
        playbackClient.play()
        playbackClient.pause()
        playbackClient.togglePlayPause()
        playbackClient.next()
        playbackClient.previous()
        playbackClient.seekTo(1000L)
        playbackClient.setRepeatMode(RepeatMode.ALL)
        playbackClient.setShuffle(true)
        playbackClient.toggleShuffle()
    }

    @Test
    fun test4_customCommands_doNotThrow() {
        playbackClient.toggleInstrumental()
        playbackClient.shuffleCurrentQueue()
    }

    @Test
    fun test5_release_cleansUpController() {
        playbackClient.release()
        playbackClient.pause()
    }

    @Test
    fun test6_repeatModeCycling() {
        val mode = RepeatMode.OFF
        val nextMode = mode.next()
        assertEquals(RepeatMode.ALL, nextMode)
        assertEquals(RepeatMode.ONE, nextMode.next())
        assertEquals(RepeatMode.OFF, nextMode.next().next())
    }

    @Test
    fun test7_repeatedRelease_isSafeAndIdempotent() {
        playbackClient.release()
        playbackClient.release()
        playbackClient.release()
        playbackClient.play()
    }

    @Test
    fun test8_customQueueCommands_doNotThrowWhenDisconnected() {
        val testSong = Song(id = "s1", title = "Title", artist = "Artist")
        playbackClient.release()

        playbackClient.playSong(testSong, listOf(testSong), 0)
        playbackClient.addToQueue(testSong)
        playbackClient.playNext(testSong)
        playbackClient.removeFromQueue(0)
        playbackClient.removeSongFromQueue("s1")
        playbackClient.reorderQueue(0, 1)
        playbackClient.clearUpcomingQueue()
        playbackClient.clearQueueAndStop()
        playbackClient.playQueueItem(0)
        playbackClient.updateFavoriteStatus("s1", true)
        playbackClient.setLyricsActive(true)
    }

    @Test
    fun test9_reconnect_isNoOpWhenReleased() {
        playbackClient.release()
        playbackClient.connect() // Must be no-op when released
        assertEquals(emptyList<Song>(), playbackClient.playbackState.value.queue)
    }

    @Test
    fun test10_toSongAndUpdateState_reconstructAndroidAutoPrefixedItems() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        try {
            val song1 = Song(
                id = "s100",
                title = "Playlist Song",
                artist = "Artist One",
                album = "Album One",
                durationSeconds = 215L,
                explicitArtworkUrl = "https://example.com/art1.jpg",
                hasInstrumental = true,
                isFavorite = true
            )
            val song2 = Song(
                id = "s200",
                title = "All Song Inst",
                artist = "Artist Two",
                album = "Album Two",
                durationSeconds = 180L,
                explicitArtworkUrl = "https://example.com/art2.jpg",
                hasInstrumental = true,
                isFavorite = false
            )
            val item1 = androidx.media3.common.MediaItem.Builder()
                .setMediaId("playlist_song|pl_9|s100")
                .setUri("https://example.com/100.mp3")
                .setMediaMetadata(
                    PlaybackMediaMetadata.buildForSong(
                        song = song1,
                        isInstrumentalActive = false,
                        artworkUri = android.net.Uri.parse("https://example.com/art1.jpg")
                    )
                )
                .build()
            val item2 = androidx.media3.common.MediaItem.Builder()
                .setMediaId("all_song|s200_inst")
                .setUri("https://example.com/200_inst.mp3")
                .setMediaMetadata(
                    PlaybackMediaMetadata.buildForSong(
                        song = song2,
                        isInstrumentalActive = true,
                        artworkUri = android.net.Uri.parse("https://example.com/art2.jpg")
                    )
                )
                .build()

            player.setMediaItems(listOf(item1, item2), 0, 0L)
            playbackClient.updateState(player)

            val stateVocal = playbackClient.playbackState.value
            assertEquals(2, stateVocal.queue.size)
            assertEquals("s100", stateVocal.currentSong?.id)
            assertEquals("s100", stateVocal.queue[0].id)
            assertEquals("s200", stateVocal.queue[1].id)
            assertTrue(stateVocal.currentSong!!.hasInstrumental)
            assertTrue(stateVocal.currentSong!!.isFavorite)
            assertEquals(215L, stateVocal.currentSong!!.durationSeconds)
            assertTrue(stateVocal.hasInstrumental)
            assertEquals(false, stateVocal.isInstrumental)

            player.seekTo(1, 0L)
            playbackClient.updateState(player)

            val stateInst = playbackClient.playbackState.value
            assertEquals("s200", stateInst.currentSong?.id)
            assertTrue(stateInst.hasInstrumental)
            assertTrue(stateInst.isInstrumental)
            assertEquals(false, stateInst.currentSong!!.isFavorite)
            assertEquals(180L, stateInst.currentSong!!.durationSeconds)
        } finally {
            player.release()
        }
    }

    @Test
    fun test11_toSongAndUpdateState_preservesPhonePlaybackAndUnknownDefaults() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        try {
            val phoneSongWithEffectiveInst = Song(
                id = "phone_1",
                title = "Phone Track",
                artist = "Phone Artist",
                album = "Phone Album",
                durationSeconds = 0L, // unknown duration must stay 0L, not invented
                instrumentalAudioUrl = "https://example.com/phone_1_inst.mp3",
                isFavorite = false
            )
            val phoneItem = androidx.media3.common.MediaItem.Builder()
                .setMediaId(CanonicalMediaId.forPlayback(phoneSongWithEffectiveInst.id, isInstrumental = true))
                .setUri("https://example.com/phone_1_inst.mp3")
                .setMediaMetadata(
                    PlaybackMediaMetadata.buildForSong(
                        song = phoneSongWithEffectiveInst,
                        isInstrumentalActive = true,
                        artworkUri = null
                    )
                )
                .build()

            val bareAutoItem = androidx.media3.common.MediaItem.Builder()
                .setMediaId("recent_song|bare_99")
                .setUri("https://example.com/bare_99.mp3")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Bare Title")
                        .build()
                )
                .build()

            player.setMediaItems(listOf(phoneItem, bareAutoItem), 0, 0L)
            playbackClient.updateState(player)

            val phoneState = playbackClient.playbackState.value
            assertEquals("phone_1", phoneState.currentSong?.id)
            assertTrue(phoneState.currentSong!!.hasInstrumental)
            assertTrue(phoneState.hasInstrumental)
            assertTrue(phoneState.isInstrumental)
            assertEquals(0L, phoneState.currentSong!!.durationSeconds)
            assertEquals(null, phoneItem.mediaMetadata.durationMs)

            player.seekTo(1, 0L)
            playbackClient.updateState(player)

            val bareState = playbackClient.playbackState.value
            assertEquals("bare_99", bareState.currentSong?.id)
            assertEquals("Bare Title", bareState.currentSong?.title)
            assertEquals(false, bareState.hasInstrumental)
            assertEquals(false, bareState.isInstrumental)
            assertEquals(false, bareState.currentSong!!.isFavorite)
            assertEquals(0L, bareState.currentSong!!.durationSeconds)
        } finally {
            player.release()
        }
    }
}
