package com.example.util

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
import com.example.torrent.MediaContainerType
import com.example.torrent.StorageSaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/**
 * Unified MediaStore and permanent gallery storage manager used identically
 * by both the Hardsub Video Encode pipeline and Torrent Downloads.
 */
object MediaStorageManager {
    private const val TAG = "MediaStorageManager"

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
     * Resolves precise MIME type matching the true container and file extension.
     * Preserves real container format: MKV stays video/x-matroska, WebM stays video/webm.
     */
    fun resolveExactMimeType(fileName: String, file: File? = null): String {
        val lower = fileName.lowercase(Locale.ROOT)
        if (file != null && file.exists()) {
            val container = detectContainerFormat(file)
            when (container) {
                MediaContainerType.MATROSKA -> {
                    return if (lower.endsWith(".webm")) "video/webm" else "video/x-matroska"
                }
                MediaContainerType.MP4 -> return "video/mp4"
                MediaContainerType.AVI -> return "video/x-msvideo"
                else -> {}
            }
        }

        return when {
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".ts") -> "video/mp2t"
            lower.endsWith(".torrent") -> "application/x-bittorrent"
            else -> "video/mp4"
        }
    }

    fun isVideoFile(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") ||
                lower.endsWith(".webm") ||
                lower.endsWith(".avi") ||
                lower.endsWith(".mov") ||
                lower.endsWith(".flv") ||
                lower.endsWith(".ts")
    }

    fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("..", "_")
            .trim()
            .ifBlank { "remsubs_media_${System.currentTimeMillis()}" }
    }

    /**
     * Resolves an available unique file in target directory without overwriting.
     */
    fun resolveUniqueFile(parentDir: File, originalFileName: String): File {
        parentDir.mkdirs()
        val safeName = sanitizeFileName(originalFileName)
        val ext = safeName.substringAfterLast(".", "").let { if (it.isNotBlank()) ".$it" else "" }
        val base = safeName.substringBeforeLast(".")

        var candidate = File(parentDir, safeName)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "${base}_$counter$ext")
            counter++
        }
        return candidate
    }

    /**
     * Common function to save verified video (both encode output and torrent download)
     * to device Gallery under Movies/RemSubs/.
     */
    suspend fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        originalFileName: String,
        mimeTypeOverride: String? = null,
        onProgress: ((progress: Float, writtenBytes: Long, totalBytes: Long) -> Unit)? = null
    ): StorageSaveResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.e(TAG, "Source file missing or empty: ${sourceFile.absolutePath}")
            return@withContext StorageSaveResult.Failure("Kaynak dosya bulunamadı veya boş.")
        }

        val safeName = sanitizeFileName(originalFileName.ifBlank { sourceFile.name })
        val isVideo = isVideoFile(safeName)
        val mimeType = mimeTypeOverride ?: resolveExactMimeType(safeName, sourceFile)

        // 1. Guaranteed permanent app storage file in Movies
        val permanentLocalDir = if (isVideo) {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "movies")
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        }.apply { mkdirs() }

        val localPermanentFile = resolveUniqueFile(permanentLocalDir, safeName)
        try {
            if (sourceFile.canonicalPath != localPermanentFile.canonicalPath) {
                sourceFile.copyTo(localPermanentFile, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warning copying to permanent local dir: ${e.localizedMessage}")
        }

        val finalVerifiedFile = if (localPermanentFile.exists() && localPermanentFile.length() > 0L) {
            localPermanentFile
        } else {
            sourceFile
        }

        // 2. Publish to MediaStore for Android Gallery / Google Photos indexing
        try {
            val publicUri = writeToSharedStorage(context, finalVerifiedFile, safeName, mimeType, isVideo, onProgress)

            // 3. MediaScanner trigger for immediate Gallery visibility
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalVerifiedFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } catch (e: Exception) {
                Log.w(TAG, "MediaScanner warning: ${e.localizedMessage}")
            }

            Log.i(TAG, "Video successfully saved to Gallery Movies/RemSubs: ${finalVerifiedFile.name}")
            StorageSaveResult.Success(permanentUri = publicUri, permanentFile = finalVerifiedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to Gallery: ${e.localizedMessage}", e)
            StorageSaveResult.Failure("Galeriye kaydetme hatası: ${e.localizedMessage}")
        }
    }

    /**
     * Publishes an existing file to the Gallery (Movies/RemSubs) with a single MediaStore copy.
     * Unlike [saveVideoToGallery] it keeps no extra app-private duplicate, which matters for
     * multi-GB torrent downloads that already live in app storage.
     */
    suspend fun publishToGallery(
        context: Context,
        sourceFile: File,
        displayName: String = sourceFile.name,
        onProgress: ((progress: Float, writtenBytes: Long, totalBytes: Long) -> Unit)? = null
    ): StorageSaveResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            return@withContext StorageSaveResult.Failure("Kaynak dosya bulunamadı veya boş.")
        }
        val safeName = sanitizeFileName(displayName.ifBlank { sourceFile.name })
        val mimeType = resolveExactMimeType(safeName, sourceFile)
        try {
            val publicUri = writeToSharedStorage(context, sourceFile, safeName, mimeType, isVideoFile(safeName), onProgress)
                ?: return@withContext StorageSaveResult.Failure("Galeri kaydı oluşturulamadı.")
            StorageSaveResult.Success(permanentUri = publicUri, permanentFile = sourceFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing to Gallery: ${e.localizedMessage}", e)
            val reason = if (e.localizedMessage?.contains("No space", ignoreCase = true) == true) {
                "Galeriye kaydedilemedi: cihazda yeterli boş alan yok."
            } else {
                "Galeriye kaydetme hatası: ${e.localizedMessage}"
            }
            StorageSaveResult.Failure(reason)
        }
    }

    /**
     * Copies [source] into shared storage (MediaStore on Android 10+, public Movies/Downloads
     * folder before that). Returns the public Uri, or null when MediaStore refused the insert.
     */
    private fun writeToSharedStorage(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        onProgress: ((progress: Float, writtenBytes: Long, totalBytes: Long) -> Unit)?
    ): Uri? {
        val totalBytes = source.length()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Legacy Android 9 and below
            val targetDir = if (isVideo) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RemSubs")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RemSubs")
            }.apply { mkdirs() }

            val publicDest = resolveUniqueFile(targetDir, displayName)
            source.copyTo(publicDest, overwrite = true)
            return Uri.fromFile(publicDest)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (isVideo) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RemSubs")
            } else {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RemSubs")
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collectionUri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val targetUri = try {
            context.contentResolver.insert(collectionUri, values)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore insert warning: ${e.localizedMessage}")
            null
        } ?: return null

        try {
            val buffer = ByteArray(256 * 1024)
            var bytesWritten = 0L
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                FileInputStream(source).use { fis ->
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                        bytesWritten += read
                        val prog = (bytesWritten.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        onProgress?.invoke(prog, bytesWritten, totalBytes)
                    }
                    os.flush()
                }
            }

            // Release pending flag to publish in Gallery
            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(targetUri, publishValues, null, null)
            return targetUri
        } catch (e: Exception) {
            // Don't leave a half-written, invisible pending entry behind
            try { context.contentResolver.delete(targetUri, null, null) } catch (_: Exception) {}
            throw e
        }
    }
}
