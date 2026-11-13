package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.PerformanceRegressionTests
import com.example.data.datastore.PreferencesManager
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CanonicalMetadataSyncTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var repository: MusicRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var playbackManager: PlaybackManager
    private lateinit var playbackClient: PlaybackClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MusicRepository(
            musicDao = database.musicDao(),
            apiService = PerformanceRegressionTests.ConcurrencyTrackingApiService(),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            context = context
        )
        preferencesManager = PreferencesManager(context)
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = repository,
            preferencesManager = preferencesManager,
            snapshotRepository = PlaybackSnapshotRepository(preferencesManager),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
        playbackClient = PlaybackClient(
            context = context,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            autoConnect = false
        )
    }

    @After
    fun tearDown() {
        playbackClient.release()
        playbackManager.release()
        database.close()
    }

    @Test
    fun `session onAddMediaItems preserves Auto mediaIds and embeds complete Song metadata`() = runBlocking {
        val song = Song(
            id = "s_meta_1",
            title = "Auto Meta Track",
            artist = "Auto Artist",
            album = "Auto Album",
            durationSeconds = 245L,
            explicitArtworkUrl = "https://example.com/art.jpg",
            explicitAudioUrl = "https://example.com/audio.mp3",
            hasInstrumental = true,
            isFavorite = true
        )
        database.musicDao().upsertSongs(listOf(song.toEntity()))

        val session = playbackManager.mediaSession
        val callback = YimlySessionCallback(playbackManager)
        val controllerInfo = createTestControllerInfo()

        val requested = listOf(
            MediaItem.Builder().setMediaId("playlist_song|pl_1|s_meta_1").build(),
            MediaItem.Builder().setMediaId("favorite_song|s_meta_1_inst").build()
        )

        val resolved = callback.onAddMediaItems(session, controllerInfo, requested).get()
        assertEquals(2, resolved.size)
        // Android Auto media IDs remain unchanged
        assertEquals("playlist_song|pl_1|s_meta_1", resolved[0].mediaId)
        assertEquals("favorite_song|s_meta_1_inst", resolved[1].mediaId)

        val extras0 = resolved[0].mediaMetadata.extras
        assertNotNull(extras0)
        assertEquals("s_meta_1", extras0!!.getString(PlaybackMediaMetadata.EXTRA_CANONICAL_SONG_ID))
        assertTrue(extras0.getBoolean(PlaybackMediaMetadata.EXTRA_HAS_INSTRUMENTAL))
        assertFalse(extras0.getBoolean(PlaybackMediaMetadata.EXTRA_IS_INSTRUMENTAL_ACTIVE))
        assertTrue(extras0.getBoolean(PlaybackMediaMetadata.EXTRA_IS_FAVORITE))
        assertEquals(245L, extras0.getLong(PlaybackMediaMetadata.EXTRA_DURATION_SECONDS))
        assertEquals(245_000L, resolved[0].mediaMetadata.durationMs)

        val extras1 = resolved[1].mediaMetadata.extras
        assertNotNull(extras1)
        assertEquals("s_meta_1", extras1!!.getString(PlaybackMediaMetadata.EXTRA_CANONICAL_SONG_ID))
        assertTrue(extras1.getBoolean(PlaybackMediaMetadata.EXTRA_HAS_INSTRUMENTAL))
        assertTrue(extras1.getBoolean(PlaybackMediaMetadata.EXTRA_IS_INSTRUMENTAL_ACTIVE))
        assertTrue(extras1.getBoolean(PlaybackMediaMetadata.EXTRA_IS_FAVORITE))
    }

    @Test
    fun `buildMediaItem embeds canonical metadata and leaves unknown duration unset`() = runBlocking {
        val song = Song(
            id = "s_phone_99",
            title = "Phone Track",
            artist = "Artist",
            album = "Album",
            durationSeconds = 0L,
            explicitAudioUrl = "https://example.com/phone99.mp3",
            hasInstrumental = true,
            isFavorite = true
        )

        val item = playbackManager.buildMediaItem(song, wantInstrumental = true)
        assertEquals("s_phone_99_inst", item.mediaId)
        val extras = item.mediaMetadata.extras!!
        assertEquals("s_phone_99", extras.getString(PlaybackMediaMetadata.EXTRA_CANONICAL_SONG_ID))
        assertTrue(extras.getBoolean(PlaybackMediaMetadata.EXTRA_HAS_INSTRUMENTAL))
        assertTrue(extras.getBoolean(PlaybackMediaMetadata.EXTRA_IS_INSTRUMENTAL_ACTIVE))
        assertTrue(extras.getBoolean(PlaybackMediaMetadata.EXTRA_IS_FAVORITE))
        assertFalse(extras.containsKey(PlaybackMediaMetadata.EXTRA_DURATION_SECONDS))
        assertNull(item.mediaMetadata.durationMs)
    }

    @Test
    fun `queue sync matches Auto prefixed and instrumental IDs while keeping duplicate entries distinct`() {
        val syncListener = object : QueueMediaSyncListener {
            override fun onMediaItemAdded(index: Int, mediaItem: MediaItem) {}
            override fun onMediaItemRemoved(index: Int) {}
            override fun onMediaItemMoved(fromIndex: Int, toIndex: Int) {}
            override fun onQueueCleared() {}
        }
        val queueManager = PlaybackQueueManager(
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            mediaItemBuilder = { song, inst -> playbackManager.buildMediaItem(song, inst) },
            syncListener = syncListener,
            ioDispatcher = Dispatchers.Main.immediate,
            mainDispatcher = Dispatchers.Main.immediate
        )

        val songDup = Song(id = "dup_1", title = "Dup", artist = "A")
        val songMid = Song(id = "mid_2", title = "Mid", artist = "B")
        queueManager.updateQueueState(listOf(songDup, songMid, songDup), 0)

        val entry0 = queueManager.queueState.value.entries[0].entryId
        val entry2 = queueManager.queueState.value.entries[2].entryId
        assertNotEquals(entry0, entry2)

        assertTrue(queueManager.syncCurrentEntryFromPlayer(2, "playlist_song|pl_1|dup_1_inst"))
        assertEquals(entry2, queueManager.queueState.value.currentEntryId)
        assertEquals(2, queueManager.currentQueueIndex.value)

        // Malformed playlist ID without song segment must never match even if playlistId matches songId
        queueManager.updateQueueState(listOf(Song(id = "pl_1", title = "Collision", artist = "C")), 0)
        assertFalse(queueManager.syncCurrentEntryFromPlayer(0, "playlist_song|pl_1"))
    }

    @Test
    fun `updateFavoriteStatus propagates to queue, ExoPlayer items and PlaybackClient without disrupting playback`() = runBlocking {
        val song1 = Song(
            id = "fav_s1",
            title = "Track One",
            artist = "Artist 1",
            durationSeconds = 200L,
            explicitAudioUrl = "https://example.com/fav1.mp3",
            hasInstrumental = true,
            isFavorite = false
        )
        val song2 = Song(
            id = "fav_s2",
            title = "Track Two",
            artist = "Artist 2",
            durationSeconds = 220L,
            explicitAudioUrl = "https://example.com/fav2.mp3",
            hasInstrumental = false,
            isFavorite = false
        )

        // Initialize queue & ExoPlayer timeline (including duplicate song1 and Auto-prefixed active song2)
        val player = playbackManager.ensurePlayerInitialized()
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                playbackClient.updateState(p)
            }
        })

        val queueManagerField = PlaybackManager::class.java.getDeclaredField("queueManager").apply {
            isAccessible = true
        }
        val queueManager = queueManagerField.get(playbackManager) as PlaybackQueueManager
        queueManager.updateQueueState(listOf(song1, song2, song1), 1)
        val initialEntryIds = queueManager.queueState.value.entries.map { it.entryId }

        val item0 = MediaItem.Builder()
            .setMediaId("all_song|fav_s1")
            .setUri(Uri.parse(song1.explicitAudioUrl!!))
            .setMediaMetadata(PlaybackMediaMetadata.buildForSong(song1, isInstrumentalActive = false, artworkUri = null))
            .build()
        val item1 = MediaItem.Builder()
            .setMediaId("playlist_song|pl_7|fav_s2")
            .setUri(Uri.parse(song2.explicitAudioUrl!!))
            .setMediaMetadata(PlaybackMediaMetadata.buildForSong(song2, isInstrumentalActive = false, artworkUri = null))
            .build()
        val item2 = MediaItem.Builder()
            .setMediaId("recent_song|fav_s1")
            .setUri(Uri.parse(song1.explicitAudioUrl!!))
            .setMediaMetadata(PlaybackMediaMetadata.buildForSong(song1, isInstrumentalActive = false, artworkUri = null))
            .build()

        player.setMediaItems(listOf(item0, item1, item2), 1, 42_000L)
        player.prepare()
        player.playWhenReady = true
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, player.currentMediaItemIndex)
        assertTrue(player.currentPosition >= 42_000L)
        assertTrue(player.playWhenReady)

        // Toggle favorite on active track (fav_s2) and duplicate non-active track (fav_s1)
        playbackManager.updateFavoriteStatus("fav_s2", true)
        playbackManager.updateFavoriteStatus("fav_s1", true)
        shadowOf(Looper.getMainLooper()).idle()

        // 1. QueueController updated while keeping exact entryIds and order
        val updatedQueueState = queueManager.queueState.value
        assertEquals(initialEntryIds, updatedQueueState.entries.map { it.entryId })
        assertEquals(listOf("fav_s1", "fav_s2", "fav_s1"), updatedQueueState.entries.map { it.song.id })
        assertTrue(updatedQueueState.entries.all { it.song.isFavorite })

        // 2. ExoPlayer MediaItems updated in-place without disturbing index, position, or playWhenReady
        assertEquals(3, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertTrue("Playback position must not reset", player.currentPosition >= 42_000L)
        assertTrue("playWhenReady must stay intact", player.playWhenReady)
        assertEquals("all_song|fav_s1", player.getMediaItemAt(0).mediaId)
        assertEquals("playlist_song|pl_7|fav_s2", player.getMediaItemAt(1).mediaId)
        assertEquals("recent_song|fav_s1", player.getMediaItemAt(2).mediaId)
        assertTrue(player.getMediaItemAt(0).mediaMetadata.extras!!.getBoolean(PlaybackMediaMetadata.EXTRA_IS_FAVORITE))
        assertTrue(player.getMediaItemAt(1).mediaMetadata.extras!!.getBoolean(PlaybackMediaMetadata.EXTRA_IS_FAVORITE))
        assertTrue(player.getMediaItemAt(2).mediaMetadata.extras!!.getBoolean(PlaybackMediaMetadata.EXTRA_IS_FAVORITE))

        // 3. PlaybackClient reflects canonical IDs & updated favorite status
        val clientState = playbackClient.playbackState.value
        assertEquals("fav_s2", clientState.currentSong?.id)
        assertTrue(clientState.currentSong!!.isFavorite)
        assertEquals(listOf("fav_s1", "fav_s2", "fav_s1"), clientState.queue.map { it.id })
        assertTrue(clientState.queue.all { it.isFavorite })
    }

    private fun createTestControllerInfo(): androidx.media3.session.MediaSession.ControllerInfo {
        val constructors = androidx.media3.session.MediaSession.ControllerInfo::class.java.declaredConstructors
        for (c in constructors) {
            c.isAccessible = true
            val paramTypes = c.parameterTypes
            val args = arrayOfNulls<Any>(paramTypes.size)
            for (i in paramTypes.indices) {
                when {
                    paramTypes[i] == Int::class.javaPrimitiveType -> args[i] = 0
                    paramTypes[i] == Boolean::class.javaPrimitiveType -> args[i] = false
                    paramTypes[i] == String::class.java -> args[i] = "com.test.client"
                    paramTypes[i] == android.os.Bundle::class.java -> args[i] = android.os.Bundle()
                    else -> args[i] = null
                }
            }
            try {
                return c.newInstance(*args) as androidx.media3.session.MediaSession.ControllerInfo
            } catch (_: Throwable) {
            }
        }
        throw IllegalStateException("Could not create ControllerInfo")
    }
}
