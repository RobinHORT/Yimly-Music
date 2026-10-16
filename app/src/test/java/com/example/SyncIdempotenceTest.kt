package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.db.PlaylistEntity
import com.example.data.db.SongEntity
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.data.models.UpdateLyricsOffsetRequest
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncIdempotenceTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var fakeApiService: FakeSyncApiService
    private lateinit var musicRepository: MusicRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeApiService = FakeSyncApiService()
        musicRepository = MusicRepository(
            musicDao = database.musicDao(),
            apiService = fakeApiService,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_repeatedSyncProducesExactSameLocalCountWithoutDuplicates() = runBlocking {
        // Setup 10 songs on server
        val initialSongs = (1..10).map { i ->
            Song(
                id = "song_$i",
                title = "Song $i",
                artist = "Artist $i",
                album = "Album $i",
                durationSeconds = 180L + i
            )
        }
        fakeApiService.songs = initialSongs

        // First sync
        val result1 = musicRepository.syncWithBackend()
        assertTrue(result1.isSuccess)
        val songs1 = musicRepository.allSongs.first()
        assertEquals("Initial sync should yield 10 songs", 10, songs1.size)
        assertEquals("Database song count should be 10", 10, database.musicDao().getSongCount())

        // Second sync (exact same server library)
        val result2 = musicRepository.syncWithBackend()
        assertTrue(result2.isSuccess)
        val songs2 = musicRepository.allSongs.first()
        assertEquals("Second sync must remain 10 songs (no duplicates)", 10, songs2.size)
        assertEquals("Database song count must remain 10", 10, database.musicDao().getSongCount())

        // Third sync
        val result3 = musicRepository.syncWithBackend()
        assertTrue(result3.isSuccess)
        val songs3 = musicRepository.allSongs.first()
        assertEquals("Third sync must remain 10 songs (no duplicates)", 10, songs3.size)
        assertEquals("Database song count must remain 10", 10, database.musicDao().getSongCount())
    }

    @Test
    fun test2_appRestartSyncMaintainsIdempotence() = runBlocking {
        val serverSongs = listOf(
            Song(id = "101", title = "Track A", artist = "Artist A", album = "Album A", durationSeconds = 200L),
            Song(id = "102", title = "Track B", artist = "Artist B", album = "Album B", durationSeconds = 210L)
        )
        fakeApiService.songs = serverSongs

        // Sync first time
        musicRepository.syncWithBackend()
        assertEquals(2, musicRepository.allSongs.first().size)

        // Simulate app restart by creating a new repository instance pointing to the same DB
        val newRepo = MusicRepository(
            musicDao = database.musicDao(),
            apiService = fakeApiService,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )

        // Sync again
        newRepo.syncWithBackend()
        val songsAfterRestart = newRepo.allSongs.first()
        assertEquals("After restart and sync, song count must still be 2", 2, songsAfterRestart.size)
        assertEquals(2, database.musicDao().getSongCount())
    }

    @Test
    fun test3_serverAddsSongIncrementsCountOnceAndRemainsIdempotent() = runBlocking {
        val serverSongs = (1..10).map { i ->
            Song(id = "s_$i", title = "Track $i", artist = "Artist", album = "Album", durationSeconds = 180L)
        }.toMutableList()
        fakeApiService.songs = serverSongs

        musicRepository.syncWithBackend()
        assertEquals(10, database.musicDao().getSongCount())

        // Server adds 1 new song
        serverSongs.add(Song(id = "s_11", title = "Track 11", artist = "Artist", album = "Album", durationSeconds = 190L))
        fakeApiService.songs = serverSongs

        musicRepository.syncWithBackend()
        assertEquals("Count should increment to 11", 11, database.musicDao().getSongCount())

        // Sync again
        musicRepository.syncWithBackend()
        assertEquals("Count should remain 11 after repeated sync", 11, database.musicDao().getSongCount())
    }

    @Test
    fun test4_serverDeletesSongReconcilesLocalLibraryCorrectly() = runBlocking {
        val serverSongs = (1..11).map { i ->
            Song(id = "s_$i", title = "Track $i", artist = "Artist", album = "Album", durationSeconds = 180L)
        }.toMutableList()
        fakeApiService.songs = serverSongs

        musicRepository.syncWithBackend()
        assertEquals(11, database.musicDao().getSongCount())

        // Server deletes song s_11
        serverSongs.removeIf { it.id == "s_11" }
        fakeApiService.songs = serverSongs

        musicRepository.syncWithBackend()
        assertEquals("Count should reduce to 10 after deletion sync", 10, database.musicDao().getSongCount())
        assertNull("s_11 should be removed from database", database.musicDao().getSongById("s_11"))

        // Sync again
        musicRepository.syncWithBackend()
        assertEquals("Count should remain 10 after repeated sync", 10, database.musicDao().getSongCount())
    }

    @Test
    fun test5_serverMetadataUpdateUpdatesInPlaceWithoutCreatingDuplicates() = runBlocking {
        fakeApiService.songs = listOf(
            Song(id = "track_99", title = "Original Title", artist = "Old Artist", album = "Old Album", durationSeconds = 200L)
        )

        musicRepository.syncWithBackend()
        val original = database.musicDao().getSongById("track_99")
        assertNotNull(original)
        assertEquals("Original Title", original!!.title)
        assertEquals("Old Artist", original.artist)
        assertEquals(1, database.musicDao().getSongCount())

        // Server updates metadata
        fakeApiService.songs = listOf(
            Song(id = "track_99", title = "Updated Title", artist = "New Artist", album = "New Album", durationSeconds = 205L)
        )

        musicRepository.syncWithBackend()
        assertEquals("Song count must remain 1 (no duplicate row)", 1, database.musicDao().getSongCount())
        val updated = database.musicDao().getSongById("track_99")
        assertNotNull(updated)
        assertEquals("Updated Title", updated!!.title)
        assertEquals("New Artist", updated.artist)
        assertEquals("New Album", updated.album)
        assertEquals(205L, updated.durationSeconds)
    }

    @Test
    fun test6_favoritesPreservedAcrossRepeatedSyncs() = runBlocking {
        fakeApiService.songs = listOf(
            Song(id = "fav_1", title = "Fav Track", artist = "Artist", album = "Album", durationSeconds = 180L, isFavorite = false)
        )

        musicRepository.syncWithBackend()
        database.musicDao().updateFavorite("fav_1", true)
        assertTrue(database.musicDao().getSongById("fav_1")!!.isFavorite)

        // Repeated sync with server (where server might send isFavorite = false in songs list)
        musicRepository.syncWithBackend()
        musicRepository.syncWithBackend()

        val song = database.musicDao().getSongById("fav_1")
        assertNotNull(song)
        assertTrue("isFavorite must be preserved across syncs", song!!.isFavorite)
        assertEquals(1, database.musicDao().getSongCount())
    }

    @Test
    fun test7_playlistMembershipPreservedWithoutDuplicateEntries() = runBlocking {
        val song1 = Song(id = "pl_song_1", title = "Playlist Song 1", artist = "Artist", album = "Album", durationSeconds = 180L)
        val playlist = Playlist(
            id = "pl_1",
            name = "My Playlist",
            songs = listOf(song1)
        )
        fakeApiService.songs = listOf(song1)
        fakeApiService.playlists = listOf(playlist)

        musicRepository.syncWithBackend()
        val plSongs1 = database.musicDao().getSongsForPlaylist("pl_1").first()
        assertEquals(1, plSongs1.size)
        assertEquals(1, database.musicDao().getSongCount())

        // Repeated syncs
        musicRepository.syncWithBackend()
        musicRepository.syncWithBackend()

        val plSongs2 = database.musicDao().getSongsForPlaylist("pl_1").first()
        assertEquals("Playlist must still contain exactly 1 song, no duplicates", 1, plSongs2.size)
        assertEquals(1, database.musicDao().getSongCount())
    }

    @Test
    fun test8_networkFailureDoesNotWipeLocalLibrary() = runBlocking {
        fakeApiService.songs = (1..5).map { i ->
            Song(id = "net_song_$i", title = "Net Track $i", artist = "Artist", album = "Album", durationSeconds = 180L)
        }

        // Initial successful sync
        musicRepository.syncWithBackend()
        assertEquals(5, database.musicDao().getSongCount())

        // Simulate server network failure
        fakeApiService.shouldFail = true

        val failResult = musicRepository.syncWithBackend()
        // Sync should gracefully return failure result or handle it without throwing
        // Local library must remain intact!
        assertEquals("Local songs must NOT be wiped on network failure", 5, database.musicDao().getSongCount())
        val localSongs = musicRepository.allSongs.first()
        assertEquals("allSongs flow must retain 5 songs during outage", 5, localSongs.size)
    }

    // Fake API service implementation for testing
    open class FakeSyncApiService : YimlyApiService {
        var songs: List<Song> = emptyList()
        var albums: List<Album> = emptyList()
        var artists: List<Artist> = emptyList()
        var playlists: List<Playlist> = emptyList()
        var favorites: List<Song> = emptyList()
        var shouldFail: Boolean = false

        override suspend fun login(request: com.example.data.models.LoginRequest): com.example.data.models.LoginResponse {
            throw NotImplementedError()
        }

        override suspend fun getProfile(): com.example.data.models.UserProfile {
            throw NotImplementedError()
        }

        override suspend fun logout(): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun getSongs(query: String?, page: Int?, limit: Int?): List<Song> {
            if (shouldFail) throw IOException("Network unreachable")
            return songs
        }

        override suspend fun getSongById(id: String): Song {
            if (shouldFail) throw IOException("Network unreachable")
            return songs.first { it.id == id }
        }

        override suspend fun getAlbums(): List<Album> {
            if (shouldFail) throw IOException("Network unreachable")
            return albums
        }

        override suspend fun getAlbumSongs(id: String): List<Song> {
            if (shouldFail) throw IOException("Network unreachable")
            return songs
        }

        override suspend fun getArtists(): List<Artist> {
            if (shouldFail) throw IOException("Network unreachable")
            return artists
        }

        override suspend fun getArtistSongs(id: String): List<Song> {
            if (shouldFail) throw IOException("Network unreachable")
            return songs
        }

        override suspend fun getPlaylists(): List<Playlist> {
            if (shouldFail) throw IOException("Network unreachable")
            return playlists
        }

        override suspend fun getPlaylistById(id: String): Playlist {
            if (shouldFail) throw IOException("Network unreachable")
            return playlists.first { it.id == id }
        }

        override suspend fun getPlaylistSongs(id: String): List<Song> {
            if (shouldFail) throw IOException("Network unreachable")
            return playlists.firstOrNull { it.id == id }?.songs ?: emptyList()
        }

        override suspend fun createPlaylist(request: com.example.data.models.CreatePlaylistRequest): Playlist {
            throw NotImplementedError()
        }

        override suspend fun updatePlaylist(id: String, request: com.example.data.models.UpdatePlaylistRequest): Playlist {
            throw NotImplementedError()
        }

        override suspend fun uploadPlaylistCover(id: String, coverBody: okhttp3.RequestBody): Playlist {
            throw NotImplementedError()
        }

        override suspend fun deletePlaylist(id: String): Map<String, Any> {
            throw NotImplementedError()
        }

        override suspend fun addSongToPlaylist(id: String, request: com.example.data.models.AddPlaylistSongRequest): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun sharePlaylist(id: String, request: com.example.data.models.SharePlaylistRequest): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun getCollaborators(id: String): List<com.example.data.models.Collaborator> {
            return emptyList()
        }

        override suspend fun removeCollaborator(playlistId: String, userId: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun revokeShare(playlistId: String, userId: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun getFavorites(): List<Song> {
            if (shouldFail) throw IOException("Network unreachable")
            return favorites
        }

        override suspend fun addFavorite(songId: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun removeFavorite(songId: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun search(query: String): com.example.data.models.SearchResult {
            return com.example.data.models.SearchResult()
        }

        override suspend fun getLyrics(songId: String): okhttp3.ResponseBody {
            throw NotImplementedError()
        }

        override suspend fun createLyrics(id: String, body: okhttp3.RequestBody): okhttp3.ResponseBody {
            throw NotImplementedError()
        }

        override suspend fun updateLyrics(id: String, body: okhttp3.RequestBody): okhttp3.ResponseBody {
            throw NotImplementedError()
        }

        override suspend fun patchLyrics(id: String, body: okhttp3.RequestBody): okhttp3.ResponseBody {
            throw NotImplementedError()
        }

        override suspend fun updateLyricsJson(id: String, request: Map<String, String>): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun deleteLrc(id: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun deleteSong(id: String): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun getLyricsOffset(id: String): com.example.data.models.LyricsOffsetResponse {
            return com.example.data.models.LyricsOffsetResponse(id, 0L)
        }

        override suspend fun updateLyricsOffset(id: String, request: UpdateLyricsOffsetRequest): Map<String, Any> {
            return mapOf("success" to true)
        }

        override suspend fun getSession(code: String): com.example.data.models.YimlySession {
            throw NotImplementedError()
        }

        override suspend fun createSession(): com.example.data.models.YimlySession {
            throw NotImplementedError()
        }

        override suspend fun joinSession(code: String): com.example.data.models.YimlySession {
            throw NotImplementedError()
        }

        override suspend fun requestSongInSession(code: String, song: Song): Map<String, Any> {
            return mapOf("success" to true)
        }
    }
}
