package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.models.LyricsData
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.data.models.Song
import com.example.playback.RepeatMode
import com.example.ui.components.YimlyMicIcon
import com.example.ui.lyrics.LyricsView
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.YimlyPinkGlow

@Composable
fun FullscreenLyricsDialog(
    song: Song,
    lyricsData: LyricsData?,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    songOffsetMs: Long,
    lyricsConfig: LyricsDisplayConfig,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    onSaveLyricsConfig: (LyricsDisplayConfig) -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val displayedPosition = if (isSeeking) seekPosition.toLong() else currentPositionMs
    val safeDuration = durationMs.coerceAtLeast(1L)

    fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .clipToBounds()
        ) {
            if (!song.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.28f }
                )
            }

            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            YimlyPinkGlow.copy(alpha = 0.48f),
                            BackgroundDark.copy(alpha = 0.90f),
                            BackgroundDark,
                            BackgroundDark
                        )
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Close full-screen lyrics",
                            tint = TextPrimaryDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "NOW SINGING",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                        color = YimlyPink,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(62.dp).clip(MaterialTheme.shapes.medium)
                            .background(BackgroundDark.copy(alpha = 0.65f)),
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
                            YimlyMicIcon(tint = YimlyPink, modifier = Modifier.size(30.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            song.title,
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            song.artist,
                            color = TextSecondaryDark,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(BackgroundDark.copy(alpha = 0.30f))
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    LyricsView(
                        lyricsData = lyricsData,
                        currentPositionMs = displayedPosition,
                        isPlaying = isPlaying,
                        songOffsetMs = songOffsetMs,
                        config = lyricsConfig,
                        onSeekTo = onSeekTo,
                        onOpenSettings = onOpenSettings,
                        onOffsetChange = onLyricsOffsetChange,
                        onToggleFormatMode = {
                            val newMode = if (lyricsConfig.formatMode == LyricsFormatMode.ELRC) {
                                LyricsFormatMode.LRC
                            } else {
                                LyricsFormatMode.ELRC
                            }
                            onSaveLyricsConfig(lyricsConfig.copy(formatMode = newMode))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(12.dp))

                Slider(
                    value = displayedPosition.toFloat().coerceIn(0f, safeDuration.toFloat()),
                    onValueChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        onSeekTo(seekPosition.toLong())
                    },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = YimlyPink,
                        inactiveTrackColor = TextSecondaryDark.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(displayedPosition), color = TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(safeDuration), color = TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) YimlyPink else TextSecondaryDark,
                                modifier = Modifier.size(24.dp)
                            )
                            if (isShuffle) {
                                Box(Modifier.size(4.dp).clip(CircleShape).background(YimlyPink))
                            }
                        }
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous",
                            tint = TextPrimaryDark, modifier = Modifier.size(36.dp))
                    }

                    Box(
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(YimlyPink),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next",
                            tint = TextPrimaryDark, modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = onCycleRepeatMode, modifier = Modifier.size(48.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.OFF) YimlyPink else TextSecondaryDark,
                                modifier = Modifier.size(24.dp)
                            )
                            if (repeatMode != RepeatMode.OFF) {
                                Box(Modifier.size(4.dp).clip(CircleShape).background(YimlyPink))
                            }
                        }
                    }
                }
            }
        }
    }
}
