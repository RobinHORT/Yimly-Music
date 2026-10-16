package com.example.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.datastore.PreferencesManager
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.UserProfile
import com.example.ui.components.YimlyCard
import com.example.ui.components.YimlyHeaderBrand
import com.example.ui.components.YimlyLogoBadge
import com.example.ui.components.YimlyMicIcon
import com.example.ui.lyrics.LyricsSettingsDialog
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    serverUrl: String,
    lyricsConfig: LyricsDisplayConfig,
    onUpdateServerUrl: (String) -> Unit,
    onSaveLyricsConfig: (LyricsDisplayConfig) -> Unit,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
    onSyncLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editableUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var showLyricsDialog by remember { mutableStateOf(false) }

    if (showLyricsDialog) {
        LyricsSettingsDialog(
            initialConfig = lyricsConfig,
            onDismiss = { showLyricsDialog = false },
            onSaveConfig = onSaveLyricsConfig
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryDark
            )
        }

        // 1. Account Section
        item {
            YimlyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(YimlyPink, YimlyPink.copy(alpha = 0.5f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        YimlyMicIcon(tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName ?: userProfile?.username ?: "Yimly Member",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = userProfile?.email ?: "Streaming Tier",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }

                    if (userProfile != null) {
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SurfaceElevatedDark)
                                .border(1.dp, SurfaceBorderDark, CircleShape)
                                .testTag("logout_btn")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Log out", tint = YimlyPink)
                        }
                    } else {
                        Button(
                            onClick = onLoginClick,
                            colors = ButtonDefaults.buttonColors(containerColor = YimlyPink, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Yimly Server Status Section
        item {
            YimlyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Yimly Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YimlyPink.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, YimlyPink.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(YimlyPink))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ONLINE", color = YimlyPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Native Android client connected to Yimly cloud server ($serverUrl)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )

                    // Sync Library Button
                    Button(
                        onClick = onSyncLibrary,
                        modifier = Modifier.fillMaxWidth().testTag("sync_library_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevatedDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Library with Server", color = YimlyPink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 4. Lyrics Display Configuration Trigger
        item {
            YimlyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_lyrics_config_card"),
                onClick = { showLyricsDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = YimlyPink, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Lyrics Display Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text("Fonts, sizes, line opacity, alignment, and timing", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                        }
                    }

                    Icon(Icons.Default.Tune, contentDescription = null, tint = YimlyPink)
                }
            }
        }

        // 5. About Yimly Branding Badge & Footer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                YimlyHeaderBrand(badgeSize = 44.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Yimly Native Client v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
                Text(
                    text = "yimly.robinhort.link",
                    style = MaterialTheme.typography.labelSmall,
                    color = YimlyPink.copy(alpha = 0.8f)
                )
            }
        }
    }
}
