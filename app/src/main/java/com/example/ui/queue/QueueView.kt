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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
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
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    val currentSong = queue.getOrNull(currentIndex)
    val upcomingSongs = if (currentIndex < queue.size - 1) {
        queue.subList(currentIndex + 1, queue.size)
    } else {
        emptyList()
    }
    // The expanded player is a Now Playing playlist: keep the active song in the list.
    val displayedSongs = if (compactMode) queue else upcomingSongs
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, queue.size, compactMode, listState) {
        if (!compactMode || currentIndex !in queue.indices) return@LaunchedEffect

        coroutineScope {
            var idleJob: Job? = null
            var isAutoRecentering = false
            var skipNextIdleCountdown = false

            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { scrolling ->
                    if (scrolling) {
                        // Our own animation also marks the list as scrolling. Do not cancel
                        // that animation; only user-initiated scrolling should reset the timer.
                        if (!isAutoRecentering) idleJob?.cancel()
                    } else if (skipNextIdleCountdown) {
                        // Do not immediately schedule another timer when our animation ends.
                        skipNextIdleCountdown = false
                    } else {
                        idleJob?.cancel()
                        idleJob = launch {
                            delay(4_000)
                            if (!listState.isScrollInProgress && currentIndex in queue.indices) {
                                isAutoRecentering = true
                                try {
                                    listState.animateScrollToItem(currentIndex)
                                } finally {
                                    isAutoRecentering = false
                                    skipNextIdleCountdown = true
                                }
                            }
                        }
                    }
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("queue_view")
    ) {
        // Queue Header with Clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compactMode) 16.dp else 8.dp,
                    end = if (compactMode) 16.dp else 8.dp,
                    top = if (compactMode) 18.dp else 8.dp,
                    bottom = if (compactMode) 18.dp else 8.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (compactMode) "Now Playing" else "Play Queue",
                    style = if (compactMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                if (compactMode && displayedSongs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${displayedSongs.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondaryDark.copy(alpha = 0.70f)
                    )
                }
            }

            if (upcomingSongs.isNotEmpty() && !compactMode) {
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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (compactMode) 16.dp else 8.dp,
                end = if (compactMode) 16.dp else 8.dp,
                top = 0.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 8.dp)
        ) {
            // 1. Currently Playing Header & Card
            if (currentSong != null && !compactMode) {
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
            if (!compactMode) item {
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

            if (displayedSongs.isEmpty() && !compactMode) {
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
            }
            if (displayedSongs.isNotEmpty()) {
                itemsIndexed(displayedSongs) { relIndex, song ->
                    val actualIndex = if (compactMode) relIndex else currentIndex + 1 + relIndex
                    val isActive = compactMode && actualIndex == currentIndex

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (compactMode) Modifier
                                else Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (isActive) YimlyPink else SurfaceBorderDark, RoundedCornerShape(12.dp))
                            )
                            .clickable { onPlayItem(actualIndex) }
                            .testTag("queue_item_$actualIndex"),
                        color = if (isActive) YimlyPink.copy(alpha = 0.16f) else if (compactMode) Color.Transparent else SurfaceCardDark
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (compactMode) 0.dp else 8.dp, vertical = if (compactMode) 4.dp else 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reorder arrows remain available in the full queue, but are hidden
                            // in the compact expanded-player queue to keep the artwork aligned first.
                            if (!compactMode) {
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
                            }

                            // Artwork
                            Box(
                                modifier = Modifier
                                    .size(if (compactMode) 32.dp else 40.dp)
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
                                    color = if (isActive) YimlyPink else TextPrimaryDark,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
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
                                modifier = Modifier.size(if (compactMode) 30.dp else 36.dp).testTag("remove_queue_$actualIndex")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(if (compactMode) 16.dp else 18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
