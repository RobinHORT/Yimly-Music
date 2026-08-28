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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.models.Collaborator
import com.example.data.models.Playlist
import com.example.data.models.Song
import com.example.ui.components.SongItemRow
import com.example.ui.components.YimlyMicIcon
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
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
    onDeletePlaylist: (() -> Unit)? = null,
    onUpdatePlaylist: ((String, String?, Boolean?) -> Unit)? = null,
    onRemoveSongFromPlaylist: ((String) -> Unit)? = null,
    onSharePlaylist: ((String, String) -> Unit)? = null,
    onGetCollaborators: ((onResult: (List<Collaborator>) -> Unit) -> Unit)? = null,
    onRevokeShare: ((String) -> Unit)? = null,
    onUpdateArtwork: ((String) -> Unit)? = null,
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    val vm = getViewModel()
    val vmIsAdminState = vm?.isAdmin?.collectAsState(initial = false)
    val userProfileState = vm?.userProfile?.collectAsState(initial = null)
    val isUserAdmin = isAdmin ||
            (vmIsAdminState?.value == true) ||
            (userProfileState?.value?.isAdmin == true) ||
            (userProfileState?.value?.role.equals("administrator", ignoreCase = true)) ||
            (userProfileState?.value?.role.equals("admin", ignoreCase = true))

    var showShareDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(playlist.name) }
    var editDesc by remember { mutableStateOf(playlist.description ?: "") }
    var editIsPublic by remember { mutableStateOf(playlist.isPublic) }
    var showArtworkEditor by remember { mutableStateOf(false) }

    if (showArtworkEditor) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showArtworkEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.example.ui.components.PlaylistArtworkEditor(
                playlistId = playlist.id,
                onDismiss = { showArtworkEditor = false },
                onSave = { uri ->
                    onUpdateArtwork?.invoke(uri)
                    showArtworkEditor = false
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("playlist_detail_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Hero Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                com.example.ui.components.PlaylistArtwork(
                    coverUrl = playlist.coverUrl,
                    songs = playlist.songs,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    BackgroundDark
                                )
                            )
                        )
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevatedDark.copy(alpha = 0.8f))
                            .border(1.dp, SurfaceBorderDark, CircleShape)
                            .testTag("playlist_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playlist.canEdit && onUpdatePlaylist != null) {
                            IconButton(
                                onClick = { showArtworkEditor = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevatedDark.copy(alpha = 0.8f))
                                    .border(1.dp, SurfaceBorderDark, CircleShape)
                                    .testTag("edit_artwork_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Edit Artwork",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    editName = playlist.name
                                    editDesc = playlist.description ?: ""
                                    showEditDialog = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevatedDark.copy(alpha = 0.8f))
                                    .border(1.dp, SurfaceBorderDark, CircleShape)
                                    .testTag("edit_playlist_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Playlist",
                                    tint = Color.White
                                )
                            }
                        }

                        if ((playlist.isOwner || playlist.permission == "owner") && onSharePlaylist != null) {
                            IconButton(
                                onClick = { showShareDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevatedDark.copy(alpha = 0.8f))
                                    .border(1.dp, SurfaceBorderDark, CircleShape)
                                    .testTag("share_playlist_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Playlist",
                                    tint = YimlyPink
                                )
                            }
                        }

                        if ((playlist.isOwner || playlist.permission == "owner" || isUserAdmin) && onDeletePlaylist != null) {
                            IconButton(
                                onClick = onDeletePlaylist,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevatedDark.copy(alpha = 0.8f))
                                    .border(1.dp, SurfaceBorderDark, CircleShape)
                                    .testTag("delete_playlist_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Playlist",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    // Owner & Permission Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badgeText = when {
                            playlist.isPublic && (playlist.isOwner || playlist.permission == "owner") -> "Community — You Own This"
                            playlist.isPublic -> "Community"
                            playlist.isOwner || playlist.permission == "owner" -> "Private / Custom"
                            playlist.canEdit -> "Shared (Can Edit)"
                            else -> "Shared (View Only)"
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

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (!playlist.ownerName.isNullOrBlank() && !playlist.isOwner) {
                            Text(
                                text = "by ${playlist.ownerName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist.description ?: "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            }
        }

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
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("playlist_play_all_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { songs.shuffled().firstOrNull()?.let { onSongClick(it, songs.shuffled()) } },
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevatedDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("playlist_shuffle_btn")
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", color = TextPrimaryDark)
                }
            }
        }

        // Tracklist or Empty State
        if (songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This playlist is empty.\nAdd songs from Search or your Library.",
                        textAlign = TextAlign.Center,
                        color = TextSecondaryDark
                    )
                }
            }
        } else {
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
                    onRemoveFromPlaylist = if (playlist.canEdit && onRemoveSongFromPlaylist != null) {
                        { onRemoveSongFromPlaylist(song.id) }
                    } else null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }

    // Edit Playlist Dialog
    if (showEditDialog && onUpdatePlaylist != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Playlist", color = TextPrimaryDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Playlist Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = YimlyPink,
                            unfocusedBorderColor = SurfaceBorderDark
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("edit_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = YimlyPink,
                            unfocusedBorderColor = SurfaceBorderDark
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("edit_playlist_desc_input")
                    )
                    if (playlist.isOwner || playlist.permission == "owner") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceElevatedDark)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { editIsPublic = !editIsPublic }
                                .testTag("edit_playlist_public_toggle")
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = editIsPublic,
                                onCheckedChange = { editIsPublic = it },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = YimlyPink)
                            )
                            Text("Publish to Community (Public)", color = TextPrimaryDark, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdatePlaylist(editName.trim(), editDesc.trim().ifBlank { null }, editIsPublic)
                            showEditDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_playlist_edit_btn")
                ) {
                    Text("Save", color = YimlyPink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceCardDark
        )
    }

    // Share Playlist & Collaborators Dialog
    if (showShareDialog && onSharePlaylist != null) {
        var targetUsername by remember { mutableStateOf("") }
        var selectedPermission by remember { mutableStateOf("view") }
        var collaborators by remember { mutableStateOf<List<Collaborator>>(emptyList()) }

        LaunchedEffect(playlist.id) {
            onGetCollaborators?.invoke { list ->
                collaborators = list
            }
        }

        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = YimlyPink)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Playlist", color = TextPrimaryDark)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Invite User", style = MaterialTheme.typography.titleSmall, color = TextPrimaryDark)
                    OutlinedTextField(
                        value = targetUsername,
                        onValueChange = { targetUsername = it },
                        placeholder = { Text("Enter username") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = YimlyPink,
                            unfocusedBorderColor = SurfaceBorderDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPermission == "view",
                                onClick = { selectedPermission = "view" },
                                colors = RadioButtonDefaults.colors(selectedColor = YimlyPink)
                            )
                            Text("View Only", color = TextPrimaryDark, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPermission == "edit",
                                onClick = { selectedPermission = "edit" },
                                colors = RadioButtonDefaults.colors(selectedColor = YimlyPink)
                            )
                            Text("Can Edit", color = TextPrimaryDark, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(
                        onClick = {
                            if (targetUsername.isNotBlank()) {
                                onSharePlaylist(targetUsername.trim(), selectedPermission)
                                targetUsername = ""
                                onGetCollaborators?.invoke { list -> collaborators = list }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YimlyPink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Invite", color = Color.White)
                    }

                    if (collaborators.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Current Collaborators", style = MaterialTheme.typography.titleSmall, color = TextPrimaryDark)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            collaborators.forEach { collab ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceElevatedDark, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(collab.username, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                                        Text(collab.permission.uppercase(), color = TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (onRevokeShare != null) {
                                        IconButton(
                                            onClick = {
                                                onRevokeShare(collab.userId.ifBlank { collab.id })
                                                onGetCollaborators?.invoke { list -> collaborators = list }
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Revoke", tint = Color(0xFFFF5252))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text("Done", color = YimlyPink)
                }
            },
            containerColor = SurfaceCardDark
        )
    }
}

