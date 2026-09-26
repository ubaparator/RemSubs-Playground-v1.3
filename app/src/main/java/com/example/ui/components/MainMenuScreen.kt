package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.torrent.TorrentDownloadInfo
import com.example.ui.AxiSubUiState
import java.io.File

/**
 * Main Menu Screen (Ana Menü):
 * - Magnet / Torrent İndir (Yeni bölüm: "video ile altyazı düzenleme" butonunun üstünde)
 * - Düz bir dikdörtgen: "video ile altyazı düzenleme"
 * - Kare: "yeni altyazı"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    uiState: AxiSubUiState,
    torrentDownloadInfo: TorrentDownloadInfo = TorrentDownloadInfo(),
    onOpenEditor: () -> Unit,
    onLoadDemoAndOpen: () -> Unit = onOpenEditor,
    onStartBlankProject: () -> Unit,
    onPickSubtitle: () -> Unit = {},
    onPickVideo: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onStartMagnetDownload: (String) -> Unit = {},
    onPickTorrentFile: () -> Unit = {},
    onPauseTorrentDownload: () -> Unit = {},
    onResumeTorrentDownload: () -> Unit = {},
    onCancelTorrentDownload: () -> Unit = {},
    onOpenDownloadedVideo: (File) -> Unit = {},
    onExtractSubtitleFromVideo: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_rem_logo),
                            contentDescription = "Rem Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "remsubs playground",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 0. MAGNET / TORRENT DOWNLOADER SECTION (Yerleşim: "video ile altyazı düzenleme" ÜSTÜNDE)
                TorrentDownloadSection(
                    downloadInfo = torrentDownloadInfo,
                    onStartMagnet = onStartMagnetDownload,
                    onPickTorrentFile = onPickTorrentFile,
                    onPause = onPauseTorrentDownload,
                    onResume = onResumeTorrentDownload,
                    onCancel = onCancelTorrentDownload,
                    onOpenDownloadedVideo = onOpenDownloadedVideo
                )

                // 1. Düz bir dikdörtgen: içinde "video ile altyazı düzenleme"
                Card(
                    onClick = onOpenEditor,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .testTag("menu_option_ass_editor")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "video ile altyazı düzenleme",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 2. Kare: içinde "yeni altyazı"
                Card(
                    onClick = onStartBlankProject,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .size(130.dp)
                        .testTag("menu_option_blank_project")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "yeni altyazı",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Videodan Altyazı Ayıkla (Yerleşim: "yeni altyazı" ALTINDA)
                Card(
                    onClick = onExtractSubtitleFromVideo,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .testTag("menu_option_extract_softsub")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Videodan Altyazı Ayıkla",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "MKV softsub akışını ayıklar (.ass / .srt)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
