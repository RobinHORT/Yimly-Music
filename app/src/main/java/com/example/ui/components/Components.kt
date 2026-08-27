package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.datastore.PreferencesManager
import com.example.data.models.Album
import com.example.data.models.Artist
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceBorderSubtle
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.YimlyPinkGlow

/**
 * Yimly Official Microphone SVG Icon
 */
@Composable
fun YimlyMicIcon(
    modifier: Modifier = Modifier,
    tint: Color = YimlyPink
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_yimly_mic),
        contentDescription = "Yimly Microphone",
        tint = tint,
        modifier = modifier
    )
}

/**
 * Yimly Official Logo Badge: squircle with microphone inside
 */
@Composable
fun YimlyLogoBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    badgeColor: Color = YimlyPink,
    iconColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(badgeSize * 0.32f))
            .background(
                Brush.linearGradient(
                    colors = listOf(badgeColor, badgeColor.copy(alpha = 0.85f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_yimly_mic_white),
            contentDescription = "Yimly Logo",
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Full Yimly Header Brand Wordmark
 */
@Composable
fun YimlyHeaderBrand(
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
    badgeSize: Dp = 38.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        YimlyLogoBadge(
            badgeSize = badgeSize,
            iconSize = (badgeSize.value * 0.54f).dp
        )
        Column {
            Text(
                text = "Yimly",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimaryDark,
                letterSpacing = (-0.5).sp,
                lineHeight = 22.sp
            )
            if (showSubtitle) {
                Text(
                    text = "KARAOKE STUDIO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryDark,
                    letterSpacing = 1.5.sp,
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Standard Yimly Card container with uniform background, border, and rounded corners
 */
@Composable
fun YimlyCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCardDark
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) YimlyPink else SurfaceBorderDark
        )
    ) {
        content()
    }
}

@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    color: Color = YimlyPink,
    height: Dp = 16.dp,
    barWidth: Dp = 3.dp
) {
    val transition = rememberInfiniteTransition(label = "equalizer")

    val h1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val heights = listOf(h1, h2, h3)

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount.coerceAtMost(3)) {
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height * heights[i])
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SongItemRow(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onSongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToNowPlaying: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onPlay: () -> Unit = onSongClick,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToPlaylistClick: (() -> Unit)? = null,
    isAdmin: Boolean = false,
    onEditLrc: (() -> Unit)? = null,
    onUploadLrc: (() -> Unit)? = null,
    onDeleteLrc: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isUserAdmin = isAdmin

    val getViewModel = {
        runCatching {
            val activity = context as? androidx.activity.ComponentActivity
            if (activity != null) {
                androidx.lifecycle.ViewModelProvider(activity).get(com.example.ui.MainViewModel::class.java)
            } else {
                null
            }
        }.getOrNull()
    }

    var showEditLrcDialog by remember { mutableStateOf(false) }
    var editLrcText by remember { mutableStateOf("") }
    var showDeleteLrcDialog by remember { mutableStateOf(false) }
    var showDeleteSongDialog by remember { mutableStateOf(false) }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    if (onUploadLrc != null) onUploadLrc()
                    else getViewModel()?.updateLyrics(song.id, text)
                }
            } catch (_: Exception) {}
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isCurrentSong) 1.dp else 0.dp,
                color = if (isCurrentSong) YimlyPink.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSongClick)
            .testTag("song_item_${song.id}"),
        color = if (isCurrentSong) YimlyPinkGlow.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevatedDark)
                    .border(0.8.dp, SurfaceBorderDark, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!song.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = "Song artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    YimlyMicIcon(
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (isCurrentSong) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            AnimatedEqualizer(color = YimlyPink)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Playing",
                                tint = YimlyPink,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentSong) YimlyPink else TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = song.formattedDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Favorite Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(40.dp).testTag("fav_btn_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Favorited" else "Favorite",
                    tint = if (song.isFavorite) YimlyPink else TextSecondaryDark,
                    modifier = Modifier.size(20.dp)
                )
            }

            // More Menu Button
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp).testTag("menu_btn_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(SurfaceElevatedDark)
                        .border(1.dp, SurfaceBorderDark, RoundedCornerShape(12.dp))
                ) {
                    // 1. Play
                    DropdownMenuItem(
                        text = { Text("Play", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = YimlyPink,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onPlay()
                        },
                        modifier = Modifier.testTag("menu_play_${song.id}")
                    )

                    // 2. Add to Now Playing
                    DropdownMenuItem(
                        text = { Text("Add to Now Playing", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = YimlyPink,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            if (onAddToQueue != null) {
                                onAddToQueue()
                            } else {
                                onAddToNowPlaying()
                            }
                        },
                        modifier = Modifier.testTag("menu_add_to_now_playing_${song.id}")
                    )

                    // 3. Add to Playlist
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = YimlyPink,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            if (onAddToPlaylistClick != null) {
                                onAddToPlaylistClick()
                            } else {
                                onAddToPlaylist()
                            }
                        },
                        modifier = Modifier.testTag("menu_add_to_playlist_${song.id}")
                    )

                    // 4. Add to Favourites
                    DropdownMenuItem(
                        text = { Text("Add to Favourites", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = YimlyPink,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleFavorite()
                        },
                        modifier = Modifier.testTag("menu_add_to_favourites_${song.id}")
                    )

                    // Optional remove when inside an editable playlist
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Playlist", color = Color(0xFFFF5252), fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemoveFromPlaylist()
                            },
                            modifier = Modifier.testTag("menu_remove_from_playlist_${song.id}")
                        )
                    }

                    // Admin Actions
                    if (isUserAdmin) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = SurfaceBorderDark)

                        DropdownMenuItem(
                            text = { Text("Edit LRC", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = YimlyPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (onEditLrc != null) {
                                    onEditLrc()
                                } else {
                                    coroutineScope.launch {
                                        val vm = getViewModel()
                                        val currentText = vm?.getLyricsText(song.id) ?: (song.lyricsText ?: "")
                                        editLrcText = currentText
                                        showEditLrcDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("menu_edit_lrc_${song.id}")
                        )

                        DropdownMenuItem(
                            text = { Text("Upload / Replace LRC", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = null,
                                    tint = YimlyPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (onUploadLrc != null) {
                                    onUploadLrc()
                                } else {
                                    lrcPickerLauncher.launch("*/*")
                                }
                            },
                            modifier = Modifier.testTag("menu_upload_lrc_${song.id}")
                        )

                        DropdownMenuItem(
                            text = { Text("Delete LRC", color = Color(0xFFFF5252), fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (onDeleteLrc != null) {
                                    onDeleteLrc()
                                } else {
                                    showDeleteLrcDialog = true
                                }
                            },
                            modifier = Modifier.testTag("menu_delete_lrc_${song.id}")
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = SurfaceBorderDark)

                        DropdownMenuItem(
                            text = { Text("Delete Song", color = Color(0xFFFF5252), fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (onDeleteSong != null) {
                                    onDeleteSong()
                                } else {
                                    showDeleteSongDialog = true
                                }
                            },
                            modifier = Modifier.testTag("menu_delete_song_${song.id}")
                        )
                    }
                }
            }
        }
    }

    if (showEditLrcDialog) {
        AlertDialog(
            onDismissRequest = { showEditLrcDialog = false },
            title = { Text("Edit LRC - ${song.title}") },
            text = {
                OutlinedTextField(
                    value = editLrcText,
                    onValueChange = { editLrcText = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp).testTag("edit_lrc_input_${song.id}"),
                    textStyle = TextStyle(color = TextPrimaryDark)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditLrcDialog = false
                        getViewModel()?.updateLyrics(song.id, editLrcText)
                    },
                    modifier = Modifier.testTag("save_lrc_btn_${song.id}")
                ) {
                    Text("Save", color = YimlyPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLrcDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceElevatedDark
        )
    }

    if (showDeleteLrcDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLrcDialog = false },
            title = { Text("Delete LRC?") },
            text = { Text("Are you sure you want to delete the LRC lyrics for \"${song.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteLrcDialog = false
                        getViewModel()?.deleteLrc(song.id)
                    },
                    modifier = Modifier.testTag("confirm_delete_lrc_btn_${song.id}")
                ) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLrcDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceElevatedDark
        )
    }

    if (showDeleteSongDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSongDialog = false },
            title = { Text("Delete \"${song.title}\"?") },
            text = { Text("This permanently deletes the original song, matching LRC, matching instrumental, and associated server library data.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSongDialog = false
                        getViewModel()?.deleteSong(song.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    modifier = Modifier.testTag("confirm_delete_song_btn_${song.id}")
                ) {
                    Text("DELETE", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSongDialog = false }) {
                    Text("CANCEL", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceElevatedDark
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<com.example.data.models.Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (com.example.data.models.Playlist) -> Unit,
    onCreateAndAddToPlaylist: ((name: String) -> Unit)? = null
) {
    var showNewPlaylistInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add to Playlist", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.title} • ${song.artist}",
                    color = YimlyPink,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showNewPlaylistInput) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = YimlyPink,
                            unfocusedBorderColor = SurfaceBorderDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_playlist_name_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showNewPlaylistInput = false
                            newPlaylistName = ""
                        }) {
                            Text("Cancel", color = TextSecondaryDark)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreateAndAddToPlaylist?.invoke(newPlaylistName.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = YimlyPink),
                            modifier = Modifier.testTag("confirm_create_and_add_btn")
                        ) {
                            Text("Create & Add", color = Color.White)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showNewPlaylistInput = true }
                            .padding(10.dp)
                            .testTag("dialog_new_playlist_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(YimlyPink.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = YimlyPink)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("New Playlist", color = YimlyPink, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = SurfaceBorderDark, modifier = Modifier.padding(vertical = 4.dp))

                    val editablePlaylists = playlists.filter { it.canEdit || it.isOwner || it.permission == "owner" }
                    if (editablePlaylists.isEmpty()) {
                        Text(
                            "No playlists available. Create a new one above!",
                            color = TextSecondaryDark,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(editablePlaylists) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectPlaylist(playlist) }
                                        .padding(8.dp)
                                        .testTag("select_playlist_${playlist.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceElevatedDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!playlist.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = playlist.coverUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            YimlyMicIcon(tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            playlist.name,
                                            color = TextPrimaryDark,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${playlist.songCount} songs",
                                            color = TextSecondaryDark,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_add_to_playlist_dialog_btn")) {
                Text("Close", color = TextSecondaryDark)
            }
        },
        containerColor = SurfaceElevatedDark
    )
}

@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "media_card"
) {
    Card(
        modifier = modifier
            .width(154.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
        border = BorderStroke(1.dp, SurfaceBorderDark)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevatedDark)
                    .border(0.8.dp, SurfaceBorderSubtle, RoundedCornerShape(12.dp))
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    val resolvedImageUrl = if (imageUrl!!.startsWith("http")) imageUrl else "${PreferencesManager.DEFAULT_SERVER_URL}$imageUrl"
                    AsyncImage(
                        model = resolvedImageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    YimlyMicIcon(
                        tint = TextSecondaryDark,
                        modifier = Modifier.align(Alignment.Center).size(34.dp)
                    )
                }

                // Play Button Overlay with Yimly pink styling
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(YimlyPink)
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ArtistAvatarItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick)
            .testTag("artist_item_${artist.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(YimlyPink, YimlyPink.copy(alpha = 0.4f))
                    )
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(SurfaceElevatedDark)
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
                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = YimlyPink,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onActionClick)
                    .padding(4.dp)
            )
        }
    }
}
