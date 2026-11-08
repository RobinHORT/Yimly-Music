package com.example.lyrics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.models.LyricLine
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.data.models.Song
import com.example.lyrics.LyricsParser
import com.example.ui.lyrics.LyricsView
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
class LyricsAuditFixesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testWordTimestampMonotonicityAndNoArtificialDuration() {
        // Line with out-of-order and duplicate timestamps
        val rawLine = "<00:02.00>WordA <00:01.50>WordB <00:03.00>WordC <00:03.00>WordD"
        val (cleanText, words, hasTags) = LyricsParser.parseWordsFromLine(
            rawContent = rawLine,
            lineStartMs = 2000L,
            lineEndMs = 5000L,
            offsetMs = 0L
        )

        assertTrue(hasTags)
        assertEquals("WordA WordB WordC WordD", cleanText)
        assertEquals(4, words.size)

        // WordA starts at 2000
        assertEquals("WordA", words[0].word)
        assertEquals(2000L, words[0].startTimeMs)

        // WordB originally 1500, clamped monotonically to >= 2000
        assertEquals("WordB", words[1].word)
        assertTrue("WordB start time must be >= WordA start time", words[1].startTimeMs >= words[0].startTimeMs)
        assertEquals(2000L, words[1].startTimeMs)

        // WordC starts at 3000
        assertEquals("WordC", words[2].word)
        assertEquals(3000L, words[2].startTimeMs)
        assertEquals(3000L, words[2].endTimeMs) // Next word also at 3000

        // WordD is final word at 3000, bounded by lineEndMs = 5000
        assertEquals("WordD", words[3].word)
        assertEquals(3000L, words[3].startTimeMs)
        assertEquals(5000L, words[3].endTimeMs)
    }

    @Test
    fun testWordTimestampSingleWordNoArtificialDuration() {
        val rawLine = "<00:01.00>Solo"
        val (_, words, _) = LyricsParser.parseWordsFromLine(
            rawContent = rawLine,
            lineStartMs = 1000L,
            lineEndMs = 1000L,
            offsetMs = 0L
        )
        assertEquals(1, words.size)
        assertEquals("Solo", words[0].word)
        assertEquals(1000L, words[0].startTimeMs)
        assertEquals(1000L, words[0].endTimeMs)
    }

    @Test
    fun testBinarySearchFindActiveLyricSlots() {
        val lines = listOf(
            LyricLine(text = "Line 0", timeMs = 1000L),
            LyricLine(text = "Line 1", timeMs = 3000L),
            LyricLine(text = "Line 2", timeMs = 5000L),
            LyricLine(text = "Line 3", timeMs = 5000L), // Duplicate timestamp
            LyricLine(text = "Line 4", timeMs = 8000L)
        )

        // Empty lines
        val emptySlots = LyricsParser.findActiveLyricSlots(emptyList(), 2000L)
        assertEquals(-1, emptySlots.currentIndex)
        assertNull(emptySlots.currentLine)

        // Before first line
        val before = LyricsParser.findActiveLyricSlots(lines, currentPositionMs = 500L)
        assertEquals(-1, before.currentIndex)
        assertNull(before.previousLine)
        assertNull(before.currentLine)
        assertEquals("Line 0", before.nextLine?.text)

        // Exactly at first line
        val atFirst = LyricsParser.findActiveLyricSlots(lines, currentPositionMs = 1000L)
        assertEquals(0, atFirst.currentIndex)
        assertEquals("Line 0", atFirst.currentLine?.text)
        assertNull(atFirst.previousLine)
        assertEquals("Line 1", atFirst.nextLine?.text)

        // Between lines
        val between = LyricsParser.findActiveLyricSlots(lines, currentPositionMs = 2500L)
        assertEquals(0, between.currentIndex)
        assertEquals("Line 0", between.currentLine?.text)

        // Duplicate timestamp: should resolve to the latest match
        val atDup = LyricsParser.findActiveLyricSlots(lines, currentPositionMs = 5000L)
        assertEquals(3, atDup.currentIndex)
        assertEquals("Line 3", atDup.currentLine?.text)
        assertEquals("Line 2", atDup.previousLine?.text)
        assertEquals("Line 4", atDup.nextLine?.text)

        // Beyond last line
        val beyond = LyricsParser.findActiveLyricSlots(lines, currentPositionMs = 10000L)
        assertEquals(4, beyond.currentIndex)
        assertEquals("Line 4", beyond.currentLine?.text)
        assertEquals("Line 3", beyond.previousLine?.text)
        assertNull(beyond.nextLine)
    }

    @Test
    fun testEffectiveOffsetCalculation() {
        val lines = listOf(
            LyricLine(text = "First", timeMs = 5000L),
            LyricLine(text = "Second", timeMs = 10000L)
        )

        // Position 4500 with songOffset 600 -> effective pos 5100 -> line 0 active
        val slotWithOffset = LyricsParser.findActiveLyricSlots(
            lines = lines,
            currentPositionMs = 4500L,
            manualOffsetMs = 600L
        )
        assertEquals(0, slotWithOffset.currentIndex)
        assertEquals("First", slotWithOffset.currentLine?.text)

        // Position 5200 with negative offset -500 -> effective pos 4700 -> before first line
        val slotWithNegOffset = LyricsParser.findActiveLyricSlots(
            lines = lines,
            currentPositionMs = 5200L,
            manualOffsetMs = -500L
        )
        assertEquals(-1, slotWithNegOffset.currentIndex)
        assertNull(slotWithNegOffset.currentLine)
    }

    @Test
    fun testPreLyricsPlaybackStateDisplay() {
        val testLyrics = LyricsData(
            songId = "test_song",
            plainLyrics = "[00:10.00]First line",
            isSynced = true,
            hasWordSync = false,
            lines = listOf(LyricLine(text = "First line", timeMs = 10000L))
        )
        val config = LyricsDisplayConfig()

        // 1. Stopped (currentPositionMs == 0 and !isPlaying)
        composeTestRule.setContent {
            LyricsView(
                lyricsData = testLyrics,
                currentPositionMs = 0L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = false
            )
        }
        composeTestRule.onNodeWithText("♪ Music stopped ♪").assertIsDisplayed()
    }

    @Test
    fun testPreLyricsPausedPlaybackStateDisplay() {
        val testLyrics = LyricsData(
            songId = "test_song",
            plainLyrics = "[00:10.00]First line",
            isSynced = true,
            hasWordSync = false,
            lines = listOf(LyricLine(text = "First line", timeMs = 10000L))
        )
        val config = LyricsDisplayConfig()

        // 2. Paused (currentPositionMs > 0, before first lyric, !isPlaying)
        composeTestRule.setContent {
            LyricsView(
                lyricsData = testLyrics,
                currentPositionMs = 3000L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = false
            )
        }
        composeTestRule.onNodeWithText("♪ Music paused ♪").assertIsDisplayed()
    }

    @Test
    fun testPreLyricsPlayingPlaybackStateDisplay() {
        val testLyrics = LyricsData(
            songId = "test_song",
            plainLyrics = "[00:10.00]First line",
            isSynced = true,
            hasWordSync = false,
            lines = listOf(LyricLine(text = "First line", timeMs = 10000L))
        )
        val config = LyricsDisplayConfig()

        // 3. Actively Playing before first lyric
        composeTestRule.setContent {
            LyricsView(
                lyricsData = testLyrics,
                currentPositionMs = 3000L,
                songOffsetMs = 0L,
                config = config,
                onSeekTo = {},
                onOffsetChange = {},
                isPlaying = true
            )
        }
        composeTestRule.onNodeWithText("♪ Music playing ♪").assertIsDisplayed()
    }

    @Test
    fun testEffectiveElrcPathDetection() {
        val songWithElrcPath = Song(
            id = "song_elrc",
            title = "Title",
            artist = "Artist",
            album = "Album",
            elrcPath = "/lyrics/song_elrc.elrc"
        )
        assertTrue("effectiveElrcPath should be recognized as elrc file",
            LyricsFormatUtils.isElrcFile(songWithElrcPath.effectiveElrcPath))
        assertFalse("null lrc path should not be elrc",
            LyricsFormatUtils.isElrcFile(songWithElrcPath.effectiveLrcPath))
    }
}
