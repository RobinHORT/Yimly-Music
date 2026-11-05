package com.example.data.repository

import com.example.data.api.YimlyApiService
import com.example.data.db.MusicDao
import com.example.data.db.SongEntity
import com.example.data.db.toEntity
import com.example.data.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRepositoryLookupTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class FakeMusicDao : MusicDao {
        val songs = mutableMapOf<String, SongEntity>()
        var searchResults: List<SongEntity> = emptyList()
        
        override fun getAllSongs(): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAllSongsList(): List<SongEntity> = songs.values.toList()
        override fun getRecentlyAddedSongs(): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun getFavoriteSongs(): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getSongById(id: String) = songs[id]
        override suspend fun clearSongLyricsMetadata(songId: String) {}
        override suspend fun updateSongLyricOffset(songId: String, offsetMs: Long) = throw NotImplementedError("updateSongLyricOffset")
        override fun getSongsByAlbum(albumTitle: String): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun getSongsByArtist(artistName: String): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun searchSongs(query: String) = searchResults
        override suspend fun insertSongs(songs: List<SongEntity>) { this.songs.putAll(songs.associateBy { it.id }) }
        override suspend fun insertSong(song: SongEntity) { songs[song.id] = song }
        override suspend fun upsertSongs(songs: List<SongEntity>) { this.songs.putAll(songs.associateBy { it.id }) }
        override suspend fun upsertSong(song: SongEntity) { songs[song.id] = song }
        override suspend fun updateFavorite(songId: String, isFavorite: Boolean) = throw NotImplementedError("updateFavorite")
        override suspend fun clearAllFavorites() = throw NotImplementedError("clearAllFavorites")
        override suspend fun setFavorites(favoriteIds: List<String>) = throw NotImplementedError("setFavorites")
        override suspend fun replaceFavorites(favoriteIds: List<String>) = throw NotImplementedError("replaceFavorites")
        override suspend fun getSongCount() = songs.size
        override suspend fun deleteSongById(songId: String) { songs.remove(songId) }
        override suspend fun deleteStaleSongs(validIds: List<String>) = throw NotImplementedError("deleteStaleSongs")
        override fun getAllAlbums(): Flow<List<com.example.data.db.AlbumEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAlbumById(id: String) = throw NotImplementedError("getAlbumById")
        override suspend fun insertAlbums(albums: List<com.example.data.db.AlbumEntity>) = throw NotImplementedError("insertAlbums")
        override suspend fun deleteStaleAlbums(validIds: List<String>) = throw NotImplementedError("deleteStaleAlbums")
        override fun getAllArtists(): Flow<List<com.example.data.db.ArtistEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getArtistById(id: String) = throw NotImplementedError("getArtistById")
        override suspend fun insertArtists(artists: List<com.example.data.db.ArtistEntity>) = throw NotImplementedError("insertArtists")
        override suspend fun deleteStaleArtists(validIds: List<String>) = throw NotImplementedError("deleteStaleArtists")
        override fun getAllPlaylists(): Flow<List<com.example.data.db.PlaylistEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getPlaylistById(id: String) = throw NotImplementedError("getPlaylistById")
        override suspend fun insertPlaylist(playlist: com.example.data.db.PlaylistEntity) = throw NotImplementedError("insertPlaylist")
        override suspend fun updatePlaylistCoverUrl(playlistId: String, coverUrl: String?) = throw NotImplementedError("updatePlaylistCoverUrl")
        override suspend fun deletePlaylist(id: String) = throw NotImplementedError("deletePlaylist")
        override suspend fun deleteStalePlaylists(validIds: List<String>) = throw NotImplementedError("deleteStalePlaylists")
        override fun getSongsForPlaylist(playlistId: String): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun insertPlaylistSongCrossRef(crossRef: com.example.data.db.PlaylistSongCrossRef) = throw NotImplementedError("insertPlaylistSongCrossRef")
        override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = throw NotImplementedError("removeSongFromPlaylist")
        override suspend fun clearPlaylistSongs(playlistId: String) = throw NotImplementedError("clearPlaylistSongs")
        override suspend fun syncPlaylistSongs(playlistId: String, songs: List<SongEntity>) = throw NotImplementedError("syncPlaylistSongs")
        override suspend fun safeSyncPlaylistSongs(playlistId: String, songs: List<SongEntity>, serverReportedCount: Int?) = throw NotImplementedError("safeSyncPlaylistSongs")
        override suspend fun getSongsForPlaylistList(playlistId: String) = throw NotImplementedError("getSongsForPlaylistList")
        override suspend fun insertHistory(history: com.example.data.db.PlayHistoryEntity) = throw NotImplementedError("insertHistory")
        override fun getRecentlyPlayedSongs(): Flow<List<SongEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun clearHistory() = throw NotImplementedError()
        override suspend fun clearAllPlaylistSongLinks() = throw NotImplementedError()
        override suspend fun clearAllLyricResources() = throw NotImplementedError()
        override suspend fun clearAllPlaylists() = throw NotImplementedError()
        override suspend fun clearAllArtists() = throw NotImplementedError()
        override suspend fun clearAllAlbums() = throw NotImplementedError()
        override suspend fun clearAllSongs() = throw NotImplementedError()
        override suspend fun clearLocalLibraryData() = throw NotImplementedError()
        override suspend fun cleanOrphanPlaylistSongs() = throw NotImplementedError()
        override suspend fun cleanOrphanPlayHistory() = throw NotImplementedError()
        override suspend fun getLyricResource(songId: String, format: String) = throw NotImplementedError()
        override suspend fun getAllLyricResourcesForSong(songId: String) = throw NotImplementedError()
        override suspend fun insertLyricResource(resource: com.example.data.db.LyricResourceEntity) = throw NotImplementedError()
        override suspend fun deleteLyricResource(songId: String, format: String) = throw NotImplementedError()
        override suspend fun deleteAllLyricResourcesForSong(songId: String) = throw NotImplementedError()
    }

    private class FakeApiService : YimlyApiService {
        var remoteSongs = mutableMapOf<String, Song>()
        var apiCalled = false
        var remoteSearchSongs: List<Song> = emptyList()
        var searchCalled = false
        
        override suspend fun getSongById(id: String): Song {
            apiCalled = true
            return remoteSongs[id] ?: throw Exception("Song not found")
        }
        override suspend fun getSongs(query: String?, page: Int?, limit: Int?): ResponseBody = throw NotImplementedError("getSongs")
        override suspend fun login(request: LoginRequest): LoginResponse = throw NotImplementedError("login")
        override suspend fun getProfile(): UserProfile = throw NotImplementedError("getProfile")
        override suspend fun logout(): Map<String, Any> = throw NotImplementedError("logout")
        override suspend fun getAlbumSongs(id: String): List<Song> = throw NotImplementedError("getAlbumSongs")
        override suspend fun getArtistSongs(id: String): List<Song> = throw NotImplementedError("getArtistSongs")
        override suspend fun updateLyricsJson(id: String, request: Map<String, String>): Map<String, Any> = throw NotImplementedError("updateLyricsJson")
        override suspend fun getSession(code: String): YimlySession = throw NotImplementedError("getSession")
        override suspend fun createSession(): YimlySession = throw NotImplementedError("createSession")
        override suspend fun joinSession(code: String): YimlySession = throw NotImplementedError("joinSession")
        override suspend fun requestSongInSession(code: String, song: Song): Map<String, Any> = throw NotImplementedError("requestSongInSession")
        override suspend fun getAlbums(): List<Album> = throw NotImplementedError("getAlbums")
        override suspend fun getArtists(): List<Artist> = throw NotImplementedError("getArtists")
        override suspend fun getPlaylists(): List<Playlist> = throw NotImplementedError("getPlaylists")
        override suspend fun getPlaylistById(playlistId: String): Playlist = throw NotImplementedError("getPlaylistById")
        override suspend fun getPlaylistSongs(playlistId: String): List<Song> = throw NotImplementedError("getPlaylistSongs")
        override suspend fun createPlaylist(request: CreatePlaylistRequest): Playlist = throw NotImplementedError("createPlaylist")
        override suspend fun updatePlaylist(playlistId: String, request: UpdatePlaylistRequest): Playlist = throw NotImplementedError("updatePlaylist")
        override suspend fun uploadPlaylistCover(playlistId: String, body: okhttp3.RequestBody): Playlist = throw NotImplementedError("uploadPlaylistCover")
        override suspend fun deletePlaylist(playlistId: String): Map<String, Any> = throw NotImplementedError("deletePlaylist")
        override suspend fun addSongToPlaylist(playlistId: String, request: AddPlaylistSongRequest): Map<String, Any> = throw NotImplementedError("addSongToPlaylist")
        override suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Map<String, Any> = throw NotImplementedError("removeSongFromPlaylist")
        override suspend fun sharePlaylist(playlistId: String, request: SharePlaylistRequest): Map<String, Any> = throw NotImplementedError("sharePlaylist")
        override suspend fun getCollaborators(playlistId: String): List<Collaborator> = throw NotImplementedError("getCollaborators")
        override suspend fun removeCollaborator(playlistId: String, userId: String): Map<String, Any> = throw NotImplementedError("removeCollaborator")
        override suspend fun revokeShare(playlistId: String, userId: String): Map<String, Any> = throw NotImplementedError("revokeShare")
        override suspend fun getFavorites(): List<Song> = throw NotImplementedError("getFavorites")
        override suspend fun addFavorite(songId: String): Map<String, Any> = throw NotImplementedError("addFavorite")
        override suspend fun removeFavorite(songId: String): Map<String, Any> = throw NotImplementedError("removeFavorite")
        override suspend fun search(query: String): SearchResult {
            searchCalled = true
            return SearchResult(songs = remoteSearchSongs)
        }
        override suspend fun getLyrics(songId: String): ResponseBody = throw NotImplementedError("getLyrics")
        override suspend fun createLyrics(songId: String, body: okhttp3.RequestBody): ResponseBody = throw NotImplementedError("createLyrics")
        override suspend fun updateLyrics(songId: String, body: okhttp3.RequestBody): ResponseBody = throw NotImplementedError("updateLyrics")
        override suspend fun patchLyrics(songId: String, body: okhttp3.RequestBody): ResponseBody = throw NotImplementedError("patchLyrics")
        override suspend fun deleteLrc(songId: String): Map<String, Any> = throw NotImplementedError("deleteLrc")
        override suspend fun deleteSong(songId: String): Map<String, Any> = throw NotImplementedError("deleteSong")
        override suspend fun getLyricsOffset(songId: String): LyricsOffsetResponse = throw NotImplementedError("getLyricsOffset")
        override suspend fun updateLyricsOffset(songId: String, request: UpdateLyricsOffsetRequest): Map<String, Any> = throw NotImplementedError("updateLyricsOffset")
        override suspend fun resolveDownloader(request: DownloaderResolveRequest): DownloaderResolveResponse = throw NotImplementedError("resolveDownloader")
        override suspend fun startDownload(request: DownloaderStartRequest): DownloaderStartResponse = throw NotImplementedError("startDownload")
        override suspend fun getDownloadJob(jobId: String): DownloaderJobResponse = throw NotImplementedError("getDownloadJob")
        override suspend fun retryDownloadJob(jobId: String): Map<String, Any> = throw NotImplementedError("retryDownloadJob")
        override suspend fun getDownloaderSettings(): DownloaderSettingsResponse = throw NotImplementedError("getDownloaderSettings")
        override suspend fun replaceSongRecordingSource(id: String, request: Map<String, String>): Map<String, Any> = throw NotImplementedError()
        override suspend fun replaceSongInstrumental(id: String): Map<String, Any> = throw NotImplementedError()
        override suspend fun getSongReplacementStatus(id: String, sinceEpochMs: Long): Map<String, Any> = throw NotImplementedError()
        override suspend fun getSongReplacementJob(jobId: String): Map<String, Any> = throw NotImplementedError()
    }

    @Test
    fun `getSongById returns local song when ID is untrimmed and does not call API`() = runTest {
        val fakeMusicDao = FakeMusicDao()
        val fakeApiService = FakeApiService()
        
        val songId = "123"
        val localSong = Song(id = songId, title = "Local Song", artist = "Artist", album = "Album", durationSeconds = 1)
        fakeMusicDao.insertSong(localSong.toEntity())
        
        val repository = MusicRepository(fakeMusicDao, fakeApiService, testScope)
        
        // Pass untrimmed ID
        val result = repository.getSongById(" $songId ")
        
        assertEquals(songId, result?.id)
        assertEquals("Local Song", result?.title)
        assertEquals(false, fakeApiService.apiCalled)
    }

    @Test
    fun `getSongById returns local song and does not call API`() = runTest {
        val fakeMusicDao = FakeMusicDao()
        val fakeApiService = FakeApiService()
        
        val songId = "123"
        val localSong = Song(id = songId, title = "Local Song", artist = "Artist", album = "Album", durationSeconds = 1)
        fakeMusicDao.insertSong(localSong.toEntity())
        
        val repository = MusicRepository(fakeMusicDao, fakeApiService, testScope)
        
        val result = repository.getSongById(songId)
        
        assertEquals(songId, result?.id)
        assertEquals("Local Song", result?.title)
        assertEquals(false, fakeApiService.apiCalled)
    }

    @Test
    fun `searchSongs merges Yimly API results with local cache and persists remote songs`() = runTest {
        val fakeMusicDao = FakeMusicDao()
        val fakeApiService = FakeApiService()
        val localSong = Song(id = "local_1", title = "Song Local", artist = "Local Artist")
        val remoteSong = Song(id = "remote_1", title = "Song Remote", artist = "Remote Artist")
        fakeMusicDao.searchResults = listOf(localSong.toEntity())
        fakeApiService.remoteSearchSongs = listOf(remoteSong)

        val repository = MusicRepository(fakeMusicDao, fakeApiService, testScope)
        val results = repository.searchSongs("Song")

        assertEquals(setOf("local_1", "remote_1"), results.map { it.id }.toSet())
        assertEquals(true, fakeApiService.searchCalled)
        assertEquals("remote_1", fakeMusicDao.songs["remote_1"]?.id)
    }

    @Test
    fun `getSongById calls API when local song is missing`() = runTest {
        val fakeMusicDao = FakeMusicDao()
        val fakeApiService = FakeApiService()
        
        val songId = "123"
        val remoteSong = Song(id = songId, title = "Remote Song", artist = "Artist", album = "Album", durationSeconds = 1)
        fakeApiService.remoteSongs[songId] = remoteSong
        
        val repository = MusicRepository(fakeMusicDao, fakeApiService, testScope)
        
        val result = repository.getSongById(songId)
        
        assertEquals(songId, result?.id)
        assertEquals("Remote Song", result?.title)
        assertEquals(true, fakeApiService.apiCalled)
        assertEquals(songId, fakeMusicDao.songs[songId]?.id)
    }
}
