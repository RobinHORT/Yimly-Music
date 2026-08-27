package com.example.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LyricLine(
    @Json(name = "time_ms") val timeMs: Long,
    @Json(name = "text") val text: String,
    @Json(name = "end_time_ms") val endTimeMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class LyricsData(
    @Json(name = "song_id") val songId: String,
    @Json(name = "is_synced") val isSynced: Boolean = true,
    @Json(name = "lines") val lines: List<LyricLine> = emptyList(),
    @Json(name = "plain_lyrics") val plainLyrics: String? = null,
    @Json(name = "offset_ms") val offsetMs: Long = 0L
)

enum class LyricFontFamily(val displayName: String) {
    DEFAULT("Sans Serif"),
    MONOSPACE("Monospace"),
    SERIF("Serif"),
    CURSIVE("Rounded")
}

enum class LyricTextCase(val displayName: String) {
    ORIGINAL("Original"),
    UPPERCASE("UPPERCASE"),
    LOWERCASE("lowercase")
}

enum class LyricAlignment(val displayName: String) {
    START("Left"),
    CENTER("Center"),
    END("Right")
}

data class LyricsDisplayConfig(
    val fontFamily: LyricFontFamily = LyricFontFamily.DEFAULT,
    val currentLineFontSizeSp: Float = 24f,
    val otherLineFontSizeSp: Float = 15f,
    val fontWeightBold: Boolean = true,
    val otherLinesOpacity: Float = 0.35f,
    val lineSpacingDp: Float = 16f,
    val textCase: LyricTextCase = LyricTextCase.ORIGINAL,
    val alignment: LyricAlignment = LyricAlignment.CENTER,
    val animationDurationMs: Int = 250,
    val manualOffsetMs: Long = 0L
)
