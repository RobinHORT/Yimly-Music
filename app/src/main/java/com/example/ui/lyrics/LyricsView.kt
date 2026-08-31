package com.example.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricLine
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.data.models.formatLyricText
import com.example.lyrics.LyricsParser
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.toComposeFontFamily

@Composable
fun LyricsView(
    lyricsData: LyricsData?,
    currentPositionMs: Long,
    songOffsetMs: Long,
    config: LyricsDisplayConfig,
    onSeekTo: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOffsetChange: (Long) -> Unit,
    onToggleFormatMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(0) } // 0 = Focus Mode, 1 = Full Synced List

    val lines = lyricsData?.lines ?: emptyList()
    val slotState = remember(lines, currentPositionMs, songOffsetMs) {
        LyricsParser.findActiveLyricSlots(lines, currentPositionMs, songOffsetMs)
    }

    val composeFontFamily = config.fontFamily.toComposeFontFamily()

    val currentLineColor = remember(config.currentLineColorHex) {
        try {
            Color(android.graphics.Color.parseColor(config.currentLineColorHex))
        } catch (e: Exception) {
            YimlyPink
        }
    }

    val textAlign = when (config.alignment) {
        LyricAlignment.START -> TextAlign.Start
        LyricAlignment.CENTER -> TextAlign.Center
        LyricAlignment.END -> TextAlign.End
    }

    val alignmentModifier = when (config.alignment) {
        LyricAlignment.START -> Alignment.Start
        LyricAlignment.CENTER -> Alignment.CenterHorizontally
        LyricAlignment.END -> Alignment.End
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("lyrics_view")
    ) {
        // Controls Bar (Offset +/- / Mode Switcher / Format Indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher Tabs (Focus | Full View)
            TabRow(
                selectedTabIndex = viewMode,
                modifier = Modifier
                    .width(180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(10.dp)),
                containerColor = SurfaceCardDark,
                contentColor = YimlyPink,
                indicator = { tabPositions ->
                    if (viewMode < tabPositions.size) {
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[viewMode]),
                            color = YimlyPink,
                            height = 3.dp
                        )
                    }
                }
            ) {
                Tab(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    text = { Text("Focus", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selectedContentColor = YimlyPink,
                    unselectedContentColor = TextSecondaryDark
                )
                Tab(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    text = { Text("Full View", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selectedContentColor = YimlyPink,
                    unselectedContentColor = TextSecondaryDark
                )
            }

            // Right side: Format pill & Timing Offset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Format Mode indicator / toggle pill in lyrics view
                if (onToggleFormatMode != null) {
                    Surface(
                        onClick = onToggleFormatMode,
                        shape = RoundedCornerShape(12.dp),
                        color = if (config.formatMode == LyricsFormatMode.ELRC) YimlyPink.copy(alpha = 0.2f) else SurfaceElevatedDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (config.formatMode == LyricsFormatMode.ELRC) YimlyPink else SurfaceBorderDark
                        ),
                        modifier = Modifier.testTag("lyrics_view_format_mode_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = if (config.formatMode == LyricsFormatMode.ELRC) YimlyPink else TextSecondaryDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = config.formatMode.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (config.formatMode == LyricsFormatMode.ELRC) YimlyPink else TextSecondaryDark
                            )
                        }
                    }
                }

                // Timing Offset Buttons (− Sync +)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceElevatedDark)
                            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onOffsetChange(songOffsetMs - 200L) },
                            modifier = Modifier.size(28.dp).testTag("offset_minus_btn")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-0.2s", tint = TextPrimaryDark, modifier = Modifier.size(14.dp))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOffsetChange(0L) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("lyrics_sync_label"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (songOffsetMs == 0L) "Sync" else String.format(java.util.Locale.US, "%+.1fs", songOffsetMs / 1000f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (songOffsetMs != 0L) YimlyPink else TextSecondaryDark
                            )
                        }

                        IconButton(
                            onClick = { onOffsetChange(songOffsetMs + 200L) },
                            modifier = Modifier.size(28.dp).testTag("offset_plus_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+0.2s", tint = TextPrimaryDark, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        if (lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lyricsData?.plainLyrics ?: "No lyrics available for this track.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondaryDark,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        } else if (viewMode == 0) {
            // Focus Mode: Supports configurable visible lines (1, 3, 5, 7)
            val radius = ((config.visibleLines - 1) / 2).coerceAtLeast(0)
            val currentIndex = slotState.currentIndex

            val previousLines = if (currentIndex >= 0 && radius > 0) {
                (1..radius).mapNotNull { offset ->
                    val idx = currentIndex - (radius - offset + 1)
                    if (idx >= 0) lines.getOrNull(idx) else null
                }
            } else emptyList()

            val currentLine = if (currentIndex >= 0) lines.getOrNull(currentIndex) else null

            val nextLines = if (currentIndex >= 0 && radius > 0) {
                (1..radius).mapNotNull { offset ->
                    lines.getOrNull(currentIndex + offset)
                }
            } else if (currentIndex == -1) {
                lines.take(config.visibleLines)
            } else emptyList()

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignmentModifier,
                    verticalArrangement = Arrangement.spacedBy(config.lineSpacingDp.dp)
                ) {
                    // Previous Lines (Shared Other Line Size & Opacity)
                    previousLines.forEach { prev ->
                        Text(
                            text = formatLyricText(prev.text, config.textCase),
                            fontSize = config.otherLineFontSizeSp.sp,
                            fontFamily = composeFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = TextPrimaryDark.copy(alpha = config.otherLinesOpacity),
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(prev.timeMs) }
                        )
                    }

                    // CURRENT LINE (Independently Styled with Word-Level or Line-Level synchronization)
                    if (config.highlightCurrentLine) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, currentLineColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                .clickable(enabled = currentLine != null) {
                                    currentLine?.let { onSeekTo(it.timeMs) }
                                }
                                .testTag("current_lyric_slot"),
                            colors = CardDefaults.cardColors(
                                containerColor = currentLineColor.copy(alpha = 0.20f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                contentAlignment = when (config.alignment) {
                                    LyricAlignment.START -> Alignment.CenterStart
                                    LyricAlignment.CENTER -> Alignment.Center
                                    LyricAlignment.END -> Alignment.CenterEnd
                                }
                            ) {
                                if (currentLine != null) {
                                    SynchronizedLyricContent(
                                        line = currentLine,
                                        isCurrent = true,
                                        currentPositionMs = currentPositionMs,
                                        songOffsetMs = songOffsetMs,
                                        config = config,
                                        currentLineColor = currentLineColor,
                                        composeFontFamily = composeFontFamily,
                                        textAlign = textAlign
                                    )
                                } else {
                                    Text(
                                        text = formatLyricText("♪ Music playing ♪", config.textCase),
                                        fontSize = config.currentLineFontSizeSp.sp,
                                        fontFamily = composeFontFamily,
                                        fontWeight = if (config.fontWeightBold) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = currentLineColor,
                                        textAlign = textAlign,
                                        lineHeight = (config.currentLineFontSizeSp * 1.3f).sp
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = currentLine != null) {
                                    currentLine?.let { onSeekTo(it.timeMs) }
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                .testTag("current_lyric_slot"),
                            contentAlignment = when (config.alignment) {
                                LyricAlignment.START -> Alignment.CenterStart
                                LyricAlignment.CENTER -> Alignment.Center
                                LyricAlignment.END -> Alignment.CenterEnd
                            }
                        ) {
                            if (currentLine != null) {
                                SynchronizedLyricContent(
                                    line = currentLine,
                                    isCurrent = true,
                                    currentPositionMs = currentPositionMs,
                                    songOffsetMs = songOffsetMs,
                                    config = config,
                                    currentLineColor = TextPrimaryDark,
                                    composeFontFamily = composeFontFamily,
                                    textAlign = textAlign
                                )
                            } else {
                                Text(
                                    text = formatLyricText("♪ Music playing ♪", config.textCase),
                                    fontSize = config.currentLineFontSizeSp.sp,
                                    fontFamily = composeFontFamily,
                                    fontWeight = if (config.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                    color = TextPrimaryDark,
                                    textAlign = textAlign,
                                    lineHeight = (config.currentLineFontSizeSp * 1.3f).sp
                                )
                            }
                        }
                    }

                    // Next Lines (Shared Other Line Size & Opacity)
                    nextLines.forEach { next ->
                        Text(
                            text = formatLyricText(next.text, config.textCase),
                            fontSize = config.otherLineFontSizeSp.sp,
                            fontFamily = composeFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = TextPrimaryDark.copy(alpha = config.otherLinesOpacity),
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(next.timeMs) }
                        )
                    }
                }
            }
        } else {
            // Full Synced List with optional Auto-scroll
            val listState = rememberLazyListState()

            LaunchedEffect(slotState.currentIndex, config.autoScroll) {
                if (config.autoScroll && slotState.currentIndex >= 0 && slotState.currentIndex < lines.size) {
                    val target = (slotState.currentIndex - 2).coerceAtLeast(0)
                    listState.animateScrollToItem(target)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(config.lineSpacingDp.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    val isCurrent = index == slotState.currentIndex
                    val targetColor = if (isCurrent) {
                        if (config.highlightCurrentLine) currentLineColor else TextPrimaryDark
                    } else TextPrimaryDark

                    val animColor by animateColorAsState(
                        targetValue = targetColor,
                        animationSpec = tween(config.animationDurationMs),
                        label = "lyricColor"
                    )
                    val animAlpha by animateFloatAsState(
                        targetValue = if (isCurrent) 1.0f else config.otherLinesOpacity,
                        animationSpec = tween(config.animationDurationMs),
                        label = "lyricAlpha"
                    )
                    val animSize by animateFloatAsState(
                        targetValue = if (isCurrent) config.currentLineFontSizeSp else config.otherLineFontSizeSp,
                        animationSpec = tween(config.animationDurationMs),
                        label = "lyricSize"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSeekTo(line.timeMs) }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        contentAlignment = when (config.alignment) {
                            LyricAlignment.START -> Alignment.CenterStart
                            LyricAlignment.CENTER -> Alignment.Center
                            LyricAlignment.END -> Alignment.CenterEnd
                        }
                    ) {
                        if (isCurrent && config.formatMode == LyricsFormatMode.ELRC && line.words.isNotEmpty()) {
                            SynchronizedLyricContent(
                                line = line,
                                isCurrent = true,
                                currentPositionMs = currentPositionMs,
                                songOffsetMs = songOffsetMs,
                                config = config,
                                currentLineColor = animColor,
                                composeFontFamily = composeFontFamily,
                                textAlign = textAlign
                            )
                        } else {
                            Text(
                                text = formatLyricText(line.text, config.textCase),
                                fontSize = animSize.sp,
                                fontFamily = composeFontFamily,
                                fontWeight = if (isCurrent && config.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                color = animColor,
                                modifier = Modifier.alpha(animAlpha),
                                textAlign = textAlign
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a lyric line with word-by-word real-time highlight synchronization when in eLRC mode,
 * or uniform whole-line highlight when in LRC mode.
 */
@Composable
fun SynchronizedLyricContent(
    line: LyricLine,
    isCurrent: Boolean,
    currentPositionMs: Long,
    songOffsetMs: Long,
    config: LyricsDisplayConfig,
    currentLineColor: Color,
    composeFontFamily: FontFamily,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val adjustedPos = currentPositionMs + songOffsetMs

    if (config.formatMode == LyricsFormatMode.ELRC && line.words.isNotEmpty()) {
        // Word-level real-time synchronization (eLRC)
        val annotatedString = buildAnnotatedString {
            line.words.forEachIndexed { index, word ->
                val wordText = formatLyricText(word.word, config.textCase)
                val suffix = if (index < line.words.size - 1) " " else ""

                when {
                    adjustedPos >= word.endTimeMs -> {
                        // Word already sung: vibrant filled active color
                        withStyle(
                            SpanStyle(
                                color = currentLineColor,
                                fontWeight = if (config.fontWeightBold) FontWeight.ExtraBold else FontWeight.Bold
                            )
                        ) {
                            append(wordText + suffix)
                        }
                    }
                    adjustedPos >= word.startTimeMs && adjustedPos < word.endTimeMs -> {
                        // Actively singing word RIGHT NOW: glowing prominent active highlight
                        withStyle(
                            SpanStyle(
                                color = Color.White,
                                background = currentLineColor.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Black
                            )
                        ) {
                            append(wordText)
                        }
                        if (suffix.isNotEmpty()) {
                            append(suffix)
                        }
                    }
                    else -> {
                        // Upcoming word in line: softer dimmed tone
                        withStyle(
                            SpanStyle(
                                color = currentLineColor.copy(alpha = 0.40f),
                                fontWeight = FontWeight.Normal
                            )
                        ) {
                            append(wordText + suffix)
                        }
                    }
                }
            }
        }

        Text(
            text = annotatedString,
            fontSize = config.currentLineFontSizeSp.sp,
            fontFamily = composeFontFamily,
            textAlign = textAlign,
            lineHeight = (config.currentLineFontSizeSp * 1.35f).sp,
            modifier = modifier
        )
    } else {
        // Standard line-level synchronization (LRC)
        Text(
            text = formatLyricText(line.text, config.textCase),
            fontSize = config.currentLineFontSizeSp.sp,
            fontFamily = composeFontFamily,
            fontWeight = if (config.fontWeightBold) FontWeight.ExtraBold else FontWeight.Bold,
            color = currentLineColor,
            textAlign = textAlign,
            lineHeight = (config.currentLineFontSizeSp * 1.3f).sp,
            modifier = modifier
        )
    }
}
