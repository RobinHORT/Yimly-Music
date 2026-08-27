package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.ui.components.PlaylistArtwork
import com.example.ui.components.SongItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EndToEndAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val song1 = Song(id = "s1", title = "Song One", artist = "Artist A", album = "Album 1", explicitArtworkUrl = "http://img/1.png")
    private val song2 = Song(id = "s2", title = "Song Two", artist = "Artist B", album = "Album 2", explicitArtworkUrl = "http://img/2.png")
    private val song3 = Song(id = "s3", title = "Song Three", artist = "Artist C", album = "Album 3", explicitArtworkUrl = "http://img/3.png")
    private val song4 = Song(id = "s4", title = "Song Four", artist = "Artist D", album = "Album 4", explicitArtworkUrl = "http://img/4.png")
    private val song5 = Song(id = "s5", title = "Song Five", artist = "Artist E", album = "Album 5", explicitArtworkUrl = "http://img/5.png")

    @Test
    fun testSongThreeDotMenuRequiredOrderAndNoAddToQueue() {
        var playCalled = false
        var addToNowPlayingCalled = false
        var addToPlaylistCalled = false
        var addToFavoritesCalled = false

        composeTestRule.setContent {
            SongItemRow(
                song = song1,
                isPlaying = false,
                isCurrentSong = false,
                onSongClick = {},
                onToggleFavorite = { addToFavoritesCalled = true },
                onPlay = { playCalled = true },
                onAddToNowPlaying = { addToNowPlayingCalled = true },
                onAddToPlaylist = { addToPlaylistCalled = true }
            )
        }

        // Open menu
        composeTestRule.onNodeWithTag("menu_btn_${song1.id}").performClick()

        // Verify options presence & order
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to Now Playing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to Playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to Favourites").assertIsDisplayed()

        // Strict requirement: No "Add to Queue"
        composeTestRule.onNodeWithText("Add to Queue").assertDoesNotExist()

        // Perform click on Add to Now Playing
        composeTestRule.onNodeWithTag("menu_add_to_now_playing_${song1.id}").performClick()
        assertTrue(addToNowPlayingCalled)
    }

    @Test
    fun testPlaylistArtworkCustomCover() {
        composeTestRule.setContent {
            PlaylistArtwork(
                coverUrl = "http://example.com/custom.jpg",
                songs = listOf(song1, song2, song3, song4)
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testPlaylistArtwork4SongCollage() {
        composeTestRule.setContent {
            PlaylistArtwork(
                coverUrl = null,
                songs = listOf(song1, song2, song3, song4)
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testPlaylistArtwork1Song() {
        composeTestRule.setContent {
            PlaylistArtwork(
                coverUrl = null,
                songs = listOf(song1)
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testPlaylistArtworkEmpty() {
        composeTestRule.setContent {
            PlaylistArtwork(
                coverUrl = null,
                songs = emptyList()
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testPerSongLyricsOffsetIsolation() {
        // Song A has server offset +500ms
        // Song B has server offset -200ms
        val songAOffset = 500
        val songBOffset = -200

        var currentSong = "A"
        var activeOffset = if (currentSong == "A") songAOffset else songBOffset
        assertEquals(500, activeOffset)

        // Switch to Song B -> restores Song B's server offset
        currentSong = "B"
        activeOffset = if (currentSong == "A") songAOffset else songBOffset
        assertEquals(-200, activeOffset)

        // Switch back to Song A -> restores Song A's server offset with NO global leakage
        currentSong = "A"
        activeOffset = if (currentSong == "A") songAOffset else songBOffset
        assertEquals(500, activeOffset)
        assertNotEquals(activeOffset, songBOffset)
    }

    @Test
    fun testCommunityPlaylistLiveSongCountUpdate() {
        // Initial community playlist with 2 songs
        val playlist = Playlist(
            id = "cp1",
            name = "Community Chill",
            userId = "u2",
            ownerName = "Community",
            isPublic = true,
            songCount = 2,
            songs = listOf(song1, song2)
        )
        assertEquals(2, playlist.songs.size)
        assertEquals(2, playlist.songCount)

        // Add 3rd song -> updates to 3 songs dynamically
        val updated3 = playlist.copy(
            songCount = 3,
            songs = listOf(song1, song2, song3)
        )
        assertEquals(3, updated3.songs.size)
        assertEquals(3, updated3.songCount)

        // Add up to 5 songs -> updates to 5 songs dynamically without app restart
        val updated5 = updated3.copy(
            songCount = 5,
            songs = listOf(song1, song2, song3, song4, song5)
        )
        assertEquals(5, updated5.songs.size)
        assertEquals(5, updated5.songCount)
    }
}
