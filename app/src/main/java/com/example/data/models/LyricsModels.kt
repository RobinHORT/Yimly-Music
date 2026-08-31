package com.example.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LyricWord(
    @Json(name = "word") val word: String,
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long
)

@JsonClass(generateAdapter = true)
data class LyricLine(
    @Json(name = "time_ms") val timeMs: Long,
    @Json(name = "text") val text: String,
    @Json(name = "end_time_ms") val endTimeMs: Long? = null,
    @Json(name = "words") val words: List<LyricWord> = emptyList(),
    @Json(name = "has_word_timestamps") val hasWordTimestamps: Boolean = false
)

@JsonClass(generateAdapter = true)
data class LyricsData(
    @Json(name = "song_id") val songId: String,
    @Json(name = "is_synced") val isSynced: Boolean = true,
    @Json(name = "has_word_sync") val hasWordSync: Boolean = false,
    @Json(name = "lines") val lines: List<LyricLine> = emptyList(),
    @Json(name = "plain_lyrics") val plainLyrics: String? = null,
    @Json(name = "offset_ms") val offsetMs: Long = 0L
)

enum class LyricsFormatMode(val displayName: String) {
    ELRC("eLRC"),
    LRC("LRC")
}

enum class LyricFontFamily(val displayName: String) {
    DEFAULT("Default"),
    MONOSPACE("Monospace"),
    SERIF("Serif"),
    CURSIVE("Cursive"),
    SUPER_SALE("Super Sale")
}

enum class LyricTextCase(val displayName: String) {
    ORIGINAL("Original"),
    UPPERCASE("UPPERCASE"),
    LOWERCASE("lowercase"),
    TITLE_CASE("Title Case")
}

enum class LyricAlignment(val displayName: String) {
    START("Left"),
    CENTER("Center"),
    END("Right")
}

fun toTitleCase(input: String): String {
    if (input.isEmpty()) return input
    val result = StringBuilder()
    var capitalizeNext = true
    val lower = input.lowercase()
    for (i in lower.indices) {
        val ch = lower[i]
        if (ch.isWhitespace()) {
            result.append(ch)
            capitalizeNext = true
        } else if (capitalizeNext && ch.isLetter()) {
            result.append(ch.titlecase())
            capitalizeNext = false
        } else {
            result.append(ch)
        }
    }
    return result.toString()
}

fun formatLyricText(text: String, textCase: LyricTextCase): String = when (textCase) {
    LyricTextCase.ORIGINAL -> text
    LyricTextCase.UPPERCASE -> text.uppercase()
    LyricTextCase.LOWERCASE -> text.lowercase()
    LyricTextCase.TITLE_CASE -> toTitleCase(text)
}

data class LyricsDisplayConfig(
    // Lyrics Format Mode (eLRC: Word-level sync vs LRC: Line-level sync)
    val formatMode: LyricsFormatMode = LyricsFormatMode.ELRC,

    // Lyrics Display
    val visibleLines: Int = 3,
    val autoScroll: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val animationDurationMs: Int = 250,
    val lineSpacingDp: Float = 16f,
    val alignment: LyricAlignment = LyricAlignment.CENTER,

    // Font
    val fontFamily: LyricFontFamily = LyricFontFamily.DEFAULT,

    // Text Case
    val textCase: LyricTextCase = LyricTextCase.ORIGINAL,

    // Current Line
    val currentLineFontSizeSp: Float = 24f,
    val currentLineColorHex: String = "#FF3366",
    val fontWeightBold: Boolean = true,

    // Previous & Next Lines
    val otherLineFontSizeSp: Float = 15f,
    val otherLinesOpacity: Float = 0.35f,

    // Other
    val lyricsOverlay: Boolean = false,
    val manualOffsetMs: Long = 0L
)
