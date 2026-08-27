package com.example.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.ui.components.SongItemRow
import com.example.ui.components.YimlyCard
import com.example.ui.components.YimlyMicIcon
import com.example.ui.components.resolveCoverUrl
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@Composable
fun MusicLibraryScreen(
    allSongs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    favoriteSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToNowPlaying: (Song) -> Unit = onAddToQueue,
    onAddToPlaylist: (Song) -> Unit = {},
    onCreatePlaylist: (String, String?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Tracks", "Albums", "Artists", "Playlists", "Favorites")

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }
    var newPlaylistIsPublic by remember { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist", fontWeight = FontWeight.Bold, color = YimlyPink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_playlist_name_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YimlyPink,
                            focusedLabelColor = YimlyPink,
                            cursorColor = YimlyPink,
                            unfocusedContainerColor = SurfaceCardDark,
                            focusedContainerColor = SurfaceCardDark,
                            unfocusedBorderColor = SurfaceBorderDark
                        )
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_playlist_desc_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YimlyPink,
                            focusedLabelColor = YimlyPink,
                            cursorColor = YimlyPink,
                            unfocusedContainerColor = SurfaceCardDark,
                            focusedContainerColor = SurfaceCardDark,
                            unfocusedBorderColor = SurfaceBorderDark
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCardDark)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { newPlaylistIsPublic = !newPlaylistIsPublic }
                            .testTag("create_playlist_public_toggle")
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = newPlaylistIsPublic,
                            onCheckedChange = { newPlaylistIsPublic = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = YimlyPink)
                        )
                        Text("Publish to Community (Public)", color = TextPrimaryDark, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim(), newPlaylistDesc.trim().takeIf { it.isNotBlank() }, newPlaylistIsPublic)
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                            newPlaylistIsPublic = false
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_create_playlist_btn")
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showCreatePlaylistDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceElevatedDark
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("music_library_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimaryDark
                )

                IconButton(
                    onClick = { showCreatePlaylistDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevatedDark)
                        .border(1.dp, SurfaceBorderDark, CircleShape)
                        .testTag("create_playlist_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = YimlyPink,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Scrollable Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                contentColor = YimlyPink,
                divider = {},
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
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selectedContentColor = YimlyPink,
                        unselectedContentColor = TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Tracks Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (allSongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        YimlyMicIcon(tint = TextSecondaryDark.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                        Text(
                                            text = "No tracks found in library.",
                                            color = TextSecondaryDark,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else {
                            // Quick Action Header: Play All / Shuffle All
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { allSongs.firstOrNull()?.let { onSongClick(it, allSongs) } },
                                        colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.testTag("play_all_tracks_btn")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Play All (${allSongs.size})", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { allSongs.shuffled().firstOrNull()?.let { onSongClick(it, allSongs.shuffled()) } },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevatedDark),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.testTag("shuffle_all_tracks_btn")
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Shuffle", color = TextPrimaryDark)
                                    }
                                }
                            }

                            items(allSongs) { song ->
                                SongItemRow(
                                    song = song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentSong?.id == song.id,
                                    onSongClick = { onSongClick(song, allSongs) },
                                    onToggleFavorite = { onToggleFavorite(song.id) },
                                    onPlay = { onSongClick(song, allSongs) },
                                    onAddToNowPlaying = { onAddToNowPlaying(song) },
                                    onAddToPlaylist = { onAddToPlaylist(song) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Albums List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (albums.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        YimlyMicIcon(tint = TextSecondaryDark.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                        Text(
                                            text = "No albums found in library.",
                                            color = TextSecondaryDark,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else {
                            items(albums) { album ->
                                YimlyCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("library_album_${album.id}"),
                                    onClick = { onAlbumClick(album) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(SurfaceElevatedDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!album.artworkUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = album.artworkUrl,
                                                    contentDescription = album.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                YimlyMicIcon(
                                                    tint = TextSecondaryDark,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimaryDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val subtitleParts = listOfNotNull(
                                                album.artist.takeIf { it.isNotBlank() },
                                                album.year?.toString()?.takeIf { it.isNotBlank() },
                                                album.genre?.takeIf { it.isNotBlank() },
                                                "${album.songCount} ${if (album.songCount == 1) "track" else "tracks"}".takeIf { album.songCount > 0 }
                                            )
                                            Text(
                                                text = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ") else "Album",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondaryDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Artists List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (artists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        YimlyMicIcon(tint = TextSecondaryDark.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                        Text(
                                            text = "No artists found in library.",
                                            color = TextSecondaryDark,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else {
                            items(artists) { artist ->
                                YimlyCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("library_artist_${artist.id}"),
                                    onClick = { onArtistClick(artist) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(SurfaceElevatedDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!artist.avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = artist.avatarUrl,
                                                    contentDescription = artist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                YimlyMicIcon(
                                                    tint = TextSecondaryDark,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimaryDark
                                            )
                                            val subtitleParts = mutableListOf<String>()
                                            if (artist.genres.isNotEmpty()) {
                                                subtitleParts.add(artist.genres.joinToString(", "))
                                            }
                                            if (artist.songCount > 0) {
                                                subtitleParts.add("${artist.songCount} ${if (artist.songCount == 1) "track" else "tracks"}")
                                            }
                                            if (artist.monthlyListeners > 0L) {
                                                subtitleParts.add("${artist.monthlyListeners / 1_000_000}M listeners")
                                            }
                                            Text(
                                                text = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ") else "Artist",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondaryDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Playlists List
                    val myPlaylists = playlists.filter { (it.isOwner || it.permission == "owner") && !it.isPublic }
                    val sharedWithMe = playlists.filter { !it.isOwner && it.permission != "owner" && !it.isPublic }
                    val communityPlaylists = playlists.filter { it.isPublic }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (playlists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        YimlyMicIcon(tint = TextSecondaryDark.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                        Text(
                                            text = "No playlists created yet. Tap + to create one.",
                                            color = TextSecondaryDark,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else {
                            if (myPlaylists.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "MY PLAYLISTS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = YimlyPink,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(myPlaylists) { playlist ->
                                    PlaylistCardItem(playlist = playlist, onPlaylistClick = onPlaylistClick)
                                }
                            }

                            if (sharedWithMe.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "SHARED WITH ME",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2196F3),
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(sharedWithMe) { playlist ->
                                    PlaylistCardItem(playlist = playlist, onPlaylistClick = onPlaylistClick)
                                }
                            }

                            if (communityPlaylists.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "COMMUNITY PLAYLISTS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9C27B0),
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(communityPlaylists) { playlist ->
                                    PlaylistCardItem(playlist = playlist, onPlaylistClick = onPlaylistClick)
                                }
                            }
                        }
                    }
                }
                4 -> {
                    // Favorites Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            // Liked Songs Header Card
                            YimlyCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(YimlyPink, YimlyPink.copy(alpha = 0.6f))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Liked Songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                        Text("${favoriteSongs.size} favorite tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryDark)
                                    }
                                }
                            }
                        }

                        if (favoriteSongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No favorite songs yet.\nTap the heart icon on any song to save it here.",
                                        textAlign = TextAlign.Center,
                                        color = TextSecondaryDark
                                    )
                                }
                            }
                        } else {
                            items(favoriteSongs) { song ->
                                SongItemRow(
                                    song = song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentSong?.id == song.id,
                                    onSongClick = { onSongClick(song, favoriteSongs) },
                                    onToggleFavorite = { onToggleFavorite(song.id) },
                                    onPlay = { onSongClick(song, favoriteSongs) },
                                    onAddToNowPlaying = { onAddToNowPlaying(song) },
                                    onAddToPlaylist = { onAddToPlaylist(song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCardItem(
    playlist: Playlist,
    onPlaylistClick: (Playlist) -> Unit
) {
    val badgeText = when {
        playlist.isPublic && (playlist.isOwner || playlist.permission == "owner") -> "Community • You Own"
        playlist.isPublic -> "Community • by ${playlist.ownerName ?: "user"}"
        playlist.isOwner || playlist.permission == "owner" -> "Private"
        playlist.canEdit -> "Shared • Can Edit"
        else -> "Shared • View Only"
    }
    val badgeBg = when {
        playlist.isPublic -> Color(0xFF9C27B0).copy(alpha = 0.2f)
        playlist.isOwner || playlist.permission == "owner" -> YimlyPink.copy(alpha = 0.2f)
        else -> Color(0xFF2196F3).copy(alpha = 0.2f)
    }
    val badgeBorder = when {
        playlist.isPublic -> Color(0xFF9C27B0)
        playlist.isOwner || playlist.permission == "owner" -> YimlyPink
        else -> Color(0xFF2196F3)
    }

    YimlyCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_playlist_${playlist.id}"),
        onClick = { onPlaylistClick(playlist) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevatedDark),
                contentAlignment = Alignment.Center
            ) {
                if (!playlist.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playlist.coverUrl.resolveCoverUrl(),
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    YimlyMicIcon(tint = YimlyPink, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = playlist.description ?: "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
