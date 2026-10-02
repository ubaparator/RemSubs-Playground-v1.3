package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.torrent.TorrentDownloadInfo
import com.example.torrent.TorrentState
import java.io.File

@Composable
fun TorrentDownloadSection(
    downloadInfo: TorrentDownloadInfo,
    onStartMagnet: (String) -> Unit,
    onPickTorrentFile: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenDownloadedVideo: (File) -> Unit,
    onSelectVideoFile: (String) -> Unit = {},
    onOpenAnimeSearch: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSaveToGallery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var magnetInput by remember { mutableStateOf("") }
    var showFileList by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("card_torrent_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Magnet / Torrent İndir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Dahili BitTorrent & Magnet istemcisi",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (downloadInfo.state != TorrentState.IDLE) {
                    IconButton(
                        onClick = onOpenAnimeSearch,
                        modifier = Modifier.testTag("btn_header_anime_search")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Anime Ara",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State-based content
            when (downloadInfo.state) {
                TorrentState.IDLE -> {
                    FilledTonalButton(
                        onClick = onOpenAnimeSearch,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_anime_search")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Anime Ara ve İndir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("SubsPlease • Nyaa • 480p / 720p / 1080p", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Magnet input field
                    OutlinedTextField(
                        value = magnetInput,
                        onValueChange = { magnetInput = it },
                        label = { Text("Magnet bağlantısını buraya yapıştır", fontSize = 12.sp) },
                        placeholder = { Text("magnet:?xt=urn:btih:...", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_magnet_link")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (magnetInput.isNotBlank()) {
                                    onStartMagnet(magnetInput.trim())
                                }
                            },
                            enabled = magnetInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_start_magnet_download")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("İndir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onPickTorrentFile,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_pick_torrent_file")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(".torrent Seç", fontSize = 12.sp)
                        }
                    }
                }

                TorrentState.CONNECTING_TRACKERS,
                TorrentState.RESOLVING_METADATA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        TorrentTitle(downloadInfo)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = downloadInfo.statusMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (downloadInfo.connectedPeers > 0 || downloadInfo.dhtNodes > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Bağlı eş: ${downloadInfo.connectedPeers}  •  DHT: ${downloadInfo.dhtNodes} düğüm",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("btn_torrent_cancel")
                        ) {
                            Text("İptal", fontSize = 12.sp)
                        }
                    }
                }

                TorrentState.DOWNLOADING,
                TorrentState.TRANSFERRING,
                TorrentState.PAUSED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        TorrentTitle(downloadInfo)

                        if (downloadInfo.awaitingFileSelection) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = downloadInfo.statusMessage,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            downloadInfo.selectedFile?.takeIf { downloadInfo.files.size > 1 }?.let { selected ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Seçili: ${selected.displayName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { downloadInfo.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (downloadInfo.state == TorrentState.TRANSFERRING) {
                                        downloadInfo.statusMessage
                                    } else {
                                        "İlerleme: %${downloadInfo.progressPercentage}"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Hız: ${downloadInfo.speedText}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "İndirilen: ${downloadInfo.formatDownloadedSize()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Kalan süre: ${downloadInfo.etaText}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (downloadInfo.state == TorrentState.DOWNLOADING && downloadInfo.statusMessage != "İndiriliyor") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = downloadInfo.statusMessage,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Peer / Seeder count
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Seeder: ${downloadInfo.seeders}  •  Leecher: ${downloadInfo.leechers}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Bağlı eş: ${downloadInfo.connectedPeers}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (downloadInfo.pieceCount > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Parça: ${downloadInfo.pieceCount} (${TorrentDownloadInfo.formatBytes(downloadInfo.pieceSize.toLong())}/parça)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (downloadInfo.files.size > 1) {
                            val listVisible = showFileList || downloadInfo.awaitingFileSelection
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Torrent Dosyaları (${downloadInfo.files.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!downloadInfo.awaitingFileSelection) {
                                    OutlinedButton(
                                        onClick = { showFileList = !showFileList },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.testTag("btn_toggle_torrent_file_list")
                                    ) {
                                        Text(
                                            text = if (showFileList) "Gizle" else "Bölüm Değiştir",
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (listVisible) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TorrentFileList(
                                    downloadInfo = downloadInfo,
                                    onSelectVideoFile = { path ->
                                        onSelectVideoFile(path)
                                        showFileList = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Control Buttons: Durdur, Devam Et, İptal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (downloadInfo.state == TorrentState.TRANSFERRING) {
                                Text(
                                    text = "Dosya hazırlanıyor...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            } else if (downloadInfo.state == TorrentState.DOWNLOADING) {
                                if (!downloadInfo.awaitingFileSelection) {
                                    OutlinedButton(
                                        onClick = onPause,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("btn_torrent_pause")
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Durdur", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onResume,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("btn_torrent_resume")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Devam Et", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(8.dp),
                                enabled = downloadInfo.state != TorrentState.TRANSFERRING,
                                modifier = Modifier.testTag("btn_torrent_cancel")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("İptal", fontSize = 12.sp)
                            }
                        }
                    }
                }

                TorrentState.COMPLETED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "İndirme tamamlandı",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = downloadInfo.downloadedFile?.name ?: downloadInfo.torrentName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = TorrentDownloadInfo.formatBytes(downloadInfo.totalBytes) + " • Uygulama deposunda",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        when {
                            downloadInfo.isSavingToGallery -> {
                                LinearProgressIndicator(
                                    progress = { downloadInfo.gallerySaveProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Galeriye kopyalanıyor... %${(downloadInfo.gallerySaveProgress * 100).toInt()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            downloadInfo.permanentUri != null -> {
                                Text(
                                    text = "✓ Galeriye kaydedildi (Movies/RemSubs)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = onSaveToGallery,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_torrent_save_gallery")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Galeriye de Kaydet", fontSize = 12.sp)
                                }
                                downloadInfo.galleryError?.let { error ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = error,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !downloadInfo.isSavingToGallery,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Kapat", fontSize = 12.sp)
                            }

                            downloadInfo.downloadedFile?.let { file ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onOpenDownloadedVideo(file) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("btn_open_downloaded_video")
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Videoyu Düzenle / Oynat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                TorrentState.ERROR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "İndirme Hatası",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val errType = downloadInfo.errorType
                        if (errType != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "Neden: ${errType.displayTurkish}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = downloadInfo.errorMessage ?: "Bilinmeyen bir hata oluştu.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_torrent_dismiss_error")
                            ) {
                                Text("Kapat", fontSize = 12.sp)
                            }
                            if (downloadInfo.canRetry) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("btn_torrent_retry")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tekrar Dene", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentTitle(downloadInfo: TorrentDownloadInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (downloadInfo.sourceLabel.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = downloadInfo.sourceLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = downloadInfo.torrentName,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TorrentFileList(
    downloadInfo: TorrentDownloadInfo,
    onSelectVideoFile: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        downloadInfo.files.forEach { entry ->
            val isSelected = !downloadInfo.awaitingFileSelection && entry.path == downloadInfo.selectedVideoFileName

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (entry.isVideo) Icons.Default.Movie else Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = if (entry.isVideo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (entry.isVideo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (entry.isVideo) {
                            TorrentDownloadInfo.formatBytes(entry.sizeBytes)
                        } else {
                            "${TorrentDownloadInfo.formatBytes(entry.sizeBytes)} • Video değil (atlanıyor)"
                        },
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (entry.isVideo) {
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Seçili",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectVideoFile(entry.path) },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("btn_select_file_${entry.displayName}")
                        ) {
                            Text(if (downloadInfo.awaitingFileSelection) "İndir" else "Seç", fontSize = 10.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
