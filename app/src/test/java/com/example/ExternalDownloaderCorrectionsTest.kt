package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.datastore.PreferencesManager
import com.example.data.db.YimlyDatabase
import com.example.data.models.DownloaderJobResponse
import com.example.data.models.DownloaderJobTrack
import com.example.data.models.DownloaderResolveRequest
import com.example.data.models.DownloaderResolveResponse
import com.example.data.models.DownloaderSettingsResponse
import com.example.data.models.DownloaderStartRequest
import com.example.data.models.DownloaderStartResponse
import com.example.data.models.ExternalTrack
import com.example.data.models.Song
import com.example.data.models.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.MusicRepository
import com.example.playback.PlaybackManager
import com.example.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
import org.robolectric.shadows.ShadowLooper
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalDownloaderCorrectionsTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var fakeApi: FakeDownloaderTestApiService
    private lateinit var repository: MusicRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var playbackManager: PlaybackManager
    private lateinit var viewModel: MainViewModel

    class FakeDownloaderTestApiService : SyncIdempotenceTest.FakeSyncApiService() {
        var settingsLibraryId: String? = "configured_library_42"
        var throwSettingsHttpCode: Int? = null
        var throwStartDownloadHttpCode: Int? = null

        var startDownloadCallCount = 0
        var capturedLibraryId: String? = null
        var capturedTracks: List<ExternalTrack> = emptyList()

        var jobResponse: DownloaderJobResponse = DownloaderJobResponse(
            id = "job_123",
            status = "completed",
            tracks = emptyList()
        )
        // ... (existing code)

        var retryDownloadCallCount = 0

        override suspend fun getProfile(): UserProfile {
            return UserProfile(
                id = "admin_user",
                username = "admin",
                email = "admin@example.com",
                role = "administrator",
                rawIsAdmin = true
            )
        }

        override suspend fun getDownloaderSettings(): DownloaderSettingsResponse {
            val code = throwSettingsHttpCode
            if (code != null) {
                val errorBody = "{\"error\": \"Unauthorized\"}".toResponseBody("application/json".toMediaType())
                throw HttpException(Response.error<Any>(code, errorBody))
            }
            return DownloaderSettingsResponse(libraryId = settingsLibraryId, libraryName = "Main Music Library")
        }

        override suspend fun startDownload(request: DownloaderStartRequest): DownloaderStartResponse {
            val code = throwStartDownloadHttpCode
            if (code != null) {
                val errorBody = "{\"error\": \"Forbidden\"}".toResponseBody("application/json".toMediaType())
                throw HttpException(Response.error<Any>(code, errorBody))
            }
            startDownloadCallCount++
            capturedLibraryId = request.libraryId
            capturedTracks = request.tracks
            return DownloaderStartResponse(
                jobId = "job_123",
                message = "Started",
                totalTracks = request.tracks.size,
                libraryName = "Main Music Library",
                libraryPath = "/music/lib"
            )
        }

        override suspend fun getDownloadJob(jobId: String): DownloaderJobResponse {
            return jobResponse
        }

        override suspend fun retryDownloadJob(jobId: String): Map<String, Any> {
            retryDownloadCallCount++
            return mapOf("success" to true)
        }
    }

    private fun pumpLooper(ms: Long = 100L) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = PreferencesManager(context)
        fakeApi = FakeDownloaderTestApiService()
        repository = MusicRepository(
            musicDao = database.musicDao(),
            apiService = fakeApi,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
        authRepository = AuthRepository(
            apiService = fakeApi,
            preferencesManager = preferencesManager,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
        playbackManager = PlaybackManager(
            context = context,
            musicRepository = repository,
            preferencesManager = preferencesManager,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        )
        viewModel = MainViewModel(
            musicRepository = repository,
            playbackManager = playbackManager,
            authRepository = authRepository,
            preferencesManager = preferencesManager
        )
        viewModel.downloaderPollDelayMs = 10L
        viewModel.downloaderIndexingDelayMs = 10L
        runBlocking {
            preferencesManager.saveAuthSession(
                token = "test_token_123",
                userId = "admin_user",
                username = "admin",
                email = "admin@example.com",
                displayName = "Admin",
                avatarUrl = null,
                role = "administrator",
                isAdmin = true,
                rememberMe = true
            )
        }
        pumpLooper(50L)
    }

    @Test
    fun testCompletedJobWithEmptyTracksListFinishesImmediately() = runBlocking {
        val track = ExternalTrack(title = "Nightcall", artist = "Kavinsky", album = "Drive", durationSeconds = 259L)
        fakeApi.jobResponse = DownloaderJobResponse(
            id = "job_123",
            status = "completed",
            tracks = emptyList() // Empty tracks list on completion
        )
        // Make the song appear in sync
        fakeApi.songs = listOf(
            Song(id = "server_song_999", title = "Nightcall", artist = "Kavinsky", album = "Drive", durationSeconds = 259L)
        )

        var completedSongId: String? = null
        viewModel.downloadExternalTrack(track) { songId ->
            completedSongId = songId
        }

        // Wait for coroutine indexing loop
        var attempts = 0
        while (completedSongId == null && attempts < 50) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        assertEquals("server_song_999", completedSongId)
        val status = viewModel.downloadStatusMap.value["nightcall|kavinsky"]
        assertEquals("Completed", status)
        assertEquals(1, fakeApi.startDownloadCallCount)
        assertEquals("configured_library_42", fakeApi.capturedLibraryId)
    }

    @Test
    fun testFailedJobHandlesRetryAndExplicitTerminalState() = runBlocking {
        val track = ExternalTrack(title = "Failed Track", artist = "Bad Artist")
        fakeApi.jobResponse = DownloaderJobResponse(
            id = "job_failed",
            status = "failed",
            tracks = listOf(DownloaderJobTrack(title = "Failed Track", artist = "Bad Artist", status = "failed", error = "Extractor crashed"))
        )

        var callbackCalled = false
        var resultId: String? = "sentinel"
        viewModel.downloadExternalTrack(track) { id ->
            callbackCalled = true
            resultId = id
        }

        var attempts = 0
        while (!callbackCalled && attempts < 50) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        assertTrue(callbackCalled)
        assertNull(resultId)
        val status = viewModel.downloadStatusMap.value["failed track|bad artist"]
        assertTrue("Status should indicate failure: $status", status?.startsWith("Failed") == true)
        assertTrue(fakeApi.retryDownloadCallCount >= 1)
    }

    @Test
    fun testCancelledJobTerminatesWithoutPollingTimeout() = runBlocking {
        val track = ExternalTrack(title = "Cancelled Track", artist = "Artist C")
        fakeApi.jobResponse = DownloaderJobResponse(
            id = "job_cancel",
            status = "cancelled",
            tracks = emptyList()
        )

        var callbackCalled = false
        var resultId: String? = "sentinel"
        viewModel.downloadExternalTrack(track) { id ->
            callbackCalled = true
            resultId = id
        }

        var attempts = 0
        while (!callbackCalled && attempts < 500) {
            pumpLooper(200L)
            kotlinx.coroutines.delay(20L)
            attempts++
        }

        assertTrue(callbackCalled)
        assertNull(resultId)
        val status = viewModel.downloadStatusMap.value["cancelled track|artist c"]
        assertTrue("Status was $status, expected Cancelled", status?.contains("Cancelled", ignoreCase = true) == true)
    }

    @Test
    fun testMissingDownloaderSettingsDoesNotFallbackToHardcodedLibrary() = runBlocking {
        val track = ExternalTrack(title = "No Lib Track", artist = "Artist N")
        fakeApi.settingsLibraryId = null // Server returns null/empty library ID

        var callbackCalled = false
        var resultId: String? = "not_null"
        viewModel.downloadExternalTrack(track) { id ->
            callbackCalled = true
            resultId = id
        }

        var attempts = 0
        while (!callbackCalled && attempts < 50) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        assertTrue(callbackCalled)
        assertNull(resultId)
        // Verify startDownload was never called with "1" or any fallback
        assertEquals(0, fakeApi.startDownloadCallCount)
        assertNull(fakeApi.capturedLibraryId)
        val status = viewModel.downloadStatusMap.value["no lib track|artist n"]
        assertEquals("Failed: No library configured", status)
    }

    @Test
    fun testInaccessibleDownloaderSettingsForbiddenRejection() = runBlocking {
        val track = ExternalTrack(title = "Forbidden Track", artist = "Artist F")
        fakeApi.throwSettingsHttpCode = 403 // Non-admin access denied

        var callbackCalled = false
        var resultId: String? = "not_null"
        viewModel.downloadExternalTrack(track) { id ->
            callbackCalled = true
            resultId = id
        }

        var attempts = 0
        while (!callbackCalled && attempts < 50) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        assertTrue(callbackCalled)
        assertNull(resultId)
        assertEquals(0, fakeApi.startDownloadCallCount)
        val status = viewModel.downloadStatusMap.value["forbidden track|artist f"]
        assertEquals("Failed: Access denied", status)
        assertTrue(viewModel.uiMessage.value?.contains("Administrator privileges required") == true)
    }

    @Test
    fun testDuplicateDownloadPreventionAndPlusButtonRetainsSongId() = runBlocking {
        // 1. Start with a missing song
        val track = ExternalTrack(title = "Missing Song", artist = "New Artist")
        fakeApi.jobResponse = DownloaderJobResponse(
            id = "job_new",
            status = "completed",
            tracks = listOf(DownloaderJobTrack(title = "Missing Song", artist = "New Artist", status = "completed", progress = 100))
        )
        fakeApi.songs = listOf(
            Song(id = "server_new_123", title = "Missing Song", artist = "New Artist", durationSeconds = 180L)
        )

        var downloadResultId: String? = null
        viewModel.downloadExternalTrack(track) { id ->
            downloadResultId = id
        }

        var attempts = 0
        while (downloadResultId == null && attempts < 100) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        // Verify successful download
        assertEquals("server_new_123", downloadResultId)
        assertEquals(1, fakeApi.startDownloadCallCount)

        // 2. Press + again on already-downloaded track
        var plusResultId: String? = null
        viewModel.downloadExternalTrack(track) { id ->
            plusResultId = id
        }

        // Returns immediately with the cached real Song ID!
        assertEquals("server_new_123", plusResultId)
        // startDownload MUST NOT be called again
        assertEquals(1, fakeApi.startDownloadCallCount)
    }

    @Test
    fun testIndexingTimeoutReportsPendingAndAllowsSafeRetry() = runBlocking {
        val track = ExternalTrack(title = "Slow Index Track", artist = "Slow Artist")
        fakeApi.jobResponse = DownloaderJobResponse(
            id = "job_slow",
            status = "completed",
            tracks = listOf(DownloaderJobTrack(title = "Slow Index Track", artist = "Slow Artist", status = "completed", progress = 100))
        )
        // Server downloaded track, but indexing has NOT finished yet (songs list empty)
        fakeApi.songs = emptyList()

        var callbackCalled = false
        var resultId: String? = "sentinel"
        viewModel.downloadExternalTrack(track) { id ->
            callbackCalled = true
            resultId = id
        }

        var attempts = 0
        while (!callbackCalled && attempts < 100) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        // Indexing timed out -> callback receives null
        assertTrue(callbackCalled)
        assertNull(resultId)
        val status = viewModel.downloadStatusMap.value["slow index track|slow artist"]
        assertEquals("Downloaded (Indexing pending)", status)
        assertEquals(1, fakeApi.startDownloadCallCount)

        // Now backend indexing finishes!
        fakeApi.songs = listOf(
            Song(id = "server_slow_888", title = "Slow Index Track", artist = "Slow Artist", durationSeconds = 200L)
        )

        // Safe retry without downloading again
        var retryResultId: String? = null
        viewModel.retryLibraryIndexing(track) { id ->
            retryResultId = id
        }

        attempts = 0
        while (retryResultId == null && attempts < 100) {
            pumpLooper(100L)
            kotlinx.coroutines.delay(10L)
            attempts++
        }

        assertEquals("server_slow_888", retryResultId)
        // startDownload is STILL 1 (did not re-download)
        assertEquals(1, fakeApi.startDownloadCallCount)
        assertEquals("Completed", viewModel.downloadStatusMap.value["slow index track|slow artist"])
    }

    @Test
    fun testExternalTrackNeverEntersPlaybackOrQueue() {
        val externalTrack = ExternalTrack(title = "External Only", artist = "External Artist")

        // Playback queue and current song must be empty
        assertEquals(emptyList<Song>(), playbackManager.playbackInfo.value.queue)
        assertNull(playbackManager.playbackInfo.value.currentSong)

        // Download external track does not mutate queue or playback info
        viewModel.downloadExternalTrack(externalTrack)

        assertEquals(emptyList<Song>(), playbackManager.playbackInfo.value.queue)
        assertNull(playbackManager.playbackInfo.value.currentSong)
    }
}
