package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleCue
import com.example.ui.AxiSubUiState

@Composable
fun SubtitleListSection(
    uiState: AxiSubUiState,
    onPickSubtitle: () -> Unit,
    onLoadDemoSubtitle: () -> Unit,
    onSeekToCue: (Long) -> Unit,
    onAdjustTimeOffset: (Long) -> Unit,
    onResetTimeOffset: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEditCue: (SubtitleCue) -> Unit,
    onAddNewCue: () -> Unit,
    onExportAss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Filter cues by search query
    val filteredCues = if (uiState.searchQuery.isBlank()) {
        uiState.subtitles
    } else {
        uiState.subtitles.filter {
            it.cleanText.contains(uiState.searchQuery, ignoreCase = true) ||
                    it.actor.contains(uiState.searchQuery, ignoreCase = true) ||
                    it.styleName.contains(uiState.searchQuery, ignoreCase = true)
        }
    }

    // Scroll to active cue automatically when playing
    LaunchedEffect(uiState.activeCues.firstOrNull()?.id) {
        val activeCue = uiState.activeCues.firstOrNull()
        if (activeCue != null) {
            val index = filteredCues.indexOfFirst { it.id == activeCue.id }
            if (index >= 0) {
                listState.animateScrollToItem((index - 1).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Action Bar: Select Subtitle & Export .ASS & Demo Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPickSubtitle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("select_subtitle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Altyazı (.srt / .ass)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            FilledTonalButton(
                onClick = onExportAss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("export_ass_button_list")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(".ASS Çıktı", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onLoadDemoSubtitle,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("demo_subtitle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("Demo", fontSize = 12.sp)
            }
        }

        if (uiState.wasConvertedFromSrt) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Transform,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SRT altyazısı .ASS'ye çevrildi. Değişiklikler eşzamanlı 2. .ASS'ye işleniyor!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onExportAss) {
                        Text("Çıktı Al", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Time Synchronization Bar (Fine Sync Adjustment: -500ms, -100ms, +100ms, +500ms)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Senkron: ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (uiState.subtitleStyle.timeOffsetMs >= 0) "+" else ""}${uiState.subtitleStyle.timeOffsetMs} ms",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.subtitleStyle.timeOffsetMs != 0L) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SyncStepChip(text = "-0.5s") { onAdjustTimeOffset(-500L) }
                    SyncStepChip(text = "-0.1s") { onAdjustTimeOffset(-100L) }
                    SyncStepChip(text = "+0.1s") { onAdjustTimeOffset(100L) }
                    SyncStepChip(text = "+0.5s") { onAdjustTimeOffset(500L) }
                    if (uiState.subtitleStyle.timeOffsetMs != 0L) {
                        SyncStepChip(text = "0") { onResetTimeOffset() }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search in dialogues
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Altyazılarda ara...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Temizle",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("search_subtitles_field")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle Cue List header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DİYALOG SATIRLARI (${filteredCues.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onAddNewCue)
                        .testTag("add_cue_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Yeni Satır",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = "Tıkla & Zamana Git",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle Cues LazyColumn
        if (filteredCues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.subtitles.isEmpty()) "Henüz altyazı yüklenmedi" else "Aramaya uygun satır bulunamadı",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.subtitles.isEmpty()) {
                            Button(
                                onClick = onPickSubtitle,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Altyazı (.ass / .srt) Seç")
                            }
                        }
                        OutlinedButton(
                            onClick = onAddNewCue,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Yeni Satır Oluştur")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("subtitles_lazy_column")
            ) {
                itemsIndexed(
                    items = filteredCues,
                    key = { _, cue -> cue.id }
                ) { _, cue ->
                    val isActive = uiState.activeCues.any { it.id == cue.id }
                    SubtitleCueItem(
                        cue = cue,
                        isActive = isActive,
                        onClick = { onSeekToCue(cue.startTimeMs) },
                        onEdit = { onEditCue(cue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleCueItem(
    cue: SubtitleCue,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val cardBackground = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val borderModifier = if (isActive) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
    } else {
        Modifier
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("subtitle_row_${cue.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index & Active indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Aktif",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = cue.id.toString(),
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text & Timestamp
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${cue.formatStartTime()}  ➔  ${cue.formatEndTime()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (cue.styleName.isNotBlank() && cue.styleName != "Default") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = cue.styleName,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = cue.cleanText,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )

                // Custom placement badge if enabled
                if (cue.customPositionEnabled) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Özel Konum: ${cue.customVerticalAlign.name}-${cue.customHorizontalAlign.name}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Edit button for this cue
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("edit_cue_btn_${cue.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Altyazıyı Düzenle",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SyncStepChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
