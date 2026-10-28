package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.models.Song
import com.example.playback.RepeatMode
import com.example.ui.components.YimlyMicIcon
import com.example.ui.lyrics.LyricsView
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceElevatedDark

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
                .pointerInput(onPrevious, onNext) {
                    var horizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onDragEnd = {
                            when {
                                horizontalDrag > 90f -> onPrevious()
                                horizontalDrag < -90f -> onNext()
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            horizontalDrag += dragAmount
                            change.consume()
                        }
                    )
                }
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
                modifier = Modifier.fillMaxSize().background(Color(0xFF190D18).copy(alpha = 0.28f))
            )

            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFBE185D).copy(alpha = 0.42f),
                            Color(0xFF9D174D).copy(alpha = 0.52f),
                            Color(0xFF230C1D).copy(alpha = 0.78f)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close full-screen lyrics", tint = TextPrimaryDark, modifier = Modifier.size(28.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = TextPrimaryDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = TextSecondaryDark, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(SurfaceElevatedDark)
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp)).padding(horizontal = 3.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onLyricsOffsetChange(songOffsetMs - 200L) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = "-0.2s", tint = TextPrimaryDark, modifier = Modifier.size(14.dp))
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onLyricsOffsetChange(0L) }.padding(horizontal = 5.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Text(text = if (songOffsetMs == 0L) "Sync" else String.format(java.util.Locale.US, "%+.1fs", songOffsetMs / 1000f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (songOffsetMs != 0L) YimlyPink else TextSecondaryDark)
                            }
                            IconButton(onClick = { onLyricsOffsetChange(songOffsetMs + 200L) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "+0.2s", tint = TextPrimaryDark, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Lyrics are deliberately rendered directly on the artwork backdrop:
                // no lyric card, border, seek bar, or playback-control row.
                LyricsView(
                    lyricsData = lyricsData,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    songOffsetMs = songOffsetMs,
                    config = lyricsConfig.copy(
                        highlightCurrentLine = false,
                        currentLineColorHex = "#FFFFFF"
                    ),
                    showFocusControls = false,
                    floatingFocusMode = true,
                    onSeekTo = onSeekTo,
                    onOpenSettings = onOpenSettings,
                    onOffsetChange = onLyricsOffsetChange,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}
