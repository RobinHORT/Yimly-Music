package com.example.lyrics

import com.example.data.models.LyricLine
import com.example.data.models.LyricsData
import java.util.regex.Pattern

object LyricsParser {

    private val LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?](.*)")
    private val OFFSET_TAG_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)

    /**
     * Parses an LRC string into structured LyricsData.
     * If no timestamps are present, generates spaced lyric lines across songDurationMs.
     */
    fun parse(rawText: String?, songId: String, songDurationMs: Long = 180000L): LyricsData {
        if (rawText.isNullOrBlank()) {
            return LyricsData(
                songId = songId,
                isSynced = false,
                lines = emptyList(),
                plainLyrics = "No lyrics available for this track."
            )
        }

        val lines = rawText.lines()
        val parsedLines = mutableListOf<LyricLine>()
        var offsetMs = 0L

        // Check for offset tag
        for (line in lines) {
            val offsetMatcher = OFFSET_TAG_PATTERN.matcher(line.trim())
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || trimmed.startsWith("[al:")) {
                continue
            }

            // Extract all timestamps in the line (e.g. [01:23.45][02:34.56] Chorus)
            val matcher = LRC_LINE_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3)
                val millis = when {
                    fracStr == null -> 0L
                    fracStr.length == 1 -> fracStr.toLong() * 100L
                    fracStr.length == 2 -> fracStr.toLong() * 10L
                    else -> fracStr.take(3).toLong()
                }
                val text = matcher.group(4)?.trim().orEmpty()
                val timestamp = (min * 60 * 1000) + (sec * 1000) + millis + offsetMs

                if (text.isNotBlank()) {
                    parsedLines.add(LyricLine(timeMs = timestamp, text = text))
                }
            }
        }

        // If we found valid LRC timestamped lines
        if (parsedLines.isNotEmpty()) {
            val sorted = parsedLines.sortedBy { it.timeMs }
            // Populate end times for smoother transitions
            val withEndTimes = sorted.mapIndexed { index, item ->
                val nextTime = sorted.getOrNull(index + 1)?.timeMs ?: (item.timeMs + 5000L)
                item.copy(endTimeMs = nextTime)
            }
            return LyricsData(
                songId = songId,
                isSynced = true,
                lines = withEndTimes,
                plainLyrics = withEndTimes.joinToString("\n") { it.text },
                offsetMs = offsetMs
            )
        }

        // Fallback: plain text without timestamps
        val cleanPlainLines = lines
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") }

        if (cleanPlainLines.isEmpty()) {
            return LyricsData(
                songId = songId,
                isSynced = false,
                lines = emptyList(),
                plainLyrics = rawText
            )
        }

        // Generate synthetic timestamps for nice progressive highlighting
        val stepMs = if (songDurationMs > 0 && cleanPlainLines.isNotEmpty()) {
            (songDurationMs * 0.85 / cleanPlainLines.size).toLong().coerceAtLeast(3000L)
        } else {
            4000L
        }

        val syntheticLines = cleanPlainLines.mapIndexed { index, text ->
            val start = (index * stepMs) + 2000L
            val end = start + stepMs
            LyricLine(timeMs = start, text = text, endTimeMs = end)
        }

        return LyricsData(
            songId = songId,
            isSynced = true,
            lines = syntheticLines,
            plainLyrics = cleanPlainLines.joinToString("\n"),
            offsetMs = 0L
        )
    }

    /**
     * Given current position and list of lines, finds:
     * - currentIndex
     * - previousLine
     * - currentLine
     * - nextLine
     */
    fun findActiveLyricSlots(
        lines: List<LyricLine>,
        currentPositionMs: Long,
        manualOffsetMs: Long = 0L
    ): LyricSlotState {
        if (lines.isEmpty()) {
            return LyricSlotState(
                currentIndex = -1,
                previousLine = null,
                currentLine = null,
                nextLine = null
            )
        }

        val adjustedPos = currentPositionMs + manualOffsetMs
        var activeIndex = -1

        for (i in lines.indices) {
            val line = lines[i]
            if (adjustedPos >= line.timeMs) {
                val nextLineTime = lines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE
                if (adjustedPos < nextLineTime) {
                    activeIndex = i
                    break
                }
                activeIndex = i
            } else if (i == 0 && adjustedPos < line.timeMs) {
                // Before first line
                return LyricSlotState(
                    currentIndex = -1,
                    previousLine = null,
                    currentLine = null,
                    nextLine = lines.firstOrNull()
                )
            }
        }

        val prev = if (activeIndex > 0) lines.getOrNull(activeIndex - 1) else null
        val current = if (activeIndex >= 0) lines.getOrNull(activeIndex) else null
        val next = if (activeIndex >= 0) lines.getOrNull(activeIndex + 1) else lines.firstOrNull()

        return LyricSlotState(
            currentIndex = activeIndex,
            previousLine = prev,
            currentLine = current,
            nextLine = next
        )
    }
}

data class LyricSlotState(
    val currentIndex: Int,
    val previousLine: LyricLine?,
    val currentLine: LyricLine?,
    val nextLine: LyricLine?
)
