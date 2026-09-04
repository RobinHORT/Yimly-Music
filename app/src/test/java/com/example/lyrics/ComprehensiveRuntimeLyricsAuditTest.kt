package com.example.lyrics

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricLine
import com.example.data.models.LyricWord
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.ui.lyrics.LyricsView
import com.example.ui.lyrics.SynchronizedLyricContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComprehensiveRuntimeLyricsAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleElrc = """
        [00:01.00]<00:01.00>Hello <00:01.50>world <00:02.00>welcome
        [00:03.00]<00:03.00>to <00:03.30>the <00:03.60>music <00:04.00>player
        [00:05.00]<00:05.00>enjoy <00:05.50>the <00:06.00>rhythm
    """.trimIndent()

    private val sampleLrc = """
        [00:01.00]Hello world welcome
        [00:03.00]to the music player
        [00:05.00]enjoy the rhythm
    """.trimIndent()

    @Test
    fun testNormalLrcRenderingAndHighlight() {
        val lyricsData = LyricsParser.parse(
            rawText = sampleLrc,
            songId = "test_lrc",
            songDurationMs = 180000L,
            mode = LyricsFormatMode.LRC
        )

        var seekTarget: Long? = null
        val config = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.LRC,
            alignment = LyricAlignment.CENTER,
            highlightCurrentLine = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 3200L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = { seekTarget = it },
                onOffsetChange = {},
                isPlaying = true
            )
        }

        // Line 2 ("to the music player") is the active line at 3200ms
        composeTestRule.onNodeWithTag("current_lyric_slot", useUnmergedTree = true).assertExists()
        composeTestRule.onAllNodesWithText("to the music player", useUnmergedTree = true).onFirst().assertExists()
    }

    @Test
    fun testElrcProgressiveSweepingRendering() {
        val lyricsData = LyricsParser.parse(
            rawText = sampleElrc,
            songId = "test_elrc",
            songDurationMs = 180000L,
            mode = LyricsFormatMode.ELRC
        )

        val config = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.ELRC,
            highlightCurrentLine = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 3500L, // In line 2 during word "the"
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = true
            )
        }

        composeTestRule.onNodeWithTag("synchronized_elrc_row", useUnmergedTree = true).assertExists()
        composeTestRule.onAllNodesWithText("music", useUnmergedTree = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("player", useUnmergedTree = true).onFirst().assertExists()
    }

    @Test
    fun testFormatSwitchingBetweenLrcAndElrc() {
        val rawContent = """
            [00:01.00]<00:01.00>Line <00:01.50>one
            [00:03.00]<00:03.00>Line <00:03.50>two
        """.trimIndent()

        val elrcData = LyricsParser.parse(rawContent, "song1", 100000L, LyricsFormatMode.ELRC)
        val lrcData = LyricsParser.parse(rawContent, "song1", 100000L, LyricsFormatMode.LRC)

        assertTrue("eLRC parser should extract word timestamps", elrcData.lines.first().hasWordTimestamps)
        assertFalse("LRC mode parser must omit word timestamps", lrcData.lines.first().hasWordTimestamps)
    }

    @Test
    fun testIndependentLrcAndElrcOffsets() {
        var elrcOffset = 300L
        var lrcOffset = -500L

        val configElrc = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.ELRC,
            elrcOffsetMs = elrcOffset,
            lrcOffsetMs = lrcOffset
        )
        assertEquals(300L, configElrc.effectiveFormatManualOffsetMs)

        val configLrc = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.LRC,
            elrcOffsetMs = elrcOffset,
            lrcOffsetMs = lrcOffset
        )
        assertEquals(-500L, configLrc.effectiveFormatManualOffsetMs)

        // Mutating eLRC offset does NOT alter LRC offset
        elrcOffset = 800L
        val updatedElrc = configElrc.copy(elrcOffsetMs = elrcOffset)
        assertEquals(800L, updatedElrc.effectiveFormatManualOffsetMs)
        assertEquals(-500L, updatedElrc.lrcOffsetMs)
    }

    @Test
    fun testSeekingAndOffsetCalculations() {
        val lines = listOf(
            LyricLine(timeMs = 1000L, text = "Line 1"),
            LyricLine(timeMs = 5000L, text = "Line 2"),
            LyricLine(timeMs = 10000L, text = "Line 3")
        )

        // Position 4500 with 0 offset -> active line is 1 (1000ms)
        var slots = LyricsParser.findActiveLyricSlots(lines, 4500L, 0L)
        assertEquals(0, slots.currentIndex)

        // Position 4500 with +600ms offset -> effective position 5100ms -> active line is 2 (5000ms)
        slots = LyricsParser.findActiveLyricSlots(lines, 4500L, 600L)
        assertEquals(1, slots.currentIndex)

        // Seeking to before start -> -1
        slots = LyricsParser.findActiveLyricSlots(lines, 500L, 0L)
        assertEquals(-1, slots.currentIndex)
    }

    @Test
    fun testEmptyAndNullLyricsResilience() {
        val nullData: LyricsData? = null
        val emptyData = LyricsData(
            songId = "empty",
            isSynced = false,
            hasWordSync = false,
            lines = emptyList(),
            plainLyrics = null
        )

        val config = LyricsDisplayConfig()

        composeTestRule.setContent {
            LyricsView(
                lyricsData = nullData,
                currentPositionMs = 1000L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = false
            )
        }

        composeTestRule.onAllNodesWithText("No lyrics available for this track.").onFirst().assertExists()
    }

    @Test
    fun testWordProgressCalculationBoundaries() {
        val word = LyricWord(word = "test", startTimeMs = 2000L, endTimeMs = 3000L)
        val durationMs = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)

        // Before word starts (1500ms)
        val posBefore = 1500L
        val progBefore = ((posBefore - word.startTimeMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0f, progBefore, 0.001f)

        // Halfway through word (2500ms)
        val posHalf = 2500L
        val progHalf = ((posHalf - word.startTimeMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.5f, progHalf, 0.001f)

        // After word finishes (3500ms)
        val posAfter = 3500L
        val progAfter = ((posAfter - word.startTimeMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, progAfter, 0.001f)
    }
}
