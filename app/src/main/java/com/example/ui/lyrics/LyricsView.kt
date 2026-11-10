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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricLine
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.data.models.formatLyricText
import com.example.lyrics.LyricsParser
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.BackgroundDark
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
    var viewMode by remember { mutableStateOf(1) } // 0 = Focus Mode, 1 = Full Synced List

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
        } else if ((viewMode == 0 || floatingFocusMode) && !rotatingSections) {
            // Focus Mode (compact player / tabbed Focus mode): single active line or pre-lyrics status centered in display
            val currentIndex = slotState.currentIndex
            val currentLine = currentIndex.takeIf { it >= 0 }?.let { lines.getOrNull(it) }
            val showPreLyrics = currentIndex < 0
            val preLyricsStatus = when {
                !isPlaying && currentPositionMs == 0L -> "♪ Music stopped ♪"
                !isPlaying -> "♪ Music paused ♪"
                else -> "♪ Music playing ♪"
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (showPreLyrics) {
                    Text(
                        text = formatLyricText(preLyricsStatus, config.textCase),
                        fontSize = config.currentLineFontSizeSp.sp,
                        fontFamily = composeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = currentLineColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag(if (floatingFocusMode) "floating_focus_lyric" else "current_lyric_slot")
                    )
                } else currentLine?.let { line ->
                    if (config.formatMode == LyricsFormatMode.ELRC) {
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
                            fontSizeSp = config.currentLineFontSizeSp,
                            modifier = Modifier.fillMaxWidth().testTag(if (floatingFocusMode) "floating_focus_lyric" else "current_lyric_slot")
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
                            modifier = Modifier.fillMaxWidth().testTag(if (floatingFocusMode) "floating_focus_lyric" else "current_lyric_slot")
                        )
                    }
                }
            }
        } else {
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
                onSeekTo = onSeekTo,
                rotatingSections = rotatingSections,
                playbackSpeed = playbackSpeed,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

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
    onSeekTo: (Long) -> Unit,
    rotatingSections: Boolean,
    playbackSpeed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val showPreLyrics = currentIndex < 0
    val preLyricsStatus = when {
        !isPlaying && currentPositionMs == 0L -> "♪ Music stopped ♪"
        !isPlaying -> "♪ Music paused ♪"
        else -> "♪ Music playing ♪"
    }

    BoxWithConstraints(modifier = modifier.testTag(
        if (rotatingSections) "fullscreen_credit_lyrics" else "continuous_lyrics_display"
    )) {
        val boxHalfHeight = maxHeight / 2
        val density = LocalDensity.current
        val uniformFontSizeSp = config.currentLineFontSizeSp
        val spacingDp = config.lineSpacingDp

        val itemHeights = remember { mutableStateMapOf<Int, Int>() }
        val defaultItemHeightPx = with(density) { ((uniformFontSizeSp * 1.3f).sp.toPx() + spacingDp.dp.toPx() + 24.dp.toPx()).toInt() }
        val spacingPx = with(density) { spacingDp.dp.toPx().toInt() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val halfViewport = viewportHeightPx / 2f

        val itemAnchors = remember(lines, showPreLyrics, config.formatMode) {
            val anchors = mutableListOf<Long>()
            if (showPreLyrics) {
                anchors.add(0L)
            }
            lines.forEach { line ->
                val targetTime = if (config.formatMode == LyricsFormatMode.ELRC) {
                    line.words.firstOrNull()?.startTimeMs ?: line.timeMs
                } else {
                    line.timeMs
                }
                anchors.add(targetTime)
            }
            anchors
        }

        val anchorScrollYList = remember(itemAnchors, itemHeights.toMap(), viewportHeightPx, spacingPx, defaultItemHeightPx) {
            val list = FloatArray(itemAnchors.size)
            var currentTop = halfViewport
            for (i in itemAnchors.indices) {
                val h = itemHeights[i] ?: defaultItemHeightPx
                val centerY = currentTop + h / 2f
                list[i] = centerY - halfViewport
                currentTop += h + spacingPx
            }
            list
        }

        var currentScrollY by remember { mutableFloatStateOf(0f) }

        val currentPosState = rememberUpdatedState(currentPositionMs)
        val effectiveOffsetState = rememberUpdatedState(effectiveOffsetMs)
        val isPlayingState = rememberUpdatedState(isPlaying)
        val playbackSpeedState = rememberUpdatedState(playbackSpeed)

        var animatedPosMs by remember { mutableLongStateOf(currentPositionMs + effectiveOffsetMs) }

        // Frame-synchronized playback clock using withFrameNanos anchored to Media3 samples
        LaunchedEffect(config.autoScroll, itemAnchors.size) {
            if (!config.autoScroll || itemAnchors.isEmpty()) return@LaunchedEffect

            var lastReportedPos = currentPosState.value + effectiveOffsetState.value
            var lastNanoTime = System.nanoTime()
            var basePosMs = lastReportedPos

            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    val playing = isPlayingState.value
                    val authoritativePos = currentPosState.value + effectiveOffsetState.value
                    val speed = playbackSpeedState.value

                    if (authoritativePos != lastReportedPos) {
                        basePosMs = authoritativePos
                        lastReportedPos = authoritativePos
                        lastNanoTime = frameTimeNanos
                        animatedPosMs = authoritativePos
                    } else if (playing) {
                        val elapsedNanos = frameTimeNanos - lastNanoTime
                        val elapsedMs = (elapsedNanos / 1_000_000L) * speed
                        animatedPosMs = basePosMs + elapsedMs.toLong()
                    } else {
                        lastNanoTime = frameTimeNanos
                        basePosMs = authoritativePos
                        animatedPosMs = authoritativePos
                    }
                }
            }
        }

        // Smooth piecewise linear interpolation toward target Y based on animatedPosMs
        LaunchedEffect(animatedPosMs, itemAnchors, anchorScrollYList) {
            if (itemAnchors.isEmpty()) return@LaunchedEffect
            val adjustedPos = animatedPosMs

            val firstAnchor = itemAnchors.first()
            val lastAnchor = itemAnchors.last()

            val targetY = when {
                adjustedPos <= firstAnchor -> anchorScrollYList.getOrNull(0) ?: 0f
                adjustedPos >= lastAnchor -> anchorScrollYList.lastOrNull() ?: 0f
                else -> {
                    var low = 0
                    var high = itemAnchors.size - 2
                    var foundIndex = 0
                    while (low <= high) {
                        val mid = (low + high) / 2
                        if (itemAnchors[mid] <= adjustedPos) {
                            foundIndex = mid
                            low = mid + 1
                        } else {
                            high = mid - 1
                        }
                    }
                    val t0 = itemAnchors[foundIndex]
                    val t1 = itemAnchors.getOrElse(foundIndex + 1) { t0 }
                    val y0 = anchorScrollYList.getOrElse(foundIndex) { 0f }
                    val y1 = anchorScrollYList.getOrElse(foundIndex + 1) { y0 }
                    val dt = (t1 - t0).coerceAtLeast(1L)
                    val frac = ((adjustedPos - t0).toFloat() / dt.toFloat()).coerceIn(0f, 1f)
                    y0 + frac * (y1 - y0)
                }
            }

            val diff = targetY - currentScrollY
            currentScrollY = targetY
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = -currentScrollY },
                verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
            ) {
                Spacer(modifier = Modifier.height(boxHalfHeight))

                if (showPreLyrics) {
                    val h = itemHeights[0] ?: defaultItemHeightPx

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size -> itemHeights[0] = size.height }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatLyricText(preLyricsStatus, config.textCase),
                            fontSize = uniformFontSizeSp.sp,
                            fontFamily = composeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = currentLineColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("current_lyric_slot")
                        )
                    }
                }

                val lineOffsetIndex = if (showPreLyrics) 1 else 0
                lines.forEachIndexed { index, line ->
                    val actualIndex = index + lineOffsetIndex
                    val h = itemHeights[actualIndex] ?: defaultItemHeightPx

                    val adjustedPos = animatedPosMs
                    val isHighlighted = index <= currentIndex || line.timeMs <= adjustedPos
                    val isCurrent = index == currentIndex
                    val lineColor = if (isHighlighted) currentLineColor else TextSecondaryDark

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size -> itemHeights[actualIndex] = size.height }
                            .clickable { onSeekTo((line.timeMs - (animatedPosMs - currentPosState.value)).coerceAtLeast(0L)) }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                            .testTag(
                                when {
                                    isCurrent && rotatingSections -> "fullscreen_credit_active_line"
                                    isCurrent -> "current_lyric_slot"
                                    else -> "continuous_lyric_line_$index"
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (config.formatMode == LyricsFormatMode.ELRC) {
                            SynchronizedLyricContent(
                                line = line,
                                isCurrent = isCurrent,
                                currentPositionMs = animatedPosMs,
                                effectiveOffsetMs = 0L,
                                config = config,
                                currentLineColor = currentLineColor,
                                composeFontFamily = composeFontFamily,
                                textAlign = TextAlign.Center,
                                isPlaying = isPlaying,
                                fontSizeSp = uniformFontSizeSp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = formatLyricText(line.text, config.textCase),
                                fontSize = uniformFontSizeSp.sp,
                                fontFamily = composeFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = lineColor,
                                textAlign = if (config.formatMode == LyricsFormatMode.ELRC) TextAlign.Center else textAlign,
                                lineHeight = (uniformFontSizeSp * 1.3f).sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(boxHalfHeight))
            }

            // Top Edge Fade Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BackgroundDark, BackgroundDark.copy(alpha = 0f))
                        )
                    )
            )

            // Bottom Edge Fade Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BackgroundDark.copy(alpha = 0f), BackgroundDark)
                        )
                    )
            )
        }
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
    val adjustedPos = currentPositionMs + effectiveOffsetMs
    val spaceDp = (fontSizeSp * 0.25f).dp
    val rowSpacingDp = (config.lineSpacingDp * 0.35f).coerceAtLeast(4f).dp

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (line.hasWordTimestamps || line.words.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCurrent) Modifier.testTag("synchronized_elrc_row") else Modifier
                    ),
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
                        inactiveColor = TextSecondaryDark,
                        fontSize = fontSizeSp.sp,
                        fontFamily = composeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            val isHighlighted = isCurrent || line.timeMs <= adjustedPos
            Text(
                text = formatLyricText(line.text, config.textCase),
                fontSize = fontSizeSp.sp,
                fontFamily = composeFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) currentLineColor else TextSecondaryDark,
                textAlign = textAlign,
                lineHeight = (fontSizeSp * 1.3f).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("synchronized_elrc_row")
            )
        }
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
        if (progress > 0f) {
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
                    .drawWithContent {
                        if (progress >= 1f) {
                            drawContent()
                        } else {
                            val sweepX = size.width * progress
                            clipRect(left = 0f, top = 0f, right = sweepX, bottom = size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
            )
        }
    }
}
