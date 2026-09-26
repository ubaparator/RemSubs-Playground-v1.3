package com.example.torrent

import android.net.Uri
import java.io.File
import java.util.Locale

enum class TorrentState(val displayTurkish: String) {
    IDLE("Hazır"),
    CONNECTING_TRACKERS("İzleyicilere bağlanılıyor..."),
    RESOLVING_METADATA("Magnet meta verisi çözümleniyor..."),
    DOWNLOADING("İndiriliyor"),
    TRANSFERRING("Cihaz depolamasına aktarılıyor..."),
    PAUSED("Duraklatıldı"),
    COMPLETED("İndirme tamamlandı"),
    ERROR("Hata")
}

data class TorrentDownloadInfo(
    val magnetUri: String = "",
    val torrentName: String = "Torrent İndirmesi",
    val infoHashHex: String = "",
    val state: TorrentState = TorrentState.IDLE,
    val statusMessage: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Float = 0f,
    val progressPercentage: Int = 0,
    val speedBytesPerSec: Long = 0L,
    val speedText: String = "0 KB/s",
    val etaText: String = "--:--",
    val seeders: Int = 0,
    val leechers: Int = 0,
    val connectedPeers: Int = 0,
    val downloadedFile: File? = null,
    val permanentUri: Uri? = null,
    val errorMessage: String? = null
) {
    fun formatDownloadedSize(): String {
        return "${formatBytes(downloadedBytes)} / ${if (totalBytes > 0) formatBytes(totalBytes) else "Hesaplanıyor..."}"
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
