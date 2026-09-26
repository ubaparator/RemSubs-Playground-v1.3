package com.example.encode

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.example.model.SubtitleCue
import com.example.model.SubtitleStyle
import com.example.parser.AssGenerator
import com.example.util.FontManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/**
 * Dedicated internal FFmpeg Hardsub Encoding Manager.
 *
 * Runs FFmpeg in-process via embedded libffmpegkit with libass and libx264.
 * Completely internal: NO external processes, NO Termux, NO shell commands.
 */
object FfmpegEncodeManager {
    private const val TAG = "FfmpegEncodeManager"

    private val _encodeState = MutableStateFlow(EncodeState())
    val encodeState: StateFlow<EncodeState> = _encodeState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var activeSession: FFmpegSession? = null

    @Volatile
    private var isCancelledByUser: Boolean = false

    @Volatile
    private var activeTotalDurationMs: Long = 0L

    @Volatile
    private var activeTotalFrames: Long = 900L

    @Volatile
    private var activeOriginalFileName: String = "video.mp4"

    private val fullLogBuffer = StringBuilder()

    fun resetState() {
        cancelEncode()
        activeSession = null
        isCancelledByUser = false
        fullLogBuffer.clear()
        _encodeState.value = EncodeState()
    }

    fun cancelEncode() {
        isCancelledByUser = true
        val session = activeSession
        if (session != null) {
            try {
                session.cancel()
                FFmpegKit.cancel(session.sessionId)
            } catch (t: Throwable) {
                Log.w(TAG, "Cancel session warning: ${t.localizedMessage}")
            }
        } else {
            try {
                FFmpegKit.cancel()
            } catch (_: Throwable) {}
        }

        _encodeState.update {
            it.copy(
                isPreparing = false,
                isEncoding = false,
                isCancelled = true,
                currentPhaseText = "İşlem iptal edildi",
                errorMessage = "Encode kullanıcı tarafından iptal edildi."
            )
        }
    }

    /**
     * Primary entry point for internal FFmpeg + libass Hardsub encoding.
     */
    suspend fun startEncode(
        context: Context,
        videoUri: Uri,
        cues: List<SubtitleCue>,
        style: SubtitleStyle,
        customFontFile: File? = null,
        additionalStyles: List<String> = emptyList(),
        settings: EncodingSettings = EncodingSettings(),
        sourceMetadata: SourceVideoMetadata = SourceVideoMetadata()
    ) = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        isCancelledByUser = false
        fullLogBuffer.clear()

        var tempInputVideo: File? = null
        var tempAssFile: File? = null
        var tempOutputFile: File? = null

        try {
            // Determine video encoder name
            val videoEncoderName = resolveVideoEncoderName(settings)

            // Phase 1: Preparation
            _encodeState.update {
                EncodeState(
                    isPreparing = true,
                    currentPhaseText = "Hazırlanıyor...",
                    currentEncoderName = "$videoEncoderName (Dahili FFmpeg)",
                    sourceMetadata = sourceMetadata
                )
            }

            // Phase 2: Copy input video from Uri to cache directory
            _encodeState.update { it.copy(currentPhaseText = "Video kopyalanıyor...") }
            val originalName = FontManager.getFileName(context, videoUri) ?: "input_video.mp4"
            activeOriginalFileName = originalName
            val safeBaseName = originalName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "video" }

            val cacheWorkingDir = File(context.cacheDir, "remsubs_encode").apply { mkdirs() }
            val inputVideoFile = File(cacheWorkingDir, "in_${System.currentTimeMillis()}_$safeBaseName.mp4")
            tempInputVideo = inputVideoFile

            copyUriToFileStreaming(context, videoUri, inputVideoFile)

            if (!inputVideoFile.exists() || inputVideoFile.length() <= 0L) {
                val errorMsg = "Video açılamadı veya girdi dosyası okunamadı."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isPreparing = false, errorMessage = errorMsg) }
                return@withContext
            }

            if (isCancelledByUser) return@withContext

            // Calculate video duration and FPS for progress tracking
            val (durationMs, videoFps) = extractVideoDurationAndFps(context, inputVideoFile)
            activeTotalDurationMs = if (durationMs > 0) durationMs else sourceMetadata.durationMs
            val effectiveFps = if (videoFps > 0) videoFps else sourceMetadata.fps.coerceAtLeast(24.0)
            activeTotalFrames = if (activeTotalDurationMs > 0) {
                ((activeTotalDurationMs / 1000.0) * effectiveFps).toLong().coerceAtLeast(1L)
            } else {
                900L
            }

            _encodeState.update {
                it.copy(
                    totalFrames = activeTotalFrames,
                    sourceMetadata = if (sourceMetadata.durationMs > 0) sourceMetadata else sourceMetadata.copy(durationMs = activeTotalDurationMs)
                )
            }

            // Phase 3: Setup Fonts & Generate ASS Subtitle File
            _encodeState.update { it.copy(currentPhaseText = "Fontlar ve altyazı hazırlanıyor...") }

            val fontsDir = FontSetupHelper.setupFonts(context, customFontFile)

            val videoWidth = if (sourceMetadata.width > 0) sourceMetadata.width else 1920
            val videoHeight = if (sourceMetadata.height > 0) sourceMetadata.height else 1080
            val assContent = AssGenerator.generateAss(
                title = "remsubs_hardsub",
                subtitles = cues,
                style = style,
                applyTimeOffset = false,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                additionalStyles = additionalStyles
            )

            val assFile = File(cacheWorkingDir, "sub_${System.currentTimeMillis()}.ass")
            assFile.writeText(assContent, Charsets.UTF_8)
            tempAssFile = assFile

            if (!assFile.exists() || assFile.length() <= 0L) {
                val errorMsg = "Altyazı dosyası oluşturulamadı."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isPreparing = false, errorMessage = errorMsg) }
                return@withContext
            }

            if (isCancelledByUser) return@withContext

            // Phase 4: Output File in cache
            val tempOutput = File(cacheWorkingDir, "out_${System.currentTimeMillis()}_$safeBaseName.mp4")
            if (tempOutput.exists()) tempOutput.delete()
            tempOutputFile = tempOutput

            // Phase 5: Build FFmpeg command arguments
            _encodeState.update { it.copy(currentPhaseText = "FFmpeg motoru başlatılıyor...") }

            val escapedAssPath = escapeForAssFilter(assFile.absolutePath)
            val escapedFontsDirPath = escapeForAssFilter(fontsDir.absolutePath)
            val vfArg = "ass='${escapedAssPath}':fontsdir='${escapedFontsDirPath}'"

            val crf = settings.crf.coerceIn(16, 28)
            val preset = settings.preset.ifBlank { "veryfast" }

            val isAudioCopyable = isCodecMp4Compatible(sourceMetadata.audioCodec)

            var encodeSession = executeFfmpegSession(
                inputVideoFile = inputVideoFile,
                vfArg = vfArg,
                tempOutputFile = tempOutput,
                videoEncoderName = videoEncoderName,
                preset = preset,
                crf = crf,
                settings = settings,
                copyAudio = isAudioCopyable,
                totalFrames = activeTotalFrames,
                totalDurationMs = activeTotalDurationMs,
                startTimeMs = startTimeMs
            )

            // If audio copy failed due to container incompatibility, retry once with AAC re-encode
            if (isAudioCopyable && !ReturnCode.isSuccess(encodeSession.returnCode) && !isCancelledByUser) {
                val logs = encodeSession.allLogsAsString ?: fullLogBuffer.toString()
                if (logs.contains("could not find tag for codec", ignoreCase = true) ||
                    logs.contains("not currently supported in container", ignoreCase = true) ||
                    logs.contains("muxer does not support", ignoreCase = true) ||
                    logs.contains("Error initializing output stream", ignoreCase = true)
                ) {
                    Log.w(TAG, "-c:a copy container ile uyumsuz oldu, -c:a aac ile yeniden deneniyor...")
                    if (tempOutput.exists()) tempOutput.delete()
                    encodeSession = executeFfmpegSession(
                        inputVideoFile = inputVideoFile,
                        vfArg = vfArg,
                        tempOutputFile = tempOutput,
                        videoEncoderName = videoEncoderName,
                        preset = preset,
                        crf = crf,
                        settings = settings,
                        copyAudio = false, // Force AAC
                        totalFrames = activeTotalFrames,
                        totalDurationMs = activeTotalDurationMs,
                        startTimeMs = startTimeMs
                    )
                }
            }

            activeSession = null

            if (isCancelledByUser || ReturnCode.isCancel(encodeSession.returnCode)) {
                _encodeState.update {
                    it.copy(
                        isPreparing = false,
                        isEncoding = false,
                        isCancelled = true,
                        currentPhaseText = "İşlem iptal edildi",
                        errorMessage = "Encode kullanıcı tarafından iptal edildi."
                    )
                }
                cleanupFiles(tempInputVideo, tempAssFile, tempOutputFile)
                return@withContext
            }

            if (!ReturnCode.isSuccess(encodeSession.returnCode)) {
                val allLogs = encodeSession.allLogsAsString ?: fullLogBuffer.toString()
                Log.e(TAG, "FFmpeg encode failed. ReturnCode: ${encodeSession.returnCode?.value}\n$allLogs")
                val friendlyError = parseFriendlyError(allLogs)
                _encodeState.update {
                    it.copy(
                        isPreparing = false,
                        isEncoding = false,
                        currentPhaseText = "Encode başarısız",
                        errorMessage = friendlyError,
                        fullLogs = allLogs.takeLast(8000)
                    )
                }
                cleanupFiles(tempInputVideo, tempAssFile, tempOutputFile)
                return@withContext
            }

            // Phase 6: Validate Output
            _encodeState.update {
                it.copy(
                    isEncoding = false,
                    isPreparing = true,
                    currentPhaseText = "Çıktı doğrulanıyor..."
                )
            }

            val allLogs = encodeSession.allLogsAsString ?: fullLogBuffer.toString()
            val validation = validateHardsubOutput(tempOutput, inputVideoFile, allLogs)
            if (validation is ValidationResult.Failure) {
                Log.e(TAG, "Validation failed: ${validation.reason}")
                _encodeState.update {
                    it.copy(
                        isPreparing = false,
                        isEncoding = false,
                        errorMessage = "Video encode edilemedi: ${validation.reason}",
                        currentPhaseText = "Hata",
                        fullLogs = allLogs.takeLast(8000)
                    )
                }
                cleanupFiles(tempInputVideo, tempAssFile, tempOutputFile)
                return@withContext
            }

            // Phase 7: Publish to permanent storage (Movies/RemSubs)
            _encodeState.update { it.copy(currentPhaseText = "Kalıcı depolamaya kaydediliyor...") }

            val permanentFile = publishToPermanentStorage(context, tempOutput, originalName)
            if (permanentFile == null || !permanentFile.exists() || permanentFile.length() <= 0L) {
                Log.e(TAG, "Permanent file publishing failed.")
                _encodeState.update {
                    it.copy(
                        isPreparing = false,
                        isEncoding = false,
                        errorMessage = "Çıktı dosyası kaydedilemedi.",
                        currentPhaseText = "Hata"
                    )
                }
                cleanupFiles(tempInputVideo, tempAssFile, tempOutputFile)
                return@withContext
            }

            // Success! Clean up temporary files
            cleanupFiles(tempInputVideo, tempAssFile, tempOutputFile)

            val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000
            Log.i(TAG, "Hardsub encode completed successfully: ${permanentFile.absolutePath} ($elapsedSec s)")

            _encodeState.update {
                it.copy(
                    isPreparing = false,
                    isEncoding = false,
                    isCompleted = true,
                    progress = 1.0f,
                    progressPercentage = 100,
                    elapsedSeconds = elapsedSec,
                    averageEstimatedFinishText = "00:00",
                    outputVideoFile = permanentFile,
                    currentPhaseText = "Encode tamamlandı.",
                    errorMessage = null
                )
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error in internal FFmpeg hardsub encode: ${t.localizedMessage}", t)
            cleanupFiles(tempInputVideo, tempAssFile, tempOutputFile)
            _encodeState.update {
                it.copy(
                    isPreparing = false,
                    isEncoding = false,
                    errorMessage = "Video encode edilemedi: ${t.localizedMessage ?: "Bilinmeyen hata"}"
                )
            }
        }
    }

    private suspend fun executeFfmpegSession(
        inputVideoFile: File,
        vfArg: String,
        tempOutputFile: File,
        videoEncoderName: String,
        preset: String,
        crf: Int,
        settings: EncodingSettings,
        copyAudio: Boolean,
        totalFrames: Long,
        totalDurationMs: Long,
        startTimeMs: Long
    ): FFmpegSession {
        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-i")
        args.add(inputVideoFile.absolutePath)
        args.add("-vf")
        args.add(vfArg)
        args.add("-map")
        args.add("0:v:0")
        args.add("-map")
        args.add("0:a?")
        args.add("-c:v")
        args.add(videoEncoderName)

        if (videoEncoderName == "libx264" || videoEncoderName == "libx265") {
            args.add("-preset")
            args.add(preset)
            args.add("-crf")
            args.add(crf.toString())
            args.add("-pix_fmt")
            args.add("yuv420p")
        } else if (videoEncoderName.contains("mediacodec")) {
            val bitrate = if (settings.bitrate != "Otomatik") settings.bitrate else "4500k"
            args.add("-b:v")
            args.add(bitrate)
            args.add("-pix_fmt")
            args.add("yuv420p")
        } else {
            args.add("-preset")
            args.add(preset)
            args.add("-crf")
            args.add(crf.toString())
            args.add("-pix_fmt")
            args.add("yuv420p")
        }

        if (copyAudio) {
            args.add("-c:a")
            args.add("copy")
        } else {
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("192k")
        }

        args.add(tempOutputFile.absolutePath)

        _encodeState.update {
            it.copy(
                isPreparing = false,
                isEncoding = true,
                currentPhaseText = "Encode yapılıyor..."
            )
        }

        Log.i(TAG, "Executing internal FFmpeg: ${args.joinToString(" ")}")

        val completionDeferred = CompletableDeferred<FFmpegSession>()

        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            { completedSession ->
                completionDeferred.complete(completedSession)
            },
            { log ->
                val line = log.message ?: ""
                synchronized(fullLogBuffer) {
                    if (fullLogBuffer.length > 20000) {
                        fullLogBuffer.delete(0, 10000)
                    }
                    fullLogBuffer.append(line).append("\n")
                }
                _encodeState.update { it.copy(lastLogLine = line.takeLast(200)) }
            },
            { stats ->
                val timeMs = stats.time.toLong()
                val currentFrame = stats.videoFrameNumber.toLong()
                val currentFps = stats.videoFps.toDouble()
                val speed = stats.speed

                val progressByFrames = if (totalFrames > 0 && currentFrame > 0) {
                    (currentFrame.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.99f)
                } else 0f

                val progressByTime = if (totalDurationMs > 0 && timeMs > 0) {
                    (timeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                } else 0f

                val progress = maxOf(progressByFrames, progressByTime)
                val pct = (progress * 100).toInt()

                val fpsRatio = if (totalFrames > 0 && currentFps > 0) {
                    currentFps / totalFrames.toDouble()
                } else 0.0

                val remainingSec = if (currentFps > 0.5 && totalFrames > currentFrame) {
                    ((totalFrames - currentFrame) / currentFps).toLong()
                } else if (totalDurationMs > timeMs && speed > 0.1) {
                    val remainingMs = totalDurationMs - timeMs
                    (remainingMs / (speed * 1000.0)).toLong().coerceAtLeast(0L)
                } else {
                    0L
                }

                val finishText = if (remainingSec > 0) {
                    HardsubEncoder.formatTimeSeconds(remainingSec)
                } else "--:--"

                val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000

                _encodeState.update {
                    it.copy(
                        progress = progress,
                        progressPercentage = pct,
                        currentFrame = currentFrame,
                        currentFps = currentFps,
                        fpsToTotalFramesRatio = fpsRatio,
                        estimatedRemainingSeconds = remainingSec,
                        averageEstimatedFinishText = finishText,
                        elapsedSeconds = elapsedSec
                    )
                }
            }
        )

        activeSession = session

        return try {
            completionDeferred.await()
        } catch (t: Throwable) {
            try { session.cancel() } catch (_: Throwable) {}
            session
        }
    }

    private fun resolveVideoEncoderName(settings: EncodingSettings): String {
        return when (settings.encoderOption) {
            EncoderOption.MEDIA_CODEC_H264 -> "h264_mediacodec"
            EncoderOption.MEDIA_CODEC_H265 -> "hevc_mediacodec"
            EncoderOption.LIBX265 -> "libx265"
            EncoderOption.VP9 -> "libvpx-vp9"
            EncoderOption.AV1 -> "libaom-av1"
            else -> "libx264"
        }
    }

    private fun isCodecMp4Compatible(audioCodec: String): Boolean {
        val lower = audioCodec.lowercase(Locale.ROOT)
        return lower.contains("aac") ||
                lower.contains("mp4a") ||
                lower.contains("mp3") ||
                lower.contains("ac3") ||
                lower.contains("eac3") ||
                lower.contains("alac") ||
                lower.contains("opus")
    }

    private fun parseFriendlyError(logs: String): String {
        val lower = logs.lowercase(Locale.ROOT)
        return when {
            lower.contains("no such filter: 'ass'") -> "libass subtitle filtresi bulunamadı."
            lower.contains("cannot open font") || lower.contains("font not found") -> "Font dosyası yüklenemedi."
            lower.contains("unknown encoder 'libx264'") -> "libx264 video kodlayıcı bulunamadı."
            lower.contains("no space left on device") -> "Cihazda yeterli depolama alanı yok."
            lower.contains("permission denied") -> "Depolama izni hatası."
            lower.contains("invalid data found") -> "Video veya altyazı dosyası bozuk."
            else -> "Video encode edilemedi. FFmpeg hatası oluştu."
        }
    }

    private sealed class ValidationResult {
        object Success : ValidationResult()
        data class Failure(val reason: String) : ValidationResult()
    }

    private fun validateHardsubOutput(
        outputFile: File,
        inputVideoFile: File,
        allLogs: String
    ): ValidationResult {
        if (!outputFile.exists() || outputFile.length() < 1024L) {
            return ValidationResult.Failure("Çıktı dosyası boş veya oluşturulamadı.")
        }

        if (outputFile.canonicalPath == inputVideoFile.canonicalPath) {
            return ValidationResult.Failure("Çıktı dosyası kaynak dosya ile aynı.")
        }

        val lowerLogs = allLogs.lowercase(Locale.ROOT)
        val filterError = lowerLogs.contains("could not initialize libass") ||
                lowerLogs.contains("failed to configure filter") ||
                lowerLogs.contains("no such filter: 'ass'") ||
                lowerLogs.contains("error initializing filter 'ass'")

        if (filterError) {
            return ValidationResult.Failure("libass filtre başlatma hatası oluştu.")
        }

        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            extractor.setDataSource(outputFile.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

            val duration = durationStr?.toLongOrNull() ?: 0L
            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0

            var hasVideoTrack = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    hasVideoTrack = true
                    break
                }
            }

            if (!hasVideoTrack || duration <= 0L || width <= 0 || height <= 0) {
                return ValidationResult.Failure("Geçersiz video akışı veya çözünürlük ($width x $height).")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Validation exception: ${e.localizedMessage}")
            return ValidationResult.Failure(e.localizedMessage ?: "Doğrulama hatası")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }

        return ValidationResult.Success
    }

    /**
     * Publishes verified hardsub video to permanent user storage (Movies/RemSubs)
     * avoiding name collisions:
     * video.mp4 -> video_encoded.mp4 -> video_encoded_1.mp4 -> video_encoded_2.mp4 ...
     */
    private fun publishToPermanentStorage(
        context: Context,
        tempOutputFile: File,
        originalFileName: String
    ): File? {
        val moviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "RemSubs"
        ).apply { mkdirs() }

        val permanentFile = resolveUniqueOutputFile(moviesDir, originalFileName)

        return try {
            FileInputStream(tempOutputFile).use { input ->
                FileOutputStream(permanentFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, permanentFile.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RemSubs")
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                try {
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore video insert warning: ${e.localizedMessage}")
                }
            }

            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(permanentFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            } catch (_: Exception) {}

            permanentFile
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing video to permanent storage: ${e.localizedMessage}", e)
            null
        }
    }

    fun resolveUniqueOutputFile(parentDir: File, originalFileName: String): File {
        val base = originalFileName.substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "video" }

        var candidate = File(parentDir, "${base}_encoded.mp4")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "${base}_encoded_$counter.mp4")
            counter++
        }
        return candidate
    }

    fun escapeForAssFilter(path: String): String {
        return path
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace(":", "\\:")
            .replace(",", "\\,")
            .replace("[", "\\[")
            .replace("]", "\\]")
    }

    private fun copyUriToFileStreaming(context: Context, uri: Uri, destFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(256 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
    }

    private fun extractVideoDurationAndFps(context: Context, videoFile: File): Pair<Long, Double> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val fpsStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            } else null
            val fps = fpsStr?.toDoubleOrNull() ?: 30.0
            Pair(durationMs, fps)
        } catch (_: Exception) {
            Pair(0L, 30.0)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun cleanupFiles(vararg files: File?) {
        for (f in files) {
            try {
                if (f != null && f.exists()) {
                    f.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
