package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.db.PlaylistEntity
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.Playlist
import com.example.data.repository.MusicRepository
import com.example.ui.components.resolveCoverUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistSharingTest {

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
    fun testPlaylistEntityPersistenceWithSharingAndCommunity() = runBlocking {
        // 1. Owned private playlist
        val privatePlaylist = PlaylistEntity(
            id = "pl_private_1",
            name = "My Private Playlist",
            description = "Only for me",
            isOwner = true,
            canEdit = true,
            permission = "owner",
            isPublic = false
        )
        database.musicDao().insertPlaylist(privatePlaylist)

        // 2. Shared view-only playlist
        val sharedViewPlaylist = PlaylistEntity(
            id = "pl_shared_view_2",
            name = "Shared Playlist",
            description = "View only for me",
            isOwner = false,
            canEdit = false,
            permission = "view",
            ownerName = "test",
            isPublic = false
        )
        database.musicDao().insertPlaylist(sharedViewPlaylist)

        // 3. Shared can-edit playlist
        val sharedEditPlaylist = PlaylistEntity(
            id = "pl_shared_edit_3",
            name = "Shared Collab",
            description = "Can edit by me",
            isOwner = false,
            canEdit = true,
            permission = "edit",
            ownerName = "test",
            isPublic = false
        )
        database.musicDao().insertPlaylist(sharedEditPlaylist)

        // 4. Community playlist
        val communityPlaylist = PlaylistEntity(
            id = "pl_community_4",
            name = "Community Chill Vibes",
            description = "Global playlist",
            isOwner = false,
            canEdit = false,
            permission = "owner",
            ownerName = "curator",
            isPublic = true
        )
        database.musicDao().insertPlaylist(communityPlaylist)

        val allPlaylists = database.musicDao().getAllPlaylists().first()
        assertEquals(4, allPlaylists.size)

        // Verify private
        val pl1 = database.musicDao().getPlaylistById("pl_private_1")
        assertNotNull(pl1)
        assertTrue(pl1!!.isOwner)
        assertTrue(pl1.canEdit)
        assertFalse(pl1.isPublic)
        assertEquals("owner", pl1.permission)

        // Verify shared view
        val pl2 = database.musicDao().getPlaylistById("pl_shared_view_2")
        assertNotNull(pl2)
        assertFalse(pl2!!.isOwner)
        assertFalse(pl2.canEdit)
        assertFalse(pl2.isPublic)
        assertEquals("view", pl2.permission)
        assertEquals("test", pl2.ownerName)

        // Verify shared edit
        val pl3 = database.musicDao().getPlaylistById("pl_shared_edit_3")
        assertNotNull(pl3)
        assertFalse(pl3!!.isOwner)
        assertTrue(pl3.canEdit)
        assertFalse(pl3.isPublic)
        assertEquals("edit", pl3.permission)

        // Verify community
        val pl4 = database.musicDao().getPlaylistById("pl_community_4")
        assertNotNull(pl4)
        assertTrue(pl4!!.isPublic)
        assertEquals("curator", pl4.ownerName)
    }

    @Test
    fun testPlaylistModelConversion() {
        val model = Playlist(
            id = "100",
            name = "Test Model",
            description = "Description",
            songCount = 5,
            ownerName = "test_owner",
            isOwner = false,
            canEdit = true,
            permission = "edit",
            isPublic = true
        )

        val entity = model.toEntity()
        assertEquals("100", entity.id)
        assertEquals("Test Model", entity.name)
        assertEquals("test_owner", entity.ownerName)
        assertFalse(entity.isOwner)
        assertTrue(entity.canEdit)
        assertEquals("edit", entity.permission)
        assertTrue(entity.isPublic)

        val convertedModel = entity.toPlaylist()
        assertEquals(model.id, convertedModel.id)
        assertEquals(model.name, convertedModel.name)
        assertEquals(model.ownerName, convertedModel.ownerName)
        assertEquals(model.isOwner, convertedModel.isOwner)
        assertEquals(model.canEdit, convertedModel.canEdit)
        assertEquals(model.permission, convertedModel.permission)
        assertEquals(model.isPublic, convertedModel.isPublic)
    }

    @Test
    fun testCommunityPlaylistFilteringForHomeScreen() {
        val playlists = listOf(
            Playlist(id = "1", name = "My Personal", isPublic = false, isOwner = true, canEdit = true),
            Playlist(id = "2", name = "Shared With Me", isPublic = false, isOwner = false, canEdit = false),
            Playlist(id = "3", name = "Yimly Community Test", isPublic = true, isOwner = true, canEdit = true, ownerName = "test"),
            Playlist(id = "4", name = "Global Chill", isPublic = true, isOwner = false, canEdit = false, ownerName = "Hort")
        )

        val communityPlaylists = playlists.filter { it.isPublic }
        assertEquals(2, communityPlaylists.size)
        assertEquals(listOf("3", "4"), communityPlaylists.map { it.id })

        val privateAndShared = playlists.filter { !it.isPublic }
        assertEquals(2, privateAndShared.size)
        assertEquals(listOf("1", "2"), privateAndShared.map { it.id })
    }

    @Test
    fun testCommunityPlaylistCoverUrlResolution() {
        val relativeCoverPlaylist = Playlist(
            id = "rel_1",
            name = "Community Playlist 1",
            rawCoverUrl = "/api/playlists/rel_1/cover",
            isPublic = true
        )
        val resolved = relativeCoverPlaylist.coverUrl.resolveCoverUrl()
        assertEquals("https://yimly.robinhort.link/api/playlists/rel_1/cover", resolved)

        val absoluteCoverPlaylist = Playlist(
            id = "abs_2",
            name = "Community Playlist 2",
            rawCoverUrl = "https://custom.server.com/cover.jpg",
            isPublic = true
        )
        val resolvedAbs = absoluteCoverPlaylist.coverUrl.resolveCoverUrl()
        assertEquals("https://custom.server.com/cover.jpg", resolvedAbs)

        val localFilePlaylist = Playlist(
            id = "loc_3",
            name = "Local Playlist",
            rawCoverUrl = "file:///data/user/0/com.example/files/cover.jpg",
            isPublic = true
        )
        val resolvedLocal = localFilePlaylist.coverUrl.resolveCoverUrl()
        assertEquals("file:///data/user/0/com.example/files/cover.jpg", resolvedLocal)
    }
}
