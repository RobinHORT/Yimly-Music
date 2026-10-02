package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.datastore.PreferencesManager
import com.example.data.datastore.ServerUrlConfig
import com.example.data.models.Song

fun String?.resolveCoverUrl(baseUrl: String = ServerUrlConfig.activeServerUrl): String? {
    if (this.isNullOrBlank()) return null
    val trimmed = this.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true) ||
        trimmed.startsWith("content://", ignoreCase = true)
    ) {
        return trimmed
    }
    val cleanBase = baseUrl.trimEnd('/')
    val cleanPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return "$cleanBase$cleanPath"
}

@Composable
fun PlaylistArtwork(
    coverUrl: String?,
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val resolvedCover = remember(coverUrl) { coverUrl.resolveCoverUrl() }
    if (!resolvedCover.isNullOrEmpty()) {
        AsyncImage(
            model = resolvedCover,
            contentDescription = "Playlist Cover",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        AutomaticPlaylistArtwork(songs = songs, modifier = modifier)
    }
}

@Composable
fun AutomaticPlaylistArtwork(
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val albumCovers = songs.mapNotNull { if (it.hasArtwork) "${ServerUrlConfig.activeServerUrl}/api/songs/${it.id}/artwork" else null }.take(4)

    Box(modifier = modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
        when (albumCovers.size) {
            0 -> {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
            }
            1 -> {
                AsyncImage(
                    model = albumCovers[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            2 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(model = albumCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    AsyncImage(model = albumCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                }
            }
            3 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(model = albumCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AsyncImage(model = albumCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Crop)
                        AsyncImage(model = albumCovers[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Crop)
                    }
                }
            }
            else -> { // 4 or more
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = albumCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = albumCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = albumCovers[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = albumCovers[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}
