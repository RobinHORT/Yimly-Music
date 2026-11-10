package com.example.lyrics

import com.example.data.models.LyricLine
import com.example.data.models.LyricWord
import com.example.data.models.LyricsFormatMode
import com.example.ui.lyrics.fullscreenLyricTimestamp
import com.example.ui.lyrics.fullscreenLyricsSeekPosition
import com.example.ui.lyrics.fullscreenLyricsScrollProgress
import com.example.ui.lyrics.fullscreenLyricsPixelDelta
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class ContinuousLyricsDisplayTest {

    @Test
    fun testClockExtrapolation() {
        val basePosMs = 1000L
        val elapsedNanos = 500_000_000L // 500ms
        val speed = 1.0f
        
        val elapsedMs = (elapsedNanos.toDouble() / 1_000_000.0) * speed
        val animatedPosMs = basePosMs.toDouble() + elapsedMs
        
        assertEquals(1500.0, animatedPosMs, 0.001)
    }

    @Test
    fun testClockExtrapolationWithSpeed() {
        val basePosMs = 1000L
        val elapsedNanos = 500_000_000L // 500ms
        val speed = 1.5f
        
        val elapsedMs = (elapsedNanos.toDouble() / 1_000_000.0) * speed
        val animatedPosMs = basePosMs.toDouble() + elapsedMs
        
        assertEquals(1750.0, animatedPosMs, 0.001)
    }

    @Test
    fun testInterpolationFraction() {
        val t0 = 1000L
        val t1 = 2000L
        val adjustedPos = 1500.0
        
        val dt = (t1 - t0).coerceAtLeast(1L)
        val frac = ((adjustedPos - t0.toDouble()) / dt.toDouble()).coerceIn(0.0, 1.0).toFloat()
        
        assertEquals(0.5f, frac, 0.001f)
    }

    @Test
    fun testAnchorCalculationWithInitialSpacing() {
        val halfViewport = 500f
        val spacingPx = 20
        val h0 = 100
        val h1 = 120
        
        // Item 0
        // Column spacedBy puts spacing between children. Spacer is the first child.
        // First lyric item top = halfViewport + spacingPx
        var currentTop = halfViewport + spacingPx
        val centerY0 = currentTop + h0 / 2f
        val list0 = centerY0 - halfViewport
        
        assertEquals(spacingPx + h0 / 2f, list0, 0.001f)
        
        // Item 1
        currentTop += h0 + spacingPx
        val centerY1 = currentTop + h1 / 2f
        val list1 = centerY1 - halfViewport
        
        assertEquals(2 * spacingPx + h0 + h1 / 2f, list1, 0.001f)
    }
    @Test
    fun fullscreenLrcUsesLineTimestampAsScrollAnchor() {
        val line = LyricLine(timeMs = 2400L, text = "LRC line")
        assertEquals(2400L, fullscreenLyricTimestamp(line, LyricsFormatMode.LRC))
    }

    @Test
    fun fullscreenElrcTapSeeksToFirstWordTimestampAdjustedForOffset() {
        val line = LyricLine(
            timeMs = 2000L,
            text = "eLRC line",
            words = listOf(
                LyricWord("Hello", 2150L, 2600L),
                LyricWord("world", 2600L, 3200L)
            ),
            hasWordTimestamps = true
        )
        assertEquals(
            1900L,
            fullscreenLyricsSeekPosition(line, LyricsFormatMode.ELRC, 250L)
        )
    }

    @Test
    fun fullscreenLrcTapSeeksToLineTimestampAdjustedForOffset() {
        val line = LyricLine(timeMs = 2400L, text = "LRC line")
        assertEquals(
            2200L,
            fullscreenLyricsSeekPosition(line, LyricsFormatMode.LRC, 200L)
        )
    }

    @Test
    fun fullscreenLyricsSeekPositionNeverReturnsNegativeMediaPosition() {
        val line = LyricLine(
            timeMs = 100L,
            text = "Early line",
            words = listOf(LyricWord("Early", 150L, 400L)),
            hasWordTimestamps = true
        )
        assertEquals(
            0L,
            fullscreenLyricsSeekPosition(line, LyricsFormatMode.ELRC, 300L)
        )
    }

    @Test
    fun fullscreenElrcUsesFirstWordTimestampAsScrollAnchor() {
        val line = LyricLine(
            timeMs = 2000L,
            text = "eLRC line",
            words = listOf(
                LyricWord("Hello", 2150L, 2600L),
                LyricWord("world", 2600L, 3200L)
            ),
            hasWordTimestamps = true
        )
        assertEquals(2150L, fullscreenLyricTimestamp(line, LyricsFormatMode.ELRC))
    }

    @Test
    fun fullscreenScrollUsesExactTimestampInterval() {
        assertEquals(
            0.5f,
            fullscreenLyricsScrollProgress(3000.0, 2000L, 4000L),
            0.001f
        )
    }

    @Test
    fun fullscreenScrollClampsBeforeAndAfterInterval() {
        assertEquals(0f, fullscreenLyricsScrollProgress(1500.0, 2000L, 4000L), 0.001f)
        assertEquals(1f, fullscreenLyricsScrollProgress(4500.0, 2000L, 4000L), 0.001f)
    }

    @Test
    fun fullscreenScrollHandlesDuplicateOrReversedTimestampsSafely() {
        assertEquals(1f, fullscreenLyricsScrollProgress(2500.0, 2000L, 2000L), 0.001f)
        assertEquals(0f, fullscreenLyricsScrollProgress(2500.0, 3000L, 2000L), 0.001f)
    }

    @Test
    fun fullscreenPixelDeltaMovesOnlyTheProgressDifference() {
        assertEquals(25f, fullscreenLyricsPixelDelta(100f, 0.25f, 0.5f), 0.001f)
        assertEquals(50f, fullscreenLyricsPixelDelta(100f, 0.5f, 1f), 0.001f)
    }

    @Test
    fun fullscreenPixelDeltaSupportsReverseSeekCorrections() {
        assertEquals(-30f, fullscreenLyricsPixelDelta(100f, 0.8f, 0.5f), 0.001f)
    }

}
