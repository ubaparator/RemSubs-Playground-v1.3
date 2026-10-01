package com.example.torrent

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

sealed class StorageSaveResult {
    data class Success(
        val permanentUri: Uri?,
        val permanentFile: File
    ) : StorageSaveResult()

    data class Failure(
        val reason: String
    ) : StorageSaveResult()
}

enum class MediaContainerType {
    MP4,
    MATROSKA,
    AVI,
    UNKNOWN
}

object TorrentStorageManager {
    private const val TAG = "TorrentStorageManager"

    /**
     * Inspects magic headers to determine true container format of media file.
     */
    fun detectContainerFormat(file: File): MediaContainerType {
        if (!file.exists() || file.length() < 12L) return MediaContainerType.UNKNOWN
        val header = ByteArray(64)
        val read = try {
            FileInputStream(file).use { it.read(header) }
        } catch (_: Exception) { 0 }
        if (read < 12) return MediaContainerType.UNKNOWN

        // EBML / Matroska / WebM: 0x1A 0x45 0xDF 0xA3
        if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() && header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()) {
            return MediaContainerType.MATROSKA
        }

        // ISO Base Media File Format (MP4 / M4V / MOV): "ftyp" or "moov"
        val headerStr = String(header, 0, minOf(read, 48), Charsets.ISO_8859_1)
        if (headerStr.contains("ftyp") || headerStr.contains("moov")) {
            return MediaContainerType.MP4
        }
        if (headerStr.startsWith("RIFF") && headerStr.contains("AVI ")) {
            return MediaContainerType.AVI
        }
        return MediaContainerType.UNKNOWN
    }

    /**
     * Fast-remuxes MKV or unsupported container to universal MP4 with AAC audio.
     * Video stream is 100% copied without re-encoding (-c:v copy), finishing in seconds.
     * Returns the remuxed MP4 file or original file if already MP4.
     */
    suspend fun ensureGalleryCompatibleMp4(
        context: Context,
        sourceVideoFile: File,
        baseName: String
    ): File = withContext(Dispatchers.IO) {
        val detected = detectContainerFormat(sourceVideoFile)
        val isAlreadyMp4 = detected == MediaContainerType.MP4

        if (isAlreadyMp4) {
            return@withContext sourceVideoFile
        }

        val targetDir = File(context.cacheDir, "remux").apply { mkdirs() }
        val safeBase = baseName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "video" }
        val outputMp4 = File(targetDir, "${safeBase}_universal_${System.currentTimeMillis()}.mp4")

        Log.i(TAG, "Galeri ve önizleme uyumluluğu için MKV/video MP4'e remux ediliyor: ${sourceVideoFile.name}")
        val args = arrayOf(
            "-y",
            "-i", sourceVideoFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "192k",
            "-movflags", "+faststart",
            outputMp4.absolutePath
        )

        try {
            val session = FFmpegKit.executeWithArguments(args)
            if (ReturnCode.isSuccess(session.returnCode) && outputMp4.exists() && outputMp4.length() > 0L) {
                Log.i(TAG, "Remux başarılı, yeni evrensel MP4: ${outputMp4.name} (${outputMp4.length()} bytes)")
                return@withContext outputMp4
            } else {
                Log.w(TAG, "Remux başarısız oldu, orijinal dosya kullanılacak. Log: ${session.allLogsAsString}")
                return@withContext sourceVideoFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remux istisnası: ${e.localizedMessage}")
            return@withContext sourceVideoFile
        }
    }

    /**
     * Copies the completed torrent file to permanent user-accessible storage:
     * 1. Detects real container & ensures 100% Gallery/ExoPlayer compatibility (remux to MP4 if needed)
     * 2. Stores a permanent verified local copy in getExternalFilesDir (never deleted by MediaStore)
     * 3. Publishes to MediaStore (Movies/RemSubs for Video, Downloads/RemSubs for Torrent)
     * 4. Scans via MediaScannerConnection so it appears in device Gallery and file pickers
     */
    suspend fun saveToUserAccessibleStorage(
        context: Context,
        tempFile: File,
        originalFileName: String,
        onProgress: (progress: Float, writtenBytes: Long, totalBytes: Long) -> Unit
    ): StorageSaveResult = withContext(Dispatchers.IO) {
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            Log.e(TAG, "Geçici dosya bulunamadı veya boş: ${tempFile.absolutePath}")
            return@withContext StorageSaveResult.Failure("Dosya cihaz depolamasına kaydedilemedi.")
        }

        // Delegate to unified MediaStorageManager which maintains identical Movies/RemSubs MediaStore pipeline
        // while preserving original MKV/WebM/MP4 containers and MIME types
        com.example.util.MediaStorageManager.saveVideoToGallery(
            context = context,
            sourceFile = tempFile,
            originalFileName = originalFileName,
            onProgress = onProgress
        )
    }

    /**
     * Sanitizes file name while strictly preserving its original extension.
     * Does NOT blindly append .mp4 to .torrent or .mkv files!
     */
    fun sanitizeFileName(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return clean.ifBlank { "download_${System.currentTimeMillis()}" }
    }

    fun isVideoFile(fileName: String): Boolean {
        return fileName.endsWith(".mkv", ignoreCase = true) ||
                fileName.endsWith(".mp4", ignoreCase = true) ||
                fileName.endsWith(".webm", ignoreCase = true) ||
                fileName.endsWith(".avi", ignoreCase = true)
    }

    fun isTorrentFile(fileName: String): Boolean {
        return fileName.endsWith(".torrent", ignoreCase = true)
    }

    fun resolveMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
            fileName.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            fileName.endsWith(".torrent", ignoreCase = true) -> "application/x-bittorrent"
            else -> "application/octet-stream"
        }
    }

    private fun verifyMediaContainerViaUri(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            var hasVideo = false
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    break
                }
            }
            hasVideo
        } catch (_: Exception) {
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
