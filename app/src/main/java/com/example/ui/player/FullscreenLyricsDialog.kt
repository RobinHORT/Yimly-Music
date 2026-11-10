package com.example.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.YimlyApplication
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
    onSaveLyricsConfig: (LyricsDisplayConfig) -> Unit,
    playbackSpeed: Float = 1.0f
) {
    val context = LocalContext.current
    val app = context.applicationContext as? YimlyApplication
    val musicRepository = app?.musicRepository

    var activeLyricsData by remember(song.id, lyricsData) { mutableStateOf(lyricsData) }

    LaunchedEffect(song.id) {
        if (musicRepository != null) {
            val fetched = musicRepository.getLyricsForSong(song, LyricsFormatMode.ELRC, allowSongFallback = true)
            activeLyricsData = fetched
        }
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
            ArtworkMeshBackground(artworkUrl = song.artworkUrl)

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
                    lyricsData = activeLyricsData,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    songOffsetMs = songOffsetMs,
                    config = lyricsConfig.copy(
                        formatMode = LyricsFormatMode.ELRC,
                        highlightCurrentLine = false,
                        currentLineColorHex = "#FFFFFF"
                    ),
                    showFocusControls = false,
                    floatingFocusMode = false,
                    fullscreenCreditMode = true,
                    onSeekTo = onSeekTo,
                    onOpenSettings = onOpenSettings,
                    onOffsetChange = onLyricsOffsetChange,
                    playbackSpeed = playbackSpeed,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}


/**
 * Animated artwork-colour mesh used only by the fullscreen lyrics backdrop.
 * The artwork is sampled off the main thread; palette changes crossfade over one second.
 */
@Composable
private fun ArtworkMeshBackground(artworkUrl: String?) {
    val context = LocalContext.current
    var artworkColors by remember {
        mutableStateOf(
            listOf(
                Color(0xFF8E1B5A),
                Color(0xFF54245F),
                Color(0xFFB83280)
            )
        )
    }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(artworkUrl)
                .size(96, 96)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = result.drawable?.toBitmap() ?: throw IllegalStateException("Drawable is null")
            withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).generate()
                listOf(
                    palette.vibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: 0xFF8E1B5A.toInt(),
                    palette.darkVibrantSwatch?.rgb
                        ?: palette.darkMutedSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: 0xFF54245F.toInt(),
                    palette.lightVibrantSwatch?.rgb
                        ?: palette.lightMutedSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: 0xFFB83280.toInt()
                ).map(::Color)
            }
        }.onSuccess { extractedColors ->
            artworkColors = extractedColors
        }
    }

    val colorOne by animateColorAsState(
        targetValue = artworkColors[0],
        animationSpec = tween(durationMillis = 1000),
        label = "meshArtworkColorOne"
    )
    val colorTwo by animateColorAsState(
        targetValue = artworkColors[1],
        animationSpec = tween(durationMillis = 1000),
        label = "meshArtworkColorTwo"
    )
    val colorThree by animateColorAsState(
        targetValue = artworkColors[2],
        animationSpec = tween(durationMillis = 1000),
        label = "meshArtworkColorThree"
    )

    val transition = rememberInfiniteTransition(label = "lyricsArtworkMesh")
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Reverse
        ),
        label = "lyricsMeshWaveOffset"
    )

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Canvas(modifier = Modifier.fillMaxSize().blur(80.dp)) {
            val drift = waveOffset
            val radius = maxOf(size.width, size.height) * 0.78f

            val topLeft = Offset(
                x = -size.width * 0.04f + drift,
                y = -size.height * 0.05f + drift * 0.35f
            )
            val bottomRight = Offset(
                x = size.width * 1.04f - drift * 0.55f,
                y = size.height * 1.05f - drift
            )
            val upperRight = Offset(
                x = size.width * 1.02f - drift * 0.3f,
                y = size.height * 0.18f + drift * 0.65f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to colorOne.copy(alpha = 0.95f),
                        0.48f to colorOne.copy(alpha = 0.52f),
                        1f to Color.Transparent
                    ),
                    center = topLeft,
                    radius = radius
                ),
                radius = radius,
                center = topLeft
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to colorTwo.copy(alpha = 0.92f),
                        0.5f to colorTwo.copy(alpha = 0.5f),
                        1f to Color.Transparent
                    ),
                    center = bottomRight,
                    radius = radius
                ),
                radius = radius,
                center = bottomRight
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to colorThree.copy(alpha = 0.78f),
                        0.52f to colorThree.copy(alpha = 0.38f),
                        1f to Color.Transparent
                    ),
                    center = upperRight,
                    radius = radius * 0.72f
                ),
                radius = radius * 0.72f,
                center = upperRight
            )
        }

        // Keep lyrics and controls legible over even very bright artwork palettes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
    }
}
