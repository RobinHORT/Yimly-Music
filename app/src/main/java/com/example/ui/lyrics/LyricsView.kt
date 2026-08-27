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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricFontFamily
import com.example.data.models.LyricTextCase
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.lyrics.LyricsParser
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.YimlyPinkGlow

@Composable
fun LyricsView(
    lyricsData: LyricsData?,
    currentPositionMs: Long,
    songOffsetMs: Long,
    config: LyricsDisplayConfig,
    onSeekTo: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOffsetChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(0) } // 0 = Three Slot Focus Mode, 1 = Full Synced List

    val lines = lyricsData?.lines ?: emptyList()
    val slotState = remember(lines, currentPositionMs, songOffsetMs) {
        LyricsParser.findActiveLyricSlots(lines, currentPositionMs, songOffsetMs)
    }

    val composeFontFamily = when (config.fontFamily) {
        LyricFontFamily.MONOSPACE -> FontFamily.Monospace
        LyricFontFamily.SERIF -> FontFamily.Serif
        LyricFontFamily.CURSIVE -> FontFamily.Cursive
        LyricFontFamily.DEFAULT -> FontFamily.Default
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

    fun formatText(text: String): String = when (config.textCase) {
        LyricTextCase.UPPERCASE -> text.uppercase()
        LyricTextCase.LOWERCASE -> text.lowercase()
        LyricTextCase.ORIGINAL -> text
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("lyrics_view")
    ) {
        // Controls Bar (Offset +/- / Settings / Mode toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher Tabs
            TabRow(
                selectedTabIndex = viewMode,
                modifier = Modifier
                    .width(200.dp)
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

            // Offset Adjuster & Settings Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Timing Offset Buttons
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

                    Text(
                        text = if (songOffsetMs == 0L) "Sync" else String.format("%+.1fs", songOffsetMs / 1000f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (songOffsetMs != 0L) YimlyPink else TextSecondaryDark,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { onOffsetChange(songOffsetMs + 200L) },
                        modifier = Modifier.size(28.dp).testTag("offset_plus_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+0.2s", tint = TextPrimaryDark, modifier = Modifier.size(14.dp))
                    }

                    if (songOffsetMs != 0L) {
                        IconButton(
                            onClick = { onOffsetChange(0L) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset offset", tint = YimlyPink, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevatedDark)
                        .border(1.dp, SurfaceBorderDark, CircleShape)
                        .testTag("lyrics_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Lyrics Settings",
                        tint = YimlyPink,
                        modifier = Modifier.size(18.dp)
                    )
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
            // Three-Slot Focus Mode: Previous, CURRENT, Next
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignmentModifier,
                    verticalArrangement = Arrangement.spacedBy(config.lineSpacingDp.dp)
                ) {
                    // 1. Previous Line
                    Text(
                        text = formatText(slotState.previousLine?.text ?: "•••"),
                        fontSize = config.otherLineFontSizeSp.sp,
                        fontFamily = composeFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimaryDark.copy(alpha = config.otherLinesOpacity),
                        textAlign = textAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = slotState.previousLine != null) {
                                slotState.previousLine?.let { onSeekTo(it.timeMs) }
                            }
                    )

                    // 2. CURRENT LINE (Dominant, Highlighted with Yimly Pink)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, YimlyPink.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .clickable(enabled = slotState.currentLine != null) {
                                slotState.currentLine?.let { onSeekTo(it.timeMs) }
                            }
                            .testTag("current_lyric_slot"),
                        colors = CardDefaults.cardColors(
                            containerColor = YimlyPinkGlow.copy(alpha = 0.25f)
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
                            Text(
                                text = formatText(slotState.currentLine?.text ?: "♪ Music playing ♪"),
                                fontSize = config.currentLineFontSizeSp.sp,
                                fontFamily = composeFontFamily,
                                fontWeight = if (config.fontWeightBold) FontWeight.ExtraBold else FontWeight.Bold,
                                color = YimlyPink,
                                textAlign = textAlign,
                                lineHeight = (config.currentLineFontSizeSp * 1.3f).sp
                            )
                        }
                    }

                    // 3. Next Line
                    Text(
                        text = formatText(slotState.nextLine?.text ?: "•••"),
                        fontSize = config.otherLineFontSizeSp.sp,
                        fontFamily = composeFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimaryDark.copy(alpha = config.otherLinesOpacity),
                        textAlign = textAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = slotState.nextLine != null) {
                                slotState.nextLine?.let { onSeekTo(it.timeMs) }
                            }
                    )
                }
            }
        } else {
            // Full Synced List with Auto-scroll
            val listState = rememberLazyListState()

            LaunchedEffect(slotState.currentIndex) {
                if (slotState.currentIndex >= 0 && slotState.currentIndex < lines.size) {
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
                    val animColor by animateColorAsState(
                        targetValue = if (isCurrent) YimlyPink else TextPrimaryDark,
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
                        Text(
                            text = formatText(line.text),
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
