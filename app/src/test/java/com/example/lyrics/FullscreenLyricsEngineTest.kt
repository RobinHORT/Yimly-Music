package com.example.lyrics

import com.example.data.models.LyricLine
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullscreenLyricsEngineTest {

    @Test
    fun testInitialFiveSectionPopulationAndUpcomingVisibility() {
        val lines = (0..11).map { i ->
            LyricLine(
                timeMs = (i * 2000L) + 1000L,
                text = "Line $i",
                endTimeMs = (i * 2000L) + 3000L
            )
        }

        // Before first line starts (e.g. position 500ms)
        // Section 0 gets line 0, but position < line.timeMs (1000L) -> should be invisible (alpha 0f)
        val slot0Before = LyricsParser.calculateRotatingSectionSlot(lines, section = 0, sectionCount = 5, currentPositionMs = 500L)
        assertNotNull(slot0Before.line)
        assertEquals("Line 0", slot0Before.line?.text)
        assertFalse(slot0Before.isVisible)
        assertEquals(0f, slot0Before.alpha)

        // At position 1000ms (Line 0 starts) -> should be visible (alpha 1f)
        val slot0Active = LyricsParser.calculateRotatingSectionSlot(lines, section = 0, sectionCount = 5, currentPositionMs = 1000L)
        assertEquals("Line 0", slot0Active.line?.text)
        assertTrue(slot0Active.isVisible)
        assertEquals(1f, slot0Active.alpha)
    }

    @Test
    fun testSequentialRotationThroughTenLinesAndSectionReplacement() {
        val lines = (0..12).map { i ->
            LyricLine(
                timeMs = (i * 2000L) + 1000L,
                text = "Line $i",
                endTimeMs = (i * 2000L) + 3000L
            )
        }

        // Section 0 receives lines 0, 5, 10
        // At pos 1000ms -> Line 0 (section 0)
        val s0_at0 = LyricsParser.calculateRotatingSectionSlot(lines, section = 0, sectionCount = 5, currentPositionMs = 1000L)
        assertEquals("Line 0", s0_at0.line?.text)

        // At pos 11000ms -> Line 5 (section 0: 5 % 5 == 0, timeMs = 5 * 2000 + 1000 = 11000ms)
        val s0_at5 = LyricsParser.calculateRotatingSectionSlot(lines, section = 0, sectionCount = 5, currentPositionMs = 11000L)
        assertEquals("Line 5", s0_at5.line?.text)
        assertTrue(s0_at5.isVisible)

        // At pos 21000ms -> Line 10 (section 0: 10 % 5 == 0, timeMs = 10 * 2000 + 1000 = 21000ms)
        val s0_at10 = LyricsParser.calculateRotatingSectionSlot(lines, section = 0, sectionCount = 5, currentPositionMs = 21000L)
        assertEquals("Line 10", s0_at10.line?.text)
        assertTrue(s0_at10.isVisible)
    }

    @Test
    fun testSyncOffsetAdjustmentsInRotatingEngine() {
        val lines = listOf(
            LyricLine(timeMs = 5000L, text = "Delayed Line")
        )

        // Position 4500ms with offset 600ms -> effective pos 5100ms -> line is active
        val slotWithOffset = LyricsParser.calculateRotatingSectionSlot(
            lines = lines,
            section = 0,
            sectionCount = 5,
            currentPositionMs = 4500L,
            effectiveOffsetMs = 600L
        )
        assertEquals("Delayed Line", slotWithOffset.line?.text)
        assertTrue(slotWithOffset.isVisible)
        assertEquals(1f, slotWithOffset.alpha)
    }

    @Test
    fun testMissingWordTimestampsResilience() {
        val rawLrc = "[00:01.00]Plain LRC line without words"
        val parsed = LyricsParser.parse(rawLrc, "song_plain", mode = LyricsFormatMode.ELRC)
        assertFalse(parsed.lines.first().hasWordTimestamps)
        assertTrue(parsed.lines.first().words.isEmpty())
        assertEquals("Plain LRC line without words", parsed.lines.first().text)
    }
}
