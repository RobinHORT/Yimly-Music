package com.example.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.Song
import com.example.ui.components.AnimatedEqualizer
import com.example.ui.components.YimlyCard
import com.example.ui.components.YimlyMicIcon
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@Composable
fun QueueView(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onReorderItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong = queue.getOrNull(currentIndex)
    val upcomingSongs = if (currentIndex < queue.size - 1) {
        queue.subList(currentIndex + 1, queue.size)
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("queue_view")
    ) {
        // Queue Header with Clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Play Queue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )

            if (upcomingSongs.isNotEmpty()) {
                Button(
                    onClick = onClearQueue,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevatedDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("clear_queue_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = null,
                        tint = YimlyPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Next", color = TextPrimaryDark, fontSize = 12.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Currently Playing Header & Card
            if (currentSong != null) {
                item {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        color = YimlyPink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    YimlyCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevatedDark),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentSong.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = currentSong.artworkUrl,
                                        contentDescription = currentSong.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    YimlyMicIcon(tint = YimlyPink, modifier = Modifier.size(22.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPlaying) {
                                        AnimatedEqualizer(color = YimlyPink)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = YimlyPink,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = YimlyPink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentSong.artist} • ${currentSong.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = currentSong.formattedDuration(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            }

            // 2. Next Up Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NEXT FROM: QUEUE (${upcomingSongs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (upcomingSongs.isEmpty()) {
                item {
                    YimlyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            YimlyMicIcon(
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Queue is empty",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Add songs to queue from library or search",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(upcomingSongs) { relIndex, song ->
                    val actualIndex = currentIndex + 1 + relIndex

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                            .clickable { onPlayItem(actualIndex) }
                            .testTag("queue_item_$actualIndex"),
                        color = SurfaceCardDark
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Up / Down Reorder
                            Column {
                                if (relIndex > 0) {
                                    IconButton(
                                        onClick = { onReorderItem(actualIndex, actualIndex - 1) },
                                        modifier = Modifier.size(24.dp).testTag("move_up_$actualIndex")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (relIndex < upcomingSongs.size - 1) {
                                    IconButton(
                                        onClick = { onReorderItem(actualIndex, actualIndex + 1) },
                                        modifier = Modifier.size(24.dp).testTag("move_down_$actualIndex")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Artwork
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevatedDark),
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
                                    YimlyMicIcon(tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = song.formattedDuration(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )

                            // Remove from Queue
                            IconButton(
                                onClick = { onRemoveItem(actualIndex) },
                                modifier = Modifier.size(36.dp).testTag("remove_queue_$actualIndex")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
