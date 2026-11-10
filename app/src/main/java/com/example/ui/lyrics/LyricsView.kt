package com.example.ui.lyrics

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
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
import com.example.ui.theme.LTKaraoke

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
    isPlaying: Boolean = true,
    playbackSpeed: Float = 1.0f,
    showFocusControls: Boolean = true,
    floatingFocusMode: Boolean = false,
    rotatingSections: Boolean = false,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(0) } // 0 = Focus Mode, 1 = Full Synced List

    val lines = lyricsData?.lines ?: emptyList()
    val activeManualOffset = if (config.formatMode == LyricsFormatMode.ELRC) {
        if (config.elrcOffsetMs != 0L) config.elrcOffsetMs else config.manualOffsetMs
    } else {
        if (config.lrcOffsetMs != 0L) config.lrcOffsetMs else config.manualOffsetMs
    }
    val effectiveOffset = songOffsetMs + activeManualOffset
    val slotState = remember(lines, currentPositionMs, effectiveOffset) {
        LyricsParser.findActiveLyricSlots(lines, currentPositionMs, effectiveOffset)
    }

    val composeFontFamily = LTKaraoke

    val currentLineColor = remember(config.currentLineColorHex) {
        try {
            Color(android.graphics.Color.parseColor(config.currentLineColorHex))
        } catch (e: Exception) {
            YimlyPink
        }
    }

    // eLRC lyrics are permanently horizontally centered (single-line, multi-line, wrapped);
    // LRC lyrics respect user-configured alignment.
    val textAlign = if (config.formatMode == LyricsFormatMode.ELRC) {
        TextAlign.Center
    } else {
        when (config.alignment) {
            LyricAlignment.START -> TextAlign.Start
            LyricAlignment.CENTER -> TextAlign.Center
            LyricAlignment.END -> TextAlign.End
        }
    }

    val effectiveContentAlignment = if (config.formatMode == LyricsFormatMode.ELRC) {
        Alignment.Center
    } else {
        when (config.alignment) {
            LyricAlignment.START -> Alignment.CenterStart
            LyricAlignment.CENTER -> Alignment.Center
            LyricAlignment.END -> Alignment.CenterEnd
        }
    }

    val alignmentModifier = if (config.formatMode == LyricsFormatMode.ELRC) {
        Alignment.CenterHorizontally
    } else {
        when (config.alignment) {
            LyricAlignment.START -> Alignment.Start
            LyricAlignment.CENTER -> Alignment.CenterHorizontally
            LyricAlignment.END -> Alignment.End
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("lyrics_view")
    ) {
        if (showFocusControls) {
        // Controls Bar (Offset +/- / Mode Switcher / Format Indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume clicks to isolate offset controls from underlying views */ },
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

            // Right side: Timing Offset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume clicks */ }
            ) {
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
        }

        if (lines.isEmpty() && floatingFocusMode) {
            // Untimed/plain lyrics wrap naturally in the available focus area.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = lyricsData?.plainLyrics.orEmpty(),
                    fontSize = config.currentLineFontSizeSp.sp,
                    fontFamily = composeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = YimlyPink.copy(alpha = 0.96f),
                    textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().testTag("floating_focus_lyric")
                )
            }
        } else if (lines.isEmpty()) {
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
        } else if (viewMode == 0 && floatingFocusMode) {
            // Floating focus mode for the expanded player: one lyric line, no card,
            // border, panel, previous lines, or upcoming lines.
            val currentLine = slotState.currentIndex.takeIf { it >= 0 }?.let { lines.getOrNull(it) }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                currentLine?.let { line ->
                    if (config.formatMode == LyricsFormatMode.ELRC && line.hasWordTimestamps && line.words.isNotEmpty()) {
                        SynchronizedLyricContent(
                            line = line,
                            isCurrent = true,
                            currentPositionMs = currentPositionMs,
                            effectiveOffsetMs = effectiveOffset,
                            config = config,
                            currentLineColor = currentLineColor,
                            composeFontFamily = composeFontFamily,
                            textAlign = TextAlign.Center,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxWidth().testTag("floating_focus_lyric")
                        )
                    } else {
                        Text(
                            text = formatLyricText(line.text, config.textCase),
                            fontSize = config.currentLineFontSizeSp.sp,
                            fontFamily = composeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = currentLineColor.copy(alpha = 0.96f),
                            textAlign = TextAlign.Center,
                            lineHeight = (config.currentLineFontSizeSp * 1.2f).sp,
                            modifier = Modifier.fillMaxWidth().testTag("floating_focus_lyric")
                        )
                    }
                }
            }
        } else if (rotatingSections || viewMode == 0) {
            ContinuousLyricsDisplay(
                lines = lines,
                currentIndex = slotState.currentIndex,
                currentPositionMs = currentPositionMs,
                effectiveOffsetMs = effectiveOffset,
                config = config,
                currentLineColor = currentLineColor,
                composeFontFamily = composeFontFamily,
                textAlign = textAlign,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                onSeekTo = onSeekTo,
                rotatingSections = rotatingSections,
                modifier = Modifier.fillMaxSize()
            )
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
                            .clickable { onSeekTo((line.timeMs - effectiveOffset).coerceAtLeast(0L)) }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        contentAlignment = effectiveContentAlignment
                    ) {
                        if (isCurrent && config.formatMode == LyricsFormatMode.ELRC && line.hasWordTimestamps && line.words.isNotEmpty()) {
                            SynchronizedLyricContent(
                                line = line,
                                isCurrent = true,
                                currentPositionMs = currentPositionMs,
                                effectiveOffsetMs = effectiveOffset,
                                config = config,
                                currentLineColor = animColor,
                                composeFontFamily = composeFontFamily,
                                textAlign = textAlign,
                                isPlaying = isPlaying
                            )
                        } else {
                            Text(
                                text = formatLyricText(line.text, config.textCase),
                                fontSize = animSize.sp,
                                fontFamily = composeFontFamily,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Light,
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

/** Timestamp used to anchor a fullscreen line; eLRC starts at its first word. */
internal fun fullscreenLyricTimestamp(
    line: LyricLine,
    formatMode: LyricsFormatMode
): Long = if (formatMode == LyricsFormatMode.ELRC && line.hasWordTimestamps) {
    line.words.firstOrNull()?.startTimeMs ?: line.timeMs
} else {
    line.timeMs
}

/** Playback-time fraction between two lyric anchors, clamped to the current interval. */
internal fun fullscreenLyricsScrollProgress(
    playbackPositionMs: Double,
    currentLineTimeMs: Long,
    nextLineTimeMs: Long
): Float {
    val durationMs = (nextLineTimeMs - currentLineTimeMs).coerceAtLeast(1L)
    return ((playbackPositionMs - currentLineTimeMs.toDouble()) / durationMs.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}

/** Incremental pixel movement for a linear timestamp-driven glide. */
internal fun fullscreenLyricsPixelDelta(
    measuredDistancePx: Float,
    previousProgress: Float,
    currentProgress: Float
): Float = measuredDistancePx * (currentProgress - previousProgress)

/**
 * One continuous lyrics stream shared by Focus and fullscreen lyrics.
 *
 * Every lyric remains in the same LazyColumn, so changing the active index moves
 * the stream rather than replacing a stack of independently composed sections.
 * Word-level eLRC progress is calculated for every visible line from the same
 * playback clock, preserving the outgoing line's completed sweep while it fades.
 */
@Composable
private fun ContinuousLyricsDisplay(
    lines: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    effectiveOffsetMs: Long,
    config: LyricsDisplayConfig,
    currentLineColor: Color,
    composeFontFamily: FontFamily,
    textAlign: TextAlign,
    isPlaying: Boolean,
    playbackSpeed: Float,
    onSeekTo: (Long) -> Unit,
    rotatingSections: Boolean,
    modifier: Modifier = Modifier
) {
    val lineTimestamps = remember(lines, config.formatMode) {
        lines.map { fullscreenLyricTimestamp(it, config.formatMode) }
    }
    var activeDisplayIndex by remember(lines) { mutableStateOf(currentIndex) }
    var animatedPlaybackPositionMs by remember(lines) { mutableStateOf(currentPositionMs.toDouble()) }
    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val latestEffectiveOffsetMs by rememberUpdatedState(effectiveOffsetMs)
    val showPreLyrics = activeDisplayIndex < 0
    val preLyricsStatus = when {
        !isPlaying && currentPositionMs == 0L -> "♪ Music stopped ♪"
        !isPlaying -> "♪ Music paused ♪"
        else -> "♪ Music playing ♪"
    }

    BoxWithConstraints(modifier = modifier.testTag(
        if (rotatingSections) "fullscreen_credit_lyrics" else "continuous_lyrics_display"
    )) {
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        // Half-viewport end padding lets the first and last lyric lines reach centre.
        val topPadding = if (rotatingSections) maxHeight / 2
            else if (showPreLyrics) (maxHeight / 2 - 36.dp).coerceAtLeast(16.dp)
            else 16.dp
        // Coordinates are measured in root space, then converted to this lyrics viewport.
        // A plain map avoids recomposing the entire list for every layout callback.
        val containerBounds = remember(lines) { FloatArray(2) }
        val lineCentresInRoot = remember(lines) { mutableMapOf<Int, Float>() }

        // A frame clock extrapolates between authoritative player samples. scrollBy()
        // receives only the incremental pixel delta, so no easing curve or per-frame
        // scrollToItem() repositioning can introduce a stepped effect.
        LaunchedEffect(lines, rotatingSections, config.formatMode) {
            if (rotatingSections && lines.isNotEmpty()) {
                var lastSamplePositionMs = latestPositionMs
                var anchorPositionMs = latestPositionMs.toDouble()
                var anchorFrameNanos = 0L
                var previousPlaying = latestIsPlaying
                var previousSpeed = latestPlaybackSpeed
                var previousOffsetMs = latestEffectiveOffsetMs
                var lastSampleFrameNanos = 0L
                var previousActiveIndex = -1
                var previousProgress = 0f
                var needsCentreAnchor = true

                while (isActive) {
                    val frameNanos = withFrameNanos { it }
                    val samplePositionMs = latestPositionMs
                    val playingNow = latestIsPlaying
                    val speedNow = latestPlaybackSpeed.coerceAtLeast(0f)

                    // Detect seeks by comparing the player sample delta with the elapsed
                    // frame time. Ordinary periodic samples stay on the current glide;
                    // discontinuities and offset changes re-anchor the active line.
                    val elapsedSinceSampleMs = if (lastSampleFrameNanos == 0L) 0.0 else
                        (frameNanos - lastSampleFrameNanos).coerceAtLeast(0L) / 1_000_000.0
                    val expectedSampleAdvanceMs = if (previousPlaying) {
                        elapsedSinceSampleMs * previousSpeed.coerceAtLeast(0f)
                    } else 0.0
                    val actualSampleAdvanceMs =
                        (samplePositionMs - lastSamplePositionMs).toDouble()
                    val sampleChanged = samplePositionMs != lastSamplePositionMs
                    val sampleLooksLikeSeek = sampleChanged &&
                        kotlin.math.abs(actualSampleAdvanceMs - expectedSampleAdvanceMs) > 500.0
                    val offsetChanged = latestEffectiveOffsetMs != previousOffsetMs

                    val playbackStateChanged =
                        playingNow != previousPlaying || speedNow != previousSpeed
                    if (
                        anchorFrameNanos == 0L ||
                        sampleChanged ||
                        playbackStateChanged
                    ) {
                        anchorPositionMs = samplePositionMs.toDouble()
                        anchorFrameNanos = frameNanos
                        lastSamplePositionMs = samplePositionMs
                        previousPlaying = playingNow
                        previousSpeed = speedNow
                    }
                    if (sampleChanged || playbackStateChanged || lastSampleFrameNanos == 0L) {
                        lastSampleFrameNanos = frameNanos
                    }
                    if (sampleLooksLikeSeek || offsetChanged) {
                        needsCentreAnchor = true
                        previousActiveIndex = -1
                        previousProgress = 0f
                    }
                    previousOffsetMs = latestEffectiveOffsetMs

                    val playbackPositionMs = if (playingNow) {
                        anchorPositionMs +
                            ((frameNanos - anchorFrameNanos).coerceAtLeast(0L) / 1_000_000.0) *
                            speedNow
                    } else {
                        samplePositionMs.toDouble()
                    }
                    animatedPlaybackPositionMs = playbackPositionMs

                    val adjustedPositionMs = playbackPositionMs + latestEffectiveOffsetMs
                    val activeIndex = lineTimestamps.indexOfLast { it <= adjustedPositionMs }
                    activeDisplayIndex = activeIndex

                    if (activeIndex < 0) {
                        if (listState.firstVisibleItemIndex != 0 ||
                            listState.firstVisibleItemScrollOffset != 0
                        ) {
                            listState.scrollToItem(0)
                        }
                        previousActiveIndex = -1
                        previousProgress = 0f
                        needsCentreAnchor = true
                        continue
                    }

                    // A non-adjacent index change indicates an initial position or seek.
                    // Reposition once to make the selected line measurable, then centre it.
                    if (previousActiveIndex >= 0 && kotlin.math.abs(activeIndex - previousActiveIndex) > 1) {
                        listState.scrollToItem(activeIndex)
                        previousActiveIndex = activeIndex
                        previousProgress = 0f
                        needsCentreAnchor = true
                        continue
                    }

                    val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                    if (activeIndex !in visibleIndices) {
                        listState.scrollToItem(activeIndex)
                        previousActiveIndex = activeIndex
                        previousProgress = 0f
                        needsCentreAnchor = true
                        continue
                    }

                    val currentCentre = lineCentresInRoot[activeIndex]
                        ?.minus(containerBounds[0])
                    if (currentCentre == null) continue

                    val viewportHeightPx = containerBounds[1]
                    if (viewportHeightPx <= 0f) continue
                    val viewportCentre = viewportHeightPx / 2f
                    val nextIndex = activeIndex + 1
                    val nextTimeMs = lineTimestamps.getOrNull(nextIndex)
                    val progress = if (nextTimeMs != null) {
                        fullscreenLyricsScrollProgress(
                            adjustedPositionMs,
                            lineTimestamps[activeIndex],
                            nextTimeMs
                        )
                    } else {
                        0f
                    }

                    if (needsCentreAnchor) {
                        // Initial load / seek: one measured correction to put the active
                        // line at the exact viewport centre before starting the glide.
                        listState.scrollBy(currentCentre - viewportCentre)
                        needsCentreAnchor = false
                        previousActiveIndex = activeIndex
                        // We have centred the active line at the current playback instant.
                        // Continue only the remaining fraction of this timestamp interval.
                        previousProgress = progress
                        continue
                    }

                    val advancedFromPreviousLine =
                        previousActiveIndex >= 0 && activeIndex == previousActiveIndex + 1
                    val previousIntervalProgress = previousProgress
                    if (activeIndex != previousActiveIndex) {
                        previousActiveIndex = activeIndex
                        previousProgress = 0f
                    }

                    val previousCentre = if (advancedFromPreviousLine) {
                        lineCentresInRoot[activeIndex - 1]?.minus(containerBounds[0])
                    } else null
                    val nextIsVisible = nextIndex in visibleIndices
                    val nextCentre = if (nextIsVisible) {
                        lineCentresInRoot[nextIndex]?.minus(containerBounds[0])
                    } else null

                    // If a frame crosses a timestamp boundary, finish the tiny remainder
                    // of the previous interval before applying this line's new progress.
                    // This keeps the newly active line centred at its exact timestamp.
                    val boundaryCorrectionPx = if (
                        advancedFromPreviousLine && previousCentre != null
                    ) {
                        (currentCentre - previousCentre) * (1f - previousIntervalProgress)
                    } else 0f

                    if (nextCentre != null) {
                        // Pixel gap comes from the two real row centres, including wrapped
                        // text height, row padding, and LazyColumn item spacing.
                        val measuredGapPx = nextCentre - currentCentre
                        val intervalDeltaPx = if (advancedFromPreviousLine) {
                            measuredGapPx * progress
                        } else {
                            fullscreenLyricsPixelDelta(
                                measuredGapPx,
                                previousProgress,
                                progress
                            )
                        }
                        val totalPixelDelta = boundaryCorrectionPx + intervalDeltaPx
                        if (totalPixelDelta != 0f && measuredGapPx > 0f) {
                            listState.scrollBy(totalPixelDelta)
                        }
                        previousProgress = progress
                    } else {
                        if (boundaryCorrectionPx != 0f) {
                            listState.scrollBy(boundaryCorrectionPx)
                        }
                        previousProgress = progress
                    }
                }
            }
        }

        LaunchedEffect(currentIndex, lines.size, rotatingSections) {
            if (!rotatingSections && currentIndex in lines.indices) {
                listState.animateScrollToItem(
                    index = currentIndex,
                    scrollOffset = -with(density) { (maxHeight / 2 - 36.dp).roundToPx() }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    containerBounds[0] = coordinates.positionInRoot().y
                    containerBounds[1] = coordinates.size.height.toFloat()
                }
                // Fade only the fullscreen credit-mode viewport. The offscreen layer
                // keeps DstIn masking local to the lyrics, leaving dialog controls untouched.
                .then(
                    if (rotatingSections) {
                        Modifier
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                if (size.height > 0f) {
                                    // Keep a fully transparent gap at both viewport edges,
                                    // then fade smoothly into the fully visible centre band.
                                    val edgeGapFraction = 0.05f
                                    val fadeFraction = 0.30f
                                    val topFadeEnd = edgeGapFraction + fadeFraction
                                    val bottomFadeStart = 1f - edgeGapFraction - fadeFraction
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0f to Color.Transparent,
                                                edgeGapFraction to Color.Transparent,
                                                topFadeEnd to Color.Black,
                                                bottomFadeStart to Color.Black,
                                                (1f - edgeGapFraction) to Color.Transparent,
                                                1f to Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = size.height
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = if (rotatingSections) maxHeight / 2 else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(config.lineSpacingDp.dp)
        ) {
            if (showPreLyrics) {
                item(key = "pre_lyrics_status") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatLyricText(preLyricsStatus, config.textCase),
                            fontSize = config.currentLineFontSizeSp.sp,
                            fontFamily = composeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = currentLineColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("current_lyric_slot")
                        )
                    }
                }
            }
            itemsIndexed(lines, key = { index, line -> "${line.timeMs}_${index}" }) { index, line ->
                val distance = if (showPreLyrics) index + 1 else kotlin.math.abs(index - activeDisplayIndex)
                // Fullscreen credit mode keeps every lyric fully visible as it passes.
                // Earlier lines retain their highlighted state instead of fading back out.
                val targetAlpha = if (rotatingSections) 1f else continuousLyricsLineAlpha(distance, config.otherLinesOpacity)
                val lineAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = config.animationDurationMs),
                    label = "continuousLyricOpacity"
                )
                val isCurrent = index == activeDisplayIndex
                val lineColor = if (config.formatMode == LyricsFormatMode.ELRC) {
                    currentLineColor
                } else if (rotatingSections && index <= activeDisplayIndex) {
                    currentLineColor
                } else if (isCurrent && config.highlightCurrentLine) {
                    currentLineColor
                } else {
                    TextPrimaryDark
                }
                // Keep every line at the configured current-line size; highlighting changes colour only.
                val lineSize = config.currentLineFontSizeSp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(lineAlpha)
                        .clickable { onSeekTo((line.timeMs - effectiveOffsetMs).coerceAtLeast(0L)) }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .onGloballyPositioned { coordinates ->
                            lineCentresInRoot[index] =
                                coordinates.positionInRoot().y + coordinates.size.height / 2f
                        }
                        .testTag(
                            when {
                                isCurrent && rotatingSections -> "fullscreen_credit_active_line"
                                isCurrent -> "current_lyric_slot"
                                else -> "continuous_lyric_line_$index"
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (config.formatMode == LyricsFormatMode.ELRC &&
                        line.hasWordTimestamps && line.words.isNotEmpty()
                    ) {
                        // Keep timestamp-driven word progress active on outgoing and
                        // incoming lines as well as the current line.
                        SynchronizedLyricContent(
                            line = line,
                            isCurrent = isCurrent,
                            currentPositionMs = animatedPlaybackPositionMs.toLong(),
                            effectiveOffsetMs = effectiveOffsetMs,
                            config = config,
                            currentLineColor = lineColor,
                            composeFontFamily = composeFontFamily,
                            textAlign = TextAlign.Center,
                            isPlaying = isPlaying,
                            fontSizeSp = lineSize,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = formatLyricText(line.text, config.textCase),
                            fontSize = lineSize.sp,
                            fontFamily = composeFontFamily,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = lineColor,
                            textAlign = if (config.formatMode == LyricsFormatMode.ELRC) TextAlign.Center else textAlign,
                            lineHeight = (lineSize * 1.2f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Opacity curve for the continuous stream: current, near, distant, then faded out. */
internal fun continuousLyricsLineAlpha(distance: Int, otherLinesOpacity: Float): Float {
    if (distance <= 0) return 1f
    val nearOpacity = otherLinesOpacity.coerceIn(0.1f, 0.85f)
    return when (distance) {
        1 -> nearOpacity
        2 -> nearOpacity * 0.55f
        3 -> nearOpacity * 0.2f
        else -> 0f
    }
}

/**
 * Progressive Sweeping eLRC and line-level LRC lyrics renderer matching Yimly Server specification:
 * - Highlight mode: progressive_sweeping
 * - Driven by actual eLRC word timestamps (startTimeMs, endTimeMs)
 * - Animated at ~60 FPS with frame-synchronized timing (withFrameMillis)
 * - Progresses continuously and smoothly across words
 * - All words remain at normal 1.0x scale (zero scale, zero layout shift or jitter)
 * - Permanently centered horizontally in FlowRow (single-line, multi-line, wrapped)
 * - No discrete 3-state past/current/future styling, no background glow or box
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SynchronizedLyricContent(
    line: LyricLine,
    isCurrent: Boolean,
    currentPositionMs: Long,
    effectiveOffsetMs: Long = 0L,
    config: LyricsDisplayConfig,
    currentLineColor: Color,
    composeFontFamily: FontFamily,
    textAlign: TextAlign,
    isPlaying: Boolean = true,
    fontSizeSp: Float = config.currentLineFontSizeSp,
    modifier: Modifier = Modifier
) {
    if (config.formatMode == LyricsFormatMode.ELRC && line.hasWordTimestamps && line.words.isNotEmpty()) {
        val adjustedPos = currentPositionMs + effectiveOffsetMs

        val spaceDp = (fontSizeSp * 0.25f).dp
        val rowSpacingDp = (config.lineSpacingDp * 0.35f).coerceAtLeast(4f).dp

        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .testTag("synchronized_elrc_row"),
            horizontalArrangement = Arrangement.spacedBy(spaceDp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(rowSpacingDp, Alignment.CenterVertically)
        ) {
            line.words.forEach { word ->
                val wordText = formatLyricText(word.word, config.textCase)
                val durationMs = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
                val progress = ((adjustedPos - word.startTimeMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                ProgressiveSweepWord(
                    text = wordText,
                    progress = progress,
                    activeColor = currentLineColor,
                    inactiveColor = currentLineColor.copy(alpha = 0.35f),
                    fontSize = fontSizeSp.sp,
                    fontFamily = composeFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        // Standard line-level synchronization (LRC)
        Text(
            text = formatLyricText(line.text, config.textCase),
            fontSize = fontSizeSp.sp,
            fontFamily = composeFontFamily,
            fontWeight = FontWeight.Bold,
            color = currentLineColor,
            textAlign = textAlign,
            lineHeight = (fontSizeSp * 1.3f).sp,
            modifier = modifier
        )
    }
}

/**
 * Renders an individual eLRC word with a continuous progressive horizontal highlight sweep.
 *
 * Guarantees:
 * 1. Strictly 1.0x scale (no enlargement, no word scaling).
 * 2. Zero layout shift and zero jitter - both layers share identical layout metrics.
 * 3. Smooth continuous sweep driven by word timestamps and frame-synchronized timing.
 * 4. No discrete past/current/future background boxes, glow, or 3-state styles.
 */
@Composable
fun ProgressiveSweepWord(
    text: String,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Base layer: Inactive / upcoming text at normal 1.0x scale
        Text(
            text = text,
            color = inactiveColor,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false
        )

        // Active layer: Highlight smoothly sweeps across text at normal 1.0x scale
        Text(
            text = text,
            color = activeColor,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    if (progress <= 0f) return@drawWithContent

                    if (progress >= 1f) {
                        drawContent()
                    } else {
                        val sweepX = size.width * progress
                        val feather = (size.width * 0.15f).coerceIn(4f, 20f)
                        val start = ((sweepX - feather) / size.width).coerceIn(0f, 1f)
                        val end = ((sweepX + feather) / size.width).coerceIn(0f, 1f)

                        try {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White,
                                        start to Color.White,
                                        end to Color.Transparent,
                                        1.0f to Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        } catch (e: Throwable) {
                            clipRect(left = 0f, top = 0f, right = sweepX, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                }
        )
    }
}
