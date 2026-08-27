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
import com.example.data.models.LyricTextCase
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.formatLyricText
import com.example.data.models.toTitleCase
import com.example.ui.lyrics.LyricsSettingsDialog
import com.example.ui.lyrics.LyricsView
import com.example.ui.theme.SuperSaleFontFamily
import com.example.ui.theme.toComposeFontFamily
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
    fun testFontFamilyMappingForAllOptions() {
        val defaultFont = LyricFontFamily.DEFAULT.toComposeFontFamily()
        val monoFont = LyricFontFamily.MONOSPACE.toComposeFontFamily()
        val serifFont = LyricFontFamily.SERIF.toComposeFontFamily()
        val cursiveFont = LyricFontFamily.CURSIVE.toComposeFontFamily()
        val superSaleFont = LyricFontFamily.SUPER_SALE.toComposeFontFamily()

        assertEquals(androidx.compose.ui.text.font.FontFamily.Default, defaultFont)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, monoFont)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Serif, serifFont)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Cursive, cursiveFont)
        assertEquals(SuperSaleFontFamily, superSaleFont)
    }

    @Test
    fun testLyricsSettingsDialogFullSectionsAndTitleCaseSelection() {
        var savedConfig: LyricsDisplayConfig? = null
        val initialConfig = LyricsDisplayConfig(
            fontFamily = LyricFontFamily.DEFAULT,
            textCase = LyricTextCase.ORIGINAL,
            currentLineFontSizeSp = 24f,
            otherLineFontSizeSp = 15f,
            otherLinesOpacity = 0.35f
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
        composeTestRule.onNodeWithText("Font").assertExists()
        composeTestRule.onNodeWithText("Text Case").assertExists()
        composeTestRule.onNodeWithText("Current Line").assertExists()
        composeTestRule.onNodeWithText("Previous & Next Lines").assertExists()
        composeTestRule.onNodeWithText("Other").assertExists()

        // Verify all 5 font family options exist
        composeTestRule.onNodeWithText("Default").assertExists()
        composeTestRule.onNodeWithText("Monospace").assertExists()
        composeTestRule.onNodeWithText("Serif").assertExists()
        composeTestRule.onNodeWithText("Cursive").assertExists()
        composeTestRule.onNodeWithText("Super Sale").assertExists()

        // Verify all 4 text case options exist
        composeTestRule.onNodeWithText("Original").assertExists()
        composeTestRule.onNodeWithText("UPPERCASE").assertExists()
        composeTestRule.onNodeWithText("lowercase").assertExists()
        composeTestRule.onNodeWithText("Title Case").assertExists()

        // Select Title Case and Super Sale
        composeTestRule.onNodeWithText("Super Sale").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Title Case").performScrollTo().performClick()

        // Apply changes
        composeTestRule.onNodeWithText("Apply").performScrollTo().performClick()

        // Check saved config
        assertEquals(LyricFontFamily.SUPER_SALE, savedConfig?.fontFamily)
        assertEquals(LyricTextCase.TITLE_CASE, savedConfig?.textCase)
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
