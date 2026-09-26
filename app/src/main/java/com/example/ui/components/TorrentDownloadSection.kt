package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    modifier: Modifier = Modifier
) {
    var magnetInput by remember { mutableStateOf("") }

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
                Column {
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State-based content
            when (downloadInfo.state) {
                TorrentState.IDLE -> {
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
                            .padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = downloadInfo.statusMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
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
                        // Title / Filename
                        Text(
                            text = downloadInfo.torrentName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { downloadInfo.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Info Table
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
                                text = if (downloadInfo.state == TorrentState.TRANSFERRING) {
                                    "Kalıcı Depolama"
                                } else {
                                    "Hız: ${downloadInfo.speedText}"
                                },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Control Buttons: Durdur, Devam Et, İptal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (downloadInfo.state == TorrentState.TRANSFERRING) {
                                Text(
                                    text = "Depolamaya aktarılıyor...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            } else if (downloadInfo.state == TorrentState.DOWNLOADING) {
                                OutlinedButton(
                                    onClick = onPause,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("btn_torrent_pause")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Durdur", fontSize = 12.sp)
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
                            text = downloadInfo.torrentName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
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
                        Text(
                            text = downloadInfo.errorMessage ?: "Bilinmeyen bir hata oluştu.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Tekrar Dene", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
