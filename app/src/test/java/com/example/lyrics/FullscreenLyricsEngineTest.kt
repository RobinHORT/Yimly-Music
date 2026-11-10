package com.example.lyrics

import com.example.data.models.LyricLine
import com.example.data.models.LyricsFormatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullscreenLyricsEngineTest {

    @Test
    fun testMissingWordTimestampsResilience() {
        val rawLrc = "[00:01.00]Plain LRC line without words"
        val parsed = LyricsParser.parse(rawLrc, "song_plain", mode = LyricsFormatMode.ELRC)
        assertFalse(parsed.lines.first().hasWordTimestamps)
        assertTrue(parsed.lines.first().words.isEmpty())
        assertEquals("Plain LRC line without words", parsed.lines.first().text)
    }
}
