package com.example.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.Artist
import com.example.data.models.Song
import com.example.ui.components.SectionHeader
import com.example.ui.components.SongItemRow
import com.example.ui.components.YimlyMicIcon
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToNowPlaying: (Song) -> Unit = onAddToQueue,
    onAddToPlaylist: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("artist_detail_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Hero Artist Image Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                if (!artist.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.avatarUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SurfaceElevatedDark),
                        contentAlignment = Alignment.Center
                    ) {
                        YimlyMicIcon(tint = SurfaceBorderDark, modifier = Modifier.size(120.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    BackgroundDark
                                )
                            )
                        )
                )

                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 24.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevatedDark.copy(alpha = 0.8f))
                        .border(1.dp, SurfaceBorderDark, CircleShape)
                        .testTag("artist_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val subtitleParts = mutableListOf<String>()
                    if (artist.genres.isNotEmpty()) {
                        subtitleParts.add(artist.genres.joinToString(", "))
                    }
                    if (songs.isNotEmpty()) {
                        subtitleParts.add("${songs.size} ${if (songs.size == 1) "track" else "tracks"}")
                    }
                    if (artist.monthlyListeners > 0L) {
                        subtitleParts.add("${artist.monthlyListeners / 1_000_000}M listeners")
                    }
                    Text(
                        text = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ") else "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                    if (!artist.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = artist.bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            // Action Buttons Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { songs.firstOrNull()?.let { onSongClick(it, songs) } },
                        colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("artist_play_all_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Artist", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { songs.shuffled().firstOrNull()?.let { onSongClick(it, songs.shuffled()) } },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevatedDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("artist_shuffle_btn")
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = TextPrimaryDark)
                    }
                }
            }

            // Popular Tracks
            item {
                SectionHeader(title = "Popular Tracks")
            }

            items(songs) { song ->
                SongItemRow(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { onSongClick(song, songs) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onPlay = { onSongClick(song, songs) },
                    onAddToNowPlaying = { onAddToNowPlaying(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks found for this artist.",
                        color = TextSecondaryDark,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
