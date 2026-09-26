package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.mkv.MkvSubtitleTrack
import java.io.File

@Composable
fun MkvSubtitleExtractionDialog(
    isOpen: Boolean,
    isInspecting: Boolean,
    isExtracting: Boolean,
    mkvFileName: String,
    tracks: List<MkvSubtitleTrack>,
    selectedTrack: MkvSubtitleTrack?,
    extractedFile: File?,
    errorMessage: String?,
    onTrackSelect: (MkvSubtitleTrack) -> Unit,
    onStartExtraction: () -> Unit,
    onOpenInEditor: (File) -> Unit,
    onSaveToDevice: (File) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (!isInspecting && !isExtracting) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isInspecting && !isExtracting,
            dismissOnClickOutside = !isInspecting && !isExtracting,
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
                .testTag("dialog_mkv_extract_subtitles")
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
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Videodan Altyazı Ayıkla",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "MKV Softsub Akışı • Orijinal Format Korunur",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!isInspecting && !isExtracting) {
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

                // Content Views
                when {
                    isInspecting -> {
                        InspectingContent(mkvFileName = mkvFileName)
                    }

                    isExtracting -> {
                        ExtractingContent(selectedTrack = selectedTrack)
                    }

                    extractedFile != null && extractedFile.exists() -> {
                        ExtractionSuccessContent(
                            extractedFile = extractedFile,
                            track = selectedTrack,
                            onOpenInEditor = { onOpenInEditor(extractedFile) },
                            onShare = { shareExtractedSubtitle(context, extractedFile) },
                            onSave = { onSaveToDevice(extractedFile) },
                            onClose = onDismiss
                        )
                    }

                    errorMessage != null -> {
                        ExtractionErrorContent(
                            errorMessage = errorMessage,
                            onClose = onDismiss
                        )
                    }

                    tracks.isEmpty() -> {
                        NoSubtitlesFoundContent(onClose = onDismiss)
                    }

                    else -> {
                        TrackSelectionContent(
                            mkvFileName = mkvFileName,
                            tracks = tracks,
                            selectedTrack = selectedTrack,
                            onTrackSelect = onTrackSelect,
                            onStartExtraction = onStartExtraction,
                            onClose = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectingContent(mkvFileName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MKV inceleniyor...",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mkvFileName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Softsub akışları aranıyor, lütfen bekleyin.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ExtractingContent(selectedTrack: MkvSubtitleTrack?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Altyazı ayıklanıyor...",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (selectedTrack != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${selectedTrack.trackTitle} (${selectedTrack.languageDisplayName})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Orijinal ${selectedTrack.codecName.uppercase()} akışı dışa aktarılıyor (OCR yapılmaz)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TrackSelectionContent(
    mkvFileName: String,
    tracks: List<MkvSubtitleTrack>,
    selectedTrack: MkvSubtitleTrack?,
    onTrackSelect: (MkvSubtitleTrack) -> Unit,
    onStartExtraction: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // File badge
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "MKV Dosyası",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = mkvFileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title and Subtitle as per specifications
        Text(
            text = "Altyazı Parçaları",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Altyazı seç",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Track items
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tracks.forEach { track ->
                val isSelected = selectedTrack?.streamIndex == track.streamIndex
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTrackSelect(track) }
                        .testTag("track_item_${track.streamIndex}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // Example format: "Altyazı 1 — Türkçe — ASS"
                            Text(
                                text = "Altyazı ${track.subtitleTrackNumber} — ${track.languageDisplayName} — ${track.codecName.uppercase()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Tür: ${track.codecName.uppercase()}  •  Dil: ${track.languageDisplayName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (track.trackTitle != "Altyazı ${track.subtitleTrackNumber}") {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Başlık: ${track.trackTitle}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Badges
                            if (track.isDefault || track.isForced) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (track.isDefault) {
                                        Badge(text = "Varsayılan", color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (track.isForced) {
                                        Badge(text = "Zorunlu", color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Info Note
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Seçilen parça orijinal akıştan doğrudan ayıklanır. Format, zamanlama ve stiller olduğu gibi korunur.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onClose) {
                Text("Vazgeç")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onStartExtraction,
                enabled = selectedTrack != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("btn_extract_subtitle")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Altyazıyı Ayıkla", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ExtractionSuccessContent(
    extractedFile: File,
    track: MkvSubtitleTrack?,
    onOpenInEditor: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val extension = extractedFile.extension.uppercase()
    val sizeKb = (extractedFile.length() / 1024.0).let { String.format("%.1f KB", it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Success Header
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Altyazı başarıyla ayıklandı.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Orijinal softsub dosyası başarıyla kurtarıldı.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // File Details Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Oluşturulan Dosya",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = extractedFile.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Format: .$extension  •  Boyut: $sizeKb  •  Dil: ${track?.languageDisplayName ?: "Bilinmiyor"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenInEditor,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_extracted_in_editor")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Düzenleyicide Aç", fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_share_extracted_subtitle")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paylaş")
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_save_extracted_subtitle")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cihaza Kaydet")
                }
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kapat")
            }
        }
    }
}

@Composable
private fun NoSubtitlesFoundContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Bu MKV dosyasında softsub bulunamadı.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Bu video dosyasında gömülü softsub altyazı akışı yer almıyor.\n\nGörüntüye önceden yakılmış (hardcoded / burned-in) altyazılar birer altyazı akışı değildir ve bu işlemle ayıklanamaz. Yalnızca MKV kapsayıcısında saklanan gerçek softsub parçaları ayıklanabilir.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onClose) {
                Text("Tamam")
            }
        }
    }
}

@Composable
private fun ExtractionErrorContent(
    errorMessage: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Altyazı ayıklanamadı.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onClose) {
                Text("Kapat")
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun shareExtractedSubtitle(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Altyazıyı Paylaş"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
