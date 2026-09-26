package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.encode.EncodeState
import com.example.encode.EncoderOption
import com.example.encode.EncodingSettings
import com.example.encode.HardsubEncoder
import com.example.ui.AxiSubUiState
import java.io.File
import java.util.Locale

@Composable
fun HardsubEncodeDialog(
    uiState: AxiSubUiState,
    encodeState: EncodeState,
    onDismiss: () -> Unit,
    onStartEncode: () -> Unit,
    onCancelEncode: () -> Unit,
    onPlayEncodedVideo: (File) -> Unit,
    onSaveToDevice: (File) -> Unit,
    onUpdateSettings: (EncodingSettings) -> Unit = {},
    onOpenCompatibilityTest: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!encodeState.isEncoding && !encodeState.isPreparing) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !encodeState.isEncoding,
            dismissOnClickOutside = !encodeState.isEncoding,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .testTag("dialog_hardsub_encode")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Hardsub Encode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Dinamik Kodlayıcı • libass • Uyumluluk Odaklı",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!encodeState.isEncoding && !encodeState.isPreparing) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Content based on state
                when {
                    encodeState.isEncoding || encodeState.isPreparing -> {
                        EncodingProgressContent(
                            encodeState = encodeState,
                            onCancelEncode = onCancelEncode,
                            showLogs = showLogs,
                            onToggleLogs = { showLogs = !showLogs }
                        )
                    }

                    encodeState.isCompleted && encodeState.outputVideoFile != null -> {
                        EncodingSuccessContent(
                            outputFile = encodeState.outputVideoFile,
                            encodeState = encodeState,
                            onPlay = { onPlayEncodedVideo(encodeState.outputVideoFile) },
                            onShare = { shareVideoFile(context, encodeState.outputVideoFile) },
                            onSave = { onSaveToDevice(encodeState.outputVideoFile) },
                            onClose = onDismiss
                        )
                    }

                    encodeState.errorMessage != null || encodeState.isCancelled -> {
                        EncodingErrorContent(
                            encodeState = encodeState,
                            onRetry = onStartEncode,
                            onClose = onDismiss,
                            showLogs = showLogs,
                            onToggleLogs = { showLogs = !showLogs }
                        )
                    }

                    else -> {
                        ReadyToEncodeContent(
                            uiState = uiState,
                            settings = uiState.encodingSettings,
                            onUpdateSettings = onUpdateSettings,
                            onOpenCompatibilityTest = onOpenCompatibilityTest,
                            onStartEncode = onStartEncode,
                            onClose = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyToEncodeContent(
    uiState: AxiSubUiState,
    settings: EncodingSettings,
    onUpdateSettings: (EncodingSettings) -> Unit,
    onOpenCompatibilityTest: () -> Unit,
    onStartEncode: () -> Unit,
    onClose: () -> Unit
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // 1. Kaynak Video Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Kaynak Video",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.videoTitle ?: "Video seçili",
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val metaLines = uiState.sourceVideoMetadata.toDisplayLines()
                metaLines.forEach { (label, value) ->
                    InfoRow(label = "$label:", value = value)
                }

                InfoRow(
                    label = "Altyazı Satırları:",
                    value = "${uiState.subtitles.size} satır (.ass senkronize)"
                )
                InfoRow(
                    label = "Font:",
                    value = "${uiState.subtitleStyle.fontName} (${uiState.subtitleStyle.fontSizeSp.toInt()} sp)"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Encoder Seçimi (Özet ve Geçiş)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Encoder Modu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = settings.encoderOption.displayName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenCompatibilityTest,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_open_compatibility_test")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Uyumluluk Testi", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle advanced encoding settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvancedSettings = !showAdvancedSettings }
                        .padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gelişmiş Kodlama Ayarları",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (showAdvancedSettings) "Gizle ▲" else "Göster ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showAdvancedSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Encoder List Chips
                        Text(
                            text = "Encoder Seçimi:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EncoderOption.values().forEach { opt ->
                                FilterChip(
                                    selected = settings.encoderOption == opt,
                                    onClick = { onUpdateSettings(settings.copy(encoderOption = opt)) },
                                    label = { Text(opt.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        // CRF Control (Only if encoder supports CRF!)
                        if (settings.encoderOption.supportsCrf) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "CRF (Sabit Hız Faktörü):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${settings.crf} ${if (settings.crf == 23) "(Önerilen)" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.crf.toFloat(),
                                onValueChange = { onUpdateSettings(settings.copy(crf = it.toInt())) },
                                valueRange = 16f..28f,
                                steps = 11,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Preset Control (Only if encoder supports Preset!)
                        if (settings.encoderOption.supportsPreset) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Preset (Kodlama Hızı):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium").forEach { p ->
                                    FilterChip(
                                        selected = settings.preset == p,
                                        onClick = { onUpdateSettings(settings.copy(preset = p)) },
                                        label = { Text(p, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Bitrate Control (Shown for MediaCodec or custom rate)
                        if (settings.encoderOption.isHardware || !settings.encoderOption.supportsCrf) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Hedef Bitrate:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Otomatik", "2000k", "3500k", "5000k", "8000k", "12000k").forEach { b ->
                                    FilterChip(
                                        selected = settings.bitrate == b,
                                        onClick = { onUpdateSettings(settings.copy(bitrate = b)) },
                                        label = { Text(b, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // FPS Selection
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "FPS (Kare Hızı):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Kaynakla Aynı", "24", "30", "60").forEach { fpsOpt ->
                                FilterChip(
                                    selected = settings.fps == fpsOpt,
                                    onClick = { onUpdateSettings(settings.copy(fps = fpsOpt)) },
                                    label = { Text(fpsOpt, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Pixel Format Selection
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pixel Format:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("yuv420p", "nv12", "Otomatik").forEach { pixOpt ->
                                FilterChip(
                                    selected = settings.pixelFormat == pixOpt,
                                    onClick = { onUpdateSettings(settings.copy(pixelFormat = pixOpt)) },
                                    label = { Text(pixOpt, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Hardware Acceleration Option
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Donanım Hızlandırma:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Otomatik", "Zorunlu Açık", "Kapalı (Saf Yazılım)").forEach { hwOpt ->
                                FilterChip(
                                    selected = settings.hardwareAcceleration == hwOpt,
                                    onClick = { onUpdateSettings(settings.copy(hardwareAcceleration = hwOpt)) },
                                    label = { Text(hwOpt, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tüm renkler, stiller ve satır özel konumları (pos) videoya kalıcı olarak hardsub işlenecektir.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onClose) {
                Text("Vazgeç")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onStartEncode,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("btn_confirm_start_encode")
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Encode Al", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EncodingProgressContent(
    encodeState: EncodeState,
    onCancelEncode: () -> Unit,
    showLogs: Boolean,
    onToggleLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        if (encodeState.isPreparing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = encodeState.currentPhaseText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Percent and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = encodeState.currentPhaseText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Encoder: ${encodeState.currentEncoderName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "%${encodeState.progressPercentage}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { encodeState.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Grid
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            icon = Icons.Default.VideoFile,
                            label = "İşlenen / Toplam Kare",
                            value = "${encodeState.currentFrame} / ${encodeState.totalFrames}"
                        )
                        MetricItem(
                            icon = Icons.Default.Speed,
                            label = "Anlık Hız (FPS)",
                            value = String.format(Locale.US, "%.1f FPS", encodeState.currentFps)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            icon = Icons.Default.HourglassTop,
                            label = "FPS / Toplam Kare Oranı",
                            value = String.format(Locale.US, "%.5f / sn", encodeState.fpsToTotalFramesRatio),
                            highlight = true
                        )
                        MetricItem(
                            icon = Icons.Default.Timer,
                            label = "Kalan Süre",
                            value = encodeState.averageEstimatedFinishText,
                            highlight = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Geçen Süre: ${HardsubEncoder.formatTimeSeconds(encodeState.elapsedSeconds)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (encodeState.lastLogLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "FFmpeg: ${encodeState.lastLogLine.trim()}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleLogs) {
                    Text(if (showLogs) "Logları Gizle" else "Logları Göster", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onCancelEncode,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_cancel_encode")
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("İptal Et")
                }
            }

            AnimatedVisibility(visible = showLogs) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = encodeState.fullLogs.ifBlank { encodeState.lastLogLine },
                        color = Color.Green,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun EncodingSuccessContent(
    outputFile: File,
    encodeState: EncodeState,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val fileSizeMb = String.format(Locale.US, "%.2f MB", outputFile.length().toDouble() / (1024.0 * 1024.0))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Hardsub Başarıyla Gömüldü!",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Altyazılar ${encodeState.currentEncoderName} ile kalıcı olarak işlendi",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InfoRow(label = "Çıktı Dosyası:", value = outputFile.name)
                InfoRow(label = "Dosya Boyutu:", value = fileSizeMb)
                InfoRow(
                    label = "Tamamlanma Süresi:",
                    value = "${HardsubEncoder.formatTimeSeconds(encodeState.elapsedSeconds)} saniye"
                )
                InfoRow(
                    label = "İşlenen Toplam Kare:",
                    value = "${encodeState.totalFrames} kare"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_play_encoded_video")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Videoyu Oynat / Önizle")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paylaş", fontSize = 13.sp)
                }

                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cihaza Kaydet", fontSize = 13.sp)
                }
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Kapat")
            }
        }
    }
}

@Composable
private fun EncodingErrorContent(
    encodeState: EncodeState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    showLogs: Boolean,
    onToggleLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (encodeState.isCancelled) "İşlem İptal Edildi" else "Encode Hatası",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = encodeState.errorMessage ?: "Bilinmeyen bir hata oluştu.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(onClick = onToggleLogs) {
            Text(if (showLogs) "Hata Günlüğünü Gizle" else "Hata Günlüğünü Göster", fontSize = 12.sp)
        }

        AnimatedVisibility(visible = showLogs) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = encodeState.fullLogs.ifBlank { "Kayıtlı detaylı log bulunamadı." },
                    color = Color.Red,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onClose) {
                Text("Kapat")
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = onRetry) {
                Text("Yeniden Dene")
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun shareVideoFile(context: Context, file: File) {
    if (!file.exists() || file.length() == 0L) {
        Toast.makeText(context, "Paylaşılacak video dosyası bulunamadı veya boş!", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Hardsub Videoyu Paylaş"))
    } catch (e: Exception) {
        Toast.makeText(context, "Paylaşım hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
