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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricFontFamily
import com.example.data.models.LyricTextCase
import com.example.data.models.LyricsDisplayConfig
import com.example.data.models.LyricsFormatMode
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.toComposeFontFamily

val COLOR_PRESETS = listOf(
    Pair("Pink", "#FF3366"),
    Pair("White", "#FFFFFF"),
    Pair("Cyan", "#00E5FF"),
    Pair("Yellow", "#FFD600"),
    Pair("Green", "#00E676"),
    Pair("Purple", "#E040FB")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsSettingsDialog(
    initialConfig: LyricsDisplayConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (LyricsDisplayConfig) -> Unit
) {
    // 1. Lyrics Display state
    var formatMode by remember { mutableStateOf(initialConfig.formatMode) }
    var visibleLines by remember { mutableIntStateOf(initialConfig.visibleLines) }
    var autoScroll by remember { mutableStateOf(initialConfig.autoScroll) }
    var highlightCurrentLine by remember { mutableStateOf(initialConfig.highlightCurrentLine) }
    var animDuration by remember { mutableIntStateOf(initialConfig.animationDurationMs) }
    var lineSpacing by remember { mutableFloatStateOf(initialConfig.lineSpacingDp) }
    var alignment by remember { mutableStateOf(initialConfig.alignment) }

    // 2. Font state
    var fontFamily by remember { mutableStateOf(initialConfig.fontFamily) }

    // 3. Text Case state
    var textCase by remember { mutableStateOf(initialConfig.textCase) }

    // 4. Current Line state
    var currentSize by remember { mutableFloatStateOf(initialConfig.currentLineFontSizeSp) }
    var currentColorHex by remember { mutableStateOf(initialConfig.currentLineColorHex) }

    // 5. Previous & Next Lines state (Shared)
    var otherSize by remember { mutableFloatStateOf(initialConfig.otherLineFontSizeSp) }
    var otherOpacity by remember { mutableFloatStateOf(initialConfig.otherLinesOpacity) }

    // 6. Other state
    var lyricsOverlay by remember { mutableStateOf(initialConfig.lyricsOverlay) }
    var manualOffset by remember { mutableLongStateOf(initialConfig.effectiveFormatManualOffsetMs) }

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

                // ==========================================
                // 1. LYRICS DISPLAY SECTION
                // ==========================================
                Text(
                    text = "Lyrics Display",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )

                // Lyrics Format Mode (eLRC vs LRC)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Format Mode", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text(
                            if (formatMode == LyricsFormatMode.ELRC) "Word-level (eLRC)" else "Line-level (LRC)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YimlyPink
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsFormatMode.values().forEach { mode ->
                            FilterChip(
                                selected = formatMode == mode,
                                onClick = { formatMode = mode },
                                label = { Text(if (mode == LyricsFormatMode.ELRC) "eLRC (Word Sync)" else "LRC (Line Sync)", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YimlyPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceCardDark,
                                    labelColor = TextSecondaryDark
                                )
                            )
                        }
                    }
                }

                // Visible Lines
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Visible Lines", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("$visibleLines lines", style = MaterialTheme.typography.bodyMedium, color = YimlyPink)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 3, 5, 7).forEach { lines ->
                            FilterChip(
                                selected = visibleLines == lines,
                                onClick = { visibleLines = lines },
                                label = { Text("$lines", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YimlyPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceCardDark,
                                    labelColor = TextSecondaryDark
                                )
                            )
                        }
                    }
                }

                // Auto Scroll Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Scroll", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    Switch(
                        checked = autoScroll,
                        onCheckedChange = { autoScroll = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YimlyPink,
                            uncheckedTrackColor = SurfaceBorderDark
                        )
                    )
                }

                // Highlight Current Line Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Highlight Current Line", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    Switch(
                        checked = highlightCurrentLine,
                        onCheckedChange = { highlightCurrentLine = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YimlyPink,
                            uncheckedTrackColor = SurfaceBorderDark
                        )
                    )
                }

                // Animation Duration Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Animation Duration", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("$animDuration ms", style = MaterialTheme.typography.bodyMedium, color = YimlyPink)
                    }
                    Slider(
                        value = animDuration.toFloat(),
                        onValueChange = { animDuration = it.toInt() },
                        valueRange = 100f..600f,
                        colors = SliderDefaults.colors(
                            thumbColor = YimlyPink,
                            activeTrackColor = YimlyPink,
                            inactiveTrackColor = SurfaceBorderDark
                        )
                    )
                }

                // Line Spacing Slider
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

                // Alignment Chips
                Column {
                    Text("Alignment", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LyricAlignment.values().forEach { align ->
                            FilterChip(
                                selected = alignment == align,
                                onClick = { alignment = align },
                                label = { Text(align.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YimlyPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceCardDark,
                                    labelColor = TextSecondaryDark
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)

                // ==========================================
                // 2. FONT SECTION
                // ==========================================
                Text(
                    text = "Font",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Font Family",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricFontFamily.values().forEach { font ->
                        FilterChip(
                            selected = fontFamily == font,
                            onClick = { fontFamily = font },
                            label = {
                                Text(
                                    text = font.displayName,
                                    fontFamily = font.toComposeFontFamily(),
                                    fontSize = 12.sp
                                )
                            },
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

                // Current Line Color Swatches
                Column {
                    Text("Current Line Color", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        COLOR_PRESETS.forEach { (name, hex) ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                YimlyPink
                            }
                            val isSelected = currentColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else SurfaceBorderDark,
                                        shape = CircleShape
                                    )
                                    .clickable { currentColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = name,
                                        tint = if (hex == "#FFFFFF") Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)

                // ==========================================
                // 5. PREVIOUS & NEXT LINES SECTION (SHARED)
                // ==========================================
                Text(
                    text = "Previous & Next Lines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )

                // Shared Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("${otherSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = YimlyPink)
                    }
                    Slider(
                        value = otherSize,
                        onValueChange = { otherSize = it },
                        valueRange = 12f..24f,
                        colors = SliderDefaults.colors(
                            thumbColor = YimlyPink,
                            activeTrackColor = YimlyPink,
                            inactiveTrackColor = SurfaceBorderDark
                        )
                    )
                }

                // Shared Opacity
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("${(otherOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = YimlyPink)
                    }
                    Slider(
                        value = otherOpacity,
                        onValueChange = { otherOpacity = it },
                        valueRange = 0.10f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = YimlyPink,
                            activeTrackColor = YimlyPink,
                            inactiveTrackColor = SurfaceBorderDark
                        )
                    )
                }

                HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)

                // ==========================================
                // 6. OTHER SECTION
                // ==========================================
                Text(
                    text = "Other",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )

                // Lyrics Overlay Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Lyrics Overlay", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text("Show floating lyrics on mini player", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                    }
                    Switch(
                        checked = lyricsOverlay,
                        onCheckedChange = { lyricsOverlay = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YimlyPink,
                            uncheckedTrackColor = SurfaceBorderDark
                        )
                    )
                }

                // Offset Control
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Offset", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                        Text(
                            text = if (manualOffset == 0L) "Sync (0.0s)" else String.format(java.util.Locale.US, "%+.1fs", manualOffset / 1000f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (manualOffset != 0L) YimlyPink else TextSecondaryDark
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { manualOffset -= 200L },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("-0.2s", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { manualOffset = 0L },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = YimlyPink),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { manualOffset += 200L },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+0.2s", fontSize = 12.sp)
                        }
                    }
                }

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
                                formatMode = formatMode,
                                visibleLines = visibleLines,
                                autoScroll = autoScroll,
                                highlightCurrentLine = highlightCurrentLine,
                                animationDurationMs = animDuration,
                                lineSpacingDp = lineSpacing,
                                alignment = alignment,
                                fontFamily = fontFamily,
                                textCase = textCase,
                                currentLineFontSizeSp = currentSize,
                                currentLineColorHex = currentColorHex,
                                otherLineFontSizeSp = otherSize,
                                otherLinesOpacity = otherOpacity,
                                lyricsOverlay = lyricsOverlay,
                                manualOffsetMs = manualOffset,
                                lrcOffsetMs = if (formatMode == LyricsFormatMode.LRC) manualOffset else initialConfig.lrcOffsetMs,
                                elrcOffsetMs = if (formatMode == LyricsFormatMode.ELRC) manualOffset else initialConfig.elrcOffsetMs
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
