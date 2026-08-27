package com.example.ui.session

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.GuestSongRequest
import com.example.data.models.Song
import com.example.data.models.UserProfile
import com.example.data.models.YimlySession
import com.example.ui.components.AnimatedEqualizer
import com.example.ui.components.SectionHeader
import com.example.ui.components.YimlyCard
import com.example.ui.components.YimlyMicIcon
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@Composable
fun SessionScreen(
    userProfile: UserProfile?,
    activeSession: YimlySession?,
    isHost: Boolean,
    allSongs: List<Song>,
    onHostSession: (String) -> Unit,
    onJoinSession: (String, String) -> Unit,
    onRequestSong: (Song, String) -> Unit,
    onApproveRequest: (GuestSongRequest) -> Unit,
    onDismissRequest: (String) -> Unit,
    onToggleAllowGuestRequests: () -> Unit,
    onLeaveSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var joinCodeInput by remember { mutableStateOf("") }
    var showRequestSongSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("session_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Yimly Live Sessions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Stream together in real time with friends",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }

                if (activeSession != null) {
                    IconButton(
                        onClick = onLeaveSession,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceElevatedDark)
                            .border(1.dp, SurfaceBorderDark, CircleShape)
                            .testTag("leave_session_btn")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Leave Session", tint = YimlyPink)
                    }
                }
            }
        }

        if (activeSession == null) {
            // NO ACTIVE SESSION: Host or Join Card
            item {
                YimlyCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(YimlyPink, YimlyPink.copy(alpha = 0.5f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            YimlyMicIcon(tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Start a Group Session",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Host a room and share the 6-letter invite code for synchronized group listening and guest song queues.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryDark,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onHostSession(userProfile?.displayName ?: userProfile?.username ?: "Yimly Host") },
                            modifier = Modifier.fillMaxWidth().testTag("start_host_session_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Host New Session", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            item {
                YimlyCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Join a Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter the 6-character room code from your host:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { if (it.length <= 6) joinCodeInput = it.uppercase() },
                            placeholder = { Text("e.g. YM78XQ", color = TextSecondaryDark) },
                            modifier = Modifier.fillMaxWidth().testTag("join_code_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YimlyPink,
                                focusedContainerColor = SurfaceElevatedDark,
                                unfocusedContainerColor = SurfaceElevatedDark,
                                unfocusedBorderColor = SurfaceBorderDark,
                                cursorColor = YimlyPink
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (joinCodeInput.isNotBlank()) {
                                    onJoinSession(joinCodeInput.trim(), userProfile?.displayName ?: "Guest Listener")
                                }
                            },
                            enabled = joinCodeInput.length >= 4,
                            modifier = Modifier.fillMaxWidth().testTag("submit_join_session_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Join Room", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // ACTIVE SESSION CARD
            item {
                YimlyCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = YimlyPink.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, YimlyPink.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(YimlyPink)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isHost) "HOSTING SESSION" else "CONNECTED",
                                        color = YimlyPink,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${activeSession.participantsCount} listening",
                                    color = YimlyPink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Room Code Display Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevatedDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "INVITE CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 2.sp,
                                    color = TextSecondaryDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeSession.roomCode,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YimlyPink,
                                    letterSpacing = 4.sp
                                )
                                Text(
                                    text = "Host: ${activeSession.hostName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        // Host toggles
                        if (isHost) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Allow Guest Song Requests", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                                Switch(
                                    checked = activeSession.allowGuestRequests,
                                    onCheckedChange = { onToggleAllowGuestRequests() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = YimlyPink,
                                        uncheckedThumbColor = TextSecondaryDark,
                                        uncheckedTrackColor = SurfaceElevatedDark
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Currently Streaming Track
            val currentSong = activeSession.currentSong
            if (currentSong != null) {
                item {
                    Text(
                        text = "NOW BROADCASTING",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        color = YimlyPink,
                        fontWeight = FontWeight.Bold
                    )
                    YimlyCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
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
                                    YimlyMicIcon(tint = YimlyPink, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = YimlyPink
                                )
                                Text(
                                    text = "${currentSong.artist} • ${currentSong.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondaryDark
                                )
                            }
                            AnimatedEqualizer(color = YimlyPink)
                        }
                    }
                }
            }

            // Incoming Guest Requests (for Host) or Request Song Button (for Guest)
            if (isHost) {
                item {
                    SectionHeader(title = "Guest Requests (${activeSession.guestRequests.size})")
                }

                if (activeSession.guestRequests.isEmpty()) {
                    item {
                        YimlyCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No incoming song requests from guests.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryDark,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(activeSession.guestRequests) { req ->
                        YimlyCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.song.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryDark
                                    )
                                    Text(
                                        text = "${req.song.artist} • Requested by ${req.requestedBy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = YimlyPink
                                    )
                                }

                                IconButton(
                                    onClick = { onApproveRequest(req) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(YimlyPink.copy(alpha = 0.2f))
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Approve", tint = YimlyPink, modifier = Modifier.size(18.dp))
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { onDismissRequest(req.id) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.2f))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                // Guest Request Action
                item {
                    Button(
                        onClick = { showRequestSongSheet = !showRequestSongSheet },
                        modifier = Modifier.fillMaxWidth().testTag("guest_request_song_toggle_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Request a Song in Session", fontWeight = FontWeight.Bold)
                    }
                }

                if (showRequestSongSheet) {
                    item {
                        Text(
                            text = "TAP A SONG TO REQUEST:",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = YimlyPink,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(allSongs) { song ->
                        YimlyCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                                }
                                Button(
                                    onClick = {
                                        onRequestSong(song, userProfile?.displayName ?: "Guest")
                                        showRequestSongSheet = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Request", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
