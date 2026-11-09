package com.example.lyrics

import com.example.ui.lyrics.continuousLyricsLineAlpha
import com.example.ui.lyrics.continuousLyricsTransitionProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuousLyricsDisplayTest {

    @Test
    fun transitionProgressTracksTheActiveLyricTimeSlot() {
        assertEquals(0f, continuousLyricsTransitionProgress(1_000L, 1_000L, 3_000L), 0.001f)
        assertEquals(0.5f, continuousLyricsTransitionProgress(2_000L, 1_000L, 3_000L), 0.001f)
        assertEquals(1f, continuousLyricsTransitionProgress(3_500L, 1_000L, 3_000L), 0.001f)
    }

    @Test
    fun transitionProgressUsesSafeFallbackForFinalLine() {
        assertEquals(0.5f, continuousLyricsTransitionProgress(5_500L, 4_000L, null), 0.001f)
    }

    @Test
    fun previousCurrentAndIncomingLinesCrossfadeAsSpecified() {
        // At the beginning of a line slot.
        assertEquals(1f, continuousLyricsLineAlpha(4, 5, 0f), 0.001f)
        assertEquals(1f, continuousLyricsLineAlpha(5, 5, 0f), 0.001f)
        assertEquals(0f, continuousLyricsLineAlpha(6, 5, 0f), 0.001f)

        // Halfway through: current stays solid while previous and next crossfade.
        assertEquals(0.5f, continuousLyricsLineAlpha(4, 5, 0.5f), 0.001f)
        assertEquals(1f, continuousLyricsLineAlpha(5, 5, 0.5f), 0.001f)
        assertEquals(0.5f, continuousLyricsLineAlpha(6, 5, 0.5f), 0.001f)

        // At the end of the slot, previous is gone and incoming is fully visible.
        assertEquals(0f, continuousLyricsLineAlpha(4, 5, 1f), 0.001f)
        assertEquals(1f, continuousLyricsLineAlpha(5, 5, 1f), 0.001f)
        assertEquals(1f, continuousLyricsLineAlpha(6, 5, 1f), 0.001f)
    }

    @Test
    fun linesOutsideTransitionWindowAreHiddenAndProgressIsClamped() {
        assertEquals(0f, continuousLyricsLineAlpha(2, 5, 0.5f), 0.001f)
        assertEquals(0f, continuousLyricsLineAlpha(8, 5, 0.5f), 0.001f)
        assertEquals(0f, continuousLyricsLineAlpha(6, -1, 0.5f), 0.001f)
        assertEquals(0f, continuousLyricsLineAlpha(6, 5, -1f), 0.001f)
        assertEquals(1f, continuousLyricsLineAlpha(6, 5, 2f), 0.001f)
    }
}