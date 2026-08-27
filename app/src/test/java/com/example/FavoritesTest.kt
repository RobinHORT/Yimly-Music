package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.SongEntity
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.example.data.api.YimlyApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FavoritesTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var musicRepository: MusicRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://yimly.robinhort.link/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        val apiService = retrofit.create(YimlyApiService::class.java)

        musicRepository = MusicRepository(
            musicDao = database.musicDao(),
            apiService = apiService,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testFavoriteToggleCycleAndPersistence() = runBlocking {
        val songId = "song_fav_test_1"
        val songEntity = SongEntity(
            id = songId,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationSeconds = 200,
            isFavorite = false
        )
        database.musicDao().insertSong(songEntity)

        // 1. Initial state: not favorite
        val initialSong = database.musicDao().getSongById(songId)
        assertNotNull(initialSong)
        assertFalse(initialSong!!.isFavorite)

        // 2. Add to favorites (OFF -> ON) via DAO updateFavorite or repository logic
        database.musicDao().updateFavorite(songId, true)
        var favSong = database.musicDao().getSongById(songId)
        assertTrue(favSong!!.isFavorite)

        // 3. Sync test: re-inserting song via upsertSongs should preserve isFavorite = true
        val incomingRemoteSong = Song(
            id = songId,
            title = "Test Song Updated",
            artist = "Test Artist",
            album = "Test Album",
            durationSeconds = 200,
            isFavorite = false // remote says false
        )
        database.musicDao().upsertSongs(listOf(incomingRemoteSong.toEntity()))
        val preservedSong = database.musicDao().getSongById(songId)
        assertTrue(preservedSong!!.isFavorite) // Should remain true due to upsert preservation

        // 4. Remove from favorites (ON -> OFF)
        database.musicDao().updateFavorite(songId, false)
        val unfavSong = database.musicDao().getSongById(songId)
        assertFalse(unfavSong!!.isFavorite)

        // 5. Repeated toggle: OFF -> ON -> OFF -> ON -> OFF
        database.musicDao().updateFavorite(songId, true)
        assertTrue(database.musicDao().getSongById(songId)!!.isFavorite)

        database.musicDao().updateFavorite(songId, false)
        assertFalse(database.musicDao().getSongById(songId)!!.isFavorite)

        database.musicDao().updateFavorite(songId, true)
        assertTrue(database.musicDao().getSongById(songId)!!.isFavorite)

        database.musicDao().updateFavorite(songId, false)
        assertFalse(database.musicDao().getSongById(songId)!!.isFavorite)
    }
}
