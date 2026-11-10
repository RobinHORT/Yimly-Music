package com.example.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.LyricTextCase
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsColorTheme
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsSettingsDialog(
    initialConfig: LyricsDisplayConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (LyricsDisplayConfig) -> Unit
) {
    var lineSpacing by remember { mutableFloatStateOf(initialConfig.lineSpacingDp) }
    var textCase by remember { mutableStateOf(initialConfig.textCase) }
    var currentSize by remember { mutableFloatStateOf(initialConfig.currentLineFontSizeSp) }
    var selectedColorTheme by remember { mutableStateOf(initialConfig.colorTheme) }
    var lrcOffsetMs by remember {
        mutableLongStateOf(if (initialConfig.lrcOffsetMs != 0L) initialConfig.lrcOffsetMs else initialConfig.manualOffsetMs)
    }
    var elrcOffsetMs by remember {
        mutableLongStateOf(if (initialConfig.elrcOffsetMs != 0L) initialConfig.elrcOffsetMs else initialConfig.manualOffsetMs)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(1.dp, SurfaceBorderDark, RoundedCornerShape(20.dp))
                .testTag("lyrics_settings_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceElevatedDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Lyrics Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = YimlyPink
                )

                // Lyrics Display: spacing is the only adjustable layout value used by the live renderers.
                Text(
                    text = "Lyrics Display",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Line Spacing", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("${lineSpacing.toInt()} dp", style = MaterialTheme.typography.bodyMedium, color = YimlyPink)
                    }
                    Slider(
                        value = lineSpacing,
                        onValueChange = { lineSpacing = it },
                        valueRange = 8f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = YimlyPink,
                            activeTrackColor = YimlyPink,
                            inactiveTrackColor = SurfaceBorderDark
                        )
                    )
                }

                HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)

                // ==========================================
                // 3. TEXT CASE SECTION
                // ==========================================
                Text(
                    text = "Text Case",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricTextCase.values().forEach { tc ->
                        FilterChip(
                            selected = textCase == tc,
                            onClick = { textCase = tc },
                            label = { Text(tc.displayName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YimlyPink,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = TextSecondaryDark
                            )
                        )
                    }
                }

                HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)

                // ==========================================
                // 4. CURRENT LINE SECTION
                // ==========================================
                Text(
                    text = "Current Line",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )

                // Current Line Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current Line Size", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("${currentSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = YimlyPink)
                    }
                    Slider(
                        value = currentSize,
                        onValueChange = { currentSize = it },
                        valueRange = 16f..36f,
                        colors = SliderDefaults.colors(
                            thumbColor = YimlyPink,
                            activeTrackColor = YimlyPink,
                            inactiveTrackColor = SurfaceBorderDark
                        )
                    )
                }

                // Fullscreen karaoke active / inactive colour themes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lyrics Colour Theme", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsColorTheme.values().forEach { theme ->
                            FilterChip(
                                selected = selectedColorTheme == theme,
                                onClick = { selectedColorTheme = theme },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(theme.activeColorHex))))
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(theme.inactiveColorHex)).copy(alpha = theme.inactiveAlpha)))
                                        Text(theme.displayName, fontSize = 11.sp)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YimlyPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceCardDark,
                                    labelColor = TextSecondaryDark
                                ),
                                modifier = Modifier.testTag("lyrics_color_theme_${theme.name.lowercase()}")
                            )
                        }
                    }
                }

                HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)

                // Independent timing offsets for fixed LRC Focus and eLRC fullscreen modes.
                Text(
                    text = "Lyrics Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                LyricsOffsetControl("Focus Mode (LRC)", lrcOffsetMs) { lrcOffsetMs = it }
                LyricsOffsetControl("Fullscreen (eLRC)", elrcOffsetMs) { elrcOffsetMs = it }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions: Cancel & Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Cancel", color = TextSecondaryDark)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val updated = initialConfig.copy(
                                textCase = textCase,
                                lineSpacingDp = lineSpacing,
                                currentLineFontSizeSp = currentSize,
                                currentLineColorHex = selectedColorTheme.activeColorHex,
                                colorTheme = selectedColorTheme,
                                manualOffsetMs = 0L,
                                lrcOffsetMs = lrcOffsetMs,
                                elrcOffsetMs = elrcOffsetMs
                            )
                            onSaveConfig(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YimlyPink,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
private fun LyricsOffsetControl(
    label: String,
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
            Text(
                text = if (offsetMs == 0L) "Sync (0.0s)" else String.format(java.util.Locale.US, "%+.1fs", offsetMs / 1000f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (offsetMs != 0L) YimlyPink else TextSecondaryDark
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { onOffsetChange(offsetMs - 200L) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark), shape = RoundedCornerShape(8.dp)) {
                Text("-0.2s", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onOffsetChange(0L) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = YimlyPink), shape = RoundedCornerShape(8.dp)) {
                Text("Reset", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onOffsetChange(offsetMs + 200L) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark), shape = RoundedCornerShape(8.dp)) {
                Text("+0.2s", fontSize = 12.sp)
            }
        }
    }
}
