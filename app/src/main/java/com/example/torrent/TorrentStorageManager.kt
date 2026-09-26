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

object TorrentStorageManager {
    private const val TAG = "TorrentStorageManager"

    /**
     * Copies the completed torrent temporary cache file to permanent user-accessible storage.
     *
     * Strict lifecycle:
     * - Distinguishes between video files (.mkv, .mp4, .webm) and .torrent metadata files.
     * - Uses MediaStore.Video for videos (Movies/RemSubs) and MediaStore.Downloads for .torrent.
     * - Android 10+: Sets IS_PENDING = 1 while writing complete file.
     * - Verifies destination file and container validity before publishing (IS_PENDING = 0).
     * - If copying or verification fails, deletes incomplete MediaStore entry.
     * - Deletes temporary cache file ONLY after successful permanent verification.
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

        val totalBytes = tempFile.length()
        val safeFileName = sanitizeFileName(originalFileName.ifBlank { tempFile.name })
        val isVideo = isVideoFile(safeFileName)
        val isTorrent = isTorrentFile(safeFileName)
        val mimeType = resolveMimeType(safeFileName)

        var targetUri: Uri? = null
        var permanentFile: File? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Modern Android 10+ (API 29+) MediaStore
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (isVideo) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RemSubs")
                    } else {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RemSubs")
                    }
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                // Pick correct MediaStore collection
                val collectionUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }

                targetUri = try {
                    context.contentResolver.insert(collectionUri, values)
                } catch (e: Exception) {
                    Log.e(TAG, "MediaStore insert hatası ($collectionUri): ${e.localizedMessage}")
                    null
                }

                // If collection insert failed and it's video, try fallback
                if (targetUri == null && isVideo) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RemSubs")
                    targetUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                }

                if (targetUri == null) {
                    Log.e(TAG, "MediaStore URI oluşturulamadı.")
                    return@withContext StorageSaveResult.Failure("Dosya cihaz depolamasına kaydedilemedi.")
                }

                // Stream from temporary cache file to MediaStore target
                var bytesWritten = 0L
                val buffer = ByteArray(256 * 1024)

                val outStream = context.contentResolver.openOutputStream(targetUri)
                if (outStream == null) {
                    try { context.contentResolver.delete(targetUri, null, null) } catch (_: Exception) {}
                    return@withContext StorageSaveResult.Failure("Dosya cihaz depolamasına kaydedilemedi.")
                }

                outStream.use { os ->
                    FileInputStream(tempFile).use { fis ->
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            os.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead
                            val prog = (bytesWritten.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(prog, bytesWritten, totalBytes)
                        }
                        os.flush()
                    }
                }

                // Strict Destination Verification before making visible (IS_PENDING = 0)
                var destinationVerified = false
                try {
                    context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                        val size = pfd.statSize
                        if (size > 0L && Math.abs(size - totalBytes) <= 4096L) {
                            destinationVerified = true
                            Log.i(TAG, "MediaStore dosyası başarıyla doğrulandı. Boyut: $size bytes")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "FileDescriptor doğrulama hatası: ${e.localizedMessage}")
                }

                // For videos, additionally verify media container on destination
                if (destinationVerified && isVideo) {
                    val containerValid = verifyMediaContainerViaUri(context, targetUri)
                    if (!containerValid) {
                        Log.e(TAG, "Hedef MediaStore video kapsayıcısı doğrulanamadı!")
                        destinationVerified = false
                    }
                }

                if (!destinationVerified) {
                    Log.e(TAG, "Kalıcı depolama doğrulama başarısız! Eksik veya bozuk dosya siliniyor.")
                    try { context.contentResolver.delete(targetUri, null, null) } catch (_: Exception) {}
                    return@withContext StorageSaveResult.Failure("Dosya cihaz depolamasına kaydedilemedi.")
                }

                // Publish: remove IS_PENDING so file appears in user file managers and gallery
                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(targetUri, publishValues, null, null)

                // Locate public file path on disk if available
                val publicDir = if (isVideo) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RemSubs")
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RemSubs")
                }
                val expectedPublicFile = File(publicDir, safeFileName)
                if (expectedPublicFile.exists() && expectedPublicFile.length() > 0L) {
                    permanentFile = expectedPublicFile
                }

            } else {
                // Legacy Android 9 and below: Standard public directory
                val targetDir = if (isVideo) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RemSubs")
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RemSubs")
                }.apply { mkdirs() }

                val destFile = File(targetDir, safeFileName)
                permanentFile = destFile

                var bytesWritten = 0L
                val buffer = ByteArray(256 * 1024)

                FileOutputStream(destFile).use { fos ->
                    FileInputStream(tempFile).use { fis ->
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead
                            val prog = (bytesWritten.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(prog, bytesWritten, totalBytes)
                        }
                        fos.flush()
                    }
                }

                // Legacy verification
                val isVerified = destFile.exists() && Math.abs(destFile.length() - totalBytes) <= 4096L
                if (!isVerified) {
                    try { destFile.delete() } catch (_: Exception) {}
                    return@withContext StorageSaveResult.Failure("Dosya cihaz depolamasına kaydedilemedi.")
                }

                targetUri = Uri.fromFile(destFile)
            }

            // MediaScanner Connection to index immediately
            try {
                if (permanentFile != null && permanentFile.exists()) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(permanentFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MediaScanner uyarısı: ${t.localizedMessage}")
            }

            // SUCCESS: Only delete the temporary cache file AFTER permanent copy is verified!
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.i(TAG, "Geçici indirme önbellek dosyası güvenle temizlendi: ${tempFile.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geçici dosya silinirken uyarı: ${e.localizedMessage}")
            }

            val finalFile = permanentFile ?: tempFile

            StorageSaveResult.Success(
                permanentUri = targetUri,
                permanentFile = finalFile
            )

        } catch (t: Throwable) {
            Log.e(TAG, "Kalıcı depolamaya aktarma sırasında hata: ${t.localizedMessage}", t)
            if (targetUri != null) {
                try { context.contentResolver.delete(targetUri, null, null) } catch (_: Exception) {}
            }
            if (permanentFile != null && permanentFile.exists()) {
                try { permanentFile.delete() } catch (_: Exception) {}
            }
            StorageSaveResult.Failure("Dosya cihaz depolamasına kaydedilemedi.")
        }
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
