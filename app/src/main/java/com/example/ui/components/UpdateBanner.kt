package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.torrent.TorrentDownloadInfo
import com.example.update.UpdateInfo
import com.example.update.UpdateState

/** Main-menu card for a GitHub update that is downloading, ready to install or failed. */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is UpdateState.Downloading -> UpdateCard(modifier) {
            UpdateHeader("Yeni sürüm indiriliyor: ${state.info.versionName}")
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%${(state.progress * 100).toInt()}" +
                    (state.info.sizeBytes.takeIf { it > 0L }?.let { " • ${TorrentDownloadInfo.formatBytes(it)}" } ?: ""),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        is UpdateState.ReadyToInstall -> UpdateCard(modifier) {
            UpdateHeader("Yeni sürüm hazır: ${state.info.versionName}")
            if (state.info.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.info.notes,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_install_update")
            ) {
                Text("Şimdi Güncelle", fontWeight = FontWeight.Bold)
            }
        }

        is UpdateState.Failed -> Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Güncelleme (${state.info.versionName}) indirilemedi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = state.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tekrar Dene", fontSize = 12.sp)
                }
            }
        }

        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}

/** Shown once per session when a downloaded update is ready on the main menu. */
@Composable
fun UpdateReadyDialog(
    info: UpdateInfo,
    activeDownloadName: String?,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("Güncelleme hazır", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "RemSubs ${info.versionName} indirildi. \"Güncelle\"ye bastığında sistem yükleyicisi açılır; " +
                        "verilerin ve fontların korunur.",
                    fontSize = 13.sp
                )
                if (info.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Yenilikler: ${info.notes}",
                        fontSize = 12.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeDownloadName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = "Kurulum sırasında devam eden indirme duracak (\"$activeDownloadName\"). Sonra aynı sonuca tekrar basarak kaldığı yerden sürdürebilirsin.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onInstall, modifier = Modifier.testTag("btn_dialog_install_update")) {
                Text("Güncelle")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Sonra")
            }
        }
    )
}

@Composable
private fun UpdateCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_app_update")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun UpdateHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
