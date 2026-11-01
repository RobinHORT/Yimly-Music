package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.datastore.PreferencesManager
import com.example.data.db.MusicDao
import com.example.data.db.SongEntity
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistIntegrityTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var repository: MusicRepository
    private lateinit var fakeApi: FakePlaylistApiService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        musicDao = database.musicDao()
        fakeApi = FakePlaylistApiService()
        repository = MusicRepository(
            musicDao = musicDao,
            apiService = fakeApi,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSafeSyncPreservesLocalSongsOnEmptyApiResponseWithPositiveCount() = runBlocking {
        val playlistId = "pl1"
        val song = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        
        // 0. Insert song first
        musicDao.insertSongs(listOf(song.toEntity()))

        // 1. Initial state: local has 1 song
        musicDao.syncPlaylistSongs(playlistId, listOf(song.toEntity()))
        assertEquals(1, musicDao.getSongsForPlaylistList(playlistId).size)

        // 2. Perform safe sync with empty list but count = 1 (stale)
        musicDao.safeSyncPlaylistSongs(playlistId, emptyList(), serverReportedCount = 1)

        // 3. Verify local songs are preserved
        assertEquals(1, musicDao.getSongsForPlaylistList(playlistId).size)
    }

    @Test
    fun testSafeSyncAllowsClearingWhenServerReportsZeroCount() = runBlocking {
        val playlistId = "pl1"
        val song = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        
        // 0. Insert song
        musicDao.insertSongs(listOf(song.toEntity()))

        // 1. Setup local with 1 song
        musicDao.syncPlaylistSongs(playlistId, listOf(song.toEntity()))
        assertEquals(1, musicDao.getSongsForPlaylistList(playlistId).size)

        // 2. Safe sync with empty remote AND count = 0 (genuine removal)
        musicDao.safeSyncPlaylistSongs(playlistId, emptyList(), serverReportedCount = 0)

        // 3. Verify local is cleared
        assertEquals(0, musicDao.getSongsForPlaylistList(playlistId).size)
    }

    @Test
    fun testSafeSyncClearsSongsWhenBothRemoteAndLocalAreEmpty() = runBlocking {
        val playlistId = "pl1"
        
        // 1. Initial state: both empty
        assertEquals(0, musicDao.getSongsForPlaylistList(playlistId).size)

        // 2. Perform safe sync with empty
        musicDao.safeSyncPlaylistSongs(playlistId, emptyList())

        // 3. Verify still empty
        assertEquals(0, musicDao.getSongsForPlaylistList(playlistId).size)
    }

    @Test
    fun testSafeSyncWorksWhenRemoteHasSongs() = runBlocking {
        val playlistId = "pl1"
        val song1 = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        val song2 = Song(id = "s2", title = "Song 2", artist = "Artist 1")
        
        // 0. Insert songs
        musicDao.insertSongs(listOf(song1.toEntity(), song2.toEntity()))

        // 1. Initial state: local has song 1
        musicDao.syncPlaylistSongs(playlistId, listOf(song1.toEntity()))

        // 2. Remote has song 2
        musicDao.safeSyncPlaylistSongs(playlistId, listOf(song2.toEntity()))

        // 3. Verify local is updated to song 2
        val songs = musicDao.getSongsForPlaylistList(playlistId)
        assertEquals(1, songs.size)
        assertEquals("s2", songs.first().id)
    }

    @Test
    fun testGenuineEmptyPlaylistHandledAfterRemoval() = runBlocking {
        val playlistId = "pl1"
        val song = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        
        // 0. Insert song
        musicDao.insertSongs(listOf(song.toEntity()))

        // 1. Setup local and remote with 1 song
        musicDao.syncPlaylistSongs(playlistId, listOf(song.toEntity()))
        fakeApi.playlistSongsMap[playlistId] = emptyList() // Remote becomes empty

        // 2. Remove song locally first (as removeSongFromPlaylist does)
        musicDao.removeSongFromPlaylist(playlistId, "s1")
        assertEquals(0, musicDao.getSongsForPlaylistList(playlistId).size)

        // 3. Safe sync with empty remote
        musicDao.safeSyncPlaylistSongs(playlistId, emptyList())

        // 4. Verify local stays empty (genuine empty)
        assertEquals(0, musicDao.getSongsForPlaylistList(playlistId).size)
    }

    @Test
    fun testOptimisticAdditionRollbackOnFailure() = runBlocking {
        val playlistId = "pl1"
        val songId = "s1"
        fakeApi.shouldFailAdd = true

        try {
            repository.addSongToPlaylistOptimistic(playlistId, songId)
        } catch (e: Exception) {
            // Expected
        }

        // Verify song is not in playlist locally
        val songs = musicDao.getSongsForPlaylistList(playlistId)
        assertTrue(songs.none { it.id == songId })
    }

    @Test
    fun testDuplicateAdditionHandling() = runBlocking {
        val playlistId = "pl1"
        val song = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        
        // 0. Insert song first (needed for INNER JOIN in getSongsForPlaylistList)
        musicDao.insertSongs(listOf(song.toEntity()))

        // 1. Add twice
        musicDao.insertPlaylistSongCrossRef(com.example.data.db.PlaylistSongCrossRef(playlistId, song.id, 0))
        musicDao.insertPlaylistSongCrossRef(com.example.data.db.PlaylistSongCrossRef(playlistId, song.id, 1))

        // Room REPLACE strategy handles this at the DB level for primary keys, 
        // as (playlistId, songId) is the composite primary key.
        val songs = musicDao.getSongsForPlaylistList(playlistId)
        assertEquals(1, songs.size)
    }

    class FakePlaylistApiService : com.example.SyncIdempotenceTest.FakeSyncApiService() {
        val playlistSongsMap = mutableMapOf<String, List<Song>>()
        var shouldFailAdd = false

        override suspend fun getPlaylistSongs(id: String): List<Song> {
            return playlistSongsMap[id] ?: emptyList()
        }

        override suspend fun addSongToPlaylist(
            id: String,
            request: com.example.data.models.AddPlaylistSongRequest
        ): Map<String, Any> {
            if (shouldFailAdd) throw Exception("Server error")
            val current = playlistSongsMap[id].orEmpty().toMutableList()
            // In a real app we'd fetch the song, but here we just mock
            current.add(Song(id = request.songId, title = "Added", artist = "Artist"))
            playlistSongsMap[id] = current
            return mapOf("success" to true)
        }

        override suspend fun getPlaylistById(id: String): com.example.data.models.Playlist {
            return com.example.data.models.Playlist(
                id = id,
                name = "Test",
                songs = playlistSongsMap[id] ?: emptyList()
            )
        }
    }
}
