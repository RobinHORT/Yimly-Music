package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.models.LyricsDisplayConfig
import com.example.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun serverUrlCanBeEditedAndSaved() {
        var savedUrl: String? = null
        composeTestRule.setContent {
            SettingsScreen(
                userProfile = null,
                serverUrl = "https://old.example",
                isOnline = true,
                isSyncing = false,
                lyricsConfig = LyricsDisplayConfig(),
                onUpdateServerUrl = { savedUrl = it },
                onSaveLyricsConfig = {},
                onLogout = {},
                onLoginClick = {},
                onSyncLibrary = {}
            )
        }

        composeTestRule.onNodeWithTag("server_url_input").assertExists()
        composeTestRule.onNodeWithTag("save_server_url_btn").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("server_url_input").performTextClearance()
        composeTestRule.onNodeWithTag("server_url_input").performTextInput("https://new.example/")
        composeTestRule.onNodeWithTag("save_server_url_btn").assertIsEnabled().performClick()

        assertEquals("https://new.example", savedUrl)
    }

    @Test
    fun syncButtonShowsProgressAndDisablesWhileSyncing() {
        composeTestRule.setContent {
            SettingsScreen(
                userProfile = null,
                serverUrl = "https://yimly.example",
                isOnline = false,
                isSyncing = true,
                lyricsConfig = LyricsDisplayConfig(),
                onUpdateServerUrl = {},
                onSaveLyricsConfig = {},
                onLogout = {},
                onLoginClick = {},
                onSyncLibrary = {}
            )
        }

        composeTestRule.onNodeWithText("NETWORK OFFLINE").assertExists()
        composeTestRule.onNodeWithText("Syncing library…").assertExists()
        composeTestRule.onNodeWithTag("sync_library_btn").assertIsNotEnabled()
    }
}
