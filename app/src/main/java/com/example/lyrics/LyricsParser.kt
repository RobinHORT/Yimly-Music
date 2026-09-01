package com.example.lyrics

import com.example.data.models.LyricLine
import com.example.data.models.LyricWord
import com.example.data.models.LyricsData
import java.util.regex.Pattern

object LyricsParser {

    private val LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?](.*)")
    private val OFFSET_TAG_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)
    private val INLINE_WORD_TAG_REGEX = Regex("[<\\(](\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?[>\\)]")

    /**
     * Parses an LRC or eLRC string into structured LyricsData with word-level or line-level timestamps.
     * When mode is ELRC: real inline word tags are parsed into LyricWord. If none exist, no fake word timestamps are generated.
     * When mode is LRC: inline word tags are stripped, and line-level timestamps control synchronization.
     */
    fun parse(
        rawText: String?,
        songId: String,
        songDurationMs: Long = 180000L,
        mode: com.example.data.models.LyricsFormatMode = com.example.data.models.LyricsFormatMode.ELRC
    ): LyricsData {
        if (rawText.isNullOrBlank()) {
            return LyricsData(
                songId = songId,
                isSynced = false,
                hasWordSync = false,
                lines = emptyList(),
                plainLyrics = "No lyrics available for this track."
            )
        }

        val lines = rawText.lines()
        var offsetMs = 0L

        // Check for offset tag [offset:+/-ms]
        for (line in lines) {
            val offsetMatcher = OFFSET_TAG_PATTERN.matcher(line.trim())
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
            }
        }

        val rawParsedLines = mutableListOf<Pair<Long, String>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[re:") ||
                trimmed.startsWith("[ve:") || trimmed.startsWith("[length:")
            ) {
                continue
            }

            // Extract line timestamps (e.g. [01:23.45] text or [01:23.45][02:34.56] text)
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
                val lineContent = matcher.group(4)?.trim().orEmpty()
                val timestamp = (min * 60 * 1000) + (sec * 1000) + millis + offsetMs

                if (lineContent.isNotBlank()) {
                    rawParsedLines.add(Pair(timestamp, lineContent))
                }
            }
        }

        // If we found valid LRC/eLRC timestamped lines
        if (rawParsedLines.isNotEmpty()) {
            val sorted = rawParsedLines.sortedBy { it.first }
            val finalLines = mutableListOf<LyricLine>()

            for (index in sorted.indices) {
                val (lineStartMs, rawContent) = sorted[index]
                val nextStartMs = sorted.getOrNull(index + 1)?.first ?: (lineStartMs + 5000L)
                val lineEndMs = if (nextStartMs > lineStartMs) nextStartMs else (lineStartMs + 4000L)

                if (mode == com.example.data.models.LyricsFormatMode.ELRC) {
                    val (cleanText, words, hasWordTags) = parseWordsFromLine(
                        rawContent = rawContent,
                        lineStartMs = lineStartMs,
                        lineEndMs = lineEndMs,
                        offsetMs = offsetMs
                    )

                    if (cleanText.isNotBlank()) {
                        finalLines.add(
                            LyricLine(
                                timeMs = lineStartMs,
                                text = cleanText,
                                endTimeMs = lineEndMs,
                                words = words,
                                hasWordTimestamps = hasWordTags
                            )
                        )
                    }
                } else {
                    // LRC mode: Line-level sync only. Clean out any inline word tags.
                    val cleanText = stripWordTags(rawContent)
                    if (cleanText.isNotBlank()) {
                        finalLines.add(
                            LyricLine(
                                timeMs = lineStartMs,
                                text = cleanText,
                                endTimeMs = lineEndMs,
                                words = emptyList(),
                                hasWordTimestamps = false
                            )
                        )
                    }
                }
            }

            if (finalLines.isNotEmpty()) {
                val hasAnyWordTags = mode == com.example.data.models.LyricsFormatMode.ELRC && finalLines.any { it.hasWordTimestamps }
                return LyricsData(
                    songId = songId,
                    isSynced = true,
                    hasWordSync = hasAnyWordTags,
                    lines = finalLines,
                    plainLyrics = finalLines.joinToString("\n") { it.text },
                    offsetMs = offsetMs
                )
            }
        }

        // Fallback: plain text without timestamps
        val cleanPlainLines = lines
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") }

        if (cleanPlainLines.isEmpty()) {
            return LyricsData(
                songId = songId,
                isSynced = false,
                hasWordSync = false,
                lines = emptyList(),
                plainLyrics = rawText
            )
        }

        val stepMs = if (songDurationMs > 0 && cleanPlainLines.isNotEmpty()) {
            (songDurationMs * 0.85 / cleanPlainLines.size).toLong().coerceAtLeast(3000L)
        } else {
            4000L
        }

        val syntheticLines = cleanPlainLines.mapIndexed { index, text ->
            val start = (index * stepMs) + 2000L
            val end = start + stepMs
            val cleanText = stripWordTags(text)
            LyricLine(
                timeMs = start,
                text = cleanText,
                endTimeMs = end,
                words = emptyList(),
                hasWordTimestamps = false
            )
        }

        return LyricsData(
            songId = songId,
            isSynced = false,
            hasWordSync = false,
            lines = syntheticLines,
            plainLyrics = cleanPlainLines.joinToString("\n"),
            offsetMs = 0L
        )
    }

    fun stripWordTags(rawContent: String): String {
        return rawContent.replace(INLINE_WORD_TAG_REGEX, "").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Parses real word-level timestamps from an eLRC line.
     * Does NOT generate fake word timestamps if no tags exist.
     */
    fun parseWordsFromLine(
        rawContent: String,
        lineStartMs: Long,
        lineEndMs: Long,
        offsetMs: Long
    ): Triple<String, List<LyricWord>, Boolean> {
        val matches = INLINE_WORD_TAG_REGEX.findAll(rawContent).toList()

        if (matches.isNotEmpty()) {
            val words = mutableListOf<LyricWord>()
            val parsedTags = mutableListOf<Pair<Long, Int>>() // timestamp, matchIndex

            for (match in matches) {
                val min = match.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
                val sec = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
                val fracStr = match.groupValues.getOrNull(3)
                val millis = when {
                    fracStr.isNullOrEmpty() -> 0L
                    fracStr.length == 1 -> fracStr.toLong() * 100L
                    fracStr.length == 2 -> fracStr.toLong() * 10L
                    else -> fracStr.take(3).toLong()
                }
                val wordTimeMs = (min * 60 * 1000) + (sec * 1000) + millis + offsetMs
                parsedTags.add(Pair(wordTimeMs, match.range.last + 1))
            }

            for (i in parsedTags.indices) {
                val (wordStartMs, startIndex) = parsedTags[i]
                val nextTag = parsedTags.getOrNull(i + 1)
                val rawWordSnippet = if (nextTag != null) {
                    val nextMatchStart = matches[i + 1].range.first
                    if (nextMatchStart > startIndex) {
                        rawContent.substring(startIndex, nextMatchStart)
                    } else ""
                } else {
                    if (startIndex < rawContent.length) {
                        rawContent.substring(startIndex)
                    } else ""
                }

                val cleanWord = rawWordSnippet.replace(Regex("[<\\(].*?[>\\)]"), "").trim()
                val nextStart = nextTag?.first ?: lineEndMs
                val wordEndMs = if (nextStart > wordStartMs) nextStart else (wordStartMs + 600L)

                if (cleanWord.isNotBlank()) {
                    words.add(LyricWord(word = cleanWord, startTimeMs = wordStartMs, endTimeMs = wordEndMs))
                }
            }

            val cleanLineText = stripWordTags(rawContent)
            if (words.isNotEmpty()) {
                return Triple(cleanLineText, words, true)
            }
        }

        // Standard LRC Line (No inline eLRC tags): Do NOT fabricate word timestamps
        val cleanLineText = stripWordTags(rawContent)
        return Triple(cleanLineText, emptyList(), false)
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

