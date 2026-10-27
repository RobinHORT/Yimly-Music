package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("full_player_screen")
    ) {
        val artworkSize = minOf(maxWidth * 0.58f, maxHeight * 0.29f, 220.dp)

        // Mirror Karaoke's cover-art breathing, sweeping pink border, and soft glow.
        // The animation is active only while playback is running; pausing returns
        // the artwork to its resting size and glow without touching the audio pipeline.
        val artworkTransition = rememberInfiniteTransition(label = "artworkReactiveEffect")
        val artworkPulse by artworkTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = AnimationRepeatMode.Reverse
            ),
            label = "artworkPulse"
        )
        val borderSweep by artworkTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6500, easing = LinearEasing),
                repeatMode = AnimationRepeatMode.Restart
            ),
            label = "artworkBorderSweep"
        )
        val glowPulse by artworkTransition.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.48f,
            animationSpec = infiniteRepeatable(
                animation = tween(780, easing = FastOutSlowInEasing),
                repeatMode = AnimationRepeatMode.Reverse
            ),
            label = "artworkGlowPulse"
        )
        val artworkShape = RoundedCornerShape(20.dp)
        val artworkIsActive = playbackInfo.isPlaying
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                    .shadow(
                        elevation = if (artworkIsActive) (10f + glowPulse * 18f).dp else 6.dp,
                        shape = artworkShape,
                        ambientColor = YimlyPink.copy(alpha = if (artworkIsActive) 0.18f + glowPulse * 0.34f else 0.08f),
                        spotColor = YimlyPink.copy(alpha = if (artworkIsActive) 0.20f + glowPulse * 0.40f else 0.08f)
                    )
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
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = if (artworkIsActive) artworkPulse else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(SurfaceCardDark), contentAlignment = Alignment.Center) {
                        YimlyMicIcon(tint = TextSecondaryDark, modifier = Modifier.size(80.dp))
                    }
                }

                // Pink translucent gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFBE185D).copy(alpha = 0.48f),
                                    Color(0xFF9D174D).copy(alpha = 0.58f),
                                    BackgroundDark.copy(alpha = 0.90f)
                                )
                            )
                        )
                )

                // Overlaid synchronised lyrics view
                if (showLyrics) {
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
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Rotating conic-gradient border, matching Karaoke's pink radar sweep.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Artwork is square, so rotating this border rotates only the gradient visually.
                            rotationZ = if (artworkIsActive) borderSweep else 0f
                        }
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0x29F472B6),
                                    Color(0x38F472B6),
                                    Color(0xB8F9A8D4),
                                    Color(0xE6FFF5FB),
                                    Color(0x99F472B6),
                                    Color(0x29F472B6)
                                )
                            ),
                            shape = artworkShape
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
                        .background(SurfaceCardDark.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
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
                Box(modifier = Modifier.weight(1f)) {
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
