package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleCue
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleStyle
import com.example.model.SubtitleVerticalAlign
import com.example.parser.AssGenerator
import com.example.parser.HtmlSubtitleParser
import com.example.parser.SubtitleParser
import com.example.ui.AxiSubUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

private enum class ActiveSheet {
    NONE,
    TEXT_STYLE,
    TIMING_AUDIO,
    TYPESETTING,
    MORE_TOOLS
}

/**
 * Modern, Ergonomic Single-Workspace Subtitle Editor Screen.
 * Strictly adheres to mobile-first UX:
 * 1. Video Preview (gesture zoom/pan, subtitle overlay)
 * 2. Playback / Timeline controls (frame step, timecode)
 * 3. Selected Cue In-Place Editor (quick text + +/-100ms adjust + CPS counter)
 * 4. Subtitle List (tap to seek, swipe to delete, auto-scroll to active cue)
 * 5. Minimal 5-Tool Bottom Action Bar: [+] [T] [⏱] [ASS] [⋮]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSubtitleEditorScreen(
    uiState: AxiSubUiState,
    seekEvent: SharedFlow<Long>,
    onNavigateBack: () -> Unit,
    onUpdatePosition: (Long) -> Unit,
    onUpdateDuration: (Long) -> Unit,
    onSetPlaying: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onSaveCue: (SubtitleCue) -> Unit,
    onAddNewCue: () -> Unit,
    onDeleteCue: (Int) -> Unit,
    onSelectCue: (Int?) -> Unit,
    onDuplicateCue: (SubtitleCue) -> Unit,
    onSplitCue: (SubtitleCue) -> Unit,
    onAdjustTimeOffset: (Long) -> Unit,
    onResetTimeOffset: () -> Unit,
    onUpdateStyle: ((SubtitleStyle) -> SubtitleStyle) -> Unit,
    onPickTtfFont: () -> Unit,
    onSelectPresetFont: (String, FontFamily?) -> Unit,
    onOpenExportDialog: () -> Unit,
    onOpenEncodeDialog: () -> Unit,
    onPickVideo: () -> Unit,
    onPickSubtitle: () -> Unit,
    onExtractMkvSubtitle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReplaceAllText: (String, String) -> Unit,
    onVideoDimensionsDetected: (Int, Int) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var showColorPickerType by remember { mutableStateOf<String?>(null) } // "text", "outline", "box"

    // Identify active or selected cue for the In-Place Quick Editor
    val selectedOrActiveCue = remember(uiState.subtitles, uiState.selectedCueId, uiState.currentPositionMs) {
        if (uiState.selectedCueId != null) {
            uiState.subtitles.find { it.id == uiState.selectedCueId }
        } else {
            uiState.activeCues.firstOrNull() ?: uiState.subtitles.firstOrNull()
        }
    }

    // Auto-scroll list to active cue when playing
    LaunchedEffect(uiState.activeCues) {
        val active = uiState.activeCues.firstOrNull()
        if (active != null && uiState.isPlaying) {
            val idx = uiState.subtitles.indexOfFirst { it.id == active.id }
            if (idx >= 0) {
                listState.animateScrollToItem((idx - 1).coerceAtLeast(0))
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        // --- 1. COMPACT TOP HEADER ---
        if (!uiState.isFullscreen) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("nav_back_to_menu")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Menüye Dön"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = uiState.subtitleFileName ?: "Yeni Altyazı",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${uiState.subtitles.size} Altyazı Satırı • PlayRes ${uiState.sourceVideoMetadata.width.let { if (it > 0) it else 1920 }}x${uiState.sourceVideoMetadata.height.let { if (it > 0) it else 1080 }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Undo / Redo quick access icons in top bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onUndo,
                            enabled = uiState.canUndo,
                            modifier = Modifier.size(36.dp).testTag("action_undo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Geri Al",
                                tint = if (uiState.canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(
                            onClick = onRedo,
                            enabled = uiState.canRedo,
                            modifier = Modifier.size(36.dp).testTag("action_redo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Yinele",
                                tint = if (uiState.canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }

        // --- 2. VIDEO PREVIEW PANEL ---
        VideoPlayerSection(
            uiState = uiState,
            seekEvent = seekEvent,
            onUpdatePosition = onUpdatePosition,
            onUpdateDuration = onUpdateDuration,
            onSetPlaying = onSetPlaying,
            onSeekTo = onSeekTo,
            onSetSpeed = onSetSpeed,
            onToggleFullscreen = onToggleFullscreen,
            onPlayerError = {},
            onEditActiveCue = { cue -> onSelectCue(cue.id) },
            onVideoDimensionsDetected = onVideoDimensionsDetected,
            modifier = if (uiState.isFullscreen) Modifier.weight(1f) else Modifier
        )

        if (!uiState.isFullscreen) {
            // --- 3. SELECTED CUE IN-PLACE QUICK EDITOR ---
            if (selectedOrActiveCue != null) {
                QuickCueEditorCard(
                    cue = selectedOrActiveCue,
                    currentPosMs = uiState.currentPositionMs,
                    onSave = onSaveCue,
                    onDuplicate = { onDuplicateCue(selectedOrActiveCue) },
                    onSplit = { onSplitCue(selectedOrActiveCue) },
                    onDelete = { onDeleteCue(selectedOrActiveCue.id) },
                    onSnapStartToCurrentPos = {
                        val newCue = selectedOrActiveCue.copy(
                            startTimeMs = uiState.currentPositionMs,
                            endTimeMs = (selectedOrActiveCue.endTimeMs).coerceAtLeast(uiState.currentPositionMs + 500L)
                        )
                        onSaveCue(newCue)
                    },
                    onSnapEndToCurrentPos = {
                        if (uiState.currentPositionMs > selectedOrActiveCue.startTimeMs) {
                            val newCue = selectedOrActiveCue.copy(endTimeMs = uiState.currentPositionMs)
                            onSaveCue(newCue)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // --- 4. SUBTITLE LIST (LazyColumn with Swipe-To-Delete) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.subtitles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Henüz altyazı yok",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onAddNewCue,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("empty_list_add_cue")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Altyazı Ekle")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.subtitles,
                            key = { _, cue -> cue.id }
                        ) { _, cue ->
                            val isActive = uiState.activeCues.any { it.id == cue.id }
                            val isSelected = uiState.selectedCueId == cue.id

                            SwipeableCueCard(
                                cue = cue,
                                isActive = isActive,
                                isSelected = isSelected,
                                onClick = {
                                    onSelectCue(cue.id)
                                    onSeekTo(cue.startTimeMs)
                                },
                                onDelete = { onDeleteCue(cue.id) }
                            )
                        }
                    }
                }
            }

            // --- 5. MINIMAL 5-TOOL BOTTOM ACTION BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [+] Add Subtitle at Current Playhead
                    FilledTonalIconButton(
                        onClick = onAddNewCue,
                        modifier = Modifier.testTag("bar_btn_add_cue")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Altyazı Ekle",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // [T] Text & Style
                    IconButton(
                        onClick = { activeSheet = ActiveSheet.TEXT_STYLE },
                        modifier = Modifier.testTag("bar_btn_text_style")
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Metin ve Stil",
                            tint = if (activeSheet == ActiveSheet.TEXT_STYLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // [⏱] Timing & Waveform
                    IconButton(
                        onClick = { activeSheet = ActiveSheet.TIMING_AUDIO },
                        modifier = Modifier.testTag("bar_btn_timing")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Zamanlama ve Ses",
                            tint = if (activeSheet == ActiveSheet.TIMING_AUDIO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // [ASS] Typesetting & Overrides
                    IconButton(
                        onClick = { activeSheet = ActiveSheet.TYPESETTING },
                        modifier = Modifier.testTag("bar_btn_typesetting")
                    ) {
                        Text(
                            text = "ASS",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = if (activeSheet == ActiveSheet.TYPESETTING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // [⋮] Aegisub Tools & Export & Encode
                    IconButton(
                        onClick = { activeSheet = ActiveSheet.MORE_TOOLS },
                        modifier = Modifier.testTag("bar_btn_more")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Araçlar ve Çıktı",
                            tint = if (activeSheet == ActiveSheet.MORE_TOOLS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
    }

    // --- BOTTOM SHEETS ---

    // 1. TEXT & STYLE MODAL BOTTOM SHEET [T]
    if (activeSheet == ActiveSheet.TEXT_STYLE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Metin & Stil Ayarları",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Font Family Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Ailesi:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row {
                        OutlinedButton(
                            onClick = { onSelectPresetFont("Roboto", null) },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Roboto", fontSize = 11.sp)
                        }
                        Button(
                            onClick = onPickTtfFont,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FontDownload, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(uiState.customFontName ?: "Özel TTF Yükle", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Boyut: ${uiState.subtitleStyle.fontSizeSp.toInt()} sp",
                        fontSize = 13.sp,
                        modifier = Modifier.width(100.dp)
                    )
                    Slider(
                        value = uiState.subtitleStyle.fontSizeSp,
                        onValueChange = { size -> onUpdateStyle { it.copy(fontSizeSp = size) } },
                        valueRange = 14f..60f,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bold, Italic, Underline, Box Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    FilledTonalIconButton(
                        onClick = { onUpdateStyle { it.copy(isBold = !it.isBold) } }
                    ) {
                        Icon(
                            Icons.Default.FormatBold,
                            null,
                            tint = if (uiState.subtitleStyle.isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onUpdateStyle { it.copy(isItalic = !it.isItalic) } }
                    ) {
                        Icon(
                            Icons.Default.FormatItalic,
                            null,
                            tint = if (uiState.subtitleStyle.isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onUpdateStyle { it.copy(isUnderline = !it.isUnderline) } }
                    ) {
                        Icon(
                            Icons.Default.FormatUnderlined,
                            null,
                            tint = if (uiState.subtitleStyle.isUnderline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onUpdateStyle { it.copy(hasBackgroundBox = !it.hasBackgroundBox) } }
                    ) {
                        Icon(
                            Icons.Default.Style,
                            null,
                            tint = if (uiState.subtitleStyle.hasBackgroundBox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Colors: Text, Outline, Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { showColorPickerType = "text" }) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(uiState.subtitleStyle.textColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Metin Rengi", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { showColorPickerType = "outline" }) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(uiState.subtitleStyle.outlineColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dış Çizgi", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 2. TIMING & AUDIO MODAL BOTTOM SHEET [⏱]
    if (activeSheet == ActiveSheet.TIMING_AUDIO) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Zamanlama & Ses Dalga Formu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Audio Waveform Visualization Representation
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0 until 40) {
                            val h = remember(i) { (20..70).random().dp }
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(h)
                                    .background(
                                        if (i in 15..25) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Overall Sync Offset Adjustment
                Text(
                    text = "Genel Senkron Kayması: ${uiState.subtitleStyle.timeOffsetMs} ms",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    OutlinedButton(onClick = { onAdjustTimeOffset(-500L) }) { Text("-500ms", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onAdjustTimeOffset(-100L) }) { Text("-100ms", fontSize = 11.sp) }
                    OutlinedButton(onClick = onResetTimeOffset) { Text("Sıfırla", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onAdjustTimeOffset(100L) }) { Text("+100ms", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onAdjustTimeOffset(500L) }) { Text("+500ms", fontSize = 11.sp) }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 3. TYPESETTING & OVERRIDES MODAL BOTTOM SHEET [ASS]
    if (activeSheet == ActiveSheet.TYPESETTING) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "ASS Override & Typesetting Araçları",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seçili altyazıya hızlı ASS etiketi ekleyin:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Override Tag Chips
                val tags = listOf(
                    "{\\pos(960,1000)}" to "Pozisyon",
                    "{\\fad(250,250)}" to "Fade Efekti",
                    "{\\an8}" to "Üst Hizalama",
                    "{\\an5}" to "Orta Merkez",
                    "{\\c&H00FFFF&}" to "Sarı Renk",
                    "{\\bord3}" to "Kenarlık 3px",
                    "{\\shad2}" to "Gölge 2px",
                    "{\\frz15}" to "15° Döndürme",
                    "{\\clip(0,0,1920,540)}" to "Kırpma (Clip)",
                    "{\\p1}m 0 0 l 20 0 20 20 0 20{\\p0}" to "Vektör Çizim"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.take(3).forEach { (tag, label) ->
                        AssistChip(
                            onClick = {
                                selectedOrActiveCue?.let { cue ->
                                    val updated = cue.copy(rawText = tag + cue.rawText)
                                    onSaveCue(updated)
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.drop(3).take(3).forEach { (tag, label) ->
                        AssistChip(
                            onClick = {
                                selectedOrActiveCue?.let { cue ->
                                    val updated = cue.copy(rawText = tag + cue.rawText)
                                    onSaveCue(updated)
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 4. MORE AEGISUB TOOLS MODAL BOTTOM SHEET [⋮]
    if (activeSheet == ActiveSheet.MORE_TOOLS) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Gelişmiş Araçlar & Çıktı",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Hardsub Encode Button
                Button(
                    onClick = {
                        activeSheet = ActiveSheet.NONE
                        onOpenEncodeDialog()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("sheet_btn_encode"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hardsub Encode Al (Video + Intro + libass)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ASS Export Button
                FilledTonalButton(
                    onClick = {
                        activeSheet = ActiveSheet.NONE
                        onOpenExportDialog()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("sheet_btn_export"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(".ASS / .SRT Altyazı Dışa Aktar")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pick Local Video
                OutlinedButton(
                    onClick = {
                        activeSheet = ActiveSheet.NONE
                        onPickVideo()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.VideoFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Farklı Video Dosyası Seç")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pick External Subtitle
                OutlinedButton(
                    onClick = {
                        activeSheet = ActiveSheet.NONE
                        onPickSubtitle()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Farklı Altyazı Dosyası Aç (.ass / .srt)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Extract Subtitle from MKV
                OutlinedButton(
                    onClick = {
                        activeSheet = ActiveSheet.NONE
                        onExtractMkvSubtitle()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Style, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MKV Dosyasından Softsub Çıkart")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Color Picker Dialog
    if (showColorPickerType != null) {
        val initialColor = when (showColorPickerType) {
            "text" -> uiState.subtitleStyle.textColor
            "outline" -> uiState.subtitleStyle.outlineColor
            else -> uiState.subtitleStyle.backgroundColor
        }
        RgbColorPickerDialog(
            title = when (showColorPickerType) {
                "text" -> "Metin Rengi"
                "outline" -> "Dış Çizgi Rengi"
                else -> "Arka Plan Kutusu Rengi"
            },
            initialColor = initialColor,
            onDismiss = { showColorPickerType = null },
            onColorSelected = { col ->
                when (showColorPickerType) {
                    "text" -> onUpdateStyle { it.copy(textColor = col) }
                    "outline" -> onUpdateStyle { it.copy(outlineColor = col) }
                    "box" -> onUpdateStyle { it.copy(backgroundColor = col) }
                }
                showColorPickerType = null
            }
        )
    }
}

/**
 * In-place Quick Subtitle Cue Editor Card.
 * Allows instant typing, timing fine-tuning (+/-100ms), frame snap, split, duplicate, and live CPS counter.
 */
@Composable
private fun QuickCueEditorCard(
    cue: SubtitleCue,
    currentPosMs: Long,
    onSave: (SubtitleCue) -> Unit,
    onDuplicate: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onSnapStartToCurrentPos: () -> Unit,
    onSnapEndToCurrentPos: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawText by remember(cue.id, cue.rawText) { mutableStateOf(cue.rawText) }

    // Calculate CPS (Characters Per Second)
    val durationSec = (cue.endTimeMs - cue.startTimeMs).coerceAtLeast(100L) / 1000.0
    val cps = (cue.cleanText.length / durationSec)
    val cpsColor = when {
        cps > 22.0 -> Color(0xFFF44336) // Red
        cps > 16.0 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFF4CAF50) // Green
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: #ID • Start -> End • CPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${cue.id}",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${AssGenerator.formatAssTimestamp(cue.startTimeMs)} → ${AssGenerator.formatAssTimestamp(cue.endTimeMs)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // CPS Badge
                Surface(
                    color = cpsColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f CPS", cps),
                        color = cpsColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // In-place text field
            OutlinedTextField(
                value = rawText,
                onValueChange = { newText ->
                    rawText = newText
                    val updated = cue.copy(
                        rawText = newText,
                        cleanText = HtmlSubtitleParser.cleanToPlainText(newText)
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth().testTag("quick_edit_text_field"),
                placeholder = { Text("Altyazı metni girin...", fontSize = 13.sp) },
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action Row: Start -100ms/+100ms, End -100ms/+100ms, Snap, Split, Duplicate, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timing nudges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Giriş:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            val newStart = (cue.startTimeMs - 100L).coerceAtLeast(0L)
                            onSave(cue.copy(startTimeMs = newStart))
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("-100", fontSize = 10.sp)
                    }
                    TextButton(
                        onClick = {
                            val newStart = (cue.startTimeMs + 100L).coerceAtMost(cue.endTimeMs - 200L)
                            onSave(cue.copy(startTimeMs = newStart))
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("+100", fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("Çıkış:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            val newEnd = (cue.endTimeMs - 100L).coerceAtLeast(cue.startTimeMs + 200L)
                            onSave(cue.copy(endTimeMs = newEnd))
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("-100", fontSize = 10.sp)
                    }
                    TextButton(
                        onClick = {
                            val newEnd = cue.endTimeMs + 100L
                            onSave(cue.copy(endTimeMs = newEnd))
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("+100", fontSize = 10.sp)
                    }
                }

                // Quick buttons: Split, Duplicate, Delete
                Row {
                    IconButton(onClick = onSplit, modifier = Modifier.size(28.dp)) {
                        Text("✂", fontSize = 12.sp)
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * Individual Subtitle Cue Card with Swipe-to-Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCueCard(
    cue: SubtitleCue,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    isActive -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .then(
                    if (isActive || isSelected) {
                        Modifier.border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${cue.id}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${AssGenerator.formatAssTimestamp(cue.startTimeMs)} → ${AssGenerator.formatAssTimestamp(cue.endTimeMs)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = cue.cleanText.ifBlank { cue.rawText },
                        fontSize = 13.sp,
                        fontWeight = if (isActive || isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
