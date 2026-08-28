package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.db.*
import com.example.data.models.*
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlobalDeletionSyncTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var fakeApiService: FakeYimlyApiService
    private lateinit var musicRepository: MusicRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        musicDao = database.musicDao()
        fakeApiService = FakeYimlyApiService()

        musicRepository = MusicRepository(
            musicDao = musicDao,
            apiService = fakeApiService,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDeletePlaylist_DeletesOnServer_AndAuthoritativelyReconcilesLocalRoom() = runBlocking {
        // Given: server and local DB have playlists p1, p2, p3
        val p1 = Playlist(id = "p1", name = "Rock Hits", isPublic = true, songCount = 2, canEdit = true, isOwner = true)
        val p2 = Playlist(id = "p2", name = "Pop Favorites", isPublic = false, songCount = 1, canEdit = true, isOwner = true)
        val p3 = Playlist(id = "p3", name = "Jazz Classics", isPublic = true, songCount = 0, canEdit = true, isOwner = true)

        fakeApiService.serverPlaylists = mutableListOf(p1, p2, p3)
        musicDao.insertPlaylist(p1.toEntity())
        musicDao.insertPlaylist(p2.toEntity())
        musicDao.insertPlaylist(p3.toEntity())
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("p2", "s1", 0))

        // Initial check: local DB has 3 playlists
        val initialList = musicDao.getAllPlaylists().first()
        assertEquals(3, initialList.size)

        // When: delete playlist p2
        val result = musicRepository.deletePlaylist("p2")
        assertTrue("Delete result must be success", result.isSuccess)

        // Then 1: Server delete endpoint was called
        assertTrue("Server delete endpoint must have been invoked for p2", fakeApiService.deletedPlaylistIds.contains("p2"))

        // Then 2: Local DB reconciled immediately - p2 removed
        val reconciledList = musicDao.getAllPlaylists().first()
        assertEquals(2, reconciledList.size)
        assertNull("p2 must be removed from Room", reconciledList.find { it.id == "p2" })
        assertNotNull("p1 must still exist", reconciledList.find { it.id == "p1" })
        assertNotNull("p3 must still exist", reconciledList.find { it.id == "p3" })

        // Then 3: playlist_songs for p2 are removed
        val p2Songs = musicDao.getSongsForPlaylist("p2").first()
        assertTrue("Playlist songs for p2 must be purged", p2Songs.isEmpty())

        // Then 4: A subsequent full sync does NOT bring back p2
        musicRepository.syncWithBackend()
        val afterSyncList = musicDao.getAllPlaylists().first()
        assertEquals(2, afterSyncList.size)
        assertNull("p2 must NOT reappear after subsequent resync", afterSyncList.find { it.id == "p2" })
    }

    @Test
    fun testDeleteSong_DeletesOnServer_AndAuthoritativelyReconcilesLocalRoom() = runBlocking {
        // Given: server and local DB have songs s1, s2, s3
        val s1 = Song(id = "s1", title = "Song One", artist = "Artist A", album = "Album A", durationSeconds = 200)
        val s2 = Song(id = "s2", title = "Song Two", artist = "Artist B", album = "Album B", durationSeconds = 180)
        val s3 = Song(id = "s3", title = "Song Three", artist = "Artist A", album = "Album A", durationSeconds = 220)

        fakeApiService.serverSongs = mutableListOf(s1, s2, s3)
        musicDao.insertSongs(listOf(s1.toEntity(), s2.toEntity(), s3.toEntity()))
        musicDao.updateFavorite("s2", true)
        musicDao.insertHistory(PlayHistoryEntity(songId = "s2", playedAt = System.currentTimeMillis()))
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("p1", "s2", 0))

        var evictedSongId: String? = null
        musicRepository.onSongDeletedListener = { deletedId ->
            evictedSongId = deletedId
        }

        // When: delete song s2
        val result = musicRepository.deleteSong("s2")
        assertTrue("Delete song must succeed", result.isSuccess)

        // Then 1: Server delete endpoint was called
        assertTrue("Server delete endpoint must have been invoked for s2", fakeApiService.deletedSongIds.contains("s2"))

        // Then 2: onSongDeletedListener notified for playback eviction
        assertEquals("s2", evictedSongId)

        // Then 3: Local DB reconciled - s2 removed
        val songsAfterDelete = musicDao.getAllSongs().first()
        assertEquals(2, songsAfterDelete.size)
        assertNull("s2 must not exist in Room songs", songsAfterDelete.find { it.id == "s2" })

        // Then 4: Relational cascades cleared s2 from playlist songs
        val p1Songs = musicDao.getSongsForPlaylist("p1").first()
        assertTrue("s2 must be removed from playlist songs", p1Songs.none { it.id == "s2" })

        // Then 5: Subsequent resync does NOT resurrect s2
        musicRepository.syncWithBackend()
        val songsAfterResync = musicDao.getAllSongs().first()
        assertEquals(2, songsAfterResync.size)
        assertNull("s2 must NOT reappear after resync", songsAfterResync.find { it.id == "s2" })
    }

    @Test
    fun testDeletePlaylistCover_DeletesOnServer_AndAuthoritativelyReconcilesLocalRoom() = runBlocking {
        // Given: playlist p1 with custom cover
        val p1 = Playlist(id = "p1", name = "Chill Vibes", rawCoverUrl = "https://yimly.test/covers/p1.jpg", songCount = 0, canEdit = true, isOwner = true)
        fakeApiService.serverPlaylists = mutableListOf(p1)
        musicDao.insertPlaylist(p1.toEntity())

        val initial = musicDao.getPlaylistById("p1")
        assertNotNull(initial?.coverUrl)

        // When: delete playlist cover
        val result = musicRepository.deletePlaylistCover("p1")
        assertTrue("Delete playlist cover must succeed", result.isSuccess)

        // Then 1: Server delete cover called
        assertTrue("Server deletePlaylistCover must have been called", fakeApiService.deletedCoverPlaylistIds.contains("p1"))

        // Then 2: Local DB coverUrl reconciled to null
        val afterDelete = musicDao.getPlaylistById("p1")
        assertNull("Cover URL must be null in Room after delete", afterDelete?.coverUrl)

        // Then 3: Subsequent resync does not bring back old cover
        musicRepository.syncWithBackend()
        val afterResync = musicDao.getPlaylistById("p1")
        assertNull("Cover URL must remain null after resync", afterResync?.coverUrl)
    }

    @Test
    fun testDeleteLrc_DeletesOnServer_AndAuthoritativelyReconcilesLocalRoom() = runBlocking {
        // Given: song s1 with lyrics
        val s1 = Song(id = "s1", title = "Lrc Song", artist = "Artist", album = "Album", durationSeconds = 150, hasLrc = true, lyricsText = "[00:10.00] Line 1")
        fakeApiService.serverSongs = mutableListOf(s1)
        musicDao.insertSong(s1.toEntity())

        val initialSong = musicDao.getSongById("s1")
        assertTrue(initialSong?.hasLrc == true)
        assertNotNull(initialSong?.lyricsText)

        // When: delete lyrics
        val result = musicRepository.deleteLrc("s1")
        assertTrue("Delete LRC must succeed", result.isSuccess)

        // Then 1: Server deleteLrc was called
        assertTrue("Server deleteLrc must have been called", fakeApiService.deletedLrcSongIds.contains("s1"))

        // Then 2: Local DB reconciled - hasLrc is false, lyricsText is null
        val afterDelete = musicDao.getSongById("s1")
        assertFalse("hasLrc must be false", afterDelete?.hasLrc == true)
        assertNull("lyricsText must be null", afterDelete?.lyricsText)
    }

    @Test
    fun testRemoveSongFromPlaylist_DeletesOnServer_AndReconcilesPlaylistSongs() = runBlocking {
        val s1 = Song(id = "s1", title = "Song 1", artist = "Artist", album = "Album", durationSeconds = 100)
        val s2 = Song(id = "s2", title = "Song 2", artist = "Artist", album = "Album", durationSeconds = 120)
        fakeApiService.serverSongs = mutableListOf(s1, s2)
        musicDao.insertSongs(listOf(s1.toEntity(), s2.toEntity()))

        val p1 = Playlist(id = "p1", name = "Test Playlist", songCount = 2, songs = listOf(s1, s2), canEdit = true, isOwner = true)
        fakeApiService.serverPlaylists = mutableListOf(p1)
        musicDao.insertPlaylist(p1.toEntity())
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("p1", "s1", 0))
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("p1", "s2", 1))

        // When: remove s2 from p1
        val result = musicRepository.removeSongFromPlaylist("p1", "s2")
        assertTrue("Remove song from playlist must succeed", result.isSuccess)

        // Then: Local DB reconciled - only s1 remains in p1
        val p1Songs = musicDao.getSongsForPlaylist("p1").first()
        assertEquals(1, p1Songs.size)
        assertEquals("s1", p1Songs[0].id)
    }
}

/**
 * Fake implementation of YimlyApiService simulating backend server behavior
 */
class FakeYimlyApiService : YimlyApiService {
    var serverSongs = mutableListOf<Song>()
    var serverPlaylists = mutableListOf<Playlist>()
    val deletedPlaylistIds = mutableListOf<String>()
    val deletedSongIds = mutableListOf<String>()
    val deletedCoverPlaylistIds = mutableListOf<String>()
    val deletedLrcSongIds = mutableListOf<String>()

    override suspend fun getSongs(query: String?): List<Song> = serverSongs

    override suspend fun getSongById(id: String): Song {
        return serverSongs.find { it.id == id } ?: throw RuntimeException("Not found")
    }

    override suspend fun deleteSong(id: String): Map<String, Any> {
        deletedSongIds.add(id)
        serverSongs.removeAll { it.id == id }
        return mapOf("success" to true)
    }

    override suspend fun getPlaylists(): List<Playlist> = serverPlaylists

    override suspend fun getPlaylistById(id: String): Playlist {
        return serverPlaylists.find { it.id == id } ?: throw RuntimeException("Not found")
    }

    override suspend fun getPlaylistSongs(id: String): List<Song> {
        val p = serverPlaylists.find { it.id == id }
        return p?.songs ?: emptyList()
    }

    override suspend fun deletePlaylist(id: String): Map<String, Any> {
        deletedPlaylistIds.add(id)
        serverPlaylists.removeAll { it.id == id }
        return mapOf("success" to true)
    }

    override suspend fun deletePlaylistCover(id: String): Playlist {
        deletedCoverPlaylistIds.add(id)
        val idx = serverPlaylists.indexOfFirst { it.id == id }
        val updated = if (idx != -1) {
            val p = serverPlaylists[idx]
            val clean = p.copy(rawCoverUrl = null, rawCoverImageUrl = null, rawCoverImage = null)
            serverPlaylists[idx] = clean
            clean
        } else {
            Playlist(id = id, name = "Unknown")
        }
        return updated
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Map<String, Any> {
        val idx = serverPlaylists.indexOfFirst { it.id == playlistId }
        if (idx != -1) {
            val p = serverPlaylists[idx]
            val updatedSongs = p.songs.filter { it.id != songId }
            serverPlaylists[idx] = p.copy(songs = updatedSongs, songCount = updatedSongs.size)
        }
        return mapOf("success" to true)
    }

    override suspend fun deleteLrc(id: String): Map<String, Any> {
        deletedLrcSongIds.add(id)
        val idx = serverSongs.indexOfFirst { it.id == id }
        if (idx != -1) {
            val s = serverSongs[idx]
            serverSongs[idx] = s.copy(hasLrc = false, lyricsText = null)
        }
        return mapOf("success" to true)
    }

    // Boilerplate overrides for YimlyApiService interface
    override suspend fun login(request: LoginRequest): LoginResponse = throw NotImplementedError()
    override suspend fun getProfile(): UserProfile = throw NotImplementedError()
    override suspend fun logout(): Map<String, Any> = mapOf("success" to true)
    override suspend fun getAlbums(): List<Album> = emptyList()
    override suspend fun getAlbumSongs(id: String): List<Song> = emptyList()
    override suspend fun getArtists(): List<Artist> = emptyList()
    override suspend fun getArtistSongs(id: String): List<Song> = emptyList()
    override suspend fun createPlaylist(request: CreatePlaylistRequest): Playlist = throw NotImplementedError()
    override suspend fun updatePlaylist(id: String, request: UpdatePlaylistRequest): Playlist = throw NotImplementedError()
    override suspend fun addSongToPlaylist(id: String, request: AddPlaylistSongRequest): Map<String, Any> = mapOf("success" to true)
    override suspend fun uploadPlaylistCover(id: String, cover: RequestBody): Playlist = throw NotImplementedError()
    override suspend fun uploadPlaylistArtwork(id: String, artwork: MultipartBody.Part): Playlist = throw NotImplementedError()
    override suspend fun sharePlaylist(id: String, request: SharePlaylistRequest): Map<String, Any> = throw NotImplementedError()
    override suspend fun getCollaborators(id: String): List<Collaborator> = emptyList()
    override suspend fun removeCollaborator(playlistId: String, userId: String): Map<String, Any> = mapOf("success" to true)
    override suspend fun revokeShare(playlistId: String, userId: String): Map<String, Any> = mapOf("success" to true)
    override suspend fun getFavorites(): List<Song> = emptyList()
    override suspend fun addFavorite(songId: String): Map<String, Any> = mapOf("success" to true)
    override suspend fun removeFavorite(songId: String): Map<String, Any> = mapOf("success" to true)
    override suspend fun search(query: String): SearchResult = throw NotImplementedError()
    override suspend fun getLyrics(songId: String): ResponseBody = throw NotImplementedError()
    override suspend fun updateLyrics(id: String, body: RequestBody): ResponseBody = throw NotImplementedError()
    override suspend fun updateLyricsJson(id: String, request: Map<String, String>): Map<String, Any> = mapOf("success" to true)
    override suspend fun getLyricsOffset(id: String): LyricsOffsetResponse = throw NotImplementedError()
    override suspend fun updateLyricsOffset(id: String, request: UpdateLyricsOffsetRequest): Map<String, Any> = mapOf("success" to true)
    override suspend fun getSession(code: String): YimlySession = throw NotImplementedError()
    override suspend fun createSession(): YimlySession = throw NotImplementedError()
    override suspend fun joinSession(code: String): YimlySession = throw NotImplementedError()
    override suspend fun requestSongInSession(code: String, song: Song): Map<String, Any> = mapOf("success" to true)
}
