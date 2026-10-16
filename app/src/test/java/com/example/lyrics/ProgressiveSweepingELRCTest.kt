package com.example.lyrics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.models.ELRC_HIGHLIGHT_MODE_PROGRESSIVE_SWEEPING
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricLine
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.ui.lyrics.LyricsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressiveSweepingELRCTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testIndependentLrcAndElrcOffsetsInConfig() {
        val config = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.ELRC,
            lrcOffsetMs = -400L,
            elrcOffsetMs = 250L,
            manualOffsetMs = 250L
        )

        // When in ELRC mode, effectiveFormatManualOffsetMs returns elrcOffsetMs
        assertEquals(250L, config.effectiveFormatManualOffsetMs)

        // When switched to LRC mode, effectiveFormatManualOffsetMs returns lrcOffsetMs
        val lrcConfig = config.copy(formatMode = LyricsFormatMode.LRC)
        assertEquals(-400L, lrcConfig.effectiveFormatManualOffsetMs)
        assertEquals(ELRC_HIGHLIGHT_MODE_PROGRESSIVE_SWEEPING, config.elrcHighlightMode)
    }

    @Test
    fun testElrcProgressiveSweepingRendersCenteredWithoutDiscreteStates() {
        val elrcContent = """
            [00:02.00]<00:02.00>Never <00:02.60>mind <00:03.10>I'll <00:03.50>find <00:04.00>someone
            [00:06.00]<00:06.00>like <00:06.50>you
        """.trimIndent()
        val lyricsData = LyricsParser.parse(
            rawText = elrcContent,
            songId = "song_progressive_test",
            songDurationMs = 240000L,
            mode = LyricsFormatMode.ELRC
        )

        val config = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.ELRC,
            alignment = LyricAlignment.START, // Even if set to START, eLRC must remain centered
            highlightCurrentLine = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 2800L, // Actively in the middle of word "mind"
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = true
            )
        }

        // Test tag for the eLRC FlowRow container exists
        composeTestRule.onNodeWithTag("synchronized_elrc_row", useUnmergedTree = true).assertExists()

        // Words are displayed without discrete background tags or scale distortion
        composeTestRule.onAllNodesWithText("Never", useUnmergedTree = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("mind", useUnmergedTree = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("I'll", useUnmergedTree = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("find", useUnmergedTree = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("someone", useUnmergedTree = true).onFirst().assertExists()
    }

    @Test
    fun testElrcWrappedLinePreservesStructure() {
        val longElrcLine = "[00:01.00]<00:01.00>This <00:01.30>is <00:01.60>a <00:02.00>very <00:02.30>long <00:02.60>lyric <00:03.00>line <00:03.30>that <00:03.60>wraps <00:04.00>across <00:04.30>multiple <00:04.60>rows <00:05.00>smoothly"
        val lyricsData = LyricsParser.parse(
            rawText = longElrcLine,
            songId = "long_elrc",
            songDurationMs = 240000L,
            mode = LyricsFormatMode.ELRC
        )

        val config = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.ELRC,
            highlightCurrentLine = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 3100L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = true
            )
        }

        composeTestRule.onNodeWithTag("synchronized_elrc_row", useUnmergedTree = true).assertExists()
        composeTestRule.onAllNodesWithText("wraps", useUnmergedTree = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("smoothly", useUnmergedTree = true).onFirst().assertExists()
    }
}
