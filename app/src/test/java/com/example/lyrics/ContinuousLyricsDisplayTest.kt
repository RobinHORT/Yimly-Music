package com.example.lyrics

import com.example.ui.lyrics.continuousLyricsLineAlpha
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuousLyricsDisplayTest {

    @Test
    fun continuousLineOpacityFadesWithDistance() {
        val values = (0..4).map { continuousLyricsLineAlpha(it, 0.35f) }

        assertEquals(1f, values[0], 0.001f)
        assertEquals(0.35f, values[1], 0.001f)
        assertEquals(0.1925f, values[2], 0.001f)
        assertEquals(0.07f, values[3], 0.001f)
        assertEquals(0f, values[4], 0.001f)
    }

    @Test
    fun continuousLineOpacityClampsInvalidPreferenceValues() {
        assertEquals(1f, continuousLyricsLineAlpha(0, -1f), 0.001f)
        assertEquals(0.35f, continuousLyricsLineAlpha(1, -1f), 0.001f)
        assertEquals(0.85f, continuousLyricsLineAlpha(1, 2f), 0.001f)
    }
}
