package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricFontFamily
import com.example.data.models.LyricLine
import com.example.data.models.LyricWord
import com.example.data.models.LyricTextCase
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.formatLyricText
import com.example.data.models.toTitleCase
import com.example.ui.lyrics.LyricsSettingsDialog
import com.example.ui.lyrics.LyricsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsSettingsComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTitleCaseTransformationRules() {
        assertEquals("Hello World", toTitleCase("hello world"))
        assertEquals("This Is A Song", toTitleCase("THIS IS A SONG"))
        assertEquals("Hello World Tonight", toTitleCase("hello WORLD tonight"))
        assertEquals("Don't Stop Me Now", toTitleCase("don't stop me now"))
        assertEquals("Hello, World!", toTitleCase("hello, world!"))
        assertEquals("(Let's Go)", toTitleCase("(let's go)"))
        assertEquals("", toTitleCase(""))
    }

    @Test
    fun testAllTextCasesFormatting() {
        val sample = "hello WORLD tonight"
        assertEquals("hello WORLD tonight", formatLyricText(sample, LyricTextCase.ORIGINAL))
        assertEquals("HELLO WORLD TONIGHT", formatLyricText(sample, LyricTextCase.UPPERCASE))
        assertEquals("hello world tonight", formatLyricText(sample, LyricTextCase.LOWERCASE))
        assertEquals("Hello World Tonight", formatLyricText(sample, LyricTextCase.TITLE_CASE))
    }

    @Test
    fun testLyricsSettingsDialogFullSectionsAndTitleCaseSelection() {
        var savedConfig: LyricsDisplayConfig? = null
        val initialConfig = LyricsDisplayConfig(
            fontFamily = LyricFontFamily.DEFAULT,
            textCase = LyricTextCase.ORIGINAL,
            currentLineFontSizeSp = 24f,
            otherLineFontSizeSp = 15f,
            otherLinesOpacity = 0.35f,
            elrcFadeInDurationMs = 2200,
            lyricTransitionOverlapMs = 450,
            lrcDisplayLeadInMs = 1800
        )

        composeTestRule.setContent {
            LyricsSettingsDialog(
                initialConfig = initialConfig,
                onDismiss = {},
                onSaveConfig = { savedConfig = it }
            )
        }

        // Section Headers
        composeTestRule.onNodeWithText("Lyrics Settings").assertExists()
        composeTestRule.onNodeWithText("Lyrics Display").assertExists()
        composeTestRule.onNodeWithText("Lyric Sections").assertExists()
        composeTestRule.onNodeWithText("Text Case").assertExists()
        composeTestRule.onNodeWithText("Current Line").assertExists()
        composeTestRule.onNodeWithText("Previous & Next Lines").assertExists()
        composeTestRule.onNodeWithText("Other").assertExists()
        composeTestRule.onNodeWithText("eLRC Full-line Fade In").assertExists()
        composeTestRule.onNodeWithText("Transition Overlap").assertExists()
        composeTestRule.onNodeWithText("LRC Early Display").assertExists()
        composeTestRule.onNodeWithText("2200 ms").assertExists()
        composeTestRule.onNodeWithText("450 ms").assertExists()
        composeTestRule.onNodeWithText("1800 ms").assertExists()

        // Verify all 4 text case options exist
        composeTestRule.onNodeWithText("Original").assertExists()
        composeTestRule.onNodeWithText("UPPERCASE").assertExists()
        composeTestRule.onNodeWithText("lowercase").assertExists()
        composeTestRule.onNodeWithText("Title Case").assertExists()

        // Select Title Case
        composeTestRule.onNodeWithText("Title Case").performScrollTo().performClick()

        // Apply changes
        composeTestRule.onNodeWithText("Apply").performScrollTo().performClick()

        // Check saved config
        assertEquals(LyricTextCase.TITLE_CASE, savedConfig?.textCase)
        assertEquals(2200, savedConfig?.elrcFadeInDurationMs)
        assertEquals(450, savedConfig?.lyricTransitionOverlapMs)
        assertEquals(1800, savedConfig?.lrcDisplayLeadInMs)
    }

    @Test
    fun testSharedSurroundingLineSettingsAndCurrentLineIndependence() {
        val currentLineSize = 30f
        val otherLineSize = 22f
        val otherOpacity = 0.15f

        val config = LyricsDisplayConfig(
            fontFamily = LyricFontFamily.SUPER_SALE,
            textCase = LyricTextCase.TITLE_CASE,
            currentLineFontSizeSp = currentLineSize,
            currentLineColorHex = "#FF3366",
            otherLineFontSizeSp = otherLineSize,
            otherLinesOpacity = otherOpacity,
            visibleLines = 3,
            highlightCurrentLine = true
        )

        // Verify independent settings
        assertNotEquals(config.currentLineFontSizeSp, config.otherLineFontSizeSp)
        assertEquals(30f, config.currentLineFontSizeSp)
        assertEquals(22f, config.otherLineFontSizeSp)
        assertEquals(0.15f, config.otherLinesOpacity)

        val lyricsData = LyricsData(
            songId = "test_song",
            lines = listOf(
                LyricLine(timeMs = 1000L, text = "first previous lyric"),
                LyricLine(timeMs = 2000L, text = "current active lyric"),
                LyricLine(timeMs = 3000L, text = "next future lyric")
            ),
            isSynced = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 2050L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOpenSettings = {},
                onOffsetChange = {}
            )
        }

        // Verify all 3 lines formatted in Title Case are displayed
        composeTestRule.onNodeWithText("First Previous Lyric").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current Active Lyric").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next Future Lyric").assertIsDisplayed()
    }

    @Test
    fun testFocusModeSupportsEvenSectionCounts() {
        val lyricsData = LyricsData(
            songId = "even_sections",
            lines = listOf(
                LyricLine(timeMs = 1000L, text = "one"),
                LyricLine(timeMs = 2000L, text = "two active"),
                LyricLine(timeMs = 3000L, text = "three"),
                LyricLine(timeMs = 4000L, text = "four")
            ),
            isSynced = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 2050L,
                songOffsetMs = 0L,
                config = LyricsDisplayConfig(visibleLines = 4, textCase = LyricTextCase.TITLE_CASE),
                onSeekTo = {},
                onOffsetChange = {}
            )
        }

        // Four slots are one previous, current, and two upcoming lines.
        composeTestRule.onNodeWithText("One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Two Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Three").assertIsDisplayed()
        composeTestRule.onNodeWithText("Four").assertIsDisplayed()
    }



    @Test
    fun testElrcLineEntersFadeWindowBeforeFirstWord() {
        val lyricsData = LyricsData(
            songId = "elrc_fade_window",
            hasWordSync = true,
            lines = listOf(
                LyricLine(
                    timeMs = 3000L,
                    text = "fade in lyric",
                    words = listOf(LyricWord("fade", 3000L, 3400L), LyricWord("in", 3400L, 3800L), LyricWord("lyric", 3800L, 4500L)),
                    hasWordTimestamps = true
                )
            ),
            isSynced = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 1800L,
                songOffsetMs = 0L,
                config = LyricsDisplayConfig(
                    formatMode = com.example.data.models.LyricsFormatMode.ELRC,
                    elrcFadeInDurationMs = 1500
                ),
                onSeekTo = {},
                onOffsetChange = {}
            )
        }

        // The line is mounted in the 1.5-second fade window, before its first word starts.
        composeTestRule.onNodeWithTag("synchronized_elrc_row", useUnmergedTree = true).assertExists()
    }

    @Test
    fun testLrcLineAppearsAtConfiguredEarlyDisplayBoundary() {
        val lyricsData = LyricsData(
            songId = "lrc_lead_in",
            lines = listOf(LyricLine(timeMs = 3000L, text = "early lyric line")),
            isSynced = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 1500L,
                songOffsetMs = 0L,
                config = LyricsDisplayConfig(
                    formatMode = com.example.data.models.LyricsFormatMode.LRC,
                    lrcDisplayLeadInMs = 1500
                ),
                onSeekTo = {},
                onOffsetChange = {}
            )
        }

        // The line has completed its overlap fade and is visible 1.2 seconds before its source timestamp.
        composeTestRule.onNodeWithText("early lyric line").assertIsDisplayed()
    }

    @Test
    fun testMultipleVisibleLinesRendering() {
        val config = LyricsDisplayConfig(
            visibleLines = 5,
            textCase = LyricTextCase.TITLE_CASE
        )

        val lyricsData = LyricsData(
            songId = "test_5_lines",
            lines = listOf(
                LyricLine(timeMs = 1000L, text = "line 1"),
                LyricLine(timeMs = 2000L, text = "line 2"),
                LyricLine(timeMs = 3000L, text = "line 3 active"),
                LyricLine(timeMs = 4000L, text = "line 4"),
                LyricLine(timeMs = 5000L, text = "line 5")
            ),
            isSynced = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 3050L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOpenSettings = {},
                onOffsetChange = {}
            )
        }

        // Verify all 5 lines are rendered
        composeTestRule.onNodeWithText("Line 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Line 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Line 3 Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Line 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Line 5").assertIsDisplayed()
    }
}
