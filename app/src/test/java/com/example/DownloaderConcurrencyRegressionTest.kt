package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.datastore.PreferencesManager
import com.example.data.db.YimlyDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.MusicRepository
import com.example.notifications.DownloadNotificationManager
import com.example.playback.PlaybackManager
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloaderConcurrencyRegressionTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = YimlyDatabase.getDatabase(context)
        val fakeApi = object : YimlyApiService {
            override suspend fun login(request: com.example.data.models.LoginRequest) = throw NotImplementedError()
            override suspend fun getProfile() = throw NotImplementedError()
            override suspend fun logout() = mapOf("success" to true)
            override suspend fun getSongs(query: String?, page: Int?, limit: Int?) = throw NotImplementedError()
            override suspend fun getSongById(id: String) = throw NotImplementedError()
            override suspend fun getAlbums() = emptyList<com.example.data.models.Album>()
            override suspend fun getAlbumSongs(id: String) = emptyList<com.example.data.models.Song>()
            override suspend fun getArtists() = emptyList<com.example.data.models.Artist>()
            override suspend fun getArtistSongs(id: String) = emptyList<com.example.data.models.Song>()
            override suspend fun getPlaylists() = emptyList<com.example.data.models.Playlist>()
            override suspend fun getPlaylistById(id: String) = throw NotImplementedError()
            override suspend fun getPlaylistSongs(id: String) = emptyList<com.example.data.models.Song>()
            override suspend fun createPlaylist(request: com.example.data.models.CreatePlaylistRequest) = throw NotImplementedError()
            override suspend fun updatePlaylist(id: String, request: com.example.data.models.UpdatePlaylistRequest) = throw NotImplementedError()
            override suspend fun uploadPlaylistCover(id: String, coverBody: okhttp3.RequestBody) = throw NotImplementedError()
            override suspend fun deletePlaylist(id: String) = mapOf("success" to true)
            override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = mapOf("success" to true)
            override suspend fun sharePlaylist(id: String, request: com.example.data.models.SharePlaylistRequest) = mapOf("success" to true)
            override suspend fun getCollaborators(id: String) = emptyList<com.example.data.models.Collaborator>()
            override suspend fun removeCollaborator(playlistId: String, userId: String) = mapOf("success" to true)
            override suspend fun revokeShare(playlistId: String, userId: String) = mapOf("success" to true)
            override suspend fun getFavorites() = emptyList<com.example.data.models.Song>()
            override suspend fun addFavorite(songId: String) = mapOf("success" to true)
            override suspend fun removeFavorite(songId: String) = mapOf("success" to true)
            override suspend fun search(query: String) = com.example.data.models.SearchResult()
            override suspend fun getLyrics(songId: String) = throw NotImplementedError()
            override suspend fun createLyrics(id: String, body: okhttp3.RequestBody) = throw NotImplementedError()
            override suspend fun updateLyrics(id: String, body: okhttp3.RequestBody) = throw NotImplementedError()
            override suspend fun patchLyrics(id: String, body: okhttp3.RequestBody) = throw NotImplementedError()
            override suspend fun updateLyricsJson(id: String, request: Map<String, String>) = mapOf("success" to true)
            override suspend fun deleteLrc(id: String) = mapOf("success" to true)
            override suspend fun deleteSong(id: String) = mapOf("success" to true)
            override suspend fun getLyricsOffset(id: String) = throw NotImplementedError()
            override suspend fun updateLyricsOffset(id: String, request: com.example.data.models.UpdateLyricsOffsetRequest) = mapOf("success" to true)
            override suspend fun resolveDownloader(request: com.example.data.models.DownloaderResolveRequest) = throw NotImplementedError()
            override suspend fun startDownload(request: com.example.data.models.DownloaderStartRequest) = throw NotImplementedError()
            override suspend fun getDownloadJob(jobId: String) = throw NotImplementedError()
            override suspend fun retryDownloadJob(jobId: String) = mapOf("success" to true)
            override suspend fun getDownloaderSettings() = throw NotImplementedError()
            override suspend fun getSession(code: String) = throw NotImplementedError()
            override suspend fun createSession() = throw NotImplementedError()
            override suspend fun joinSession(code: String) = throw NotImplementedError()
            override suspend fun requestSongInSession(code: String, song: com.example.data.models.Song) = mapOf("success" to true)
            override suspend fun replaceSongRecordingSource(id: String, request: Map<String, String>) = mapOf("success" to true)
            override suspend fun replaceSongInstrumental(id: String) = mapOf("success" to true)
            override suspend fun getSongReplacementStatus(id: String, sinceEpochMs: Long) = mapOf("status" to "completed")
            override suspend fun getSongReplacementJob(jobId: String) = mapOf("status" to "completed")
            override suspend fun addSongToPlaylist(id: String, request: com.example.data.models.AddPlaylistSongRequest) = mapOf("success" to true)
        }
        val repository = MusicRepository(db.musicDao(), fakeApi, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        viewModel = MainViewModel(
            repository,
            PlaybackManager(context),
            AuthRepository(fakeApi, PreferencesManager(context), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())),
            PreferencesManager(context),
            DownloadNotificationManager(context)
        )
    }

    @Test
    fun concurrentWritersAndReadersKeepPublishedMapConsistent() = runBlocking {
        val count = 200
        coroutineScope {
            val writers = (0 until count).map { index ->
                async(Dispatchers.Default) {
                    viewModel.putCompletedSongId("track_$index", "song_$index")
                }
            }
            val readers = (0 until 8).map {
                async(Dispatchers.Default) {
                    repeat(100) {
                        val snapshot = viewModel.completedTrackSongIds.value
                        snapshot.forEach { (key, value) ->
                            if (key.startsWith("track_")) {
                                assertEquals("song_${key.removePrefix("track_")}", value)
                            }
                        }
                    }
                }
            }
            (writers + readers).awaitAll()
        }

        val finalSnapshot = viewModel.completedTrackSongIds.value
        assertEquals(count, finalSnapshot.size)
        repeat(count) { index ->
            assertEquals("song_$index", finalSnapshot["track_$index"])
        }
    }

    @Test
    fun concurrentRemovalsLeaveBothPublishedAndBackingStateEmpty() = runBlocking {
        val count = 200
        repeat(count) { index -> viewModel.putCompletedSongId("track_$index", "song_$index") }

        coroutineScope {
            (0 until count).map { index ->
                async(Dispatchers.Default) {
                    viewModel.removeCompletedSongId("track_$index")
                }
            }.awaitAll()
        }

        assertEquals(emptyMap<String, String>(), viewModel.completedTrackSongIds.value)
        // Reinsert and clear concurrently with readers; clear must publish an empty snapshot.
        repeat(count) { index -> viewModel.putCompletedSongId("track_$index", "song_$index") }
        viewModel.clearCompletedSongIds()
        assertEquals(emptyMap<String, String>(), viewModel.completedTrackSongIds.value)
    }
}
