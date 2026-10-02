package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.ServerUrlConfig
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import com.example.ui.components.resolveCoverUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import okhttp3.logging.HttpLoggingInterceptor
import com.example.data.api.NetworkModule
import com.example.data.api.AuthInterceptor
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceRegressionTests {

    @Test
    fun testServerUrlConfigUpdatesSongGettersDynamically() {
        // Given a default server configuration
        ServerUrlConfig.activeServerUrl = "https://default.yimly.link"
        val song = Song(id = "track_123", title = "Test Song")

        // Then getters return URLs pointing to the active default server
        assertEquals("https://default.yimly.link/api/songs/track_123/artwork", song.artworkUrl)
        assertEquals("https://default.yimly.link/api/songs/track_123/audio?type=main", song.audioUrl)

        // When the active server URL is dynamically updated
        ServerUrlConfig.activeServerUrl = "https://custom.my-server.com:8443"

        // Then all song getters must immediately reflect the new server URL without process restart or hardcoding
        assertEquals("https://custom.my-server.com:8443/api/songs/track_123/artwork", song.artworkUrl)
        assertEquals("https://custom.my-server.com:8443/api/songs/track_123/audio?type=main", song.audioUrl)
    }

    @Test
    fun testServerUrlConfigUpdatesCoverUrlResolution() {
        ServerUrlConfig.activeServerUrl = "https://default.yimly.link"
        val relativeCoverUrl = "uploads/covers/playlist_456.jpg"

        // resolveCoverUrl without parameters defaults to active server configuration
        assertEquals("https://default.yimly.link/uploads/covers/playlist_456.jpg", relativeCoverUrl.resolveCoverUrl())

        // Change configured server URL
        ServerUrlConfig.activeServerUrl = "https://new-host.org"
        assertEquals("https://new-host.org/uploads/covers/playlist_456.jpg", relativeCoverUrl.resolveCoverUrl())
    }

    @Test
    fun testSongHasInstrumentalEffectiveDeduction() {
        // A song that has no instrumental metadata or alternate audio paths
        val standardSong = Song(
            id = "song_vocal",
            title = "Standard Vocal Track",
            hasInstrumental = false
        )
        assertFalse(standardSong.hasInstrumentalEffective)

        // A song that explicitly declares instrumental availability
        val instSong = Song(
            id = "song_inst",
            title = "Acoustic Ballad",
            hasInstrumental = true
        )
        assertTrue(instSong.hasInstrumentalEffective)

        // A song that has alternate instrumental paths
        val pathSong = Song(
            id = "song_path",
            title = "Electronic Anthem",
            instrumentalPath = "/audio/anthem_inst.mp3"
        )
        assertTrue(pathSong.hasInstrumentalEffective)
    }

    @Test
    fun testServerUrlConfigUpdatesAlbumAndArtistGettersDynamically() {
        val album = com.example.data.models.Album(id = "album_abc", title = "Test Album")
        val artist = com.example.data.models.Artist(id = "artist_xyz", name = "Test Artist")

        // Default server behavior
        ServerUrlConfig.activeServerUrl = "https://default.yimly.link"
        assertEquals("https://default.yimly.link/api/albums/album_abc/artwork", album.artworkUrl)
        assertEquals("https://default.yimly.link/api/artists/artist_xyz/artwork", artist.avatarUrl)

        // Custom server behavior
        ServerUrlConfig.activeServerUrl = "https://custom.my-server.com:8443"
        assertEquals("https://custom.my-server.com:8443/api/albums/album_abc/artwork", album.artworkUrl)
        assertEquals("https://custom.my-server.com:8443/api/artists/artist_xyz/artwork", artist.avatarUrl)
    }

    open class ConcurrencyTrackingApiService : com.example.data.api.YimlyApiService {
        val activeCallCount = AtomicInteger(0)
        val maxConcurrentCalls = AtomicInteger(0)
        val callOrder = Collections.synchronizedList(mutableListOf<String>())
        var delayMs: Long = 30L

        var songs: List<Song> = emptyList()
        var albums: List<Album> = emptyList()
        var artists: List<Artist> = emptyList()
        var playlists: List<Playlist> = emptyList()
        var favorites: List<Song> = emptyList()

        private inline fun <T> trackCall(name: String, block: () -> T): T {
            callOrder.add(name)
            val current = activeCallCount.incrementAndGet()
            maxConcurrentCalls.updateAndGet { maxOf(it, current) }
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                return block()
            } finally {
                activeCallCount.decrementAndGet()
            }
        }

        override suspend fun getSongs(query: String?, page: Int?, limit: Int?): List<Song> {
            return trackCall("getSongs") { songs }
        }

        override suspend fun getSongById(id: String): Song {
            return songs.first { it.id == id }
        }

        override suspend fun getFavorites(): List<Song> {
            return trackCall("getFavorites") { favorites }
        }

        override suspend fun getAlbums(): List<Album> {
            return trackCall("getAlbums") { albums }
        }

        override suspend fun getAlbumSongs(id: String): List<Song> {
            return songs
        }

        override suspend fun getArtists(): List<Artist> {
            return trackCall("getArtists") { artists }
        }

        override suspend fun getArtistSongs(id: String): List<Song> {
            return songs
        }

        override suspend fun getPlaylists(): List<Playlist> {
            return trackCall("getPlaylists") { playlists }
        }

        override suspend fun getPlaylistById(id: String): Playlist {
            return trackCall("getPlaylistById_$id") {
                playlists.first { it.id == id }
            }
        }

        override suspend fun getPlaylistSongs(id: String): List<Song> {
            return trackCall("getPlaylistSongs_$id") {
                playlists.firstOrNull { it.id == id }?.songs ?: emptyList()
            }
        }

        override suspend fun login(request: com.example.data.models.LoginRequest): com.example.data.models.LoginResponse = throw NotImplementedError()
        override suspend fun getProfile(): com.example.data.models.UserProfile = throw NotImplementedError()
        override suspend fun logout(): Map<String, Any> = mapOf("success" to true)
        override suspend fun createPlaylist(request: com.example.data.models.CreatePlaylistRequest): Playlist = throw NotImplementedError()
        override suspend fun updatePlaylist(id: String, request: com.example.data.models.UpdatePlaylistRequest): Playlist = throw NotImplementedError()
        override suspend fun uploadPlaylistCover(id: String, coverBody: okhttp3.RequestBody): Playlist = throw NotImplementedError()
        override suspend fun deletePlaylist(id: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun addSongToPlaylist(id: String, request: com.example.data.models.AddPlaylistSongRequest): Map<String, Any> = mapOf("success" to true)
        override suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun sharePlaylist(id: String, request: com.example.data.models.SharePlaylistRequest): Map<String, Any> = mapOf("success" to true)
        override suspend fun getCollaborators(id: String): List<com.example.data.models.Collaborator> = emptyList()
        override suspend fun removeCollaborator(playlistId: String, userId: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun revokeShare(playlistId: String, userId: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun addFavorite(songId: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun removeFavorite(songId: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun search(query: String): com.example.data.models.SearchResult = com.example.data.models.SearchResult()
        override suspend fun getLyrics(songId: String): okhttp3.ResponseBody = throw NotImplementedError()
        override suspend fun createLyrics(id: String, body: okhttp3.RequestBody): okhttp3.ResponseBody = throw NotImplementedError()
        override suspend fun updateLyrics(id: String, body: okhttp3.RequestBody): okhttp3.ResponseBody = throw NotImplementedError()
        override suspend fun patchLyrics(id: String, body: okhttp3.RequestBody): okhttp3.ResponseBody = throw NotImplementedError()
        override suspend fun updateLyricsJson(id: String, request: Map<String, String>): Map<String, Any> = mapOf("success" to true)
        override suspend fun deleteLrc(id: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun deleteSong(id: String): Map<String, Any> = mapOf("success" to true)
        override suspend fun getLyricsOffset(id: String): com.example.data.models.LyricsOffsetResponse = throw NotImplementedError()
        override suspend fun updateLyricsOffset(id: String, request: com.example.data.models.UpdateLyricsOffsetRequest): Map<String, Any> = mapOf("success" to true)
        override suspend fun getSession(code: String): com.example.data.models.YimlySession = throw NotImplementedError()
        override suspend fun createSession(): com.example.data.models.YimlySession = throw NotImplementedError()
        override suspend fun joinSession(code: String): com.example.data.models.YimlySession = throw NotImplementedError()
        override suspend fun requestSongInSession(code: String, song: Song): Map<String, Any> = mapOf("success" to true)
    }

    @Test
    fun testParallelIndependentSyncExecutionAndConcurrency() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 40L
                songs = listOf(Song(id = "s1", title = "Song 1"))
                albums = listOf(Album(id = "a1", title = "Album 1"))
                artists = listOf(Artist(id = "art1", name = "Artist 1"))
                playlists = listOf(Playlist(id = "p1", name = "Playlist 1"))
                favorites = listOf(Song(id = "s1", title = "Song 1"))
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            val startTime = System.currentTimeMillis()
            val result = repository.syncWithBackend()
            val durationMs = System.currentTimeMillis() - startTime

            assertTrue("Sync must succeed", result.isSuccess)
            assertTrue(
                "Independent network requests must execute concurrently in parallel (max was ${fakeApi.maxConcurrentCalls.get()})",
                fakeApi.maxConcurrentCalls.get() > 1
            )
            assertTrue("Parallel sync duration should be faster than sequential 200ms, took: ${durationMs}ms", durationMs < 200L)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistDependencyOrderingPreserved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 10L
                playlists = listOf(Playlist(id = "pl_99", name = "My Playlist", songs = emptyList()))
                songs = listOf(Song(id = "s_pl1", title = "Playlist Song 1"))
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            val result = repository.syncWithBackend()
            assertTrue(result.isSuccess)

            val getPlaylistsIndex = fakeApi.callOrder.indexOf("getPlaylists")
            val getPlaylistSongsIndex = fakeApi.callOrder.indexOf("getPlaylistSongs_pl_99")

            assertTrue("getPlaylists must be called", getPlaylistsIndex >= 0)
            assertTrue("getPlaylistSongs must be called for empty playlists", getPlaylistSongsIndex >= 0)
            assertTrue(
                "getPlaylists must be called before getPlaylistSongs",
                getPlaylistsIndex < getPlaylistSongsIndex
            )

            val dbPlaylist = database.musicDao().getPlaylistById("pl_99")
            assertEquals("My Playlist", dbPlaylist?.name)
        } finally {
            database.close()
        }
    }

    @Test
    fun testSuccessfulCompleteSyncAndRoomPersistence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                songs = listOf(
                    Song(id = "s10", title = "Song 10", artist = "Artist 10", album = "Album 10"),
                    Song(id = "s20", title = "Song 20", artist = "Artist 20", album = "Album 20")
                )
                albums = listOf(Album(id = "a10", title = "Album 10"))
                artists = listOf(Artist(id = "art10", name = "Artist 10"))
                playlists = listOf(Playlist(id = "p10", name = "Favorites Mix", songs = songs))
                favorites = listOf(Song(id = "s10", title = "Song 10"))
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            val result = repository.syncWithBackend()
            assertTrue(result.isSuccess)

            assertEquals(2, database.musicDao().getSongCount())
            assertEquals(1, database.musicDao().getAllAlbums().first().size)
            assertEquals(1, database.musicDao().getAllArtists().first().size)
            assertEquals(1, database.musicDao().getAllPlaylists().first().size)
            assertEquals(1, database.musicDao().getFavoriteSongs().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPartialFailureResilienceInParallelSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = object : ConcurrencyTrackingApiService() {
                override suspend fun getAlbums(): List<Album> {
                    throw IOException("Album service temporarily down")
                }
            }.apply {
                delayMs = 0L
                songs = listOf(Song(id = "s_resilient", title = "Resilient Song"))
                artists = listOf(Artist(id = "art_resilient", name = "Resilient Artist"))
                playlists = listOf(Playlist(id = "p_resilient", name = "Resilient Playlist"))
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            val result = repository.syncWithBackend()
            assertTrue("Sync must be resilient to partial endpoint failure", result.isSuccess)

            assertEquals(1, database.musicDao().getSongCount())
            assertEquals(1, database.musicDao().getAllPlaylists().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness1_recentlySyncedPlaylistDoesNotTriggerAnotherNetworkRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                playlists = listOf(
                    Playlist(
                        id = "pl_fresh",
                        name = "Fresh Playlist",
                        songs = listOf(Song(id = "s_fresh1", title = "Song 1"))
                    )
                )
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            // Initial sync/fetch
            repository.refreshPlaylistDetail("pl_fresh")
            assertTrue("Playlist must be fresh after sync", repository.isPlaylistFresh("pl_fresh"))
            assertTrue("Initial call must query getPlaylistById", fakeApi.callOrder.contains("getPlaylistById_pl_fresh"))

            // Clear call order history
            fakeApi.callOrder.clear()

            // Open the playlist again recently (within 5-min window)
            repository.refreshPlaylistDetail("pl_fresh", force = false)

            // No network requests should have been made
            assertTrue("Recently synced playlist must NOT trigger any network requests", fakeApi.callOrder.isEmpty())
            assertEquals(
                "Local database playlist must still exist",
                "Fresh Playlist",
                database.musicDao().getPlaylistById("pl_fresh")?.name
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness2_unsyncedPlaylistDoesTriggerRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                playlists = listOf(
                    Playlist(
                        id = "pl_unsynced",
                        name = "Unsynced Playlist",
                        songs = listOf(Song(id = "s_unsynced1", title = "Unsynced Song"))
                    )
                )
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            assertFalse("Unsynced playlist must NOT be fresh initially", repository.isPlaylistFresh("pl_unsynced"))
            fakeApi.callOrder.clear()

            // Call refresh
            repository.refreshPlaylistDetail("pl_unsynced", force = false)

            assertTrue("Unsynced playlist must trigger getPlaylistById network request", fakeApi.callOrder.contains("getPlaylistById_pl_unsynced"))
            assertTrue("Playlist must now be fresh", repository.isPlaylistFresh("pl_unsynced"))
            assertEquals(
                "Unsynced Playlist",
                database.musicDao().getPlaylistById("pl_unsynced")?.name
            )
            val songs = database.musicDao().getSongsForPlaylist("pl_unsynced").first()
            assertEquals(1, songs.size)
            assertEquals("s_unsynced1", songs[0].id)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness3_stalePlaylistTriggersRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                playlists = listOf(
                    Playlist(
                        id = "pl_stale",
                        name = "Original Name",
                        songs = listOf(Song(id = "s_stale1", title = "Original Song"))
                    )
                )
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            repository.refreshPlaylistDetail("pl_stale")
            assertTrue(repository.isPlaylistFresh("pl_stale"))

            // Age the cache past the 5-minute freshness window
            val staleTimestamp = System.currentTimeMillis() - MusicRepository.PLAYLIST_FRESHNESS_WINDOW_MS - 5000L
            repository.markPlaylistSynced("pl_stale", staleTimestamp)
            assertFalse("Playlist past freshness window must be considered stale", repository.isPlaylistFresh("pl_stale"))

            // Update server data
            fakeApi.playlists = listOf(
                Playlist(
                    id = "pl_stale",
                    name = "Updated Server Name",
                    songs = listOf(Song(id = "s_stale1", title = "Original Song"), Song(id = "s_stale2", title = "New Song"))
                )
            )
            fakeApi.callOrder.clear()

            // Refresh should be triggered normally because cache is stale
            repository.refreshPlaylistDetail("pl_stale", force = false)

            assertTrue("Stale playlist must trigger network refresh", fakeApi.callOrder.contains("getPlaylistById_pl_stale"))
            assertTrue("Playlist must be marked fresh after refresh", repository.isPlaylistFresh("pl_stale"))
            assertEquals("Updated Server Name", database.musicDao().getPlaylistById("pl_stale")?.name)
            assertEquals(2, database.musicDao().getSongsForPlaylist("pl_stale").first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness4_explicitManualRefreshBypassesFreshnessCheck() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                playlists = listOf(
                    Playlist(
                        id = "pl_manual",
                        name = "Manual Playlist",
                        songs = listOf(Song(id = "s_m1", title = "Song 1"))
                    )
                )
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            // Initial fetch
            repository.refreshPlaylistDetail("pl_manual")
            assertTrue("Playlist is fresh", repository.isPlaylistFresh("pl_manual"))

            fakeApi.callOrder.clear()

            // Explicit/manual refresh with force = true
            repository.refreshPlaylistDetail("pl_manual", force = true)

            assertTrue(
                "Manual refresh MUST bypass freshness check and make network request",
                fakeApi.callOrder.contains("getPlaylistById_pl_manual")
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness5_failedRefreshDoesNotDestroyValidExistingCachedPlaylistData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            var shouldFail = false
            val fakeApi = object : ConcurrencyTrackingApiService() {
                override suspend fun getPlaylistById(id: String): Playlist {
                    if (shouldFail) throw IOException("503 Service Unavailable")
                    return super.getPlaylistById(id)
                }
            }.apply {
                delayMs = 0L
                playlists = listOf(
                    Playlist(
                        id = "pl_resilient",
                        name = "Resilient Playlist",
                        songs = listOf(
                            Song(id = "s_r1", title = "Cached Song 1"),
                            Song(id = "s_r2", title = "Cached Song 2")
                        )
                    )
                )
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            // Initial successful sync
            repository.refreshPlaylistDetail("pl_resilient")
            assertEquals(2, database.musicDao().getSongsForPlaylist("pl_resilient").first().size)
            assertEquals("Resilient Playlist", database.musicDao().getPlaylistById("pl_resilient")?.name)

            // Simulate network outage/failure
            shouldFail = true
            fakeApi.callOrder.clear()

            // Trigger a forced refresh during network failure
            repository.refreshPlaylistDetail("pl_resilient", force = true)

            // Existing local data must remain fully intact and undamaged
            val remainingPlaylist = database.musicDao().getPlaylistById("pl_resilient")
            assertNotNull("Cached playlist entity must not be destroyed", remainingPlaylist)
            assertEquals("Resilient Playlist", remainingPlaylist?.name)

            val remainingSongs = database.musicDao().getSongsForPlaylist("pl_resilient").first()
            assertEquals("Cached songs must not be wiped out on failed refresh", 2, remainingSongs.size)
            assertEquals("Cached Song 1", remainingSongs[0].title)
            assertEquals("Cached Song 2", remainingSongs[1].title)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness6_existingPlaylistSongRelationshipsRemainIntact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val orderedSongs = listOf(
                Song(id = "s_rel1", title = "First Song"),
                Song(id = "s_rel2", title = "Second Song"),
                Song(id = "s_rel3", title = "Third Song")
            )

            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                playlists = listOf(
                    Playlist(
                        id = "pl_order",
                        name = "Ordered Playlist",
                        songs = orderedSongs
                    )
                )
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            repository.refreshPlaylistDetail("pl_order")

            val localSongs1 = database.musicDao().getSongsForPlaylist("pl_order").first()
            assertEquals(3, localSongs1.size)
            assertEquals("s_rel1", localSongs1[0].id)
            assertEquals("s_rel2", localSongs1[1].id)
            assertEquals("s_rel3", localSongs1[2].id)

            // Subsequent cached open without network calls
            repository.refreshPlaylistDetail("pl_order", force = false)

            val localSongs2 = database.musicDao().getSongsForPlaylist("pl_order").first()
            assertEquals(3, localSongs2.size)
            assertEquals("s_rel1", localSongs2[0].id)
            assertEquals("s_rel2", localSongs2[1].id)
            assertEquals("s_rel3", localSongs2[2].id)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPlaylistFreshness7_existingSyncAndIdempotenceBehaviourRemainsIntact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val testPlaylists = listOf(
                Playlist(
                    id = "pl_sync1",
                    name = "Sync Playlist 1",
                    songs = listOf(Song(id = "s_sync1", title = "Sync Song 1"))
                ),
                Playlist(
                    id = "pl_sync2",
                    name = "Sync Playlist 2",
                    songs = listOf(Song(id = "s_sync2", title = "Sync Song 2"))
                )
            )

            val fakeApi = ConcurrencyTrackingApiService().apply {
                delayMs = 0L
                playlists = testPlaylists
            }

            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            // Run standard library syncWithBackend
            val syncResult = repository.syncWithBackend()
            assertTrue(syncResult.isSuccess)

            // Both playlists must now be recognized as fresh
            assertTrue("pl_sync1 must be fresh after library sync", repository.isPlaylistFresh("pl_sync1"))
            assertTrue("pl_sync2 must be fresh after library sync", repository.isPlaylistFresh("pl_sync2"))

            fakeApi.callOrder.clear()

            // Open pl_sync1 (simulating navigation into the playlist detail screen)
            repository.refreshPlaylistDetail("pl_sync1", force = false)
            repository.refreshPlaylistDetail("pl_sync2", force = false)

            // Zero network requests because both were synced during startup library sync!
            assertTrue(
                "Navigating to recently synced playlists must not trigger any network requests",
                fakeApi.callOrder.isEmpty()
            )

            // Re-syncing does not create duplicates
            val secondSync = repository.syncWithBackend()
            assertTrue(secondSync.isSuccess)
            assertEquals(2, database.musicDao().getAllPlaylists().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun testDeferredExoPlayer1_constructionDoesNotEagerlyCreateExoPlayer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService()
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            assertFalse(
                "PlaybackManager construction must NOT eagerly construct ExoPlayer",
                playbackManager.isPlayerInitialized
            )
            assertNull(
                "player property must be null before any playback or session request",
                playbackManager.player
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun testDeferredExoPlayer2_firstPlaybackOperationInitializesExoPlayer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val song = Song(id = "s_lazy", title = "Lazy Track", durationSeconds = 200L)
            val fakeApi = ConcurrencyTrackingApiService().apply {
                songs = listOf(song)
            }
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            assertFalse(playbackManager.isPlayerInitialized)

            // Trigger playback
            playbackManager.playSong(song)

            assertTrue(
                "First playback operation must initialize ExoPlayer",
                playbackManager.isPlayerInitialized
            )
            assertNotNull(
                "player property must now be initialized and non-null",
                playbackManager.player
            )
            assertEquals("s_lazy", playbackManager.playbackInfo.value.currentSong?.id)

            playbackManager.release()
        } finally {
            database.close()
        }
    }

    @Test
    fun testDeferredExoPlayer3_repeatedInitializationIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService()
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            val player1 = playbackManager.ensurePlayerInitialized()
            val player2 = playbackManager.ensurePlayerInitialized()
            val player3 = playbackManager.player

            assertSame("Multiple ensurePlayerInitialized calls must resolve to the exact same instance", player1, player2)
            assertSame("player property must return the same instance", player1, player3)

            playbackManager.release()
        } finally {
            database.close()
        }
    }

    @Test
    fun testDeferredExoPlayer4_playbackControlsAndRelease() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val s1 = Song(id = "s1", title = "Song 1", durationSeconds = 180L, hasInstrumental = true)
            val s2 = Song(id = "s2", title = "Song 2", durationSeconds = 220L, hasInstrumental = true)
            val fakeApi = ConcurrencyTrackingApiService().apply {
                songs = listOf(s1, s2)
            }
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            // Play
            playbackManager.playSong(s1, listOf(s1, s2), startIndex = 0)
            assertEquals("s1", playbackManager.playbackInfo.value.currentSong?.id)

            // Seek
            playbackManager.seekTo(12000L)
            assertEquals(12000L, playbackManager.playbackInfo.value.currentPositionMs)

            // Next
            playbackManager.next()
            assertEquals("s2", playbackManager.playbackInfo.value.currentSong?.id)

            // Seeking to beginning so previous() navigates to prior track instead of restarting current track
            playbackManager.seekTo(0L)
            playbackManager.previous()
            assertEquals("s1", playbackManager.playbackInfo.value.currentSong?.id)

            // Toggle Instrumental
            playbackManager.toggleInstrumental()
            kotlinx.coroutines.withTimeout(3000L) {
                while (!playbackManager.playbackInfo.value.isInstrumental) {
                    ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(10)
                }
            }
            assertTrue(playbackManager.playbackInfo.value.isInstrumental)

            // Release
            playbackManager.release()
            assertFalse(playbackManager.isPlayerInitialized)
            assertNull(playbackManager.player)

            // Repeated release is safe
            playbackManager.release()
        } finally {
            database.close()
        }
    }

    @Test
    fun testDeferredExoPlayer5_mediaSessionAccessInitializesPlayerTransparently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val fakeApi = ConcurrencyTrackingApiService()
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            assertFalse(playbackManager.isPlayerInitialized)

            // Accessing mediaSession (as Android Auto or MediaLibraryService does)
            val session = playbackManager.mediaSession
            assertNotNull("mediaSession must be initialized on demand", session)
            assertTrue("Player must now be initialized", playbackManager.isPlayerInitialized)
            assertNotNull("Session player must be ready", session?.player)

            playbackManager.release()
        } finally {
            database.close()
        }
    }

    @Test
    fun testPositionIsolation1_seekAndPositionStateFlowUpdate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val song = Song(id = "pos_song", title = "Position Track", durationSeconds = 300L)
            val fakeApi = ConcurrencyTrackingApiService().apply {
                songs = listOf(song)
            }
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            assertEquals(0L, playbackManager.playbackPositionMs.value)

            playbackManager.playSong(song)
            playbackManager.seekTo(45000L)
            assertEquals(45000L, playbackManager.playbackPositionMs.value)
            assertEquals(45000L, playbackManager.playbackInfo.value.currentPositionMs)

            playbackManager.release()
            assertEquals(0L, playbackManager.playbackPositionMs.value)
        } finally {
            database.close()
        }
    }

    @Test
    fun testPositionIsolation2_lyricsPositionTrackingIndependence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val song = Song(id = "lyrics_song", title = "Lyrics Track", durationSeconds = 180L)
            val fakeApi = ConcurrencyTrackingApiService().apply {
                songs = listOf(song)
            }
            val repository = MusicRepository(
                musicDao = database.musicDao(),
                apiService = fakeApi,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
            val preferencesManager = com.example.data.datastore.PreferencesManager(context)
            val playbackManager = com.example.playback.PlaybackManager(
                context = context,
                musicRepository = repository,
                preferencesManager = preferencesManager,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            )

            playbackManager.playSong(song)

            // Activate lyrics tracking
            playbackManager.setLyricsActive(true)
            playbackManager.seekTo(15000L)

            assertEquals(15000L, playbackManager.lyricsPositionMs.value)
            assertEquals(15000L, playbackManager.playbackPositionMs.value)

            // Deactivate lyrics tracking
            playbackManager.setLyricsActive(false)

            playbackManager.release()
            assertEquals(0L, playbackManager.lyricsPositionMs.value)
            assertEquals(0L, playbackManager.playbackPositionMs.value)
        } finally {
            database.close()
        }
    }

    @Test
    fun testProductionLoggingLevelIsNone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesManager = com.example.data.datastore.PreferencesManager(context)

        // Instantiate NetworkModule in release/production mode (isDebug = false)
        val releaseNetworkModule = NetworkModule(
            preferencesManager = preferencesManager,
            isDebug = false
        )

        val loggingInterceptor = releaseNetworkModule.okHttpClient.interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .firstOrNull()

        assertNotNull("HttpLoggingInterceptor must be present in OkHttpClient", loggingInterceptor)
        assertEquals(
            "Production/Release builds MUST NOT use Level.BODY, must be Level.NONE",
            HttpLoggingInterceptor.Level.NONE,
            loggingInterceptor!!.level
        )

        // Verify auth interceptor is still present and working
        val authInterceptor = releaseNetworkModule.okHttpClient.interceptors
            .filterIsInstance<AuthInterceptor>()
            .firstOrNull()
        assertNotNull("AuthInterceptor must remain present in OkHttpClient", authInterceptor)
    }

    @Test
    fun testDebugLoggingLevelIsBody() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesManager = com.example.data.datastore.PreferencesManager(context)

        // Instantiate NetworkModule in debug mode (isDebug = true)
        val debugNetworkModule = NetworkModule(
            preferencesManager = preferencesManager,
            isDebug = true
        )

        val loggingInterceptor = debugNetworkModule.okHttpClient.interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .firstOrNull()

        assertNotNull("HttpLoggingInterceptor must be present in OkHttpClient", loggingInterceptor)
        assertEquals(
            "Debug builds must retain Level.BODY for diagnostics",
            HttpLoggingInterceptor.Level.BODY,
            loggingInterceptor!!.level
        )
    }

    @Test
    fun testNetworkModuleDefaultsToBuildConfigDebug() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesManager = com.example.data.datastore.PreferencesManager(context)

        // Instantiate NetworkModule using default isDebug parameter
        val defaultNetworkModule = NetworkModule(
            preferencesManager = preferencesManager
        )

        val loggingInterceptor = defaultNetworkModule.okHttpClient.interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .firstOrNull()

        val expectedLevel = if (com.example.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        assertNotNull(loggingInterceptor)
        assertEquals(expectedLevel, loggingInterceptor!!.level)
    }

    @Test
    fun testArtistDerivationAndDeduplicationCorrectness() {
        val testCatalog = listOf(
            Song(id = "s1", title = "Song Alpha", artist = "Dua Lipa; Elton John", album = "Lockdown", genre = "Pop", explicitArtworkUrl = "https://cdn.example.com/alpha.jpg"),
            Song(id = "s2", title = "Song Beta", artist = "Dua Lipa", album = "Future Nostalgia", genre = "Dance", explicitArtworkUrl = "https://cdn.example.com/beta.jpg"),
            Song(id = "s3", title = "Song Gamma", artist = "elton john", album = "Rocketman", genre = "Rock"),
            Song(id = "s4", title = "Song Delta (Instrumental)", artist = "Dua Lipa", album = "Future Nostalgia", isInstrumental = true),
            Song(id = "s5", title = "Song Epsilon", artist = "", album = "Unknown Album")
        )

        val derivedArtists = MusicRepository.deriveArtistsFromSongs(testCatalog)

        // Must derive exactly 2 valid distinct artists
        assertEquals(2, derivedArtists.size)

        val dua = derivedArtists.find { it.name.equals("Dua Lipa", ignoreCase = true) }
        assertNotNull("Dua Lipa must be present", dua)
        assertEquals("Canonical casing must be preserved", "Dua Lipa", dua?.name)
        // Song 1 and Song 2 (Song 4 is instrumental, Song 5 is empty)
        assertEquals("Dua Lipa song count must be 2", 2, dua?.songCount)
        assertTrue("Dua Lipa genres must include Pop and Dance", dua?.genres?.containsAll(listOf("Pop", "Dance")) == true)
        assertEquals("https://cdn.example.com/alpha.jpg", dua?.avatarUrl)

        val elton = derivedArtists.find { it.name.equals("Elton John", ignoreCase = true) }
        assertNotNull("Elton John must be present", elton)
        assertEquals("Elton John song count must be 2", 2, elton?.songCount)

        // Ordering must be alphabetical by lowercase name
        assertEquals("Dua Lipa", derivedArtists[0].name)
        assertEquals("Elton John", derivedArtists[1].name)
    }

    @Test
    fun testArtistIdGenerationIsDeterministic() {
        val songA = Song(id = "1", title = "Track A", artist = "Lady Gaga; Bradley Cooper")
        val songB = Song(id = "2", title = "Track B", artist = "lady gaga")

        val artistsA = MusicRepository.deriveArtistsFromSongs(listOf(songA))
        val artistsB = MusicRepository.deriveArtistsFromSongs(listOf(songB))

        val gagaA = artistsA.find { it.name.equals("Lady Gaga", ignoreCase = true) }
        val gagaB = artistsB.find { it.name.equals("lady gaga", ignoreCase = true) }

        assertNotNull(gagaA)
        assertNotNull(gagaB)
        assertEquals("Deterministic artist ID must match regardless of casing differences", gagaA?.id, gagaB?.id)
    }

    @Test
    fun testFavoritesUserIsolationAndMetadataPreservation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val song1 = Song(id = "fav_song_1", title = "Favorite Song 1", artist = "Artist A", album = "Album A", isFavorite = true, addedAt = 1000L)
            val song2 = Song(id = "fav_song_2", title = "Favorite Song 2", artist = "Artist B", album = "Album B", isFavorite = false, addedAt = 2000L)
            val song3 = Song(id = "fav_song_3", title = "Favorite Song 3", artist = "Artist C", album = "Album C", isFavorite = true, addedAt = 3000L)

            database.musicDao().upsertSongs(listOf(song1.toEntity(), song2.toEntity(), song3.toEntity()))

            // Verify query returns only favorite songs ordered by addedAt DESC
            val favorites = database.musicDao().getFavoriteSongs().first()
            assertEquals("Only songs with isFavorite=true must be returned", 2, favorites.size)
            assertEquals("fav_song_3", favorites[0].id)
            assertEquals("fav_song_1", favorites[1].id)

            // Verify metadata is strictly preserved
            assertEquals("Favorite Song 3", favorites[0].title)
            assertEquals("Artist C", favorites[0].artist)
            assertEquals("Album C", favorites[0].album)
        } finally {
            database.close()
        }
    }

    @Test
    fun testEmptyFavoritesReturnsEmptyList() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val nonFavSong = Song(id = "regular_song", title = "Regular Song", artist = "Artist", isFavorite = false)
            database.musicDao().insertSong(nonFavSong.toEntity())

            val favorites = database.musicDao().getFavoriteSongs().first()
            assertTrue("Favorites must be empty when no song is marked favorite", favorites.isEmpty())
        } finally {
            database.close()
        }
    }
}
