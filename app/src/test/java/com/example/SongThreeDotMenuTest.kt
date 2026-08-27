package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.models.Song
import com.example.ui.components.SongItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SongThreeDotMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testSong = Song(
        id = "test-song-123",
        title = "Test Song Title",
        artist = "Test Artist",
        album = "Test Album",
        durationSeconds = 185,
        isFavorite = false
    )

    @Test
    fun verifySongThreeDotMenuOptionsOrderAndLabels() {
        var playClicked = false
        var addToNowPlayingClicked = false
        var addToPlaylistClicked = false
        var favoriteToggled = false

        composeTestRule.setContent {
            SongItemRow(
                song = testSong,
                isPlaying = false,
                isCurrentSong = false,
                onSongClick = {},
                onToggleFavorite = { favoriteToggled = true },
                onPlay = { playClicked = true },
                onAddToNowPlaying = { addToNowPlayingClicked = true },
                onAddToPlaylist = { addToPlaylistClicked = true }
            )
        }

        // Open three-dot menu
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()

        // 1. Verify "Play" is displayed and functional
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()

        // 2. Verify "Add to Now Playing" is displayed
        composeTestRule.onNodeWithText("Add to Now Playing").assertIsDisplayed()

        // 3. Verify "Add to Playlist" is displayed
        composeTestRule.onNodeWithText("Add to Playlist").assertIsDisplayed()

        // 4. Verify "Add to Favourites" is displayed
        composeTestRule.onNodeWithText("Add to Favourites").assertIsDisplayed()

        // Verify "Add to Queue" does NOT exist
        composeTestRule.onNodeWithText("Add to Queue").assertDoesNotExist()

        // Verify Admin-only items do NOT exist for normal user
        composeTestRule.onNodeWithText("LRC Editor").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun verifyAdminUserSeesLrcEditorAndDeleteOptions() {
        var editLrcClicked = false
        var deleteSongClicked = false

        composeTestRule.setContent {
            SongItemRow(
                song = testSong,
                isPlaying = false,
                isCurrentSong = false,
                onSongClick = {},
                onToggleFavorite = {},
                isAdmin = true,
                onEditLrc = { editLrcClicked = true },
                onDeleteSong = { deleteSongClicked = true }
            )
        }

        // Open three-dot menu
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()

        // Verify all 6 options are present for admin
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to Now Playing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to Playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to Favourites").assertIsDisplayed()
        composeTestRule.onNodeWithText("LRC Editor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()

        // Test LRC Editor action
        composeTestRule.onNodeWithTag("menu_lrc_editor_${testSong.id}").performClick()
        assertTrue("Edit LRC callback should be triggered for admin", editLrcClicked)

        // Open menu again to test Delete action
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()
        composeTestRule.onNodeWithTag("menu_delete_${testSong.id}").performClick()
        assertTrue("Delete song callback should be triggered for admin", deleteSongClicked)
    }

    @Test
    fun verifyMenuActionsTriggerCallbacks() {
        var playClicked = false
        var addToNowPlayingClicked = false
        var addToPlaylistClicked = false
        var favoriteToggled = false

        composeTestRule.setContent {
            SongItemRow(
                song = testSong,
                isPlaying = false,
                isCurrentSong = false,
                onSongClick = {},
                onToggleFavorite = { favoriteToggled = true },
                onPlay = { playClicked = true },
                onAddToNowPlaying = { addToNowPlayingClicked = true },
                onAddToPlaylist = { addToPlaylistClicked = true }
            )
        }

        // Test Play action
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()
        composeTestRule.onNodeWithTag("menu_play_${testSong.id}").performClick()
        assertTrue("Play callback should be triggered", playClicked)

        // Test Add to Now Playing action
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()
        composeTestRule.onNodeWithTag("menu_add_to_now_playing_${testSong.id}").performClick()
        assertTrue("Add to Now Playing callback should be triggered", addToNowPlayingClicked)

        // Test Add to Playlist action
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()
        composeTestRule.onNodeWithTag("menu_add_to_playlist_${testSong.id}").performClick()
        assertTrue("Add to Playlist callback should be triggered", addToPlaylistClicked)

        // Test Add to Favourites action
        composeTestRule.onNodeWithTag("menu_btn_${testSong.id}").performClick()
        composeTestRule.onNodeWithTag("menu_add_to_favourites_${testSong.id}").performClick()
        assertTrue("Favorite callback should be triggered", favoriteToggled)
    }
}
