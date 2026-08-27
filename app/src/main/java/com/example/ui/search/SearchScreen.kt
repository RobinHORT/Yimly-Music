package com.example.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.SearchResult
import com.example.data.models.Song
import com.example.ui.components.ArtistAvatarItem
import com.example.ui.components.MediaCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SongItemRow
import com.example.ui.components.YimlyCard
import com.example.ui.components.YimlyMicIcon
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.YimlyPinkGlow

@Composable
fun SearchScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResult: SearchResult,
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
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Songs, 2 = Artists, 3 = Albums, 4 = Playlists
    val filters = listOf("All", "Songs", "Artists", "Albums", "Playlists")

    val sampleQueries = listOf(
        "Ariana hate",
        "Belinda Summer",
        "Beyonce",
        "Jay Z",
        "dont stop",
        "Synthwave",
        "Pop"
    )

    val genreCards = listOf(
        Pair("Pop & Hits", Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFFF5722)))),
        Pair("Hip-Hop & Rap", Brush.linearGradient(listOf(Color(0xFF673AB7), Color(0xFF3F51B5)))),
        Pair("Synthwave & Retro", Brush.linearGradient(listOf(Color(0xFF00BCD4), Color(0xFF009688)))),
        Pair("Latin & Dance", Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFFC107)))),
        Pair("R&B & Soul", Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63)))),
        Pair("Indie & Chill", Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))))
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("search_screen"),
        contentPadding = PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 120.dp)
    ) {
        // 1. Search Header & Search Input
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimaryDark
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input_field"),
                    placeholder = {
                        Text("What do you want to listen to?", color = TextSecondaryDark)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = YimlyPink
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag("clear_search_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextSecondaryDark
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceElevatedDark,
                        unfocusedContainerColor = SurfaceElevatedDark,
                        focusedBorderColor = YimlyPink,
                        unfocusedBorderColor = SurfaceBorderDark,
                        cursorColor = YimlyPink
                    )
                )

                // Quick Suggestion Chips
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sampleQueries) { q ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, if (searchQuery == q) YimlyPink else SurfaceBorderDark, RoundedCornerShape(16.dp))
                                .clickable { onQueryChange(q) }
                                .testTag("quick_search_$q"),
                            color = if (searchQuery == q) YimlyPink else SurfaceCardDark
                        ) {
                            Text(
                                text = q,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (searchQuery == q) Color.White else TextPrimaryDark,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Filter Tabs (All, Songs, Artists, Albums, Playlists) if search query is present
                if (searchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters.indices.toList()) { index ->
                            FilterChip(
                                selected = selectedFilter == index,
                                onClick = { selectedFilter = index },
                                label = { Text(filters[index], fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YimlyPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceCardDark,
                                    labelColor = TextSecondaryDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedFilter == index,
                                    borderColor = if (selectedFilter == index) YimlyPink else SurfaceBorderDark
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. If Search Query is Empty -> Show Genre Browse Cards
        if (searchQuery.isBlank()) {
            item {
                SectionHeader(title = "Browse All")
            }

            items(genreCards.chunked(2)) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { (genreName, brush) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(14.dp))
                                .clickable { onQueryChange(genreName.split(" ").first()) }
                                .testTag("genre_card_$genreName"),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(brush)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = genreName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 3. Search Results Display
            val showSongs = selectedFilter == 0 || selectedFilter == 1
            val showArtists = selectedFilter == 0 || selectedFilter == 2
            val showAlbums = selectedFilter == 0 || selectedFilter == 3
            val showPlaylists = selectedFilter == 0 || selectedFilter == 4

            val hasResults = searchResult.songs.isNotEmpty() ||
                    searchResult.artists.isNotEmpty() ||
                    searchResult.albums.isNotEmpty() ||
                    searchResult.playlists.isNotEmpty()

            if (!hasResults) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            YimlyMicIcon(
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Check your spelling or try search terms like artist or album",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            } else {
                // Top result (if available)
                if (selectedFilter == 0 && searchResult.songs.isNotEmpty()) {
                    val topSong = searchResult.songs.first()
                    item {
                        SectionHeader(title = "Top Result")
                        YimlyCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("top_search_result"),
                            onClick = { onSongClick(topSong, searchResult.songs) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceElevatedDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!topSong.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = topSong.artworkUrl,
                                            contentDescription = topSong.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        YimlyMicIcon(tint = YimlyPink, modifier = Modifier.size(36.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topSong.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = YimlyPink
                                    )
                                    Text(
                                        text = "Song • ${topSong.artist}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondaryDark
                                    )
                                }
                            }
                        }
                    }
                }

                // Matching Songs
                if (showSongs && searchResult.songs.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Songs (${searchResult.songs.size})")
                    }
                    items(searchResult.songs) { song ->
                        SongItemRow(
                            song = song,
                            isPlaying = isPlaying,
                            isCurrentSong = currentSong?.id == song.id,
                            onSongClick = { onSongClick(song, searchResult.songs) },
                            onToggleFavorite = { onToggleFavorite(song.id) },
                            onPlay = { onSongClick(song, searchResult.songs) },
                            onAddToNowPlaying = { onAddToNowPlaying(song) },
                            onAddToPlaylist = { onAddToPlaylist(song) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }

                // Matching Artists
                if (showArtists && searchResult.artists.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Artists (${searchResult.artists.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(searchResult.artists) { artist ->
                                ArtistAvatarItem(
                                    artist = artist,
                                    onClick = { onArtistClick(artist) }
                                )
                            }
                        }
                    }
                }

                // Matching Albums
                if (showAlbums && searchResult.albums.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Albums (${searchResult.albums.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResult.albums) { album ->
                                MediaCard(
                                    title = album.title,
                                    subtitle = "${album.artist} • ${album.year ?: ""}",
                                    imageUrl = album.artworkUrl,
                                    onClick = { onAlbumClick(album) },
                                    onPlayClick = { onAlbumClick(album) }
                                )
                            }
                        }
                    }
                }

                // Matching Playlists
                if (showPlaylists && searchResult.playlists.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Playlists (${searchResult.playlists.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResult.playlists) { playlist ->
                                MediaCard(
                                    title = playlist.name,
                                    subtitle = playlist.description ?: "${playlist.songCount} songs",
                                    imageUrl = playlist.coverUrl,
                                    onClick = { onPlaylistClick(playlist) },
                                    onPlayClick = { onPlaylistClick(playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
