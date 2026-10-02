package com.example.torrent

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.util.MediaStorageManager
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import com.frostwire.jlibtorrent.alerts.PieceFinishedAlert
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert
import com.frostwire.jlibtorrent.alerts.TorrentErrorAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
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
import java.util.Locale

/**
 * Production-grade BitTorrent client using FrostWire jlibtorrent (libtorrent 2.0+).
 * Completely eliminates any fake progress, fake delays, hardcoded sizes, or sample media copying.
 * Handles true end-to-end BitTorrent pipeline:
 * Magnet / .torrent -> Metadata -> DHT/Trackers -> Peer Discovery -> Piece Requests ->
 * SHA-1 Verification -> Piece Assembly -> Complete File -> Media Validation -> MediaStore (Movies/RemSubs) -> Gallery.
 */
object TorrentDownloadManager {
    private const val TAG = "TorrentDownloadManager"

    private val _downloadInfo = MutableStateFlow(TorrentDownloadInfo())
    val downloadInfo: StateFlow<TorrentDownloadInfo> = _downloadInfo.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var downloadMonitorJob: Job? = null

    private var _sessionManager: SessionManager? = null
    val sessionManager: SessionManager?
        get() {
            if (_sessionManager == null) {
                try {
                    val sm = SessionManager()
                    registerSessionAlerts(sm)
                    _sessionManager = sm
                } catch (t: Throwable) {
                    Log.e(TAG, "jlibtorrent native library not available in this environment: ${t.localizedMessage}", t)
                }
            }
            return _sessionManager
        }

    private var activeHandle: TorrentHandle? = null
    private var activeTorrentInfo: TorrentInfo? = null
    private var activeDownloadDir: File? = null
    private var activeContext: Context? = null
    private var isListenerRegistered = false

    private fun registerSessionAlerts(sm: SessionManager) {
        if (isListenerRegistered) return
        try {
            sm.addListener(object : AlertListener {
                override fun types(): IntArray? = null // receive all alerts

                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.METADATA_RECEIVED -> {
                            val metaAlert = alert as? MetadataReceivedAlert
                            Log.i(TAG, "[ALERT] metadata_received: ${metaAlert?.torrentName() ?: alert.message()}")
                            val handle = metaAlert?.handle() ?: activeHandle
                            if (handle != null && handle.isValid) {
                                scope.launch(Dispatchers.IO) {
                                    handleMetadataReceived(handle)
                                }
                            }
                        }
                        AlertType.TORRENT_CHECKED -> {
                            Log.i(TAG, "[ALERT] torrent_checked: ${alert.message()}")
                        }
                        AlertType.PIECE_FINISHED -> {
                            val pieceAlert = alert as? PieceFinishedAlert
                            val pieceIdx = pieceAlert?.pieceIndex() ?: -1
                            Log.d(TAG, "[ALERT] piece_finished: piece=$pieceIdx")
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val finishedAlert = alert as? TorrentFinishedAlert
                            Log.i(TAG, "[ALERT] torrent_finished: ${finishedAlert?.torrentName() ?: alert.message()}")
                            scope.launch(Dispatchers.IO) { onDownloadCompleted() }
                        }
                        AlertType.TORRENT_ERROR -> {
                            val errorAlert = alert as? TorrentErrorAlert
                            Log.e(TAG, "[ALERT] torrent_error: ${errorAlert?.error()?.message() ?: alert.message()}")
                            val (errType, desc) = classifyErrorMessage(errorAlert?.error()?.message() ?: alert.message())
                            _downloadInfo.update {
                                it.copy(
                                    state = TorrentState.ERROR,
                                    errorMessage = desc,
                                    errorType = errType,
                                    statusMessage = "Hata: $desc"
                                )
                            }
                        }
                        AlertType.SAVE_RESUME_DATA -> {
                            val resumeAlert = alert as? SaveResumeDataAlert
                            Log.i(TAG, "[ALERT] save_resume_data: ${resumeAlert?.torrentName() ?: alert.message()}")
                        }
                        else -> {}
                    }
                }
            })
            isListenerRegistered = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register alert listener: ${t.localizedMessage}")
        }
    }

    private fun ensureSessionStarted(): Boolean {
        return try {
            val sm = sessionManager ?: return false
            if (!sm.isRunning) {
                Log.i(TAG, "Starting jlibtorrent SessionManager (DHT + Peer Exchange)...")
                sm.start()
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start jlibtorrent session: ${e.localizedMessage}", e)
            false
        }
    }

    fun classifyErrorMessage(message: String?): Pair<TorrentErrorType, String> {
        val msg = message ?: ""
        val lower = msg.lowercase(Locale.ROOT)
        return when {
            lower.contains("timeout") || lower.contains("metadata") ->
                Pair(TorrentErrorType.METADATA_TIMEOUT, "Metadata zaman aşımı (Tracker/Peer yanıt vermedi)")
            lower.contains("no peer") || lower.contains("zero peers") ->
                Pair(TorrentErrorType.NO_PEERS, "Kullanılabilir eş (peer) veya seeder bulunamadı")
            lower.contains("tracker") ->
                Pair(TorrentErrorType.TRACKER_ERROR, "İzleyici (Tracker) bağlantı hatası")
            lower.contains("dht") ->
                Pair(TorrentErrorType.DHT_ERROR, "DHT ağı yanıt vermedi")
            lower.contains("permission") || lower.contains("denied") ->
                Pair(TorrentErrorType.PERMISSION, "Depolama yazma izni reddedildi")
            lower.contains("space") || lower.contains("full") ->
                Pair(TorrentErrorType.STORAGE_FULL, "Cihaz depolama alanı yetersiz")
            lower.contains("magnet") ->
                Pair(TorrentErrorType.INVALID_MAGNET, "Geçersiz veya desteklenmeyen Magnet URI")
            lower.contains("torrent") || lower.contains("bencode") || lower.contains("invalid") ->
                Pair(TorrentErrorType.INVALID_TORRENT, "Geçersiz veya bozuk .torrent dosyası")
            lower.contains("engine") ->
                Pair(TorrentErrorType.ENGINE_INIT_FAILURE, "BitTorrent motoru başlatılamadı")
            lower.contains("network") || lower.contains("connect") ->
                Pair(TorrentErrorType.NETWORK_ERROR, "Ağ bağlantısı hatası")
            else ->
                Pair(TorrentErrorType.UNKNOWN, msg.ifBlank { "Bilinmeyen indirme hatası" })
        }
    }

    fun classifyError(t: Throwable): Pair<TorrentErrorType, String> {
        return classifyErrorMessage(t.localizedMessage ?: t.message)
    }

    /**
     * Starts download from a Magnet URI.
     * Enforces real BitTorrent protocol without size guessing before metadata arrival.
     */
    fun startMagnetDownload(context: Context, magnetUri: String) {
        cancelDownload()
        activeContext = context.applicationContext

        val targetDir = context.getExternalFilesDir("torrent_downloads")
            ?: File(context.cacheDir, "torrent_downloads")
        targetDir.mkdirs()
        activeDownloadDir = targetDir

        // Extract infohash if present in URI
        val infoHashHex = extractInfoHashFromMagnet(magnetUri)

        _downloadInfo.value = TorrentDownloadInfo(
            magnetUri = magnetUri,
            infoHashHex = infoHashHex,
            state = TorrentState.RESOLVING_METADATA,
            statusMessage = "Magnet meta verisi aranıyor (Trackers & DHT)...",
            totalBytes = 0L,
            downloadedBytes = 0L,
            progress = 0f,
            progressPercentage = 0
        )
        logTorrentSnapshot(stateOverride = TorrentState.RESOLVING_METADATA.name)

        scope.launch(Dispatchers.IO) {
            try {
                if (!ensureSessionStarted()) {
                    throw IllegalStateException("engine init failure: jlibtorrent session could not start")
                }
                val sm = sessionManager ?: throw IllegalStateException("engine init failure")

                Log.i(TAG, "Starting magnet download directly in jlibtorrent: $magnetUri")
                sm.download(magnetUri, targetDir, null)
                findAndTrackHandle(infoHashHex)
            } catch (t: Throwable) {
                Log.e(TAG, "startMagnetDownload error: ${t.localizedMessage}", t)
                val (errType, desc) = classifyError(t)
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.ERROR,
                        errorMessage = desc,
                        errorType = errType,
                        statusMessage = "Hata: $desc"
                    )
                }
                logTorrentSnapshot(stateOverride = TorrentState.ERROR.name, errorOverride = desc)
            }
        }
    }

    /**
     * Starts download from a user-selected .torrent file.
     * ContentResolver reads byte payload into true TorrentInfo metadata.
     */
    fun startTorrentFileDownload(context: Context, torrentUri: Uri) {
        cancelDownload()
        activeContext = context.applicationContext

        val targetDir = context.getExternalFilesDir("torrent_downloads")
            ?: File(context.cacheDir, "torrent_downloads")
        targetDir.mkdirs()
        activeDownloadDir = targetDir

        _downloadInfo.value = TorrentDownloadInfo(
            state = TorrentState.RESOLVING_METADATA,
            statusMessage = ".torrent dosyası okunuyor...",
            totalBytes = 0L,
            downloadedBytes = 0L,
            progress = 0f,
            progressPercentage = 0
        )
        logTorrentSnapshot(stateOverride = TorrentState.RESOLVING_METADATA.name)

        scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(torrentUri)?.use { it.readBytes() }

                if (bytes == null || bytes.isEmpty()) {
                    val errPair = Pair(TorrentErrorType.INVALID_TORRENT, ".torrent dosyası okunamadı veya boş.")
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.ERROR,
                            errorMessage = errPair.second,
                            errorType = errPair.first,
                            statusMessage = errPair.second
                        )
                    }
                    logTorrentSnapshot(stateOverride = TorrentState.ERROR.name, errorOverride = errPair.second)
                    return@launch
                }

                if (!ensureSessionStarted()) {
                    throw IllegalStateException("engine init failure: jlibtorrent session could not start")
                }
                val sm = sessionManager ?: throw IllegalStateException("engine init failure")

                val ti = try {
                    TorrentInfo(bytes)
                } catch (e: Exception) {
                    val errPair = Pair(TorrentErrorType.INVALID_TORRENT, "Geçersiz .torrent dosyası: ${e.localizedMessage}")
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.ERROR,
                            errorMessage = errPair.second,
                            errorType = errPair.first,
                            statusMessage = errPair.second
                        )
                    }
                    logTorrentSnapshot(stateOverride = TorrentState.ERROR.name, errorOverride = errPair.second)
                    return@launch
                }

                activeTorrentInfo = ti
                val fileStorage = ti.files()
                val numFiles = fileStorage.numFiles()
                val fileList = mutableListOf<String>()
                for (i in 0 until numFiles) fileList.add(fileStorage.filePath(i))

                val videoFiles = fileList.filter { MediaStorageManager.isVideoFile(it) }
                val selectedVideo = if (videoFiles.isNotEmpty()) {
                    videoFiles.maxByOrNull { path ->
                        val idx = fileList.indexOf(path)
                        if (idx >= 0) fileStorage.fileSize(idx) else 0L
                    } ?: videoFiles.first()
                } else if (fileList.isNotEmpty()) fileList.first() else ""

                val priorities = Array(numFiles) { i ->
                    val path = fileStorage.filePath(i)
                    if (path == selectedVideo || path.endsWith(selectedVideo)) {
                        Priority.fromSwig(4)
                    } else if (MediaStorageManager.isVideoFile(path)) {
                        Priority.fromSwig(1)
                    } else {
                        Priority.IGNORE
                    }
                }

                sm.download(ti, targetDir, null, priorities, null, null)
                val handle = sm.find(ti.infoHash())
                activeHandle = handle

                _downloadInfo.update {
                    it.copy(
                        torrentName = ti.name(),
                        infoHashHex = ti.infoHash().toHex(),
                        totalBytes = ti.totalSize(),
                        pieceCount = ti.numPieces(),
                        pieceSize = ti.pieceLength(),
                        files = fileList,
                        selectedVideoFileName = selectedVideo,
                        state = TorrentState.DOWNLOADING,
                        statusMessage = "İndiriliyor"
                    )
                }
                logTorrentSnapshot(stateOverride = TorrentState.DOWNLOADING.name)
                startStatusMonitor()

            } catch (t: Throwable) {
                Log.e(TAG, "startTorrentFileDownload error: ${t.localizedMessage}", t)
                val (errType, desc) = classifyError(t)
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.ERROR,
                        errorMessage = desc,
                        errorType = errType,
                        statusMessage = "Hata: $desc"
                    )
                }
                logTorrentSnapshot(stateOverride = TorrentState.ERROR.name, errorOverride = desc)
            }
        }
    }

    private fun handleMetadataReceived(handle: TorrentHandle) {
        if (!handle.isValid) return
        val ti = try {
            handle.torrentFile()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get torrentFile from handle: ${e.localizedMessage}")
            null
        } ?: return

        activeTorrentInfo = ti
        activeHandle = handle

        val name = ti.name()
        val totalSize = ti.totalSize()
        val numPieces = ti.numPieces()
        val pieceLen = ti.pieceLength()
        val infoHashHex = ti.infoHash().toHex()
        val fileStorage = ti.files()
        val numFiles = fileStorage.numFiles()

        val fileList = mutableListOf<String>()
        for (i in 0 until numFiles) {
            fileList.add(fileStorage.filePath(i))
        }

        // Multi-file handling: identify primary video file and configure priorities
        var selectedVideo = _downloadInfo.value.selectedVideoFileName
        if (selectedVideo.isBlank() || !fileList.contains(selectedVideo)) {
            val videoFiles = fileList.filter { MediaStorageManager.isVideoFile(it) }
            selectedVideo = if (videoFiles.isNotEmpty()) {
                videoFiles.maxByOrNull { path ->
                    val idx = fileList.indexOf(path)
                    if (idx >= 0) fileStorage.fileSize(idx) else 0L
                } ?: videoFiles.first()
            } else if (fileList.isNotEmpty()) {
                fileList.first()
            } else ""
        }

        val priorities = Array(numFiles) { i ->
            val path = fileStorage.filePath(i)
            if (path == selectedVideo || path.endsWith(selectedVideo)) {
                Priority.fromSwig(4) // High priority for chosen video
            } else if (MediaStorageManager.isVideoFile(path)) {
                Priority.fromSwig(1) // Normal priority for other videos
            } else {
                Priority.IGNORE // Ignore non-video/sample/nfo files
            }
        }

        try {
            handle.prioritizeFiles(priorities)
        } catch (e: Exception) {
            Log.w(TAG, "prioritizeFiles error: ${e.localizedMessage}")
        }

        try {
            handle.resume()
        } catch (_: Exception) {}

        Log.i(TAG, """
            [TORRENT METADATA ACQUIRED]
            TORRENT NAME: $name
            INFOHASH: $infoHashHex
            TOTAL SIZE: $totalSize (${TorrentDownloadInfo.formatBytes(totalSize)})
            PIECE COUNT: $numPieces
            PIECE SIZE: $pieceLen
            FILES: ${fileList.joinToString(", ")}
            SELECTED VIDEO: $selectedVideo
        """.trimIndent())

        _downloadInfo.update {
            it.copy(
                torrentName = name,
                infoHashHex = infoHashHex,
                totalBytes = totalSize,
                pieceCount = numPieces,
                pieceSize = pieceLen,
                files = fileList,
                selectedVideoFileName = selectedVideo,
                state = TorrentState.DOWNLOADING,
                statusMessage = "İndiriliyor"
            )
        }
        logTorrentSnapshot(stateOverride = TorrentState.DOWNLOADING.name)

        startStatusMonitor()
    }

    private fun findAndTrackHandle(infoHashHex: String) {
        if (infoHashHex.isNotBlank()) {
            try {
                val hash = Sha1Hash(infoHashHex)
                val handle = sessionManager?.find(hash)
                if (handle != null && handle.isValid) {
                    activeHandle = handle
                }
            } catch (e: Exception) {
                Log.w(TAG, "find handle warning: ${e.localizedMessage}")
            }
        }
        startStatusMonitor()
    }

    private fun startStatusMonitor() {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = scope.launch {
            while (isActive) {
                delay(500)
                val handle = activeHandle ?: run {
                    val hash = _downloadInfo.value.infoHashHex
                    if (hash.isNotBlank()) {
                        try {
                            sessionManager?.find(Sha1Hash(hash))
                        } catch (_: Exception) { null }
                    } else null
                }
                activeHandle = handle

                if (handle != null && handle.isValid) {
                    val status = handle.status()
                    val totalDone = status.totalDone()
                    val total = if (status.total() > 0) status.total() else _downloadInfo.value.totalBytes
                    val progress = if (total > 0) (totalDone.toFloat() / total.toFloat()).coerceIn(0f, 1f) else status.progress()
                    val pct = (progress * 100).toInt()
                    val rate = status.downloadPayloadRate().toLong()
                    val peers = status.numPeers()
                    val seeds = status.numSeeds()
                    val state = status.state()

                    val speedText = formatSpeed(rate)
                    val etaSec = if (rate > 1024L && total > totalDone) {
                        (total - totalDone) / rate
                    } else 0L
                    val etaText = if (etaSec > 0) formatEta(etaSec) else "--:--"

                    // Log snapshot according to user requirement 12
                    Log.i(TAG, """
                        [TORRENT]
                        state=${state.name}
                        total=${TorrentDownloadInfo.formatBytes(total)}
                        downloaded=${TorrentDownloadInfo.formatBytes(totalDone)}
                        progress=${String.format(Locale.US, "%.1f%%", progress * 100f)}
                        speed=$speedText
                        peers=$peers
                        seeds=$seeds
                    """.trimIndent())

                    _downloadInfo.update {
                        it.copy(
                            downloadedBytes = totalDone,
                            totalBytes = total,
                            progress = progress,
                            progressPercentage = pct,
                            speedBytesPerSec = rate,
                            speedText = speedText,
                            etaText = etaText,
                            connectedPeers = peers,
                            seeders = seeds,
                            state = if (status.isFinished || state == TorrentStatus.State.FINISHED || state == TorrentStatus.State.SEEDING) {
                                TorrentState.COMPLETED
                            } else {
                                TorrentState.DOWNLOADING
                            },
                            statusMessage = if (status.isFinished) "İndirme tamamlandı" else "İndiriliyor"
                        )
                    }

                    if (status.isFinished || state == TorrentStatus.State.FINISHED || state == TorrentStatus.State.SEEDING) {
                        onDownloadCompleted()
                        break
                    }
                }
            }
        }
    }

    private suspend fun onDownloadCompleted() {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = null

        val context = activeContext ?: return
        val targetDir = activeDownloadDir ?: return
        val info = _downloadInfo.value

        _downloadInfo.update {
            it.copy(
                state = TorrentState.TRANSFERRING,
                statusMessage = "İndirilen medya dosyası doğrulanıyor...",
                speedText = "Doğrulanıyor",
                etaText = "--:--"
            )
        }

        // Find primary completed video file
        val targetFile = resolveDownloadedFile(targetDir, info.selectedVideoFileName, info.torrentName)
        if (targetFile == null || !targetFile.exists() || targetFile.length() <= 0L) {
            Log.e(TAG, "Torrent completion error: Target file not found or empty.")
            _downloadInfo.update {
                it.copy(
                    state = TorrentState.ERROR,
                    errorMessage = "İndirilen dosya bulunamadı veya boş.",
                    errorType = TorrentErrorType.UNKNOWN,
                    statusMessage = "Hata"
                )
            }
            return
        }

        // Validate video stream and media container integrity
        val validation = validateMediaFile(targetFile)
        if (validation is MediaValidationResult.Invalid) {
            Log.e(TAG, "Media validation failure: ${validation.reason}")
            _downloadInfo.update {
                it.copy(
                    state = TorrentState.ERROR,
                    errorMessage = "İndirilen dosya doğrulanamadı: ${validation.reason}",
                    errorType = TorrentErrorType.UNKNOWN,
                    statusMessage = "Hata"
                )
            }
            return
        }

        _downloadInfo.update {
            it.copy(
                state = TorrentState.TRANSFERRING,
                statusMessage = "Galeriye ve Movies/RemSubs klasörüne kaydediliyor...",
                speedText = "Kaydediliyor"
            )
        }

        // Save to Gallery under Movies/RemSubs/ preserving true container (.mkv, .mp4, .webm)
        val saveResult = MediaStorageManager.saveVideoToGallery(
            context = context,
            sourceFile = targetFile,
            originalFileName = targetFile.name
        )

        when (saveResult) {
            is StorageSaveResult.Success -> {
                Log.i(TAG, """
                    [TORRENT]
                    state=COMPLETED
                    path=${saveResult.permanentFile.absolutePath}
                    size=${saveResult.permanentFile.length()}
                """.trimIndent())

                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.COMPLETED,
                        statusMessage = "İndirme tamamlandı ve Galeriye kaydedildi",
                        progress = 1.0f,
                        progressPercentage = 100,
                        speedText = "0 KB/s",
                        etaText = "00:00",
                        downloadedFile = saveResult.permanentFile,
                        permanentUri = saveResult.permanentUri,
                        errorMessage = null,
                        errorType = null
                    )
                }
                logTorrentSnapshot(stateOverride = TorrentState.COMPLETED.name)
            }
            is StorageSaveResult.Failure -> {
                Log.e(TAG, "Gallery save failed: ${saveResult.reason}")
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.ERROR,
                        errorMessage = saveResult.reason,
                        errorType = TorrentErrorType.UNKNOWN,
                        statusMessage = "Hata"
                    )
                }
            }
        }
    }

    private fun resolveDownloadedFile(targetDir: File, selectedFileName: String, torrentName: String): File? {
        if (selectedFileName.isNotBlank()) {
            val f = File(targetDir, selectedFileName)
            if (f.exists() && f.length() > 0L) return f
        }

        // Search recursively inside targetDir for any video file
        val allFiles = targetDir.walkTopDown().filter { it.isFile && MediaStorageManager.isVideoFile(it.name) }.toList()
        if (allFiles.isNotEmpty()) {
            // Return largest video file (typically the primary movie/episode)
            return allFiles.maxByOrNull { it.length() }
        }

        // Check if any file exists matching torrentName
        val named = File(targetDir, torrentName)
        if (named.exists() && named.length() > 0L) return named

        // Fallback to any non-empty file
        return targetDir.walkTopDown().filter { it.isFile && it.length() > 0L }.maxByOrNull { it.length() }
    }

    private sealed class MediaValidationResult {
        object Valid : MediaValidationResult()
        data class Invalid(val reason: String) : MediaValidationResult()
    }

    private fun validateMediaFile(file: File): MediaValidationResult {
        if (!file.exists() || !file.canRead() || file.length() <= 0L) {
            return MediaValidationResult.Invalid("Dosya bulunamadı veya boş.")
        }

        if (MediaStorageManager.isVideoFile(file.name)) {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                val trackCount = extractor.trackCount
                if (trackCount <= 0) {
                    return MediaValidationResult.Invalid("Dosyada medya akışı bulunamadı.")
                }
                var hasVideo = false
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        hasVideo = true
                        break
                    }
                }
                if (!hasVideo) {
                    return MediaValidationResult.Invalid("Video akışı bulunamadı.")
                }
                MediaValidationResult.Valid
            } catch (e: Exception) {
                MediaValidationResult.Invalid("Video dosyası açılamadı: ${e.localizedMessage}")
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        }

        return MediaValidationResult.Valid
    }

    fun selectVideoFile(fileName: String) {
        val ti = activeTorrentInfo ?: return
        val handle = activeHandle ?: return
        if (!handle.isValid) return

        val fileStorage = ti.files()
        val numFiles = fileStorage.numFiles()
        var targetIndex = -1
        for (i in 0 until numFiles) {
            val path = fileStorage.filePath(i)
            if (path == fileName) {
                targetIndex = i
                break
            }
        }
        if (targetIndex >= 0) {
            val priorities = Array(numFiles) { i ->
                if (i == targetIndex) Priority.fromSwig(4)
                else if (MediaStorageManager.isVideoFile(fileStorage.filePath(i))) Priority.fromSwig(1)
                else Priority.IGNORE
            }
            try {
                handle.prioritizeFiles(priorities)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prioritize files: ${e.localizedMessage}")
            }
            _downloadInfo.update { it.copy(selectedVideoFileName = fileName) }
            Log.i(TAG, "Switched selected video file to: $fileName")
        }
    }

    fun pauseDownload() {
        activeHandle?.pause()
        _downloadInfo.update { it.copy(state = TorrentState.PAUSED, statusMessage = "Duraklatıldı") }
    }

    fun resumeDownload(context: Context? = null) {
        if (context != null) {
            activeContext = context.applicationContext
        }
        activeHandle?.resume()
        _downloadInfo.update { it.copy(state = TorrentState.DOWNLOADING, statusMessage = "İndiriliyor") }
        if (downloadMonitorJob == null || downloadMonitorJob?.isActive == false) {
            startStatusMonitor()
        }
    }

    fun cancelDownload() {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = null
        try {
            val handle = activeHandle
            if (handle != null && handle.isValid) {
                handle.pause()
                sessionManager?.remove(handle)
            }
        } catch (_: Exception) {}
        activeHandle = null
        activeTorrentInfo = null
        _downloadInfo.value = TorrentDownloadInfo()
    }

    private fun extractInfoHashFromMagnet(magnetUri: String): String {
        val pattern = java.util.regex.Pattern.compile("urn:btih:([a-zA-Z0-9]+)")
        val matcher = pattern.matcher(magnetUri)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mb)
            else -> String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    private fun formatEta(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun logTorrentSnapshot(
        stateOverride: String? = null,
        errorOverride: String? = null
    ) {
        val info = _downloadInfo.value
        val uri = info.magnetUri
        val file = info.selectedVideoFileName.ifBlank { info.torrentName }
        val metadataReceived = (info.state != TorrentState.RESOLVING_METADATA && info.state != TorrentState.IDLE)
        val torrentId = info.infoHashHex
        val totalSize = info.totalBytes
        val downloadedSize = info.downloadedBytes
        val downloadSpeed = info.speedText
        val peerCount = info.connectedPeers
        val seedCount = info.seeders
        val completionPct = info.progressPercentage
        val state = stateOverride ?: info.state.name
        val error = errorOverride ?: info.errorMessage

        Log.d(TAG, """
            --- TORRENT STATUS SNAPSHOT ---
            TORRENT URI: $uri
            TORRENT FILE: $file
            METADATA RECEIVED: $metadataReceived
            TORRENT ID: $torrentId
            TOTAL SIZE: $totalSize (${TorrentDownloadInfo.formatBytes(totalSize)})
            DOWNLOADED SIZE: $downloadedSize (${TorrentDownloadInfo.formatBytes(downloadedSize)})
            DOWNLOAD SPEED: $downloadSpeed
            PEER COUNT: $peerCount
            SEED COUNT: $seedCount
            COMPLETION %: $completionPct%
            STATE: $state
            ERROR: ${error ?: "None"}
            -------------------------------
        """.trimIndent())
    }
}
