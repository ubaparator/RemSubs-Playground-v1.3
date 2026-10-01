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

enum class TorrentErrorType(val code: String, val displayTurkish: String) {
    METADATA_TIMEOUT("METADATA_TIMEOUT", "Metadata zaman aşımı (Tracker/Peer yanıt vermedi)"),
    NO_PEERS("NO_PEERS", "Kullanılabilir eş (peer) veya seeder bulunamadı"),
    TRACKER_ERROR("TRACKER_ERROR", "İzleyici (Tracker) bağlantı hatası"),
    DHT_ERROR("DHT_ERROR", "DHT ağı yanıt vermedi"),
    PERMISSION("PERMISSION", "Depolama yazma izni reddedildi"),
    STORAGE_FULL("STORAGE_FULL", "Cihaz depolama alanı yetersiz"),
    INVALID_TORRENT("INVALID_TORRENT", "Geçersiz veya bozuk .torrent dosyası"),
    INVALID_MAGNET("INVALID_MAGNET", "Geçersiz veya desteklenmeyen Magnet URI"),
    NETWORK_ERROR("NETWORK_ERROR", "Ağ bağlantısı hatası"),
    ENGINE_INIT_FAILURE("ENGINE_INIT_FAILURE", "Torrent motoru başlatılamadı"),
    UNKNOWN("UNKNOWN", "Bilinmeyen torrent hatası")
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
    val pieceCount: Int = 0,
    val pieceSize: Int = 0,
    val files: List<String> = emptyList(),
    val selectedVideoFileName: String = "",
    val downloadedFile: File? = null,
    val permanentUri: Uri? = null,
    val errorMessage: String? = null,
    val errorType: TorrentErrorType? = null
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
