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
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
                    updatedAt = "2026-09-01T09:00:00.000Z"
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
