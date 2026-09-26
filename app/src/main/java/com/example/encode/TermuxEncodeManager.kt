package com.example.encode

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.SubtitleCue
import com.example.model.SubtitleStyle
import com.example.parser.AssGenerator
import com.example.util.FontManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * Dedicated Manager for executing and monitoring hardsub encoding jobs inside Termux via FFmpeg.
 *
 * Implements the official Termux RUN_COMMAND interface, creates safe shared working directories,
 * manages fonts and subtitle generation, monitors real-time stdout/stderr progress,
 * and independently validates the final output before publishing to MediaStore.
 */
object TermuxEncodeManager {
    private const val TAG = "TermuxEncodeManager"

    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"

    private const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_RUN_COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    private val _encodeState = MutableStateFlow(EncodeState())
    val encodeState: StateFlow<EncodeState> = _encodeState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var activeJobId: String = ""
    @Volatile
    private var activeJobDir: File? = null
    @Volatile
    private var activeInputVideoFile: File? = null
    @Volatile
    private var activeAssFile: File? = null
    @Volatile
    private var activeTempOutputFile: File? = null
    @Volatile
    private var activeLogFile: File? = null
    @Volatile
    private var activeStatusFile: File? = null
    @Volatile
    private var activeCues: List<SubtitleCue> = emptyList()
    @Volatile
    private var activeTotalDurationMs: Long = 0L
    @Volatile
    private var activeTotalFrames: Long = 900L
    @Volatile
    private var activeOriginalFileName: String = "video.mp4"
    @Volatile
    private var isCancelledByUser: Boolean = false

    // Result callback from TermuxCommandResultReceiver
    @Volatile
    private var receivedExitCode: Int? = null
    @Volatile
    private var receivedErrCode: Int? = null
    @Volatile
    private var receivedErrmsg: String? = null

    fun resetState() {
        cancelMonitoring()
        activeJobId = ""
        activeJobDir = null
        activeInputVideoFile = null
        activeAssFile = null
        activeTempOutputFile = null
        activeLogFile = null
        activeStatusFile = null
        activeCues = emptyList()
        receivedExitCode = null
        receivedErrCode = null
        receivedErrmsg = null
        isCancelledByUser = false
        _encodeState.value = EncodeState()
    }

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            Log.w(TAG, "Error checking Termux package: ${t.localizedMessage}")
            false
        }
    }

    fun cancelEncode(context: Context) {
        isCancelledByUser = true
        cancelMonitoring()

        val jobDir = activeJobDir
        if (jobDir != null && jobDir.exists()) {
            scope.launch(Dispatchers.IO) {
                // Try sending kill command through Termux if possible
                try {
                    val killIntent = Intent().apply {
                        setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                        action = TERMUX_RUN_COMMAND_ACTION
                        putExtra(EXTRA_RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/pkill")
                        putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-f", "ffmpeg.*${jobDir.name}"))
                        putExtra(EXTRA_RUN_COMMAND_WORKDIR, jobDir.absolutePath)
                        putExtra(EXTRA_RUN_COMMAND_BACKGROUND, true)
                        putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try { context.startForegroundService(killIntent) } catch (_: Exception) { context.startService(killIntent) }
                    } else {
                        context.startService(killIntent)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not send kill intent to Termux: ${t.localizedMessage}")
                }

                // Cleanup working dir
                cleanupWorkingDir(jobDir)
            }
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
     * Called by TermuxCommandResultReceiver when Termux finishes executing the command.
     */
    fun onCommandResult(
        jobId: String,
        exitCode: Int,
        errCode: Int,
        errmsg: String?,
        stdout: String,
        stderr: String
    ) {
        if (jobId == activeJobId) {
            receivedExitCode = exitCode
            receivedErrCode = errCode
            receivedErrmsg = errmsg
            Log.i(TAG, "Command result stored for active job: $jobId (exitCode=$exitCode, errCode=$errCode)")
        }
    }

    /**
     * Primary entry point for launching Termux FFmpeg Hardsub encoding.
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
        val jobId = "job_${startTimeMs}_${UUID.randomUUID().toString().take(6)}"

        activeJobId = jobId
        receivedExitCode = null
        receivedErrCode = null
        receivedErrmsg = null
        isCancelledByUser = false
        activeCues = cues

        try {
            // Phase 1: Termux Preparation Check
            _encodeState.update {
                EncodeState(
                    isPreparing = true,
                    currentPhaseText = "Termux hazırlanıyor...",
                    currentEncoderName = "libx264 (Termux FFmpeg)",
                    sourceMetadata = sourceMetadata
                )
            }

            if (!isTermuxInstalled(context)) {
                val errorMsg = "Termux bulunamadı. Lütfen Termux uygulamasını cihazınıza yükleyin."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isPreparing = false, errorMessage = errorMsg) }
                return@withContext
            }

            // Phase 2: Check shared storage availability & create working directory
            val moviesBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val remSubsWorkingBase = File(moviesBase, "RemSubs/.working").apply { mkdirs() }
            
            // Add .nomedia so unfinished temp files are never scanned by Android Gallery
            try {
                val noMedia = File(remSubsWorkingBase, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            } catch (_: Exception) {}

            val jobDir = File(remSubsWorkingBase, jobId).apply { mkdirs() }
            activeJobDir = jobDir

            if (!jobDir.exists() || !jobDir.canWrite()) {
                val errorMsg = "Termux paylaşılan depolama alanı oluşturulamadı. Lütfen depolama izinlerini kontrol edin."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isPreparing = false, errorMessage = errorMsg) }
                return@withContext
            }

            // Phase 3: Prepare input video file in shared directory
            _encodeState.update { it.copy(currentPhaseText = "Video hazırlanıyor...") }
            Log.i(TAG, "Video hazırlanıyor: $videoUri")

            val originalName = FontManager.getFileName(context, videoUri) ?: "input_video.mp4"
            activeOriginalFileName = originalName
            val safeBaseName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val inputVideoFile = File(jobDir, "input_$safeBaseName")
            activeInputVideoFile = inputVideoFile

            copyUriToFileStreaming(context, videoUri, inputVideoFile)

            if (!inputVideoFile.exists() || inputVideoFile.length() <= 0L) {
                val errorMsg = "Video açılamadı veya girdi dosyası kopyalanamadı."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isPreparing = false, errorMessage = errorMsg) }
                return@withContext
            }

            // Calculate video duration and FPS for progress tracking
            val (durationMs, videoFps) = extractVideoDurationAndFps(context, inputVideoFile)
            activeTotalDurationMs = durationMs
            activeTotalFrames = if (durationMs > 0) {
                ((durationMs / 1000.0) * videoFps).toLong().coerceAtLeast(1L)
            } else {
                900L
            }

            _encodeState.update {
                it.copy(
                    totalFrames = activeTotalFrames,
                    sourceMetadata = if (sourceMetadata.durationMs > 0) sourceMetadata else sourceMetadata.copy(durationMs = durationMs)
                )
            }

            // Phase 4: Subtitle Preparation & Font Setup
            _encodeState.update { it.copy(currentPhaseText = "Altyazı hazırlanıyor...") }
            Log.i(TAG, "Altyazı hazırlanıyor...")

            // Fonts directory in working job folder
            val fontsDir = File(jobDir, "fonts").apply { mkdirs() }
            val fontFiles = copyFontsToSharedDir(context, fontsDir, customFontFile)

            // Generate full ASS content matching video dimensions and style
            val videoWidth = if (sourceMetadata.width > 0) sourceMetadata.width else 1920
            val videoHeight = if (sourceMetadata.height > 0) sourceMetadata.height else 1080
            val assContent = AssGenerator.generateAss(
                title = "remsubs_termux_hardsub",
                subtitles = cues,
                style = style,
                applyTimeOffset = false,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                additionalStyles = additionalStyles
            )

            val assFile = File(jobDir, "subtitles_${System.currentTimeMillis()}.ass")
            assFile.writeText(assContent, Charsets.UTF_8)
            activeAssFile = assFile

            if (!assFile.exists() || assFile.length() <= 0L) {
                val errorMsg = "Altyazı dosyası oluşturulamadı."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isPreparing = false, errorMessage = errorMsg) }
                return@withContext
            }

            // Phase 5: Output file & paths setup
            val tempOutputFile = File(jobDir, "output_temp_$safeBaseName.mp4")
            if (tempOutputFile.exists()) tempOutputFile.delete()
            activeTempOutputFile = tempOutputFile

            val logFile = File(jobDir, "ffmpeg_encode.log")
            if (logFile.exists()) logFile.delete()
            activeLogFile = logFile

            val statusFile = File(jobDir, "encode_status.txt")
            if (statusFile.exists()) statusFile.delete()
            activeStatusFile = statusFile

            // Phase 6: Build FFmpeg command and execution bash script
            _encodeState.update { it.copy(currentPhaseText = "FFmpeg kontrol ediliyor...") }

            // Construct libass video filter with robust escaping
            val escapedAssPath = escapeForAssFilter(assFile.absolutePath)
            val vfArg = if (fontFiles.isNotEmpty()) {
                val escapedFontsDirPath = escapeForAssFilter(fontsDir.absolutePath)
                "ass='${escapedAssPath}':fontsdir='${escapedFontsDirPath}'"
            } else {
                "ass='${escapedAssPath}'"
            }

            // Script contains pre-checks for ffmpeg and libass, runs command and records exit code
            val scriptFile = File(jobDir, "run_encode.sh")
            val scriptContent = buildBashScriptContent(
                inputVideoFile = inputVideoFile,
                vfArg = vfArg,
                tempOutputFile = tempOutputFile,
                logFile = logFile,
                statusFile = statusFile,
                settings = settings
            )
            scriptFile.writeText(scriptContent, Charsets.UTF_8)
            try { scriptFile.setExecutable(true, false) } catch (_: Exception) {}

            // Phase 7: Launch via Termux RUN_COMMAND
            _encodeState.update {
                it.copy(
                    isPreparing = false,
                    isEncoding = true,
                    currentPhaseText = "Encode başlatılıyor..."
                )
            }
            Log.i(TAG, "Launching Termux RUN_COMMAND with script: ${scriptFile.absolutePath}")

            val launchSuccess = launchTermuxRunCommand(context, jobId, scriptFile, jobDir)
            if (!launchSuccess) {
                val errorMsg = "Termux komutu başlatılamadı. Lütfen Termux'un yüklü ve arka plan izinlerine sahip olduğunu kontrol edin."
                Log.e(TAG, errorMsg)
                _encodeState.update { it.copy(isEncoding = false, errorMessage = errorMsg) }
                return@withContext
            }

            _encodeState.update { it.copy(currentPhaseText = "Encode devam ediyor...") }

            // Phase 8: Monitor FFmpeg Progress and wait for completion
            startProgressMonitor(
                logFile = logFile,
                statusFile = statusFile,
                totalFrames = activeTotalFrames,
                durationMs = activeTotalDurationMs
            )

            // Wait for completion, failure, or cancellation
            val waitResult = waitForJobCompletion(jobDir, logFile, statusFile)
            cancelMonitoring()

            if (isCancelledByUser) {
                return@withContext
            }

            if (waitResult is JobWaitResult.Error) {
                Log.e(TAG, "Termux job failed: ${waitResult.message}")
                _encodeState.update {
                    it.copy(
                        isEncoding = false,
                        errorMessage = waitResult.message,
                        currentPhaseText = "Encode başarısız"
                    )
                }
                return@withContext
            }

            // Phase 9: Validate Output Independently
            _encodeState.update {
                it.copy(
                    isEncoding = false,
                    isPreparing = true,
                    currentPhaseText = "Çıktı doğrulanıyor..."
                )
            }
            Log.i(TAG, "Çıktı doğrulanıyor...")

            val allLogs = try { logFile.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
            val validationResult = validateHardsubOutput(
                outputFile = tempOutputFile,
                inputVideoFile = inputVideoFile,
                cues = cues,
                assFile = assFile,
                allLogs = allLogs
            )

            if (validationResult is ValidationResult.Failure) {
                Log.e(TAG, "Validation failed: ${validationResult.reason}")
                _encodeState.update {
                    it.copy(
                        isPreparing = false,
                        isEncoding = false,
                        errorMessage = "Altyazı videoya gömülemedi.",
                        currentPhaseText = "Hata"
                    )
                }
                // Never return or keep broken output
                try { if (tempOutputFile.exists()) tempOutputFile.delete() } catch (_: Exception) {}
                return@withContext
            }

            // Phase 10: Publish verified hardsub video to permanent user storage
            _encodeState.update { it.copy(currentPhaseText = "Depolamaya aktarılıyor...") }

            val permanentFile = publishToPermanentStorage(
                context = context,
                tempOutputFile = tempOutputFile,
                originalFileName = originalName
            )

            if (permanentFile == null || !permanentFile.exists() || permanentFile.length() <= 0L) {
                Log.e(TAG, "Permanent file publishing failed.")
                _encodeState.update {
                    it.copy(
                        isPreparing = false,
                        isEncoding = false,
                        errorMessage = "Altyazı videoya gömülemedi.",
                        currentPhaseText = "Hata"
                    )
                }
                return@withContext
            }

            // SUCCESS! Clean up temporary working directory
            cleanupWorkingDir(jobDir)

            val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000
            Log.i(TAG, "Hardsub encode successfully completed and verified: ${permanentFile.absolutePath} ($elapsedSec s)")

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
            Log.e(TAG, "Fatal error in Termux hardsub encode: ${t.localizedMessage}", t)
            cancelMonitoring()
            _encodeState.update {
                it.copy(
                    isPreparing = false,
                    isEncoding = false,
                    errorMessage = "Altyazı videoya gömülemedi."
                )
            }
        }
    }

    /**
     * Builds the bash script executed inside Termux.
     */
    private fun buildBashScriptContent(
        inputVideoFile: File,
        vfArg: String,
        tempOutputFile: File,
        logFile: File,
        statusFile: File,
        settings: EncodingSettings
    ): String {
        val crf = settings.crf.coerceIn(16, 28)
        val preset = settings.preset.ifBlank { "veryfast" }

        val escapedInput = escapeForBash(inputVideoFile.absolutePath)
        val escapedOutput = escapeForBash(tempOutputFile.absolutePath)
        val escapedLog = escapeForBash(logFile.absolutePath)
        val escapedStatus = escapeForBash(statusFile.absolutePath)

        return """
            #!/data/data/com.termux/files/usr/bin/bash
            export PATH="/data/data/com.termux/files/usr/bin:${'$'}PATH"

            # Check if FFmpeg is installed in Termux
            if [ ! -x "/data/data/com.termux/files/usr/bin/ffmpeg" ] && ! command -v ffmpeg &> /dev/null; then
                echo "TERMUX_NO_FFMPEG" > $escapedStatus
                echo "FFmpeg bulunamadı. Lütfen Termux'ta 'pkg install ffmpeg' çalıştırın." > $escapedLog
                exit 127
            fi

            # Check if FFmpeg has libass support
            if ! ffmpeg -version 2>&1 | grep -iq "libass"; then
                echo "TERMUX_NO_LIBASS" > $escapedStatus
                echo "Termux FFmpeg libass desteği ile derlenmemiş." > $escapedLog
                exit 126
            fi

            echo "STATUS=ENCODING" > $escapedStatus

            # Execute real hardsub command
            ffmpeg -y -i $escapedInput -vf "$vfArg" -c:v libx264 -preset $preset -crf $crf -c:a copy $escapedOutput > $escapedLog 2>&1
            FFMPEG_EXIT=${'$'}?

            echo "EXIT_CODE=${'$'}FFMPEG_EXIT" > $escapedStatus
            exit ${'$'}FFMPEG_EXIT
        """.trimIndent()
    }

    /**
     * Sends the Termux RUN_COMMAND intent with PendingIntent for callbacks.
     */
    private fun launchTermuxRunCommand(
        context: Context,
        jobId: String,
        scriptFile: File,
        jobDir: File
    ): Boolean {
        return try {
            val callbackIntent = Intent(context, TermuxCommandResultReceiver::class.java).apply {
                action = "com.example.TERMUX_RESULT"
                putExtra("jobId", jobId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                jobId.hashCode(),
                callbackIntent,
                flags
            )

            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = TERMUX_RUN_COMMAND_ACTION
                putExtra(EXTRA_RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf(scriptFile.absolutePath))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, jobDir.absolutePath)
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, true)
                putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
                putExtra(EXTRA_RUN_COMMAND_PENDING_INTENT, pendingIntent)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (_: Exception) {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting Termux service: ${t.localizedMessage}", t)
            false
        }
    }

    /**
     * Monitors the FFmpeg log file to update the UI progress bar and stats in real-time.
     */
    private fun startProgressMonitor(
        logFile: File,
        statusFile: File,
        totalFrames: Long,
        durationMs: Long
    ) {
        cancelMonitoring()
        monitorJob = scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var lastReadPos = 0L

            val frameRegex = Regex("""frame=\s*(\d+)""")
            val fpsRegex = Regex("""fps=\s*([\d.]+)""")
            val timeRegex = Regex("""time=(\d\d):(\d\d):(\d\d)\.(\d\d)""")
            val speedRegex = Regex("""speed=\s*([\d.]+)x""")

            while (isActive) {
                delay(400)

                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000

                // Read incremental logs
                if (logFile.exists()) {
                    try {
                        val length = logFile.length()
                        if (length > lastReadPos) {
                            FileInputStream(logFile).use { fis ->
                                fis.skip(lastReadPos)
                                val newBytes = fis.readBytes()
                                lastReadPos = length
                                val newText = String(newBytes, Charsets.UTF_8)
                                val lines = newText.lines()
                                val latestLine = lines.lastOrNull { it.isNotBlank() } ?: ""

                                var parsedFrame = 0L
                                var parsedFps = 0.0
                                var parsedCurrentTimeMs = 0L

                                lines.forEach { line ->
                                    frameRegex.find(line)?.let { m ->
                                        parsedFrame = m.groupValues[1].toLongOrNull() ?: parsedFrame
                                    }
                                    fpsRegex.find(line)?.let { m ->
                                        parsedFps = m.groupValues[1].toDoubleOrNull() ?: parsedFps
                                    }
                                    timeRegex.find(line)?.let { m ->
                                        val h = m.groupValues[1].toLongOrNull() ?: 0L
                                        val min = m.groupValues[2].toLongOrNull() ?: 0L
                                        val sec = m.groupValues[3].toLongOrNull() ?: 0L
                                        val cs = m.groupValues[4].toLongOrNull() ?: 0L
                                        parsedCurrentTimeMs = (h * 3600 + min * 60 + sec) * 1000 + cs * 10
                                    }
                                }

                                val progressByFrame = if (totalFrames > 0 && parsedFrame > 0) {
                                    (parsedFrame.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.99f)
                                } else 0f

                                val progressByTime = if (durationMs > 0 && parsedCurrentTimeMs > 0) {
                                    (parsedCurrentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.99f)
                                } else 0f

                                val bestProgress = maxOf(progressByFrame, progressByTime)
                                val pct = (bestProgress * 100).toInt()

                                val fpsRatio = if (totalFrames > 0 && parsedFps > 0) {
                                    parsedFps / totalFrames.toDouble()
                                } else 0.0

                                val remainingSec = if (parsedFps > 0.5 && totalFrames > parsedFrame) {
                                    ((totalFrames - parsedFrame) / parsedFps).toLong()
                                } else if (durationMs > parsedCurrentTimeMs && elapsedSec > 2) {
                                    val remainingMs = durationMs - parsedCurrentTimeMs
                                    (remainingMs / 1000).coerceAtLeast(0L)
                                } else {
                                    0L
                                }

                                val finishText = if (remainingSec > 0) {
                                    HardsubEncoder.formatTimeSeconds(remainingSec)
                                } else "--:--"

                                _encodeState.update {
                                    it.copy(
                                        progress = bestProgress,
                                        progressPercentage = pct,
                                        currentFrame = parsedFrame,
                                        currentFps = parsedFps,
                                        fpsToTotalFramesRatio = fpsRatio,
                                        estimatedRemainingSeconds = remainingSec,
                                        averageEstimatedFinishText = finishText,
                                        elapsedSeconds = elapsedSec,
                                        lastLogLine = latestLine.takeLast(200)
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error monitoring log file: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun cancelMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private sealed class JobWaitResult {
        object Success : JobWaitResult()
        data class Error(val message: String) : JobWaitResult()
    }

    /**
     * Polls status file and receiver results until command completes or times out.
     */
    private suspend fun waitForJobCompletion(
        jobDir: File,
        logFile: File,
        statusFile: File
    ): JobWaitResult = withContext(Dispatchers.IO) {
        val maxWaitMs = 30 * 60 * 1000L // 30 minutes max
        val startTime = System.currentTimeMillis()

        while (isActive && !isCancelledByUser) {
            delay(500)

            // Check if status file has recorded terminal status
            if (statusFile.exists()) {
                val statusContent = try { statusFile.readText(Charsets.UTF_8).trim() } catch (_: Exception) { "" }
                when {
                    statusContent.contains("TERMUX_NO_FFMPEG") -> {
                        return@withContext JobWaitResult.Error("Termux içinde FFmpeg bulunamadı. Lütfen Termux'ta 'pkg install ffmpeg' komutunu çalıştırın.")
                    }
                    statusContent.contains("TERMUX_NO_LIBASS") -> {
                        return@withContext JobWaitResult.Error("Termux içindeki FFmpeg libass filtre desteğine sahip değil.")
                    }
                    statusContent.contains("EXIT_CODE=") -> {
                        val exitCodeStr = statusContent.substringAfter("EXIT_CODE=").substringBefore("\n").trim()
                        val code = exitCodeStr.toIntOrNull() ?: -1
                        return@withContext if (code == 0) {
                            JobWaitResult.Success
                        } else {
                            val logTail = readLogTail(logFile)
                            checkLogForSpecificTermuxErrors(logTail) ?: JobWaitResult.Error("Altyazı videoya gömülemedi.")
                        }
                    }
                }
            }

            // Check PendingIntent callback
            val exitCode = receivedExitCode
            if (exitCode != null) {
                val errCode = receivedErrCode ?: 0
                val errmsg = receivedErrmsg
                if (errCode != 0 || errmsg != null) {
                    if (errmsg?.contains("allow-external-apps", ignoreCase = true) == true) {
                        return@withContext JobWaitResult.Error(
                            "Termux dış komut izni kapalı. Lütfen Termux'ta '~/.termux/termux.properties' dosyasına 'allow-external-apps = true' ekleyin."
                        )
                    }
                }
                return@withContext if (exitCode == 0) {
                    JobWaitResult.Success
                } else {
                    val logTail = readLogTail(logFile)
                    checkLogForSpecificTermuxErrors(logTail) ?: JobWaitResult.Error("Altyazı videoya gömülemedi.")
                }
            }

            if (System.currentTimeMillis() - startTime > maxWaitMs) {
                return@withContext JobWaitResult.Error("Encode işlemi zaman aşımına uğradı.")
            }
        }

        if (isCancelledByUser) {
            JobWaitResult.Error("Encode kullanıcı tarafından iptal edildi.")
        } else {
            JobWaitResult.Error("Bilinmeyen hata.")
        }
    }

    private fun readLogTail(logFile: File): String {
        return try {
            if (logFile.exists()) {
                val text = logFile.readText(Charsets.UTF_8)
                text.takeLast(2000)
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun checkLogForSpecificTermuxErrors(logTail: String): JobWaitResult.Error? {
        val lower = logTail.lowercase(Locale.ROOT)
        return when {
            lower.contains("permission denied") -> {
                JobWaitResult.Error(
                    "Termux'un paylaşılan depolama alanına erişimi yok. Lütfen Termux'ta 'termux-setup-storage' komutunu çalıştırıp izin verin."
                )
            }
            lower.contains("ffmpeg: command not found") || lower.contains("no such file or directory") && lower.contains("ffmpeg") -> {
                JobWaitResult.Error("Termux içinde FFmpeg bulunamadı. Lütfen Termux'ta 'pkg install ffmpeg' komutunu çalıştırın.")
            }
            lower.contains("no such filter: 'ass'") || lower.contains("cannot load fontconfig") -> {
                JobWaitResult.Error("Termux FFmpeg libass filtresini başlatamadı.")
            }
            else -> null
        }
    }

    /**
     * Independent output validation as mandated.
     */
    private fun validateHardsubOutput(
        outputFile: File,
        inputVideoFile: File,
        cues: List<SubtitleCue>,
        assFile: File,
        allLogs: String
    ): ValidationResult {
        // 1. Output file must exist and have length > 1024 bytes
        if (!outputFile.exists() || outputFile.length() < 1024L) {
            Log.e(TAG, "Validation failed: output file missing or empty (< 1024 bytes).")
            return ValidationResult.Failure("Altyazı videoya gömülemedi.")
        }

        // 2. Output must not be identical file or size to source
        if (outputFile.canonicalPath == inputVideoFile.canonicalPath ||
            (outputFile.length() == inputVideoFile.length() && areFilesIdentical(outputFile, inputVideoFile))) {
            Log.e(TAG, "Validation failed: output is identical to original source.")
            return ValidationResult.Failure("Altyazı videoya gömülemedi.")
        }

        // 3. Subtitle file must have existed
        if (!assFile.exists() || assFile.length() <= 0L) {
            Log.e(TAG, "Validation failed: subtitle file did not exist.")
            return ValidationResult.Failure("Altyazı videoya gömülemedi.")
        }

        // 4. FFmpeg log must show subtitle filter ran and did not report filter error
        val lowerLogs = allLogs.lowercase(Locale.ROOT)
        val filterError = lowerLogs.contains("could not initialize libass") ||
                lowerLogs.contains("failed to configure filter") ||
                lowerLogs.contains("no such filter: 'ass'") ||
                lowerLogs.contains("error initializing filter 'ass'") ||
                lowerLogs.contains("null filter graph") ||
                lowerLogs.contains("error reinitializing filters")

        if (filterError) {
            Log.e(TAG, "Validation failed: FFmpeg reported subtitle filter initialization error.")
            return ValidationResult.Failure("Altyazı videoya gömülemedi.")
        }

        // 5. MediaMetadataRetriever & MediaExtractor verification
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            extractor.setDataSource(outputFile.absolutePath)

            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
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

            if (!hasVideoTrack && hasVideo != "yes") {
                Log.e(TAG, "Validation failed: output has no valid video stream.")
                return ValidationResult.Failure("Altyazı videoya gömülemedi.")
            }
            if (duration <= 0L || width <= 0 || height <= 0) {
                Log.e(TAG, "Validation failed: invalid video dimensions ($width x $height) or duration ($duration ms).")
                return ValidationResult.Failure("Altyazı videoya gömülemedi.")
            }

            // Test decoding a frame
            val frameBitmap = try {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) { null }

            if (frameBitmap == null) {
                Log.e(TAG, "Validation failed: output frames could not be decoded.")
                return ValidationResult.Failure("Altyazı videoya gömülemedi.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Validation exception: ${e.localizedMessage}")
            return ValidationResult.Failure("Altyazı videoya gömülemedi.")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }

        return ValidationResult.Success
    }

    /**
     * Publishes verified video to permanent user storage (MediaStore on Android 10+, public Movies on legacy).
     */
    private fun publishToPermanentStorage(
        context: Context,
        tempOutputFile: File,
        originalFileName: String
    ): File? {
        val safeBase = originalFileName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalFileName = "${safeBase}_hardsub_${System.currentTimeMillis()}.mp4"

        return try {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "RemSubs"
            ).apply { mkdirs() }

            val permanentFile = File(moviesDir, finalFileName)

            // Copy with buffer
            FileInputStream(tempOutputFile).use { input ->
                FileOutputStream(permanentFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            // MediaStore publishing for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
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

            // Android MediaScanner
            try {
                android.media.MediaScannerConnection.scanFile(
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

    private fun copyFontsToSharedDir(
        context: Context,
        fontsDir: File,
        customFontFile: File?
    ): List<File> {
        val copied = mutableListOf<File>()

        // 1. User's custom font if present
        if (customFontFile != null && customFontFile.exists()) {
            try {
                val dest = File(fontsDir, customFontFile.name)
                customFontFile.copyTo(dest, overwrite = true)
                copied.add(dest)
            } catch (_: Exception) {}
        }

        // 2. Bundled app fonts from raw resources (e.g. montserrat, roboto)
        val bundledFonts = listOf(
            "montserrat.ttf" to com.example.R.font.montserrat,
            "roboto.ttf" to com.example.R.font.roboto
        )
        for ((name, resId) in bundledFonts) {
            try {
                val dest = File(fontsDir, name)
                if (!dest.exists()) {
                    context.resources.openRawResource(resId).use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (dest.exists() && dest.length() > 0) {
                    copied.add(dest)
                }
            } catch (_: Exception) {}
        }

        return copied
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

    private fun cleanupWorkingDir(dir: File) {
        try {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up working dir: ${e.localizedMessage}")
        }
    }

    private fun areFilesIdentical(file1: File, file2: File): Boolean {
        if (file1.length() != file2.length()) return false
        return try {
            FileInputStream(file1).use { f1 ->
                FileInputStream(file2).use { f2 ->
                    val buf1 = ByteArray(8192)
                    val buf2 = ByteArray(8192)
                    var read1: Int
                    var read2: Int
                    while (f1.read(buf1).also { read1 = it } != -1) {
                        read2 = f2.read(buf2)
                        if (read1 != read2 || !buf1.sliceArray(0 until read1).contentEquals(buf2.sliceArray(0 until read2))) {
                            return false
                        }
                    }
                    true
                }
            }
        } catch (_: Exception) { false }
    }

    /**
     * Escapes paths for FFmpeg filter arguments (escapes \ ' : , [ ]).
     */
    fun escapeForAssFilter(path: String): String {
        return path
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace(":", "\\:")
            .replace(",", "\\,")
            .replace("[", "\\[")
            .replace("]", "\\]")
    }

    /**
     * Robust bash argument escaping: wraps in single quotes and escapes single quotes as '\''.
     */
    fun escapeForBash(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    private sealed class ValidationResult {
        object Success : ValidationResult()
        data class Failure(val reason: String) : ValidationResult()
    }
}
