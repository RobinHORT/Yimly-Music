package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.YimlyApiService
import com.example.data.datastore.PreferencesManager
import com.example.data.db.PlaylistEntity
import com.example.data.db.YimlyDatabase
import com.example.data.db.toEntity
import com.example.data.models.AuthState
import com.example.data.models.Playlist
import com.example.data.models.UserProfile
import com.example.data.repository.AuthRepository
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistSharingTest {

    private lateinit var context: Context
    private lateinit var database: YimlyDatabase
    private lateinit var musicRepository: MusicRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, YimlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://yimly.robinhort.link/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val apiService = retrofit.create(YimlyApiService::class.java)

        musicRepository = MusicRepository(
            musicDao = database.musicDao(),
            apiService = apiService,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )

        preferencesManager = PreferencesManager(context)
        authRepository = AuthRepository(
            apiService = apiService,
            preferencesManager = preferencesManager,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            preferencesManager.clearAuthSession()
        }
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

    @Test
    fun testLoginWithTestUser() = runBlocking {
        // 1. Initially unauthenticated
        val initialAuth = authRepository.authStateFlow.first()
        assertTrue("Initially unauthenticated", initialAuth is AuthState.Unauthenticated)

        // 2. Login with test user credentials
        val result = authRepository.login(username = "test", password = "1234", rememberMe = true)
        assertTrue("Test user login should succeed", result.isSuccess)

        val user = result.getOrNull()
        assertNotNull("User profile must not be null", user)
        assertEquals("test", user?.username)
        assertTrue("User ID must not be empty", user?.id?.isNotEmpty() == true)

        // 3. AuthState must reflect authenticated test user
        val currentAuth = authRepository.authStateFlow.first()
        assertTrue("Auth state should be Authenticated", currentAuth is AuthState.Authenticated)
        val authenticatedState = currentAuth as AuthState.Authenticated
        assertEquals("test", authenticatedState.user.username)
        assertEquals(user?.id, authenticatedState.user.id)
        assertTrue("Token must be non-empty", authenticatedState.token.isNotEmpty())
    }

    @Test
    fun testPublicAndPrivatePlaylistAccessControl() = runBlocking {
        // 1. Insert a public playlist (created by test user)
        val publicPlaylist = PlaylistEntity(
            id = "pl_public_chill",
            name = "Community Public Chill",
            description = "Public for everybody",
            isOwner = true,
            userId = "usr_test",
            ownerName = "test",
            isPublic = true
        )
        database.musicDao().insertPlaylist(publicPlaylist)

        // 2. Insert a private playlist (owned by test user)
        val testPrivatePlaylist = PlaylistEntity(
            id = "pl_private_test",
            name = "Test User's Secret Diary",
            description = "Private only for test user",
            isOwner = true,
            userId = "usr_test",
            ownerName = "test",
            isPublic = false
        )
        database.musicDao().insertPlaylist(testPrivatePlaylist)

        // 3. Insert a private playlist (owned by another user)
        val otherPrivatePlaylist = PlaylistEntity(
            id = "pl_private_other",
            name = "Other User's Secret Diary",
            description = "Private only for other user",
            isOwner = false,
            userId = "usr_other",
            ownerName = "other",
            isPublic = false
        )
        database.musicDao().insertPlaylist(otherPrivatePlaylist)

        // Fetch models
        val dbPlaylists = database.musicDao().getAllPlaylists().first().map { it.toPlaylist() }
        assertEquals(3, dbPlaylists.size)

        val pubPl = dbPlaylists.first { it.id == "pl_public_chill" }
        val testPrivPl = dbPlaylists.first { it.id == "pl_private_test" }
        val otherPrivPl = dbPlaylists.first { it.id == "pl_private_other" }

        // Test User context ("usr_test", "test"):
        // Public playlist is viewable by every user
        assertTrue("Public playlist must be viewable by test user", pubPl.isViewableBy(userId = "usr_test", username = "test"))
        // Private playlist is viewable by its owner
        assertTrue("Test user must view their own private playlist", testPrivPl.isViewableBy(userId = "usr_test", username = "test", isCurrentUserOwner = true))
        // Private playlist of other user is NOT viewable by test user
        assertFalse("Test user must NOT view other user's private playlist", otherPrivPl.isViewableBy(userId = "usr_test", username = "test", isCurrentUserOwner = false))

        // Other User context ("usr_other", "other"):
        // Public playlist is viewable by other user
        assertTrue("Public playlist must be viewable by other user", pubPl.isViewableBy(userId = "usr_other", username = "other"))
        // Test user's private playlist is NOT viewable by other user
        assertFalse("Other user must NOT view test user's private playlist", testPrivPl.isViewableBy(userId = "usr_other", username = "other", isCurrentUserOwner = false))
        // Other user's private playlist IS viewable by other user (owner)
        assertTrue("Other user must view their own private playlist", otherPrivPl.isViewableBy(userId = "usr_other", username = "other", isCurrentUserOwner = true))

        // Unauthenticated / Guest context (null, null):
        // Public playlist is viewable by guest
        assertTrue("Public playlist must be viewable by guest", pubPl.isViewableBy(userId = null, username = null, isCurrentUserOwner = false))
        // Private playlists are NOT viewable by guest
        assertFalse("Guest must NOT view test user's private playlist", testPrivPl.isViewableBy(userId = null, username = null, isCurrentUserOwner = false))
        assertFalse("Guest must NOT view other user's private playlist", otherPrivPl.isViewableBy(userId = null, username = null, isCurrentUserOwner = false))
    }

    @Test
    fun testVisiblePlaylistsStreamFiltering() = runBlocking {
        // Insert public playlist and private playlists
        database.musicDao().insertPlaylist(
            PlaylistEntity(
                id = "pl_pub_1",
                name = "Global Beats",
                userId = "usr_test",
                ownerName = "test",
                isPublic = true
            )
        )
        database.musicDao().insertPlaylist(
            PlaylistEntity(
                id = "pl_priv_test_1",
                name = "Test Private Gems",
                userId = "usr_test",
                ownerName = "test",
                isPublic = false,
                isOwner = true
            )
        )
        database.musicDao().insertPlaylist(
            PlaylistEntity(
                id = "pl_priv_other_1",
                name = "Other Private Gems",
                userId = "usr_other",
                ownerName = "other",
                isPublic = false,
                isOwner = false
            )
        )

        // 1. Visible playlists for Test User
        val testUserPlaylists = musicRepository.getVisiblePlaylists(userId = "usr_test", username = "test").first()
        assertEquals(2, testUserPlaylists.size)
        assertTrue(testUserPlaylists.any { it.id == "pl_pub_1" })
        assertTrue(testUserPlaylists.any { it.id == "pl_priv_test_1" })
        assertFalse("Other user's private playlist must not be visible to test user", testUserPlaylists.any { it.id == "pl_priv_other_1" })

        // 2. Visible playlists for Other User
        val otherUserPlaylists = musicRepository.getVisiblePlaylists(userId = "usr_other", username = "other").first()
        assertEquals(2, otherUserPlaylists.size)
        assertTrue(otherUserPlaylists.any { it.id == "pl_pub_1" })
        assertTrue(otherUserPlaylists.any { it.id == "pl_priv_other_1" })
        assertFalse("Test user's private playlist must not be visible to other user", otherUserPlaylists.any { it.id == "pl_priv_test_1" })

        // 3. Visible playlists for Guest (Unauthenticated)
        val guestPlaylists = musicRepository.getVisiblePlaylists(userId = null, username = null).first()
        assertEquals(1, guestPlaylists.size)
        assertEquals("pl_pub_1", guestPlaylists.first().id)
    }

    @Test
    fun testLibraryCategorizationRespectsPublicAndPrivateOwnership() = runBlocking {
        val testUser = UserProfile(id = "usr_test", username = "test", email = "test@yimly.app")

        val playlists = listOf(
            Playlist(id = "pub_all", name = "Community Beats", isPublic = true, userId = "usr_someone", ownerName = "someone", isOwner = false),
            Playlist(id = "priv_mine", name = "My Private Jam", isPublic = false, userId = "usr_test", ownerName = "test", isOwner = true),
            Playlist(id = "priv_theirs", name = "Stranger Private", isPublic = false, userId = "usr_other", ownerName = "other", isOwner = false, canEdit = false, permission = "none")
        )

        // Filter for visible playlists for testUser:
        // - Public is viewable by every user
        // - Private is only seen by playlist owner
        val visibleForTestUser = playlists.filter { it.isViewableBy(testUser.id, testUser.username) }
        assertEquals(2, visibleForTestUser.size)
        assertTrue("Public playlist must be visible", visibleForTestUser.any { it.id == "pub_all" })
        assertTrue("Owned private playlist must be visible", visibleForTestUser.any { it.id == "priv_mine" })
        assertFalse("Other user's private playlist must not be visible", visibleForTestUser.any { it.id == "priv_theirs" })

        // UI Library categorizations for testUser
        val myPlaylists = visibleForTestUser.filter { (it.isOwner || it.permission == "owner") && !it.isPublic }
        val communityPlaylists = visibleForTestUser.filter { it.isPublic }

        assertEquals(1, myPlaylists.size)
        assertEquals("priv_mine", myPlaylists.first().id)

        assertEquals(1, communityPlaylists.size)
        assertEquals("pub_all", communityPlaylists.first().id)
    }
}
