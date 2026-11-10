package com.example.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class ContinuousLyricsDisplayTest {

    @Test
    fun testViewportOpacityGradientCalculation() {
        // Top edge: 0.0 -> 0%
        assertEquals(0f, calculateViewportOpacity(0f, 0f, 1000f), 0.001f)
        // Upper quarter: 0.25 -> 50%
        assertEquals(0.5f, calculateViewportOpacity(250f, 0f, 1000f), 0.001f)
        // Centre: 0.5 -> 100%
        assertEquals(1.0f, calculateViewportOpacity(500f, 0f, 1000f), 0.001f)
        // Lower quarter: 0.75 -> 50%
        assertEquals(0.5f, calculateViewportOpacity(750f, 0f, 1000f), 0.001f)
        // Bottom edge: 1.0 -> 0%
        assertEquals(0f, calculateViewportOpacity(1000f, 0f, 1000f), 0.001f)
    }

    private fun calculateViewportOpacity(itemCenterY: Float, currentScrollY: Float, viewportHeightPx: Float): Float {
        if (viewportHeightPx <= 0f) return 1f
        val viewportRelativeY = itemCenterY - currentScrollY
        val normY = (viewportRelativeY / viewportHeightPx).coerceIn(0f, 1f)
        return when {
            normY <= 0f || normY >= 1f -> 0f
            normY < 0.25f -> (normY / 0.25f) * 0.5f
            normY < 0.5f -> 0.5f + ((normY - 0.25f) / 0.25f) * 0.5f
            normY < 0.75f -> 1.0f - ((normY - 0.5f) / 0.25f) * 0.5f
            else -> 0.5f - ((normY - 0.75f) / 0.25f) * 0.5f
        }
    }
}
