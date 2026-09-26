package com.example.mkv

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

sealed class MkvInspectionResult {
    data class Success(
        val mkvFile: File,
        val tracks: List<MkvSubtitleTrack>
    ) : MkvInspectionResult()

    data class NoSubtitlesFound(val message: String = "Bu MKV dosyasında softsub bulunamadı.") : MkvInspectionResult()
    data class Error(val message: String) : MkvInspectionResult()
}

sealed class SubtitleExtractionResult {
    data class Success(
        val extractedFile: File,
        val formatExtension: String,
        val track: MkvSubtitleTrack
    ) : SubtitleExtractionResult()

    data class Error(val message: String) : SubtitleExtractionResult()
}

object MkvSubtitleExtractor {
    private const val TAG = "MkvSubtitleExtractor"

    /**
     * Inspects an MKV file from Content URI or File without performing OCR or modifying anything.
     * Detects only existing softsub streams in the MKV container.
     */
    suspend fun inspectMkv(context: Context, uri: Uri): MkvInspectionResult = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // Prepare local file from URI if needed for reliable random-access FFprobe
            val resolvedFile = prepareLocalMkvReference(context, uri)
            if (resolvedFile == null || !resolvedFile.exists() || resolvedFile.length() <= 0L) {
                return@withContext MkvInspectionResult.Error("MKV açılamadı.")
            }
            tempFile = resolvedFile

            val probeSession = FFprobeKit.getMediaInformation(resolvedFile.absolutePath)
            val mediaInfo = probeSession.mediaInformation

            if (mediaInfo == null) {
                Log.w(TAG, "FFprobe mediaInformation null, fail log: ${probeSession.failStackTrace}")
                return@withContext MkvInspectionResult.Error("MKV açılamadı.")
            }

            val streams = mediaInfo.streams ?: emptyList()
            val subtitleTracks = mutableListOf<MkvSubtitleTrack>()
            var subtitleCounter = 1

            for (stream in streams) {
                val streamType = stream.type?.lowercase(Locale.ROOT) ?: ""
                if (streamType != "subtitle") continue

                val streamIndex = stream.index?.toInt() ?: subtitleCounter
                val codec = stream.codec?.lowercase(Locale.ROOT) ?: "bilinmiyor"

                // Extract tags (language, title)
                var langCode = stream.getStringProperty("tags/language") ?: ""
                var title = stream.getStringProperty("tags/title") ?: ""

                // Fallback via tags JSON object if present
                val tagsObj = stream.tags
                if (tagsObj != null) {
                    if (langCode.isBlank()) {
                        langCode = when (tagsObj) {
                            is JSONObject -> tagsObj.optString("language", "")
                            is Map<*, *> -> tagsObj["language"]?.toString().orEmpty()
                            else -> ""
                        }
                    }
                    if (title.isBlank()) {
                        title = when (tagsObj) {
                            is JSONObject -> tagsObj.optString("title", "")
                            is Map<*, *> -> tagsObj["title"]?.toString().orEmpty()
                            else -> ""
                        }
                    }
                }

                val isDefault = stream.getStringProperty("disposition/default") == "1"
                val isForced = stream.getStringProperty("disposition/forced") == "1"

                val langDisplay = mapLanguageCodeToTurkish(langCode)
                val trackTitle = if (title.isNotBlank()) title else "Altyazı $subtitleCounter"

                val isTextSupported = isSupportedTextSubtitleCodec(codec)

                subtitleTracks.add(
                    MkvSubtitleTrack(
                        streamIndex = streamIndex,
                        subtitleTrackNumber = subtitleCounter++,
                        codecName = codec,
                        languageCode = if (langCode.isNotBlank()) langCode else "und",
                        languageDisplayName = langDisplay,
                        trackTitle = trackTitle,
                        isDefault = isDefault,
                        isForced = isForced,
                        isSupportedTextFormat = isTextSupported
                    )
                )
            }

            if (subtitleTracks.isEmpty()) {
                Log.i(TAG, "MKV dosyasında softsub akışı bulunamadı (toplam akış: ${streams.size}).")
                return@withContext MkvInspectionResult.NoSubtitlesFound("Bu MKV dosyasında softsub bulunamadı.")
            }

            Log.i(TAG, "MKV incelendi: ${subtitleTracks.size} softsub parçası bulundu.")
            MkvInspectionResult.Success(mkvFile = resolvedFile, tracks = subtitleTracks)

        } catch (t: Throwable) {
            Log.e(TAG, "MKV incelenirken hata: ${t.localizedMessage}", t)
            MkvInspectionResult.Error("MKV açılamadı.")
        }
    }

    /**
     * Extracts the original subtitle stream directly without OCR, re-timing, or rendering.
     * Preserves original stream format (.ass or .srt).
     */
    suspend fun extractTrack(
        context: Context,
        mkvFile: File,
        track: MkvSubtitleTrack
    ): SubtitleExtractionResult = withContext(Dispatchers.IO) {
        try {
            if (!mkvFile.exists() || mkvFile.length() <= 0L) {
                return@withContext SubtitleExtractionResult.Error("MKV açılamadı.")
            }

            // Determine correct output extension based strictly on codec
            val extension = determineExtensionForCodec(track.codecName)
            val baseName = mkvFile.nameWithoutExtension
                .replace("input_mkv_extract_", "")
                .take(30)
                .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                .ifBlank { "altyazi" }

            val langSuffix = if (track.languageCode != "und" && track.languageCode.isNotBlank()) {
                "_${track.languageCode}"
            } else {
                "_p${track.subtitleTrackNumber}"
            }

            val outDir = File(context.cacheDir, "extracted_subs").apply { mkdirs() }
            val outputFile = File(outDir, "${baseName}${langSuffix}.$extension")
            if (outputFile.exists()) outputFile.delete()

            Log.i(TAG, "Altyazı ayıklanıyor: Akış #${track.streamIndex} (${track.codecName}) -> ${outputFile.name}")

            // 1. Try direct stream copy (-c:s copy)
            val copyArgs = arrayOf(
                "-y",
                "-i", mkvFile.absolutePath,
                "-map", "0:${track.streamIndex}",
                "-c:s", "copy",
                outputFile.absolutePath
            )

            var session = FFmpegKit.executeWithArguments(copyArgs)
            var returnCode = session.returnCode
            var isSuccess = ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0L

            // 2. Fallback if copy failed for specific container stream (e.g. subrip to srt)
            if (!isSuccess) {
                val codecArg = when (extension) {
                    "ass" -> "ass"
                    "srt" -> "srt"
                    "vtt" -> "webvtt"
                    else -> "copy"
                }

                Log.w(TAG, "-c:s copy başarısız oldu, format dönüşümsüz codec modu deneniyor: -c:s $codecArg")
                val fallbackArgs = arrayOf(
                    "-y",
                    "-i", mkvFile.absolutePath,
                    "-map", "0:${track.streamIndex}",
                    "-c:s", codecArg,
                    outputFile.absolutePath
                )
                session = FFmpegKit.executeWithArguments(fallbackArgs)
                returnCode = session.returnCode
                isSuccess = ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0L
            }

            if (isSuccess && outputFile.exists() && outputFile.length() > 0L) {
                // Verify extracted file contains valid text content
                val firstBytes = try {
                    outputFile.readText(Charsets.UTF_8).take(200)
                } catch (_: Exception) { "" }

                if (firstBytes.isNotBlank()) {
                    Log.i(TAG, "Altyazı başarıyla ayıklandı (${outputFile.length()} bytes): ${outputFile.name}")
                    SubtitleExtractionResult.Success(
                        extractedFile = outputFile,
                        formatExtension = extension,
                        track = track
                    )
                } else {
                    outputFile.delete()
                    SubtitleExtractionResult.Error("Altyazı ayıklanamadı.")
                }
            } else {
                Log.e(TAG, "FFmpeg altyazı ayıklama başarısız oldu. ReturnCode: ${returnCode?.value}\n${session.allLogsAsString}")
                SubtitleExtractionResult.Error("Altyazı ayıklanamadı.")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Altyazı ayıklanırken hata: ${t.localizedMessage}", t)
            SubtitleExtractionResult.Error("Altyazı ayıklanamadı: ${t.localizedMessage ?: "Bilinmeyen hata"}")
        }
    }

    private fun isSupportedTextSubtitleCodec(codec: String): Boolean {
        val lower = codec.lowercase(Locale.ROOT)
        return lower.contains("ass") ||
                lower.contains("ssa") ||
                lower.contains("subrip") ||
                lower.contains("srt") ||
                lower.contains("vtt") ||
                lower.contains("text")
    }

    fun determineExtensionForCodec(codecName: String): String {
        val lower = codecName.lowercase(Locale.ROOT)
        return when {
            lower.contains("ass") || lower.contains("ssa") -> "ass"
            lower.contains("subrip") || lower.contains("srt") -> "srt"
            lower.contains("vtt") -> "vtt"
            else -> "ass"
        }
    }

    fun mapLanguageCodeToTurkish(code: String): String {
        val clean = code.trim().lowercase(Locale.ROOT)
        return when (clean) {
            "tur", "tr", "turkish" -> "Türkçe"
            "eng", "en", "english" -> "English"
            "jpn", "ja", "japanese" -> "Japonca"
            "ger", "deu", "de", "german" -> "Almanca"
            "fre", "fra", "fr", "french" -> "Fransızca"
            "spa", "es", "spanish" -> "İspanyolca"
            "ita", "it", "italian" -> "İtalyanca"
            "rus", "ru", "russian" -> "Rusça"
            "ara", "ar", "arabic" -> "Arapça"
            "kor", "ko", "korean" -> "Korece"
            "chi", "zho", "zh", "chinese" -> "Çince"
            "por", "pt", "portuguese" -> "Portekizce"
            "und", "" -> "Belirtilmemiş"
            else -> clean.uppercase(Locale.ROOT)
        }
    }

    private fun prepareLocalMkvReference(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") {
            uri.path?.let { p ->
                val f = File(p)
                if (f.exists() && f.canRead()) return f
            }
        }

        // Copy safely to cache for fast seeking in FFprobe
        return try {
            val temp = File(context.cacheDir, "input_mkv_extract_${System.currentTimeMillis()}.mkv")
            if (temp.exists()) temp.delete()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                    }
                    output.flush()
                }
            }
            if (temp.exists() && temp.length() > 0L) temp else null
        } catch (t: Throwable) {
            Log.e(TAG, "MKV önbelleğe kopyalanamadı: ${t.localizedMessage}", t)
            null
        }
    }
}
