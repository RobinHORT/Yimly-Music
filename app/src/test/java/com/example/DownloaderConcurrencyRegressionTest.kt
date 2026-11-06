package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AuthRepository
import com.example.data.repository.MusicRepository
import com.example.notifications.DownloadNotificationManager
import com.example.playback.PlaybackManager
import com.example.ui.MainViewModel
import com.example.data.datastore.PreferencesManager
import com.example.data.db.YimlyDatabase
import com.example.data.api.YimlyApiService
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

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

        val musicRepository = MusicRepository(
            db.musicDao(),
            fakeApi,
            CoroutineScope(SupervisorJob())
        )
        val playbackManager = PlaybackManager(context)
        val authRepository = AuthRepository(fakeApi, PreferencesManager(context), CoroutineScope(SupervisorJob()))
        val preferencesManager = PreferencesManager(context)
        val downloadNotificationManager = DownloadNotificationManager(context)

        viewModel = MainViewModel(
            musicRepository = musicRepository,
            playbackManager = playbackManager,
            authRepository = authRepository,
            preferencesManager = preferencesManager,
            downloadNotificationManager = downloadNotificationManager
        )
    }

    @Test
    fun testConcurrentPutCompletedSongIdConsistency() = runBlocking {
        val count = 100
        val scope = CoroutineScope(Dispatchers.Default + Job())
        
        val jobs = (1..count).map { i ->
            scope.launch {
                // Accessing private method via reflection to test internal state consistency
                val method = viewModel.javaClass.getDeclaredMethod("putCompletedSongId", String::class.java, String::class.java)
                method.isAccessible = true
                method.invoke(viewModel, "key_$i", "song_$i")
            }
        }
        
        jobs.joinAll()
        
        assertEquals(count, viewModel.completedTrackSongIds.value.size)
        for (i in 1..count) {
            assertEquals("song_$i", viewModel.completedTrackSongIds.value["key_$i"])
        }
    }

    @Test
    fun testConcurrentUpdateAndReadConsistency() = runBlocking {
        val count = 50
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val readCount = AtomicInteger(0)
        
        val jobs = mutableListOf<Job>()
        
        // Concurrently put and remove
        for (i in 1..count) {
            jobs += scope.launch {
                val putMethod = viewModel.javaClass.getDeclaredMethod("putCompletedSongId", String::class.java, String::class.java)
                putMethod.isAccessible = true
                putMethod.invoke(viewModel, "key_$i", "song_$i")
                
                // Concurrent read
                assertNotNull(viewModel.completedTrackSongIds.value)
                readCount.incrementAndGet()
            }
        }
        
        jobs.joinAll()
        
        assertEquals(count, viewModel.completedTrackSongIds.value.size)
    }
}
