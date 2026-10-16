package com.example.lyrics

import com.example.data.models.LyricsData
import com.example.data.models.LyricsFormatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFormatSwitchingTest {

    @Test
    fun testFormatDetectionPriorityAndCaseInsensitivity() {
        // Priority check: .elrc.lrc must be detected as ELRC even though it ends with .lrc
        assertEquals(LyricsFormatMode.ELRC, LyricsFormatUtils.detectFormat("Adele - All I Ask.elrc.lrc"))
        assertEquals(LyricsFormatMode.ELRC, LyricsFormatUtils.detectFormat("Song.ELRC.LRC"))
        assertEquals(LyricsFormatMode.ELRC, LyricsFormatUtils.detectFormat("/media/Adele/Adele - All I Ask.elrc.lrc"))

        // Standard LRC check
        assertEquals(LyricsFormatMode.LRC, LyricsFormatUtils.detectFormat("Adele - All I Ask.lrc"))
        assertEquals(LyricsFormatMode.LRC, LyricsFormatUtils.detectFormat("Song.LRC"))
        assertEquals(LyricsFormatMode.LRC, LyricsFormatUtils.detectFormat("/media/Adele/Adele - All I Ask.lrc"))

        assertTrue(LyricsFormatUtils.isElrcFile("Track.elrc.lrc"))
        assertFalse(LyricsFormatUtils.isElrcFile("Track.lrc"))

        assertTrue(LyricsFormatUtils.isLrcFile("Track.lrc"))
        assertFalse(LyricsFormatUtils.isLrcFile("Track.elrc.lrc"))
    }

    @Test
    fun testBaseSongMatchingExtractsIdenticalKey() {
        val elrcPath = "/media/Adele/Adele - All I Ask.elrc.lrc"
        val lrcPath = "/media/Adele/Adele - All I Ask.lrc"
        val audioPath = "/media/Adele/Adele - All I Ask.mp3"

        val baseElrc = LyricsFormatUtils.extractBaseSongName(elrcPath)
        val baseLrc = LyricsFormatUtils.extractBaseSongName(lrcPath)
        val baseAudio = LyricsFormatUtils.extractBaseSongName(audioPath)

        assertEquals("Adele - All I Ask", baseElrc)
        assertEquals("Adele - All I Ask", baseLrc)
        assertEquals("Adele - All I Ask", baseAudio)
        assertEquals(baseElrc, baseLrc)
    }

    @Test
    fun testElrcModeParsesRealWordTimestamps() {
        val elrcContent = """
            [00:14.15] <00:14.15>I <00:14.50>will <00:14.90>leave <00:15.30>my <00:15.70>heart
            [00:18.20] <00:18.20>at <00:18.50>the <00:18.80>door
        """.trimIndent()

        val parsedElrc = LyricsParser.parse(
            rawText = elrcContent,
            songId = "4416",
            songDurationMs = 240000L,
            mode = LyricsFormatMode.ELRC
        )

        assertTrue(parsedElrc.isSynced)
        assertTrue(parsedElrc.hasWordSync)
        assertEquals(2, parsedElrc.lines.size)

        val firstLine = parsedElrc.lines[0]
        assertEquals("I will leave my heart", firstLine.text)
        assertTrue(firstLine.hasWordTimestamps)
        assertEquals(5, firstLine.words.size)
        assertEquals("I", firstLine.words[0].word)
        assertEquals(14150L, firstLine.words[0].startTimeMs)
        assertEquals(14500L, firstLine.words[0].endTimeMs)
    }

    @Test
    fun testLrcModeStripsInlineWordTagsAndDisablesWordSync() {
        val elrcContent = """
            [00:14.15] <00:14.15>I <00:14.50>will <00:14.90>leave <00:15.30>my <00:15.70>heart
            [00:18.20] <00:18.20>at <00:18.50>the <00:18.80>door
        """.trimIndent()

        val parsedLrc = LyricsParser.parse(
            rawText = elrcContent,
            songId = "4416",
            songDurationMs = 240000L,
            mode = LyricsFormatMode.LRC
        )

        assertTrue(parsedLrc.isSynced)
        assertFalse(parsedLrc.hasWordSync)
        assertEquals(2, parsedLrc.lines.size)

        val firstLine = parsedLrc.lines[0]
        assertEquals("I will leave my heart", firstLine.text)
        assertFalse(firstLine.hasWordTimestamps)
        assertTrue(firstLine.words.isEmpty())
        assertEquals(14150L, firstLine.timeMs)
    }

    @Test
    fun testStandardLrcInElrcModeDoesNotGenerateFakeWords() {
        val standardLrc = """
            [00:14.15]I will leave my heart
            [00:18.20]at the door
        """.trimIndent()

        val parsed = LyricsParser.parse(
            rawText = standardLrc,
            songId = "4433",
            songDurationMs = 240000L,
            mode = LyricsFormatMode.ELRC
        )

        assertTrue(parsed.isSynced)
        assertFalse(parsed.hasWordSync)
        assertEquals(2, parsed.lines.size)

        val firstLine = parsed.lines[0]
        assertEquals("I will leave my heart", firstLine.text)
        assertFalse(firstLine.hasWordTimestamps)
        assertTrue(firstLine.words.isEmpty())
    }
}
