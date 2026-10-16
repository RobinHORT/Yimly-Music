package com.example.ui.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
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
import com.example.ui.theme.YimlyPinkGlow
import androidx.compose.runtime.collectAsState
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

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Player, 1 = Lyrics, 2 = Queue
    var showLyricsSettings by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        onSetLyricsActive(selectedTab == 1)
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
        val isLandscape = maxWidth > maxHeight

        // Ambient Yimly Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            YimlyPinkGlow.copy(alpha = 0.3f),
                            BackgroundDark
                        ),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(44.dp).testTag("collapse_player_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextPrimaryDark,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM ALBUM",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                        color = TextSecondaryDark
                    )
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(44.dp).testTag("player_more_options_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextPrimaryDark,
                            modifier = Modifier.size(24.dp)
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
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = TextPrimaryDark) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist(song)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Customize Lyrics", color = TextPrimaryDark) },
                            onClick = {
                                menuExpanded = false
                                showLyricsSettings = true
                            }
                        )
                    }
                }
            }

            // Mode Selector Tabs (Player | Lyrics | Queue)
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp)),
                containerColor = SurfaceCardDark,
                contentColor = YimlyPink,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = YimlyPink,
                            height = 3.dp
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Player", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    selectedContentColor = YimlyPink,
                    unselectedContentColor = TextSecondaryDark
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Lyrics", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    selectedContentColor = YimlyPink,
                    unselectedContentColor = TextSecondaryDark
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Queue (${playbackInfo.queue.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    selectedContentColor = YimlyPink,
                    unselectedContentColor = TextSecondaryDark
                )
            }

            // 2. Main Content Area (Player, Lyrics, or Queue)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                if (selectedTab == 0) {
                    // Standard Player View
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SurfaceCardDark)
                                    .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!song.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = song.artworkUrl,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    YimlyMicIcon(
                                        tint = TextSecondaryDark,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1.2f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark,
                                    maxLines = 1
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondaryDark,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        // Portrait layout with Card
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!song.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = song.artworkUrl,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        YimlyMicIcon(
                                            tint = TextSecondaryDark,
                                            modifier = Modifier.size(90.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTab == 1) {
                    // Synced Lyrics View
                    val lyricsPos = if (isUserSeeking) {
                        seekPositionMs.toLong()
                    } else if (lyricsPositionMs > 0L) {
                        lyricsPositionMs
                    } else {
                        currentPos
                    }
                    LyricsView(
                        lyricsData = lyricsData,
                        currentPositionMs = lyricsPos,
                        isPlaying = playbackInfo.isPlaying,
                        songOffsetMs = songOffsetMs,
                        config = lyricsConfig,
                        onSeekTo = onSeekTo,
                        onOpenSettings = { showLyricsSettings = true },
                        onOffsetChange = onLyricsOffsetChange,
                        onToggleFormatMode = {
                            val newMode = if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) LyricsFormatMode.LRC else LyricsFormatMode.ELRC
                            onSaveLyricsConfig(lyricsConfig.copy(formatMode = newMode))
                        }
                    )
                } else {
                    // Queue View
                    QueueView(
                        queue = playbackInfo.queue,
                        currentIndex = playbackInfo.currentQueueIndex,
                        isPlaying = playbackInfo.isPlaying,
                        onPlayItem = onPlayQueueItem,
                        onRemoveItem = onRemoveFromQueue,
                        onReorderItem = onReorderQueue,
                        onClearQueue = onClearQueue
                    )
                }
            }

            // 3. Track Details & Action Controls (Title, Artist, Heart, Instrumental, Lyrics Format)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(44.dp).testTag("full_player_fav_btn")
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) YimlyPink else TextSecondaryDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Lyrics Format Toggle Button (eLRC <-> LRC)
                    Surface(
                        onClick = {
                            val newMode = if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) LyricsFormatMode.LRC else LyricsFormatMode.ELRC
                            onSaveLyricsConfig(lyricsConfig.copy(formatMode = newMode))
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) YimlyPink.copy(alpha = 0.2f) else SurfaceCardDark,
                        border = BorderStroke(
                            1.dp,
                            if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) YimlyPink else SurfaceBorderDark
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("lyrics_format_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = "Lyrics Format Mode",
                                tint = if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) YimlyPink else TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lyricsConfig.formatMode.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) YimlyPink else TextSecondaryDark
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            android.util.Log.d("INSTRUMENTAL_DEBUG", "INSTRUMENTAL_UI_CLICK")
                            onToggleInstrumental()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (playbackInfo.isInstrumental) YimlyPink.copy(alpha = 0.2f) else SurfaceCardDark,
                        border = BorderStroke(
                            1.dp,
                            if (playbackInfo.isInstrumental) YimlyPink else SurfaceBorderDark
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("instrumental_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            YimlyMicIcon(
                                tint = if (playbackInfo.isInstrumental) YimlyPink else TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (playbackInfo.isInstrumental) "INST ON" else "INST",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (playbackInfo.isInstrumental) YimlyPink else TextSecondaryDark
                            )
                        }
                    }
                }
            }

            // 4. Progress Scrubber Slider
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
                    modifier = Modifier.fillMaxWidth().testTag("player_progress_slider")
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

            // 5. Playback Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp).testTag("shuffle_btn")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackInfo.isShuffle) YimlyPink else TextSecondaryDark,
                            modifier = Modifier.size(24.dp)
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
                    modifier = Modifier.size(54.dp).testTag("prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimaryDark,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause Button (Large Yimly Pink glowing circle)
                Box(
                    modifier = Modifier
                        .size(68.dp)
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
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(54.dp).testTag("next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimaryDark,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Button
                IconButton(
                    onClick = onCycleRepeatMode,
                    modifier = Modifier.size(48.dp).testTag("repeat_btn")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (playbackInfo.repeatMode != RepeatMode.OFF) YimlyPink else TextSecondaryDark,
                            modifier = Modifier.size(24.dp)
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
    }
}
