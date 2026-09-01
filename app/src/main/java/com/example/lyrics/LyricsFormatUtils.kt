package com.example.lyrics

import com.example.data.models.LyricsFormatMode

object LyricsFormatUtils {

    /**
     * Determines whether a path or filename corresponds to eLRC (*.elrc.lrc) or LRC (*.lrc).
     * CRITICAL: Must check .elrc.lrc before .lrc, case-insensitively.
     */
    fun detectFormat(pathOrFilename: String?): LyricsFormatMode? {
        if (pathOrFilename.isNullOrBlank()) return null
        val lower = pathOrFilename.lowercase().trim()
        return when {
            lower.endsWith(".elrc.lrc") || lower.endsWith(".elrc") -> LyricsFormatMode.ELRC
            lower.endsWith(".lrc") -> LyricsFormatMode.LRC
            else -> null
        }
    }

    /**
     * Checks if a filename or path is an eLRC file (*.elrc.lrc).
     */
    fun isElrcFile(pathOrFilename: String?): Boolean {
        if (pathOrFilename.isNullOrBlank()) return false
        val lower = pathOrFilename.lowercase().trim()
        return lower.endsWith(".elrc.lrc") || lower.endsWith(".elrc")
    }

    /**
     * Checks if a filename or path is a standard LRC file (*.lrc, but NOT *.elrc.lrc).
     */
    fun isLrcFile(pathOrFilename: String?): Boolean {
        if (pathOrFilename.isNullOrBlank()) return false
        val lower = pathOrFilename.lowercase().trim()
        return lower.endsWith(".lrc") && !lower.endsWith(".elrc.lrc")
    }

    /**
     * Extracts the base song name by removing lyric or audio extensions.
     * e.g., "Adele - All I Ask.elrc.lrc" -> "Adele - All I Ask"
     *       "Adele - All I Ask.lrc"      -> "Adele - All I Ask"
     *       "Adele - All I Ask.mp3"      -> "Adele - All I Ask"
     */
    fun extractBaseSongName(filenameOrPath: String): String {
        val name = filenameOrPath.substringAfterLast('/').substringAfterLast('\\').trim()
        val lower = name.lowercase()
        return when {
            lower.endsWith(".elrc.lrc") -> name.dropLast(9)
            lower.endsWith(".elrc") -> name.dropLast(5)
            lower.endsWith(".lrc") -> name.dropLast(4)
            lower.endsWith(".mp3") -> name.dropLast(4)
            lower.endsWith(".flac") -> name.dropLast(5)
            lower.endsWith(".wav") -> name.dropLast(4)
            lower.endsWith(".m4a") -> name.dropLast(4)
            lower.endsWith(".ogg") -> name.dropLast(4)
            else -> name
        }
    }

    /**
     * Checks if raw text contains actual word-level timing tags (e.g. <00:14.15>word).
     */
    fun hasWordTimingTags(rawLyrics: String?): Boolean {
        if (rawLyrics.isNullOrBlank()) return false
        return Regex("[<\\(]\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,3})?[>\\)]").containsMatchIn(rawLyrics)
    }

    /**
     * Converts eLRC raw content into clean standard LRC by removing all inline word tags.
     */
    fun convertElrcToLrc(rawElrc: String?): String {
        if (rawElrc.isNullOrBlank()) return ""
        val wordTagRegex = Regex("[<\\(]\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,3})?[>\\)]")
        return rawElrc.lines().joinToString("\n") { line ->
            if (line.startsWith("[")) {
                val bracketEnd = line.indexOf(']')
                if (bracketEnd != -1) {
                    val header = line.substring(0, bracketEnd + 1)
                    val body = line.substring(bracketEnd + 1).replace(wordTagRegex, "").replace(Regex("\\s+"), " ").trim()
                    if (body.isNotBlank()) "$header $body" else header
                } else {
                    line.replace(wordTagRegex, "").replace(Regex("\\s+"), " ").trim()
                }
            } else {
                line.replace(wordTagRegex, "").replace(Regex("\\s+"), " ").trim()
            }
        }
    }
}
