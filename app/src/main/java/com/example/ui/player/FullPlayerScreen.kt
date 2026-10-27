package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateTopPadding
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
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

        val artworkSize = minOf(maxWidth * 0.58f, maxHeight * 0.32f, 220.dp)

        // Keep the cover image still. Only the pink border sweeps and pulses while playing.
        val artworkTransition = rememberInfiniteTransition(label = "artworkBorderEffect")
        val borderSweep by artworkTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6500, easing = LinearEasing),
                repeatMode = AnimationRepeatMode.Restart
            ),
            label = "artworkBorderSweep"
        )
        val borderGlow by artworkTransition.animateFloat(
            initialValue = 0.48f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = AnimationRepeatMode.Reverse
            ),
            label = "artworkBorderGlow"
        )
        val artworkShape = RoundedCornerShape(20.dp)
        val artworkIsActive = playbackInfo.isPlaying
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Keep the header below the status bar without shrinking the queue's
            // available height; the queue background can now bleed past the bottom edge.
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))

            // Header: lyrics toggle on the left, track details centred, collapse on the right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showLyrics = !showLyrics },
                    modifier = Modifier.size(48.dp).testTag("lyrics_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Toggle Lyrics",
                        tint = if (showLyrics) YimlyPink else TextPrimaryDark,
                        modifier = Modifier.size(28.dp)
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

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp).testTag("player_more_options_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextPrimaryDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(SurfaceElevatedDark)
                            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (song.isFavorite) "Remove from Liked Songs" else "Save to Liked Songs", color = TextPrimaryDark) },
                            onClick = { menuExpanded = false; onToggleFavorite() }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = TextPrimaryDark) },
                            onClick = { menuExpanded = false; onAddToPlaylist(song) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (playbackInfo.isInstrumental) "Instrumental: ON" else "Instrumental: OFF", color = TextPrimaryDark) },
                            onClick = { menuExpanded = false; onToggleInstrumental() }
                        )
                        DropdownMenuItem(
                            text = { Text("Customize Lyrics", color = TextPrimaryDark) },
                            onClick = { menuExpanded = false; showLyricsSettings = true }
                        )
                    }
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
                        modifier = Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(15.dp))
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(15.dp)).background(SurfaceCardDark), contentAlignment = Alignment.Center) {
                        YimlyMicIcon(tint = TextSecondaryDark, modifier = Modifier.size(80.dp))
                    }
                }

                // Keep artwork natural normally; apply the focus tint only while lyrics are shown.
                if (showLyrics) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(15.dp)).background(
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
                        modifier = Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(15.dp))
                    )
                    // Tapping the lyric itself opens the same fullscreen lyrics view as the artwork.
                    Box(modifier = Modifier.fillMaxSize().padding(7.dp).clickable { showFullscreenLyrics = true })
                }

                // Rotating conic-gradient border, matching Karaoke's pink radar sweep.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .graphicsLayer {
                            // Rotate only the inset border layer; the album image is outside this layer.
                            rotationZ = if (artworkIsActive) borderSweep else 0f
                        }
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

            // 3. BOTTOM HALF — Queue + Controls (Fixed)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 0.dp)
            ) {
                // Fixed Controls. Track details are already shown in the header,
                // so keep this card focused on seek and playback controls.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Progress Scrubber Slider & Duration labels
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = currentPos.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
                            onValueChange = {
                                isUserSeeking = true
                                seekPositionMs = it
                            },
                            onValueChangeFinished = {
                                isUserSeeking = false
                                onSeekTo(seekPositionMs.toLong())
                            },
                            valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = YimlyPink,
                                inactiveTrackColor = SurfaceBorderDark
                            ),
                            modifier = Modifier.fillMaxWidth().height(24.dp).testTag("player_progress_slider")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPos),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                            Text(
                                text = formatTime(totalDuration),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
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
                                    tint = if (playbackInfo.isShuffle) YimlyPink else TextSecondaryDark,
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
                                tint = TextPrimaryDark,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(YimlyPink)
                                .clickable(onClick = onTogglePlayPause)
                                .testTag("full_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackInfo.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                tint = Color.White,
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
                                tint = TextPrimaryDark,
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
                                    tint = if (playbackInfo.repeatMode != RepeatMode.OFF) YimlyPink else TextSecondaryDark,
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
                Spacer(modifier = Modifier.height(2.dp))

                // Scrollable Queue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // requiredWidth bypasses the Column's reduced max-width constraint,
                        // so the queue background truly reaches both screen edges.
                        .requiredWidth(maxWidth + 32.dp)
                        .offset(x = (-16).dp, y = 24.dp)
                        // Extend the actual layout bounds past the display bottom so the
                        // background is not clipped to the safe-area content height.
                        .drawBehind {
                            drawRect(
                                color = Color(0xFF4A2942).copy(alpha = 0.88f),
                                size = Size(size.width, size.height)
                            )
                        }
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
                        compactMode = true
                    )
                }
            }
        }
    }
}
