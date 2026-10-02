package com.example.torrent

import android.net.Uri
import java.io.File
import java.util.Locale

enum class TorrentState(val displayTurkish: String) {
    IDLE("Hazır"),
    CONNECTING_TRACKERS("İzleyicilere bağlanılıyor..."),
    RESOLVING_METADATA("Magnet meta verisi çözümleniyor..."),
    DOWNLOADING("İndiriliyor"),
    TRANSFERRING("Dosya hazırlanıyor..."),
    PAUSED("Duraklatıldı"),
    COMPLETED("İndirme tamamlandı"),
    ERROR("Hata")
}

enum class TorrentErrorType(val code: String, val displayTurkish: String) {
    INVALID_TORRENT("INVALID_TORRENT", "Geçersiz veya bozuk .torrent içeriği"),
    ENGINE_INIT_FAILURE("ENGINE_INIT_FAILURE", "BitTorrent motoru başlatılamadı"),
    JNI_NATIVE_ERROR("JNI_NATIVE_ERROR", "Native (JNI/libtorrent) kütüphane hatası"),
    STORAGE_ERROR("STORAGE_ERROR", "Depolama veya dosya okuma/yazma hatası"),
    STORAGE_FULL("STORAGE_FULL", "Cihaz depolama alanı yetersiz"),
    PERMISSION("PERMISSION", "Depolama yazma izni reddedildi"),
    NETWORK_ERROR("NETWORK_ERROR", "Ağ veya soket bağlantı hatası"),
    METADATA_TIMEOUT("METADATA_TIMEOUT", "Metadata zaman aşımı (Tracker/Peer yanıt vermedi)"),
    NO_PEERS("NO_PEERS", "Kullanılabilir eş (peer) veya seeder bulunamadı"),
    TRACKER_ERROR("TRACKER_ERROR", "İzleyici (Tracker) bağlantı hatası"),
    DHT_ERROR("DHT_ERROR", "DHT ağı yanıt vermedi"),
    INVALID_MAGNET("INVALID_MAGNET", "Geçersiz veya desteklenmeyen Magnet URI"),
    UNKNOWN("UNKNOWN", "Bilinmeyen torrent hatası")
}

/** One file inside a torrent. Padding files of hybrid v1/v2 torrents are never listed. */
data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val isVideo: Boolean
) {
    val displayName: String get() = path.substringAfterLast('/')
}

data class TorrentDownloadInfo(
    val magnetUri: String = "",
    val torrentName: String = "Torrent İndirmesi",
    val sourceLabel: String = "",
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
    val dhtNodes: Long = 0L,
    val pieceCount: Int = 0,
    val pieceSize: Int = 0,
    val files: List<TorrentFileEntry> = emptyList(),
    val selectedVideoFileName: String = "",
    val awaitingFileSelection: Boolean = false,
    val downloadedFile: File? = null,
    /** Gallery (MediaStore) copy, set only after the user chooses to save one. */
    val permanentUri: Uri? = null,
    val isSavingToGallery: Boolean = false,
    val gallerySaveProgress: Float = 0f,
    val galleryError: String? = null,
    val canRetry: Boolean = false,
    val errorMessage: String? = null,
    val errorType: TorrentErrorType? = null
) {
    val isActive: Boolean
        get() = state == TorrentState.RESOLVING_METADATA ||
                state == TorrentState.CONNECTING_TRACKERS ||
                state == TorrentState.DOWNLOADING ||
                state == TorrentState.PAUSED ||
                state == TorrentState.TRANSFERRING

    val selectedFile: TorrentFileEntry?
        get() = files.firstOrNull { it.path == selectedVideoFileName }

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
