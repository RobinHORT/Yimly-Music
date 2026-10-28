package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.Song
import com.example.playback.PlaybackInfo
import com.example.playback.RepeatMode
import com.example.ui.components.YimlyMicIcon
import com.example.ui.lyrics.LyricsSettingsDialog
import com.example.ui.lyrics.LyricsView
import com.example.ui.queue.QueueView
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun FullPlayerScreen(
    playbackInfo: PlaybackInfo,
    lyricsData: LyricsData?,
    songOffsetMs: Long,
    lyricsConfig: LyricsDisplayConfig,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleInstrumental: () -> Unit = {},
    onSaveLyricsConfig: (LyricsDisplayConfig) -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onReorderQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onAddToPlaylist: (Song) -> Unit = {},
    lyricsPositionMs: Long = 0L,
    onSetLyricsActive: (Boolean) -> Unit = {},
    playbackPositionFlow: StateFlow<Long>? = null,
    modifier: Modifier = Modifier
) {
    val song = playbackInfo.currentSong ?: return

    val currentPositionMs by (playbackPositionFlow ?: remember { MutableStateFlow(playbackInfo.currentPositionMs) }).collectAsState()

    var showLyricsSettings by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showFullscreenLyrics by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onSetLyricsActive(true)
    }

    DisposableEffect(Unit) {
        onDispose {
            onSetLyricsActive(false)
        }
    }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    val currentPos = if (isUserSeeking) seekPositionMs.toLong() else currentPositionMs
    val totalDuration = if (playbackInfo.durationMs > 0) playbackInfo.durationMs else song.durationMs

    fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }

    if (showLyricsSettings) {
        LyricsSettingsDialog(
            initialConfig = lyricsConfig,
            onDismiss = { showLyricsSettings = false },
            onSaveConfig = onSaveLyricsConfig
        )
    }

    if (showFullscreenLyrics) {
        FullscreenLyricsDialog(
            song = song,
            lyricsData = lyricsData,
            currentPositionMs = currentPos,
            durationMs = totalDuration,
            isPlaying = playbackInfo.isPlaying,
            isShuffle = playbackInfo.isShuffle,
            repeatMode = playbackInfo.repeatMode,
            songOffsetMs = songOffsetMs,
            lyricsConfig = lyricsConfig,
            onDismiss = { showFullscreenLyrics = false },
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode,
            onSeekTo = onSeekTo,
            onOpenSettings = { showFullscreenLyrics = false; showLyricsSettings = true },
            onLyricsOffsetChange = onLyricsOffsetChange,
            onSaveLyricsConfig = onSaveLyricsConfig
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("full_player_screen")
    ) {
        // Use the album art as a clearly visible, full-screen backdrop, with the same
        // plum/pink treatment as the artwork overlay. Keep the queue separately tinted below.
        if (!song.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.92f }
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        // Match Yimly Karaoke's full-player cover-art overlay exactly:
                        // translucent pink at the top, deeper rose through the middle,
                        // and dark plum at the bottom.
                        Color(0xFFBE185D).copy(alpha = 0.48f),
                        Color(0xFF9D174D).copy(alpha = 0.58f),
                        Color(0xFF230C1D).copy(alpha = 0.72f)
                    )
                )
            )
        )

        val artworkSize = minOf(maxWidth * 0.56f, maxHeight * 0.28f, 210.dp)
        val sectionGap = 16.dp

        // Keep the cover image still; the edge glow pulses without a rotating square.
        val artworkTransition = rememberInfiniteTransition(label = "artworkBorderEffect")
        val borderGlow by artworkTransition.animateFloat(
            initialValue = 0.18f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = AnimationRepeatMode.Reverse
            ),
            label = "artworkBorderGlow"
        )
        val artworkShape = RoundedCornerShape(20.dp)
        val artworkIsActive = playbackInfo.isPlaying
        var dragOffsetSum by remember { mutableFloatStateOf(0f) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragOffsetSum = 0f },
                        onDragEnd = {
                            if (dragOffsetSum > 80f) {
                                onCollapse()
                            }
                            dragOffsetSum = 0f
                        },
                        onDragCancel = { dragOffsetSum = 0f },
                        onDrag = { change, dragAmount ->
                            dragOffsetSum += dragAmount.y
                            change.consume()
                        }
                    )
                }
        ) {
            // Keep the header below the status bar without shrinking the queue's
            // available height; the queue background can now bleed past the bottom edge.
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp))

            // Track header: favorite on the left, track details centred, collapse on the right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp).testTag("favorite_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (song.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (song.isFavorite) YimlyPink else TextPrimaryDark,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(48.dp).testTag("collapse_player_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextPrimaryDark,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 1. Consistent gap between header and artwork
            Spacer(modifier = Modifier.height(sectionGap))

            // Artwork is constrained by available width and height to preserve room for controls and queue.
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .clickable { showFullscreenLyrics = true }
                    .clip(artworkShape)
                    .background(SurfaceCardDark)
                    .align(Alignment.CenterHorizontally)
            ) {
                // Background album artwork
                if (!song.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(artworkShape)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(SurfaceCardDark), contentAlignment = Alignment.Center) {
                        YimlyMicIcon(tint = TextSecondaryDark, modifier = Modifier.size(80.dp))
                    }
                }

                // Keep artwork natural normally; apply the focus tint only while lyrics are shown.
                if (showLyrics) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(artworkShape).background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFBE185D).copy(alpha = 0.48f),
                                    Color(0xFF9D174D).copy(alpha = 0.58f),
                                    BackgroundDark.copy(alpha = 0.72f)
                                )
                            )
                        )
                    )
                    LyricsView(
                        lyricsData = lyricsData,
                        currentPositionMs = currentPos,
                        songOffsetMs = songOffsetMs,
                        config = lyricsConfig,
                        onSeekTo = onSeekTo,
                        onOpenSettings = { showLyricsSettings = true },
                        onOffsetChange = onLyricsOffsetChange,
                        isPlaying = playbackInfo.isPlaying,
                        showFocusControls = false,
                        floatingFocusMode = true,
                        modifier = Modifier.fillMaxSize().clip(artworkShape)
                    )
                    // Tapping the lyric itself opens the same fullscreen lyrics view as the artwork.
                    Box(modifier = Modifier.fillMaxSize().clickable { showFullscreenLyrics = true })
                }

                // Static conic-gradient edge; its brightness pulses while playing, without rotating the square.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    YimlyPink.copy(alpha = if (artworkIsActive) 0.20f + borderGlow * 0.22f else 0.20f),
                                    YimlyPink.copy(alpha = if (artworkIsActive) 0.28f + borderGlow * 0.26f else 0.28f),
                                    Color(0xFFF9A8D4).copy(alpha = if (artworkIsActive) 0.55f + borderGlow * 0.35f else 0.55f),
                                    Color(0xFFFFF5FB).copy(alpha = if (artworkIsActive) 0.65f + borderGlow * 0.30f else 0.65f),
                                    YimlyPink.copy(alpha = if (artworkIsActive) 0.42f + borderGlow * 0.32f else 0.42f),
                                    YimlyPink.copy(alpha = if (artworkIsActive) 0.20f + borderGlow * 0.22f else 0.20f)
                                )
                            ),
                            shape = RoundedCornerShape(17.dp)
                        )
                )
            }

            // 2. Matching gap between cover art and playback controls
            Spacer(modifier = Modifier.height(sectionGap))

            // Fixed Controls. Track details are already shown in the header,
            // so keep this card focused on seek and playback controls.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                    // Progress Scrubber Slider & Duration labels
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Draw the visible scrubber ourselves: Material Slider keeps a
                        // minimum visual track/thumb size even when its layout height is reduced.
                        // The 28dp container preserves a comfortable touch target while the
                        // rendered track itself stays a slim 3dp line.
                        val seekDuration = totalDuration.toFloat().coerceAtLeast(1f)
                        val seekFraction = (currentPos.toFloat() / seekDuration).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("player_progress_slider")
                                .pointerInput(totalDuration) {
                                    detectTapGestures { position ->
                                        val fraction = (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val target = fraction * seekDuration
                                        isUserSeeking = true
                                        seekPositionMs = target
                                        onSeekTo(target.toLong())
                                        isUserSeeking = false
                                    }
                                }
                                .pointerInput(totalDuration) {
                                    detectDragGestures(
                                        onDragStart = { position ->
                                            val fraction = (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                            isUserSeeking = true
                                            seekPositionMs = fraction * seekDuration
                                        },
                                        onDragEnd = {
                                            isUserSeeking = false
                                            onSeekTo(seekPositionMs.toLong())
                                        },
                                        onDragCancel = {
                                            isUserSeeking = false
                                        },
                                        onDrag = { change, _ ->
                                            val fraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                            seekPositionMs = fraction * seekDuration
                                            change.consume()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                                val trackHeight = 3.dp.toPx()
                                val centerY = size.height / 2f
                                val thumbRadius = 3.5.dp.toPx()
                                val horizontalInset = thumbRadius
                                val trackWidth = (size.width - horizontalInset * 2f).coerceAtLeast(0f)
                                val playedWidth = trackWidth * seekFraction
                                drawLine(
                                    color = Color.White.copy(alpha = 0.32f),
                                    start = Offset(horizontalInset, centerY),
                                    end = Offset(horizontalInset + trackWidth, centerY),
                                    strokeWidth = trackHeight
                                )
                                drawLine(
                                    color = YimlyPink,
                                    start = Offset(horizontalInset, centerY),
                                    end = Offset(horizontalInset + playedWidth, centerY),
                                    strokeWidth = trackHeight
                                )
                                drawCircle(
                                    color = YimlyPink,
                                    radius = thumbRadius,
                                    center = Offset(horizontalInset + playedWidth, centerY)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPos),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = formatTime(totalDuration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }

                    // Playback Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier.size(44.dp).testTag("shuffle_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (playbackInfo.isShuffle) YimlyPink else Color.White.copy(alpha = 0.82f),
                                    modifier = Modifier.size(22.dp)
                                )
                                if (playbackInfo.isShuffle) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(YimlyPink)
                                    )
                                }
                            }
                        }

                        // Previous Button
                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier.size(48.dp).testTag("prev_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                 .background(Color.White)
                                .clickable(onClick = onTogglePlayPause)
                                .testTag("full_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackInfo.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF8E1B5A),
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Next Button
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier.size(48.dp).testTag("next_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Repeat Button
                        IconButton(
                            onClick = onCycleRepeatMode,
                            modifier = Modifier.size(44.dp).testTag("repeat_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (playbackInfo.repeatMode != RepeatMode.OFF) YimlyPink else Color.White.copy(alpha = 0.82f),
                                    modifier = Modifier.size(22.dp)
                                )
                                if (playbackInfo.repeatMode != RepeatMode.OFF) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(YimlyPink)
                                    )
                                }
                            }
                        }
                    }
                }
            // 3. Matching gap between playback controls and the queue section
            Spacer(modifier = Modifier.height(sectionGap))

            // Scrollable Queue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF4A2942).copy(alpha = 0.88f))
            ) {
                com.example.ui.queue.QueueView(
                    queue = playbackInfo.queue,
                    currentIndex = playbackInfo.currentQueueIndex,
                    isPlaying = playbackInfo.isPlaying,
                    onPlayItem = onPlayQueueItem,
                    onRemoveItem = onRemoveFromQueue,
                    onReorderItem = onReorderQueue,
                    onClearQueue = onClearQueue,
                    modifier = Modifier.fillMaxSize(),
                    compactMode = true,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    lyricsEnabled = showLyrics,
                    onToggleInstrumental = onToggleInstrumental,
                    isInstrumental = playbackInfo.isInstrumental
                )
            }
        }
    }
}
