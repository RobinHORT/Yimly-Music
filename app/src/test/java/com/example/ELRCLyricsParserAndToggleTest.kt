package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.lyrics.LyricsParser
import com.example.ui.lyrics.LyricsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ELRCLyricsParserAndToggleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testParseEnhancedLRCWordTimestamps() {
        val elrcContent = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:10.00]<00:10.00>Never <00:10.50>gonna <00:11.00>give <00:11.50>you <00:12.00>up
            [00:13.00]<00:13.00>Never <00:13.40>gonna <00:13.80>let <00:14.20>you <00:14.60>down
        """.trimIndent()

        val parsed = LyricsParser.parse(elrcContent, "song_123", 180000L)

        assertTrue("Parsed lyrics should be synced", parsed.isSynced)
        assertTrue("Parsed lyrics should have word-level sync", parsed.hasWordSync)
        assertEquals(2, parsed.lines.size)

        val line1 = parsed.lines[0]
        assertEquals("Never gonna give you up", line1.text)
        assertEquals(10000L, line1.timeMs)
        assertTrue(line1.hasWordTimestamps)
        assertEquals(5, line1.words.size)

        assertEquals("Never", line1.words[0].word)
        assertEquals(10000L, line1.words[0].startTimeMs)
        assertEquals(10500L, line1.words[0].endTimeMs)

        assertEquals("gonna", line1.words[1].word)
        assertEquals(10500L, line1.words[1].startTimeMs)
        assertEquals(11000L, line1.words[1].endTimeMs)

        assertEquals("up", line1.words[4].word)
        assertEquals(12000L, line1.words[4].startTimeMs)
    }

    @Test
    fun testParseStandardLRCWithoutFakeWords() {
        val standardLrc = """
            [00:05.00]First line of standard song
            [00:10.00]Second line of standard song
        """.trimIndent()

        val parsed = LyricsParser.parse(standardLrc, "song_standard", 180000L)

        assertTrue(parsed.isSynced)
        assertFalse(parsed.hasWordSync) // No explicit word tags
        assertEquals(2, parsed.lines.size)

        val line1 = parsed.lines[0]
        assertEquals("First line of standard song", line1.text)
        assertFalse(line1.hasWordTimestamps)
        assertTrue(line1.words.isEmpty()) // No fabricated fake word timestamps
    }

    @Test
    fun testLyricsViewRendersBothELRCAndLRCModes() {
        val elrcContent = """
            [00:02.00]<00:02.00>Hello <00:02.50>beautiful <00:03.00>world
            [00:06.00]<00:06.00>Goodbye <00:06.50>dark <00:07.00>night
        """.trimIndent()
        val lyricsData = LyricsParser.parse(elrcContent, "song_test")

        var currentConfig = LyricsDisplayConfig(
            formatMode = LyricsFormatMode.ELRC,
            highlightCurrentLine = true
        )

        composeTestRule.setContent {
            LyricsView(
                lyricsData = lyricsData,
                currentPositionMs = 2600L, // In the middle of "beautiful"
                songOffsetMs = 0L,
                config = currentConfig,
                onSeekTo = {},
                onOffsetChange = {},
                onToggleFormatMode = {
                    currentConfig = currentConfig.copy(
                        formatMode = if (currentConfig.formatMode == LyricsFormatMode.ELRC) LyricsFormatMode.LRC else LyricsFormatMode.ELRC
                    )
                }
            )
        }

        composeTestRule.onNodeWithTag("lyrics_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_lyric_slot").assertIsDisplayed()
    }
}
