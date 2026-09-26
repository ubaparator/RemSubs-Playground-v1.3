package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleCue
import com.example.ui.AxiSubUiState

@Composable
fun VideoInfoSection(
    uiState: AxiSubUiState,
    onPickVideo: () -> Unit,
    onLoadDemoMedia: () -> Unit,
    onOpenEncode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hardsub Encode Action Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hardsub Encode (Videoya Göm)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tüm altyazı renkleri, kenarlıklar ve zamanlamalar libass motoru ile videoya kalıcı olarak işlenir (CRF 23 kalitesinde MP4 çıktısı alınır).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onOpenEncode,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("btn_section_start_encode")
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Videoya Hardsub Göm (Encode Yap)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Video Selection Action
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video Yönetimi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Videonuz kopyalanmadan veya depolama alanı harcanmadan doğrudan lokal dosya URI üzerinden önizlenir.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickVideo,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("select_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yerel Video Seç", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onLoadDemoMedia,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("reload_demo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Demo Yükle", fontSize = 12.sp)
                    }
                }
            }
        }

        // Current Media Status Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "YÜKLÜ DOSYA BİLGİLERİ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoRow(
                    label = "Video Başlığı:",
                    value = uiState.videoTitle ?: "Henüz seçilmedi"
                )
                InfoRow(
                    label = "Video Süresi:",
                    value = SubtitleCue.formatTimestamp(uiState.durationMs)
                )
                InfoRow(
                    label = "Altyazı Dosyası:",
                    value = uiState.subtitleFileName ?: "Henüz seçilmedi"
                )
                InfoRow(
                    label = "Altyazı Satır Sayısı:",
                    value = "${uiState.subtitles.size} satır"
                )
                InfoRow(
                    label = "Kullanılan Font:",
                    value = uiState.customFontName ?: uiState.subtitleStyle.fontName
                )
                InfoRow(
                    label = "Senkron Kayması:",
                    value = "${uiState.subtitleStyle.timeOffsetMs} ms"
                )
            }
        }

        // Quick Guide / Instructions Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "remsubs playground Nasıl Kullanılır?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                StepGuideItem("1", "Yerel videonuzu cihazınızdan seçin veya hazır demo ile test edin.")
                StepGuideItem("2", "Altyazı Seç tuşu ile .ass veya .srt dosyanızı yükleyin.")
                StepGuideItem("3", "Font sekmesinden istediğiniz .ttf dosyasını yükleyin, boyutu ve konumu ayarlayın.")
                StepGuideItem("4", "Altyazı listesinden herhangi bir satıra tıklayarak o ana doğrudan atlayın.")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StepGuideItem(step: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$step.",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
