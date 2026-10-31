package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Playlist
import com.example.data.repository.MusicRepository
import com.example.ui.components.resolveCoverUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import java.io.IOException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArtworkUploadTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var musicRepository: MusicRepository
    private var uploadedPlaylistId: String? = null
    private var uploadedBodySize: Long = 0
    private var failPlaylistUpdate = false
    private var failPlaylistRefresh = false
    private var playlistUpdateResponse = Playlist(id = "72", name = "Test Playlist", createdAt = "server-created-at")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val mockApi = object : SyncIdempotenceTest.FakeSyncApiService() {
            override suspend fun uploadPlaylistCover(id: String, coverBody: RequestBody): Playlist {
                uploadedPlaylistId = id
                uploadedBodySize = coverBody.contentLength()
                return Playlist(
                    id = id,
                    name = "Test Playlist",
                    rawCoverPath = "playlist_${id}_cover.jpg",
                    rawCoverImageUrl = "/api/playlists/$id/cover",
                    updatedAt = "2026-09-01T09:00:00.000Z",
                    createdAt = "server-created-at"
                )
            }

            override suspend fun getPlaylistById(id: String): Playlist {
                if (failPlaylistRefresh) throw IOException("Playlist refresh failed")
                return playlistUpdateResponse
            }

            override suspend fun updatePlaylist(
                id: String,
                request: com.example.data.models.UpdatePlaylistRequest
            ): Playlist {
                if (failPlaylistUpdate) throw IOException("Server rejected playlist update")
                return playlistUpdateResponse.copy(
                    id = id,
                    name = request.name,
                    description = request.description,
                    isPublic = request.isPublic ?: playlistUpdateResponse.isPublic,
                    rawCoverImageUrl = request.coverUrl
                )
            }
        }

        musicRepository = MusicRepository(
            musicDao = database.musicDao(),
            apiService = mockApi,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testArtworkUploadSendsRequestBodyAndUpdatesLocalDatabase() = runBlocking {
        val initialPlaylist = Playlist(id = "72", name = "Test Playlist")
        database.musicDao().insertPlaylist(initialPlaylist.toEntity())

        val tempFile = File(context.cacheDir, "test_upload_cover.jpg")
        val sampleBytes = ByteArray(1024) { 0x42 }
        tempFile.writeBytes(sampleBytes)

        musicRepository.updatePlaylistArtwork("72", "file://${tempFile.absolutePath}")

        assertEquals("72", uploadedPlaylistId)
        assertEquals(1024L, uploadedBodySize)

        val updated = database.musicDao().getPlaylistById("72")
        assertNotNull(updated)
        assertNotNull(updated?.coverUrl)
        assertTrue(updated!!.coverUrl!!.contains("/api/playlists/72/cover"))
    }

    @Test
    fun testConnectivityFlowReportsOfflineWithoutAndroidContext() = runBlocking {
        val repositoryWithoutContext = MusicRepository(
            apiService = mockApi,
            musicDao = database.musicDao(),
            preferencesManager = preferencesManager,
            context = null
        )

        assertFalse(repositoryWithoutContext.networkConnectivityFlow().first())
    }

    @Test
    fun testPlaylistRefreshFailureReturnsResultAndPreservesCachedPlaylist() = runBlocking {
        database.musicDao().insertPlaylist(
            Playlist(id = "72", name = "Cached Playlist", createdAt = "2024-01-01").toEntity()
        )
        failPlaylistRefresh = true

        val result = musicRepository.refreshPlaylistDetail("72", force = true)

        assertTrue(result.isFailure)
        assertEquals("Cached Playlist", database.musicDao().getPlaylistById("72")?.name)
    }

    @Test
    fun testServerLyricsOffsetUpdateAlsoUpdatesLocalSongRecord() = runBlocking {
        database.musicDao().upsertSong(
            Song(id = "offset_song", title = "Offset Song", artist = "Artist").toEntity()
        )

        val result = musicRepository.updateLyricsOffset("offset_song", 1425L)

        assertTrue(result.isSuccess)
        assertEquals(1425L, database.musicDao().getSongById("offset_song")?.lyricOffset)
    }

    @Test
    fun testPlaylistUpdatePreservesOriginalCreatedAt() = runBlocking {
        val originalCreatedAt = "2024-05-01T12:00:00Z"
        database.musicDao().insertPlaylist(
            Playlist(id = "72", name = "Old Name", createdAt = originalCreatedAt).toEntity()
        )
        playlistUpdateResponse = Playlist(
            id = "72",
            name = "New Name",
            createdAt = "server-reset-created-at"
        )

        val updated = musicRepository.updatePlaylist("72", "New Name", "Description")

        assertEquals(originalCreatedAt, updated.createdAt)
        assertEquals(originalCreatedAt, database.musicDao().getPlaylistById("72")?.createdAt)
    }

    @Test
    fun testFailedServerArtworkClearKeepsLocalCover() = runBlocking {
        val originalCover = "https://cdn.example/old-cover.jpg"
        database.musicDao().insertPlaylist(
            Playlist(id = "72", name = "Test Playlist", rawCoverImageUrl = originalCover).toEntity()
        )
        failPlaylistUpdate = true

        var failed = false
        try {
            musicRepository.updatePlaylistArtwork("72", null)
        } catch (_: IOException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(originalCover, database.musicDao().getPlaylistById("72")?.coverUrl)
    }

    @Test
    fun testPlaylistCoverUrlResolutionWithCacheBuster() {
        val playlist = Playlist(
            id = "72",
            name = "Test Playlist",
            rawCoverImageUrl = "/api/playlists/72/cover",
            updatedAt = "2026-09-01T09:00:00.000Z"
        )
        val coverUrl = playlist.coverUrl
        assertNotNull(coverUrl)
        assertTrue(coverUrl!!.contains("api/playlists/72/cover"))
        assertTrue(coverUrl.contains("?t="))

        val resolved = coverUrl.resolveCoverUrl()
        assertNotNull(resolved)
        assertTrue(resolved!!.startsWith("https://") || resolved.startsWith("http://"))
    }
}
