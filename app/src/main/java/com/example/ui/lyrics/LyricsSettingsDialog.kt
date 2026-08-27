package com.example.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.YimlyPink
import com.example.ui.theme.YimlyPinkGlow

@Composable
fun LyricsSettingsDialog(
    initialConfig: LyricsDisplayConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (LyricsDisplayConfig) -> Unit
) {
    var fontFamily by remember { mutableStateOf(initialConfig.fontFamily) }
    var currentSize by remember { mutableFloatStateOf(initialConfig.currentLineFontSizeSp) }
    var otherSize by remember { mutableFloatStateOf(initialConfig.otherLineFontSizeSp) }
    var fontWeightBold by remember { mutableStateOf(initialConfig.fontWeightBold) }
    var otherOpacity by remember { mutableFloatStateOf(initialConfig.otherLinesOpacity) }
    var lineSpacing by remember { mutableFloatStateOf(initialConfig.lineSpacingDp) }
    var textCase by remember { mutableStateOf(initialConfig.textCase) }
    var alignment by remember { mutableStateOf(initialConfig.alignment) }
    var animDuration by remember { mutableIntStateOf(initialConfig.animationDurationMs) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Lyrics Customization",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = YimlyPink
                )

                // 1. Font Family
                Text(
                    text = "Font Family",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimaryDark
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LyricFontFamily.values().forEach { font ->
                        FilterChip(
                            selected = fontFamily == font,
                            onClick = { fontFamily = font },
                            label = { Text(font.name.lowercase().capitalize(), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YimlyPink,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = TextSecondaryDark
                            )
                        )
                    }
                }

                // 2. Text Alignment
                Text(
                    text = "Alignment",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimaryDark
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LyricAlignment.values().forEach { align ->
                        FilterChip(
                            selected = alignment == align,
                            onClick = { alignment = align },
                            label = { Text(align.name.lowercase().capitalize(), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YimlyPink,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = TextSecondaryDark
                            )
                        )
                    }
                }

                // 3. Text Case
                Text(
                    text = "Text Case",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimaryDark
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LyricTextCase.values().forEach { tc ->
                        FilterChip(
                            selected = textCase == tc,
                            onClick = { textCase = tc },
                            label = { Text(tc.name.lowercase().capitalize(), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YimlyPink,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceCardDark,
                                labelColor = TextSecondaryDark
                            )
                        )
                    }
                }

                // 4. Current Line Font Size
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

                // 5. Line Spacing
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

                // 6. Bold Current Line Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bold Active Line", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    Switch(
                        checked = fontWeightBold,
                        onCheckedChange = { fontWeightBold = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YimlyPink,
                            uncheckedTrackColor = SurfaceBorderDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions
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
                                fontFamily = fontFamily,
                                currentLineFontSizeSp = currentSize,
                                otherLineFontSizeSp = otherSize,
                                fontWeightBold = fontWeightBold,
                                otherLinesOpacity = otherOpacity,
                                lineSpacingDp = lineSpacing,
                                textCase = textCase,
                                alignment = alignment,
                                animationDurationMs = animDuration
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
