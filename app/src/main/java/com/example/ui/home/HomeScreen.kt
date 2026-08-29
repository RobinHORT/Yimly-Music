package com.example.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.data.models.UserProfile
import com.example.ui.components.ArtistAvatarItem
import com.example.ui.components.MediaCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SongItemRow
import com.example.ui.components.YimlyCard
import com.example.ui.components.YimlyHeaderBrand
import com.example.ui.components.YimlyMicIcon
import com.example.ui.components.resolveCoverUrl
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.YimlyPinkGlow
import java.util.Calendar

@Composable
fun HomeScreen(
    userProfile: UserProfile?,
    allSongs: List<Song>,
    recentlyPlayed: List<Song>,
    recentlyAdded: List<Song>,
    favoriteSongs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onFavoritesClick: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToNowPlaying: (Song) -> Unit = onAddToQueue,
    onAddToPlaylist: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Header with Yimly Branding & Greeting
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YimlyHeaderBrand(
                        badgeSize = 40.dp,
                        showSubtitle = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimaryDark
                )
                if (userProfile != null) {
                    Text(
                        text = "Welcome back, ${userProfile.displayName ?: userProfile.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            }
        }

        // 2. Hero Banner Card with Yimly Microphone Branding
        item {
            YimlyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    SurfaceElevatedDark,
                                    SurfaceCardDark
                                )
                            )
                        )
                ) {
                    // Ambient Yimly Glow & Micro Backdrop
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        YimlyMicIcon(
                            tint = YimlyPink.copy(alpha = 0.15f),
                            modifier = Modifier.size(96.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YimlyPink
                        ) {
                            Text(
                                text = "YIMLY KARAOKE STREAMING",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Stream Everywhere",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "High Fidelity • Synced Lyrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        // 3. Quick Play Shortcuts (Liked Songs & Top Playlists)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Liked Songs Quick Card
                    YimlyCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("home_quick_favorites"),
                        onClick = onFavoritesClick
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(YimlyPink, YimlyPink.copy(alpha = 0.6f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Liked Songs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    // First playlist quick card
                    val topPl = playlists.firstOrNull()
                    if (topPl != null) {
                        YimlyCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .testTag("home_quick_playlist"),
                            onClick = { onPlaylistClick(topPl) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(SurfaceElevatedDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!topPl.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = topPl.coverUrl.resolveCoverUrl(),
                                            contentDescription = topPl.name,
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
                                Text(
                                    text = topPl.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Recently Played Section
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently Played")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed) { song ->
                        MediaCard(
                            title = song.title,
                            subtitle = song.artist,
                            imageUrl = song.artworkUrl,
                            onClick = { onSongClick(song, recentlyPlayed) },
                            onPlayClick = { onSongClick(song, recentlyPlayed) },
                            testTag = "recent_song_${song.id}"
                        )
                    }
                }
            }
        }

        // 5. Recently Added Section
        if (recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently Added")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyAdded) { song ->
                        MediaCard(
                            title = song.title,
                            subtitle = song.artist,
                            imageUrl = song.artworkUrl,
                            onClick = { onSongClick(song, recentlyAdded) },
                            onPlayClick = { onSongClick(song, recentlyAdded) },
                            testTag = "added_song_${song.id}"
                        )
                    }
                }
            }
        }

        // 6. Featured Albums
        if (albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums) { album ->
                        MediaCard(
                            title = album.title,
                            subtitle = "${album.artist} • ${album.year ?: ""}",
                            imageUrl = album.artworkUrl,
                            onClick = { onAlbumClick(album) },
                            onPlayClick = { onAlbumClick(album) },
                            testTag = "album_card_${album.id}"
                        )
                    }
                }
            }
        }

        // 7. Artists Section
        if (artists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artists) { artist ->
                        ArtistAvatarItem(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
        }

        // 8. Community Playlists Section
        val communityPlaylists = playlists.filter { it.isPublic }
        item {
            SectionHeader(title = "Community Playlists")
            if (communityPlaylists.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.testTag("home_community_playlists_row")
                ) {
                    items(communityPlaylists) { playlist ->
                        CommunityPlaylistHomeCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCardDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_community_playlists_empty")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF9C27B0).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = Color(0xFF9C27B0),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "No community playlists yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Publish a playlist to share with the community",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            }
        }

        // 9. Playlists Section
        val myAndSharedPlaylists = playlists.filter { !it.isPublic }
        if (myAndSharedPlaylists.isNotEmpty()) {
            item {
                SectionHeader(title = "Playlists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(myAndSharedPlaylists) { playlist ->
                        MediaCard(
                            title = playlist.name,
                            subtitle = playlist.description ?: "${playlist.songCount} songs",
                            imageUrl = playlist.coverUrl,
                            onClick = { onPlaylistClick(playlist) },
                            onPlayClick = { onPlaylistClick(playlist) },
                            testTag = "playlist_card_${playlist.id}"
                        )
                    }
                }
            }
        }

        // 10. All Songs Feed
        if (allSongs.isNotEmpty()) {
            item {
                SectionHeader(title = "Made for You")
            }
            items(allSongs.take(8)) { song ->
                SongItemRow(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { onSongClick(song, allSongs) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onPlay = { onSongClick(song, allSongs) },
                    onAddToNowPlaying = { onAddToNowPlaying(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun CommunityPlaylistHomeCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCardDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .testTag("home_community_playlist_${playlist.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Artwork container with badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevatedDark),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.components.PlaylistArtwork(
                    coverUrl = playlist.coverUrl,
                    songs = playlist.songs,
                    modifier = Modifier.fillMaxSize()
                )

                // Community badge on top-left of artwork
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF9C27B0).copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "COMMUNITY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playlist Name
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Owner name
            Text(
                text = "by ${playlist.ownerName ?: "test"}",
                style = MaterialTheme.typography.bodySmall,
                color = YimlyPink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Song count
            Text(
                text = "${playlist.songCount} songs",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark,
                maxLines = 1
            )
        }
    }
}
