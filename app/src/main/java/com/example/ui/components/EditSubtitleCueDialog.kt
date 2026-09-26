package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleCue
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleVerticalAlign
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditSubtitleCueDialog(
    cue: SubtitleCue,
    currentVideoPositionMs: Long,
    onDismiss: () -> Unit,
    onSave: (SubtitleCue) -> Unit,
    onDelete: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf(cue.cleanText) }
    var startTimeMs by remember { mutableLongStateOf(cue.startTimeMs) }
    var endTimeMs by remember { mutableLongStateOf(cue.endTimeMs) }
    var startText by remember { mutableStateOf(cue.formatStartTime()) }
    var endText by remember { mutableStateOf(cue.formatEndTime()) }

    // Custom single-cue position state
    var customPositionEnabled by remember { mutableStateOf(cue.customPositionEnabled) }
    var customVerticalAlign by remember { mutableStateOf(cue.customVerticalAlign) }
    var customHorizontalAlign by remember { mutableStateOf(cue.customHorizontalAlign) }
    var customVerticalOffsetDp by remember { mutableFloatStateOf(cue.customVerticalOffsetDp) }
    var customHorizontalOffsetDp by remember { mutableFloatStateOf(cue.customHorizontalOffsetDp) }
    var customFontSizeSp by remember { mutableFloatStateOf(cue.customFontSizeSp ?: 22f) }
    var customTextColorArgb by remember { mutableStateOf(cue.customTextColorArgb) }
    var customOutlineColorArgb by remember { mutableStateOf(cue.customOutlineColorArgb) }
    var customIsItalic by remember { mutableStateOf(cue.customIsItalic) }
    var customIsBold by remember { mutableStateOf(cue.customIsBold) }
    var customIsUnderline by remember { mutableStateOf(cue.customIsUnderline) }

    var showCueTextColorPicker by remember { mutableStateOf(false) }
    var showCueOutlineColorPicker by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("edit_subtitle_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${cue.id}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Altyazı Düzenleyici",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text Input Header with Quick Formatting Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALTYAZI METNİ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { text = "<i>$text</i>" }
                            .testTag("tag_italic_button")
                    ) {
                        Text(
                            text = "<i> İtalik",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { text = "<b>$text</b>" }
                            .testTag("tag_bold_button")
                    ) {
                        Text(
                            text = "<b> Kalın",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { text = "<u>$text</u>" }
                            .testTag("tag_underline_button")
                    ) {
                        Text(
                            text = "<u> Altı Çizili",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { text = "$text\\N" }
                            .testTag("tag_newline_button")
                    ) {
                        Text(
                            text = "\\N Satır",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Diyalog metnini girin...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_dialog_text_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time Interval Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ZAMAN ARALIĞI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(0)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (endTimeMs >= startTimeMs) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Süre: ${String.format("%.2f", durationMs / 1000f)}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (endTimeMs >= startTimeMs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Start Time Controls
                    Text(
                        text = "Başlangıç Zamanı",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { input ->
                                startText = input
                                SubtitleCue.parseTimestamp(input)?.let { parsed ->
                                    startTimeMs = parsed
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("edit_dialog_start_time")
                        )

                        Button(
                            onClick = {
                                startTimeMs = currentVideoPositionMs
                                startText = SubtitleCue.formatTimestamp(currentVideoPositionMs)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("⏱ Anlık Al", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TimeQuickStepButton("-1s") {
                            startTimeMs = (startTimeMs - 1000L).coerceAtLeast(0L)
                            startText = SubtitleCue.formatTimestamp(startTimeMs)
                        }
                        TimeQuickStepButton("-100ms") {
                            startTimeMs = (startTimeMs - 100L).coerceAtLeast(0L)
                            startText = SubtitleCue.formatTimestamp(startTimeMs)
                        }
                        TimeQuickStepButton("+100ms") {
                            startTimeMs += 100L
                            startText = SubtitleCue.formatTimestamp(startTimeMs)
                        }
                        TimeQuickStepButton("+1s") {
                            startTimeMs += 1000L
                            startText = SubtitleCue.formatTimestamp(startTimeMs)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // End Time Controls
                    Text(
                        text = "Bitiş Zamanı",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { input ->
                                endText = input
                                SubtitleCue.parseTimestamp(input)?.let { parsed ->
                                    endTimeMs = parsed
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("edit_dialog_end_time")
                        )

                        Button(
                            onClick = {
                                endTimeMs = currentVideoPositionMs
                                endText = SubtitleCue.formatTimestamp(currentVideoPositionMs)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("⏱ Anlık Al", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TimeQuickStepButton("-1s") {
                            endTimeMs = (endTimeMs - 1000L).coerceAtLeast(0L)
                            endText = SubtitleCue.formatTimestamp(endTimeMs)
                        }
                        TimeQuickStepButton("-100ms") {
                            endTimeMs = (endTimeMs - 100L).coerceAtLeast(0L)
                            endText = SubtitleCue.formatTimestamp(endTimeMs)
                        }
                        TimeQuickStepButton("+100ms") {
                            endTimeMs += 100L
                            endText = SubtitleCue.formatTimestamp(endTimeMs)
                        }
                        TimeQuickStepButton("+1s") {
                            endTimeMs += 1000L
                            endText = SubtitleCue.formatTimestamp(endTimeMs)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Single Subtitle Placement Menu (Özel Tekil Yerleşim Menüsü)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (customPositionEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_placement_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Bu Altyazıya Özel Konum & Yerleşim",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Genel stil yerine yalnızca bu satırı özel konumlandırın",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = customPositionEnabled,
                            onCheckedChange = { customPositionEnabled = it },
                            modifier = Modifier.testTag("custom_placement_switch")
                        )
                    }

                    if (customPositionEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Vertical Alignment Options
                        Text(
                            text = "Dikey Hizalama",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = customVerticalAlign == SubtitleVerticalAlign.TOP,
                                onClick = { customVerticalAlign = SubtitleVerticalAlign.TOP },
                                label = { Text("Üst (Top)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customVerticalAlign == SubtitleVerticalAlign.MIDDLE,
                                onClick = { customVerticalAlign = SubtitleVerticalAlign.MIDDLE },
                                label = { Text("Orta (Mid)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customVerticalAlign == SubtitleVerticalAlign.BOTTOM,
                                onClick = { customVerticalAlign = SubtitleVerticalAlign.BOTTOM },
                                label = { Text("Alt (Bottom)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal Alignment Options
                        Text(
                            text = "Yatay Hizalama",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = customHorizontalAlign == SubtitleHorizontalAlign.LEFT,
                                onClick = { customHorizontalAlign = SubtitleHorizontalAlign.LEFT },
                                label = { Text("Sol (Left)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customHorizontalAlign == SubtitleHorizontalAlign.CENTER,
                                onClick = { customHorizontalAlign = SubtitleHorizontalAlign.CENTER },
                                label = { Text("Orta (Center)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customHorizontalAlign == SubtitleHorizontalAlign.RIGHT,
                                onClick = { customHorizontalAlign = SubtitleHorizontalAlign.RIGHT },
                                label = { Text("Sağ (Right)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Vertical Offset Slider (Y-Offset)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Dikey Kenar Mesafesi (Y-Offset)", fontSize = 12.sp)
                            Text(
                                text = "${customVerticalOffsetDp.roundToInt()} dp",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customVerticalOffsetDp,
                            onValueChange = { customVerticalOffsetDp = it },
                            valueRange = 0f..200f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Horizontal Offset Slider (X-Offset)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Yatay Kaydırma (X-Offset)", fontSize = 12.sp)
                            Text(
                                text = "${customHorizontalOffsetDp.roundToInt()} dp",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customHorizontalOffsetDp,
                            onValueChange = { customHorizontalOffsetDp = it },
                            valueRange = -150f..150f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Custom font size for this cue
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Özel Yazı Boyutu", fontSize = 12.sp)
                            }
                            Text(
                                text = "${customFontSizeSp.roundToInt()} sp",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customFontSizeSp,
                            onValueChange = { customFontSizeSp = it },
                            valueRange = 14f..44f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Custom font style (Bold, Italic, Underline) for this cue
                        Text(
                            text = "Özel Font Biçimi:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = customIsBold == true,
                                onClick = {
                                    customIsBold = if (customIsBold == true) null else true
                                },
                                label = { Text("Kalın", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = if (customIsBold == true) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customIsItalic == true,
                                onClick = {
                                    customIsItalic = if (customIsItalic == true) null else true
                                },
                                label = { Text("İtalik", fontSize = 11.sp, fontStyle = FontStyle.Italic) },
                                leadingIcon = if (customIsItalic == true) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customIsUnderline == true,
                                onClick = {
                                    customIsUnderline = if (customIsUnderline == true) null else true
                                },
                                label = { Text("Altı Çizili", fontSize = 11.sp, textDecoration = TextDecoration.Underline) },
                                leadingIcon = if (customIsUnderline == true) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom text color palette
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Özel Metin Rengi:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = { showCueTextColorPicker = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("cue_text_color_rgb_btn")
                            ) {
                                Icon(Icons.Default.ColorLens, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RGB ile Seç", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colorOptions = listOf(
                                null to "Varsayılan",
                                0xFFFFFFFFL to "Beyaz",
                                0xFFFFEB3BL to "Sarı",
                                0xFF00E5FFL to "Cyan",
                                0xFF76FF03L to "Yeşil",
                                0xFFFF4081L to "Pembe",
                                0xFFFF9100L to "Turuncu"
                            )
                            colorOptions.forEach { (colorVal, name) ->
                                val isSelected = customTextColorArgb == colorVal
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (colorVal != null) Color(colorVal) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { customTextColorArgb = colorVal }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (colorVal == 0xFFFFFFFFL || colorVal == 0xFFFFEB3BL || colorVal == 0xFF76FF03L) Color.Black else Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (colorVal == 0xFFFFFFFFL || colorVal == 0xFFFFEB3BL || colorVal == 0xFF76FF03L) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom outline color palette for this cue
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Özel Dış Çizgi (Kenarlık) Rengi:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = { showCueOutlineColorPicker = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("cue_outline_color_rgb_btn")
                            ) {
                                Icon(Icons.Default.ColorLens, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RGB ile Seç", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val outlineOptions = listOf(
                                null to "Varsayılan",
                                0xFF000000L to "Siyah",
                                0xFFFFFFFFL to "Beyaz",
                                0xFF263238L to "Koyu Gri",
                                0xFF0D47A1L to "Lacivert",
                                0xFFB71C1CL to "Kırmızı",
                                0xFF4A148CL to "Mor",
                                0xFFFF6F00L to "Turuncu",
                                0xFF004D40L to "Yeşil"
                            )
                            outlineOptions.forEach { (colorVal, name) ->
                                val isSelected = customOutlineColorArgb == colorVal
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (colorVal != null) Color(colorVal) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { customOutlineColorArgb = colorVal }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (colorVal == 0xFFFFFFFFL) Color.Black else Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (colorVal == 0xFFFFFFFFL) Color.Black else if (colorVal != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Mini Live Screen Placement Preview Box
                        Text(
                            text = "CANLI KONUMLANDIRMA ÖNİZLEMESİ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(
                                    top = if (customVerticalAlign == SubtitleVerticalAlign.TOP) (customVerticalOffsetDp * 0.35f).dp else 4.dp,
                                    bottom = if (customVerticalAlign == SubtitleVerticalAlign.BOTTOM) (customVerticalOffsetDp * 0.35f).dp else 4.dp,
                                    start = 12.dp,
                                    end = 12.dp
                                ),
                            contentAlignment = when (customVerticalAlign) {
                                SubtitleVerticalAlign.TOP -> when (customHorizontalAlign) {
                                    SubtitleHorizontalAlign.LEFT -> Alignment.TopStart
                                    SubtitleHorizontalAlign.CENTER -> Alignment.TopCenter
                                    SubtitleHorizontalAlign.RIGHT -> Alignment.TopEnd
                                }
                                SubtitleVerticalAlign.MIDDLE -> when (customHorizontalAlign) {
                                    SubtitleHorizontalAlign.LEFT -> Alignment.CenterStart
                                    SubtitleHorizontalAlign.CENTER -> Alignment.Center
                                    SubtitleHorizontalAlign.RIGHT -> Alignment.CenterEnd
                                }
                                SubtitleVerticalAlign.BOTTOM -> when (customHorizontalAlign) {
                                    SubtitleHorizontalAlign.LEFT -> Alignment.BottomStart
                                    SubtitleHorizontalAlign.CENTER -> Alignment.BottomCenter
                                    SubtitleHorizontalAlign.RIGHT -> Alignment.BottomEnd
                                }
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .offset(x = (customHorizontalOffsetDp * 0.4f).dp)
                                    .then(
                                        if (customOutlineColorArgb != null) {
                                            Modifier.border(
                                                1.5.dp,
                                                Color(customOutlineColorArgb!!),
                                                RoundedCornerShape(4.dp)
                                            )
                                        } else Modifier
                                    )
                            ) {
                                Text(
                                    text = if (text.isNotBlank()) text else "Altyazı Metni",
                                    color = customTextColorArgb?.let { Color(it) } ?: Color.White,
                                    fontSize = (customFontSizeSp * 0.65f).sp,
                                    fontWeight = if (customIsBold == true) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (customIsItalic == true) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (customIsUnderline == true) TextDecoration.Underline else TextDecoration.None,
                                    textAlign = when (customHorizontalAlign) {
                                        SubtitleHorizontalAlign.LEFT -> TextAlign.Start
                                        SubtitleHorizontalAlign.CENTER -> TextAlign.Center
                                        SubtitleHorizontalAlign.RIGHT -> TextAlign.End
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onDelete(cue.id) },
                    modifier = Modifier.testTag("delete_cue_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Altyazıyı Sil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("İptal")
                }

                Button(
                    onClick = {
                        val updated = cue.copy(
                            cleanText = text,
                            rawText = text,
                            startTimeMs = startTimeMs,
                            endTimeMs = endTimeMs.coerceAtLeast(startTimeMs),
                            customPositionEnabled = customPositionEnabled,
                            customVerticalAlign = customVerticalAlign,
                            customHorizontalAlign = customHorizontalAlign,
                            customVerticalOffsetDp = customVerticalOffsetDp,
                            customHorizontalOffsetDp = customHorizontalOffsetDp,
                            customFontSizeSp = if (customPositionEnabled) customFontSizeSp else null,
                            customTextColorArgb = if (customPositionEnabled) customTextColorArgb else null,
                            customOutlineColorArgb = if (customPositionEnabled) customOutlineColorArgb else null,
                            customIsItalic = if (customPositionEnabled) customIsItalic else null,
                            customIsBold = if (customPositionEnabled) customIsBold else null,
                            customIsUnderline = if (customPositionEnabled) customIsUnderline else null
                        )
                        onSave(updated)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_cue_button")
                ) {
                    Text("Kaydet & Uygula", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCueTextColorPicker) {
        val curColor = customTextColorArgb?.let { Color(it) } ?: Color.White
        RgbColorPickerDialog(
            title = "Satır İçin Metin Rengi (RGB & HEX)",
            initialColor = curColor,
            onDismiss = { showCueTextColorPicker = false },
            onColorSelected = { col ->
                val r = (col.red * 255).toInt()
                val g = (col.green * 255).toInt()
                val b = (col.blue * 255).toInt()
                val argb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                customTextColorArgb = argb
            }
        )
    }

    if (showCueOutlineColorPicker) {
        val curColor = customOutlineColorArgb?.let { Color(it) } ?: Color.Black
        RgbColorPickerDialog(
            title = "Satır İçin Kenarlık Rengi (RGB & HEX)",
            initialColor = curColor,
            onDismiss = { showCueOutlineColorPicker = false },
            onColorSelected = { col ->
                val r = (col.red * 255).toInt()
                val g = (col.green * 255).toInt()
                val b = (col.blue * 255).toInt()
                val argb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                customOutlineColorArgb = argb
            }
        )
    }
}

@Composable
private fun TimeQuickStepButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
