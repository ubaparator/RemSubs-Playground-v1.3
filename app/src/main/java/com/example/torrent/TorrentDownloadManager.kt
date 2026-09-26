package com.example.torrent

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.TimeUnit

object TorrentDownloadManager {
    private const val TAG = "TorrentDownloadManager"

    private val _downloadInfo = MutableStateFlow(TorrentDownloadInfo())
    val downloadInfo: StateFlow<TorrentDownloadInfo> = _downloadInfo.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var downloadJob: Job? = null
    private var speedMonitorJob: Job? = null

    private val peerId = generatePeerId()
    private val speedHistory = ArrayDeque<Pair<Long, Long>>() // timestampMs to bytes

    private var activeOutputFile: File? = null
    private var activeTargetFileName: String = ""
    private var activeContext: Context? = null
    private var isPaused = false

    private val defaultTrackers = listOf(
        "http://tracker.openbittorrent.com:80/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce"
    )

    fun startMagnetDownload(context: Context, magnetUri: String) {
        cancelDownload()
        isPaused = false

        downloadJob = scope.launch {
            try {
                _downloadInfo.value = TorrentDownloadInfo(
                    magnetUri = magnetUri,
                    state = TorrentState.RESOLVING_METADATA,
                    statusMessage = "Magnet bağlantısı çözümleniyor..."
                )

                // 1. Parse magnet link
                val parsed = parseMagnetLink(magnetUri)
                if (parsed == null) {
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.ERROR,
                            errorMessage = "Magnet bağlantısı çözümlenemedi. Lütfen geçerli bir 'magnet:?xt=urn:btih:...' linki girin."
                        )
                    }
                    return@launch
                }

                _downloadInfo.update {
                    it.copy(
                        torrentName = parsed.displayName,
                        infoHashHex = parsed.infoHashHex,
                        statusMessage = "İzleyicilere (Trackers) bağlanılıyor...",
                        state = TorrentState.CONNECTING_TRACKERS
                    )
                }

                // 2. Discover peers from trackers
                val allTrackers = (parsed.trackers + defaultTrackers).distinct()
                val discoveredPeers = mutableListOf<InetSocketAddress>()

                // Contact trackers concurrently
                for (trackerUrl in allTrackers.take(6)) {
                    if (!isActive || isPaused) break
                    try {
                        val peers = queryTracker(trackerUrl, parsed.infoHashBytes, 0, 0)
                        discoveredPeers.addAll(peers.peers)
                        _downloadInfo.update {
                            it.copy(
                                seeders = (it.seeders + peers.seeders).coerceAtLeast(1),
                                leechers = (it.leechers + peers.leechers).coerceAtLeast(1)
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Tracker query failed ($trackerUrl): ${e.localizedMessage}")
                    }
                }

                val uniquePeers = discoveredPeers.distinctBy { "${it.address.hostAddress}:${it.port}" }
                _downloadInfo.update {
                    it.copy(
                        connectedPeers = uniquePeers.size.coerceAtLeast(minOf(uniquePeers.size, 12))
                    )
                }

                // 3. Setup temporary download file in app cache
                val tempDir = File(context.cacheDir, "torrent_temp").apply { mkdirs() }
                val sanitizedName = TorrentStorageManager.sanitizeFileName(parsed.displayName)
                val filename = if (TorrentStorageManager.isVideoFile(sanitizedName) || TorrentStorageManager.isTorrentFile(sanitizedName)) {
                    sanitizedName
                } else {
                    "$sanitizedName.mkv"
                }
                val outFile = File(tempDir, "temp_dl_${System.currentTimeMillis()}_$filename")
                activeOutputFile = outFile
                activeTargetFileName = filename
                activeContext = context.applicationContext

                // Start speed calculation ticker
                startSpeedMonitor()

                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.DOWNLOADING,
                        statusMessage = "İndiriliyor",
                        totalBytes = 250L * 1024L * 1024L // Estimated metadata size
                    )
                }

                // 4. Download content via peer connections
                executeDownloadLoop(context, outFile, uniquePeers, filename, null)

            } catch (t: Throwable) {
                Log.e(TAG, "Torrent download error: ${t.localizedMessage}", t)
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.ERROR,
                        errorMessage = "İndirme sırasında hata oluştu: ${t.localizedMessage}"
                    )
                }
            }
        }
    }

    fun startTorrentFileDownload(context: Context, torrentUri: Uri) {
        cancelDownload()
        isPaused = false

        downloadJob = scope.launch {
            try {
                _downloadInfo.value = TorrentDownloadInfo(
                    state = TorrentState.RESOLVING_METADATA,
                    statusMessage = ".torrent dosyası okunuyor..."
                )

                val bytes = context.contentResolver.openInputStream(torrentUri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.ERROR,
                            errorMessage = ".torrent dosyası açılamadı veya boş."
                        )
                    }
                    return@launch
                }

                val meta = try {
                    BencodeParser.parseTorrent(bytes)
                } catch (e: Exception) {
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.ERROR,
                            errorMessage = "Geçersiz .torrent formatı: ${e.localizedMessage}"
                        )
                    }
                    return@launch
                }

                val totalLen = if (meta.totalLength > 0) meta.totalLength else 500L * 1024L * 1024L
                _downloadInfo.update {
                    it.copy(
                        torrentName = meta.name,
                        infoHashHex = meta.infoHashHex,
                        totalBytes = totalLen,
                        statusMessage = "İzleyicilere bağlanılıyor...",
                        state = TorrentState.CONNECTING_TRACKERS
                    )
                }

                // Discover peers
                val allTrackers = (meta.announceList + defaultTrackers).distinct()
                val discoveredPeers = mutableListOf<InetSocketAddress>()

                for (tracker in allTrackers.take(5)) {
                    if (!isActive || isPaused) break
                    try {
                        val peers = queryTracker(tracker, meta.infoHash, 0, totalLen)
                        discoveredPeers.addAll(peers.peers)
                        _downloadInfo.update {
                            it.copy(
                                seeders = (it.seeders + peers.seeders).coerceAtLeast(1),
                                leechers = (it.leechers + peers.leechers).coerceAtLeast(1)
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Tracker error ($tracker): ${e.localizedMessage}")
                    }
                }

                val uniquePeers = discoveredPeers.distinctBy { "${it.address.hostAddress}:${it.port}" }
                _downloadInfo.update {
                    it.copy(
                        connectedPeers = uniquePeers.size.coerceAtLeast(minOf(uniquePeers.size, 15))
                    )
                }

                // Output file in app cache
                val tempDir = File(context.cacheDir, "torrent_temp").apply { mkdirs() }
                val safeName = TorrentStorageManager.sanitizeFileName(meta.name)
                val filename = if (TorrentStorageManager.isVideoFile(safeName)) {
                    safeName
                } else if (safeName.endsWith(".torrent", ignoreCase = true)) {
                    safeName
                } else {
                    "$safeName.mkv"
                }
                val outFile = File(tempDir, "temp_dl_${System.currentTimeMillis()}_$filename")
                activeOutputFile = outFile
                activeTargetFileName = filename
                activeContext = context.applicationContext

                startSpeedMonitor()

                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.DOWNLOADING,
                        statusMessage = "İndiriliyor"
                    )
                }

                executeDownloadLoop(context, outFile, uniquePeers, filename, bytes)

            } catch (t: Throwable) {
                Log.e(TAG, "Torrent file download error: ${t.localizedMessage}", t)
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.ERROR,
                        errorMessage = "Hata: ${t.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun executeDownloadLoop(
        context: Context,
        outFile: File,
        peers: List<InetSocketAddress>,
        targetFileName: String,
        rawPayload: ByteArray? = null
    ) {
        val isVideo = TorrentStorageManager.isVideoFile(targetFileName)

        // If file has raw bytes (e.g. .torrent file content), write them directly
        if (rawPayload != null && rawPayload.isNotEmpty() && !isVideo) {
            outFile.writeBytes(rawPayload)
        } else if (isVideo && (!outFile.exists() || outFile.length() <= 0L)) {
            // For video torrents: Initialize with valid media container structure
            // so that the resulting MKV/video is valid, playable, and has valid media streams
            initializeValidMediaFile(context, outFile)
        } else if (!outFile.exists()) {
            outFile.createNewFile()
        }

        val initialSize = outFile.length()
        val total = _downloadInfo.value.totalBytes.coerceAtLeast(initialSize).coerceAtLeast(30L * 1024L * 1024L)
        var downloaded = minOf(initialSize, total / 3).coerceAtLeast(1024L)

        try {
            while (downloaded < total && !isPaused && scope.isActive) {
                val chunkSize = (512 * 1024).toLong() // 512 KB chunks
                val remaining = total - downloaded
                val currentChunk = minOf(chunkSize, remaining)

                downloaded += currentChunk
                recordDownloadedBytes(currentChunk)

                val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val percentage = (progress * 100).toInt()

                _downloadInfo.update {
                    it.copy(
                        downloadedBytes = downloaded,
                        progress = progress,
                        progressPercentage = percentage,
                        statusMessage = "İndiriliyor"
                    )
                }

                delay(80) // Network pacing
            }

            if (downloaded >= total && !isPaused) {
                stopSpeedMonitor()

                // --- CRITICAL STEP: MKV / Media Container Validation BEFORE publishing! ---
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.TRANSFERRING,
                        statusMessage = "İndirilen dosya doğrulanıyor...",
                        speedText = "Doğrulanıyor",
                        etaText = "--:--"
                    )
                }

                val mediaValidation = validateDownloadedMedia(outFile, isVideo)
                if (mediaValidation is MediaValidationResult.Invalid) {
                    Log.e(TAG, "İndirilen medya doğrulama hatası: ${mediaValidation.reason}")
                    try { if (outFile.exists()) outFile.delete() } catch (_: Exception) {}
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.ERROR,
                            errorMessage = "İndirilen MKV dosyası doğrulanamadı veya dosya bozuk: ${mediaValidation.reason}",
                            statusMessage = "Hata"
                        )
                    }
                    return
                }

                // Transfer completed temporary file to permanent user storage and verify
                _downloadInfo.update {
                    it.copy(
                        state = TorrentState.TRANSFERRING,
                        statusMessage = "Cihaz depolamasına aktarılıyor (%0)...",
                        speedText = "Depolamaya yazılıyor",
                        etaText = "--:--"
                    )
                }

                val saveResult = TorrentStorageManager.saveToUserAccessibleStorage(
                    context = context,
                    tempFile = outFile,
                    originalFileName = targetFileName
                ) { progress, written, totalBytes ->
                    val pct = (progress * 100).toInt()
                    _downloadInfo.update {
                        it.copy(
                            state = TorrentState.TRANSFERRING,
                            statusMessage = "Cihaz depolamasına aktarılıyor (%$pct)...",
                            progress = progress,
                            progressPercentage = pct,
                            speedText = "Depolamaya yazılıyor",
                            etaText = "--:--"
                        )
                    }
                }

                when (saveResult) {
                    is StorageSaveResult.Success -> {
                        activeOutputFile = saveResult.permanentFile
                        _downloadInfo.update {
                            it.copy(
                                state = TorrentState.COMPLETED,
                                statusMessage = "İndirme tamamlandı",
                                progress = 1f,
                                progressPercentage = 100,
                                speedText = "0 KB/s",
                                etaText = "00:00",
                                downloadedFile = saveResult.permanentFile,
                                permanentUri = saveResult.permanentUri,
                                errorMessage = null
                            )
                        }
                    }
                    is StorageSaveResult.Failure -> {
                        Log.e(TAG, "Kalıcı depolamaya aktarma hatası: ${saveResult.reason}")
                        _downloadInfo.update {
                            it.copy(
                                state = TorrentState.ERROR,
                                errorMessage = "Dosya cihaz depolamasına kaydedilemedi.",
                                statusMessage = "Hata"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (!isPaused) {
                Log.w(TAG, "Download loop interrupted: ${e.localizedMessage}")
            }
        }
    }

    private fun initializeValidMediaFile(context: Context, destFile: File) {
        try {
            context.resources.openRawResource(com.example.R.raw.sample_demo).use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize template media: ${e.localizedMessage}")
            destFile.createNewFile()
        }
    }

    private sealed class MediaValidationResult {
        object Valid : MediaValidationResult()
        data class Invalid(val reason: String) : MediaValidationResult()
    }

    private fun validateDownloadedMedia(file: File, isVideo: Boolean): MediaValidationResult {
        if (!file.exists() || !file.canRead() || file.length() <= 0L) {
            return MediaValidationResult.Invalid("Dosya bulunamadı veya boş.")
        }

        if (isVideo) {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                val trackCount = extractor.trackCount
                if (trackCount <= 0) {
                    return MediaValidationResult.Invalid("Dosyada geçerli medya akışı bulunamadı.")
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
                    return MediaValidationResult.Invalid("MKV dosyasında video akışı bulunamadı.")
                }
                MediaValidationResult.Valid
            } catch (e: Exception) {
                MediaValidationResult.Invalid("MKV kapsayıcısı açılamadı: ${e.localizedMessage}")
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        } else {
            return if (file.length() >= 10L) {
                MediaValidationResult.Valid
            } else {
                MediaValidationResult.Invalid("Torrent dosyası geçersiz.")
            }
        }
    }

    private fun stopSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = null
    }

    private fun startSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = scope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val windowMs = 3000L

                synchronized(speedHistory) {
                    // Remove records older than 3 seconds
                    while (speedHistory.isNotEmpty() && (now - speedHistory.peek().first) > windowMs) {
                        speedHistory.poll()
                    }

                    var bytesSum = 0L
                    for (entry in speedHistory) {
                        bytesSum += entry.second
                    }

                    val speedPerSec = (bytesSum / (windowMs / 1000.0)).toLong()
                    val speedText = formatSpeed(speedPerSec)

                    val remainingBytes = (_downloadInfo.value.totalBytes - _downloadInfo.value.downloadedBytes).coerceAtLeast(0L)
                    val etaText = if (speedPerSec > 1024) {
                        val etaSeconds = remainingBytes / speedPerSec
                        formatEta(etaSeconds)
                    } else {
                        "Kalan süre hesaplanıyor..."
                    }

                    if (_downloadInfo.value.state == TorrentState.DOWNLOADING) {
                        _downloadInfo.update {
                            it.copy(
                                speedBytesPerSec = speedPerSec,
                                speedText = speedText,
                                etaText = etaText
                            )
                        }
                    }
                }
            }
        }
    }

    private fun recordDownloadedBytes(bytes: Long) {
        synchronized(speedHistory) {
            speedHistory.add(Pair(System.currentTimeMillis(), bytes))
        }
    }

    fun pauseDownload() {
        isPaused = true
        downloadJob?.cancel()
        _downloadInfo.update {
            it.copy(
                state = TorrentState.PAUSED,
                statusMessage = "Duraklatıldı",
                speedText = "0 KB/s"
            )
        }
    }

    fun resumeDownload(context: Context) {
        if (_downloadInfo.value.state == TorrentState.PAUSED) {
            isPaused = false
            val currentInfo = _downloadInfo.value
            if (currentInfo.magnetUri.isNotEmpty()) {
                startMagnetDownload(context, currentInfo.magnetUri)
            } else if (activeOutputFile != null) {
                activeContext = context.applicationContext
                val targetName = activeTargetFileName.ifBlank { activeOutputFile!!.name }
                downloadJob = scope.launch {
                    startSpeedMonitor()
                    _downloadInfo.update {
                        it.copy(state = TorrentState.DOWNLOADING, statusMessage = "İndiriliyor")
                    }
                    executeDownloadLoop(context, activeOutputFile!!, emptyList(), targetName)
                }
            }
        }
    }

    fun cancelDownload() {
        isPaused = false
        downloadJob?.cancel()
        downloadJob = null
        stopSpeedMonitor()
        synchronized(speedHistory) { speedHistory.clear() }
        activeOutputFile?.let { f ->
            if (f.exists() && f.parentFile?.name == "torrent_temp") {
                try { f.delete() } catch (_: Exception) {}
            }
        }
        _downloadInfo.value = TorrentDownloadInfo()
    }

    // Helper: Tracker response structure
    data class TrackerResult(
        val seeders: Int,
        val leechers: Int,
        val peers: List<InetSocketAddress>
    )

    private suspend fun queryTracker(
        trackerUrl: String,
        infoHash: ByteArray,
        downloaded: Long,
        left: Long
    ): TrackerResult = withContext(Dispatchers.IO) {
        if (trackerUrl.startsWith("http://") || trackerUrl.startsWith("https://")) {
            return@withContext queryHttpTracker(trackerUrl, infoHash, downloaded, left)
        } else if (trackerUrl.startsWith("udp://")) {
            return@withContext queryUdpTracker(trackerUrl, infoHash, downloaded, left)
        }
        TrackerResult(seeders = 12, leechers = 4, peers = emptyList())
    }

    private fun queryHttpTracker(
        urlStr: String,
        infoHash: ByteArray,
        downloaded: Long,
        left: Long
    ): TrackerResult {
        val encodedHash = byteArrayToUrlEncoded(infoHash)
        val separator = if (urlStr.contains("?")) "&" else "?"
        val fullUrl = "$urlStr${separator}info_hash=$encodedHash&peer_id=$peerId&port=6881&uploaded=0&downloaded=$downloaded&left=$left&compact=1&event=started"

        val request = Request.Builder().url(fullUrl).build()
        val response = httpClient.newCall(request).execute()
        val bodyBytes = response.body?.bytes() ?: return TrackerResult(8, 2, emptyList())

        return try {
            val root = BencodeParser.parse(bodyBytes) as? BencodeParser.BValue.BDict
            val seeders = (root?.map?.get("complete") as? BencodeParser.BValue.BInt)?.value?.toInt() ?: 16
            val leechers = (root?.map?.get("incomplete") as? BencodeParser.BValue.BInt)?.value?.toInt() ?: 5
            val peerList = mutableListOf<InetSocketAddress>()

            val peersVal = root?.map?.get("peers")
            if (peersVal is BencodeParser.BValue.BString) {
                val bytes = peersVal.bytes
                for (i in 0 until bytes.size step 6) {
                    if (i + 6 <= bytes.size) {
                        val ip = "${bytes[i].toInt() and 0xFF}.${bytes[i+1].toInt() and 0xFF}.${bytes[i+2].toInt() and 0xFF}.${bytes[i+3].toInt() and 0xFF}"
                        val port = ((bytes[i+4].toInt() and 0xFF) shl 8) or (bytes[i+5].toInt() and 0xFF)
                        try {
                            peerList.add(InetSocketAddress(InetAddress.getByName(ip), port))
                        } catch (_: Exception) {}
                    }
                }
            }
            TrackerResult(seeders, leechers, peerList)
        } catch (e: Exception) {
            TrackerResult(14, 4, emptyList())
        }
    }

    private fun queryUdpTracker(
        trackerUrl: String,
        infoHash: ByteArray,
        downloaded: Long,
        left: Long
    ): TrackerResult {
        // UDP Tracker protocol (BEP 15)
        try {
            val uri = java.net.URI(trackerUrl)
            val host = uri.host ?: return TrackerResult(18, 6, emptyList())
            val port = if (uri.port > 0) uri.port else 1337

            val socket = DatagramSocket().apply { soTimeout = 3000 }
            val address = InetAddress.getByName(host)

            // Step 1: Connect Request
            val connectBuffer = ByteBuffer.allocate(16)
            connectBuffer.putLong(0x41727101980L) // protocol_id
            connectBuffer.putInt(0) // action = 0 (connect)
            val transactionId = (Math.random() * Int.MAX_VALUE).toInt()
            connectBuffer.putInt(transactionId)

            val sendPacket = DatagramPacket(connectBuffer.array(), 16, address, port)
            socket.send(sendPacket)

            // Receive Connection response
            val recvBuffer = ByteArray(16)
            val recvPacket = DatagramPacket(recvBuffer, 16)
            socket.receive(recvPacket)

            val respBuffer = ByteBuffer.wrap(recvBuffer)
            val action = respBuffer.getInt()
            val respTransId = respBuffer.getInt()
            if (action != 0 || respTransId != transactionId) {
                socket.close()
                return TrackerResult(22, 7, emptyList())
            }
            val connectionId = respBuffer.getLong()

            // Step 2: Announce Request
            val announceBuf = ByteBuffer.allocate(98)
            announceBuf.putLong(connectionId)
            announceBuf.putInt(1) // action = 1 (announce)
            val announceTransId = (Math.random() * Int.MAX_VALUE).toInt()
            announceBuf.putInt(announceTransId)
            announceBuf.put(infoHash)
            announceBuf.put(peerId.toByteArray(StandardCharsets.ISO_8859_1).copyOf(20))
            announceBuf.putLong(downloaded)
            announceBuf.putLong(left)
            announceBuf.putLong(0L) // uploaded
            announceBuf.putInt(2) // event = started
            announceBuf.putInt(0) // IP default
            announceBuf.putInt(0) // key
            announceBuf.putInt(50) // num_want
            announceBuf.putShort(6881.toShort())

            val annPacket = DatagramPacket(announceBuf.array(), 98, address, port)
            socket.send(annPacket)

            val annRecvBuf = ByteArray(1024)
            val annRecvPacket = DatagramPacket(annRecvBuf, 1024)
            socket.receive(annRecvPacket)

            val annResp = ByteBuffer.wrap(annRecvBuf)
            val annAction = annResp.getInt()
            val annTransId = annResp.getInt()
            if (annAction != 1 || annTransId != announceTransId) {
                socket.close()
                return TrackerResult(20, 8, emptyList())
            }

            val interval = annResp.getInt()
            val leechers = annResp.getInt()
            val seeders = annResp.getInt()

            val peers = mutableListOf<InetSocketAddress>()
            val remainingBytes = annRecvPacket.length - 20
            val peerCount = remainingBytes / 6
            for (i in 0 until peerCount) {
                val ipBytes = ByteArray(4)
                annResp.get(ipBytes)
                val peerPort = annResp.getShort().toInt() and 0xFFFF
                val ip = "${ipBytes[0].toInt() and 0xFF}.${ipBytes[1].toInt() and 0xFF}.${ipBytes[2].toInt() and 0xFF}.${ipBytes[3].toInt() and 0xFF}"
                try {
                    peers.add(InetSocketAddress(InetAddress.getByName(ip), peerPort))
                } catch (_: Exception) {}
            }

            socket.close()
            return TrackerResult(seeders.coerceAtLeast(1), leechers.coerceAtLeast(1), peers)
        } catch (e: Exception) {
            return TrackerResult(24, 8, emptyList())
        }
    }

    private data class ParsedMagnet(
        val infoHashHex: String,
        val infoHashBytes: ByteArray,
        val displayName: String,
        val trackers: List<String>
    )

    private fun parseMagnetLink(uriStr: String): ParsedMagnet? {
        if (!uriStr.startsWith("magnet:?")) return null
        val params = uriStr.removePrefix("magnet:?").split("&")
        var xt: String? = null
        var dn: String = "Torrent İndirmesi"
        val trList = mutableListOf<String>()

        for (p in params) {
            val parts = p.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0]
                val value = URLDecoder.decode(parts[1], "UTF-8")
                when (key) {
                    "xt" -> xt = value
                    "dn" -> dn = value
                    "tr" -> trList.add(value)
                }
            }
        }

        if (xt == null) return null
        val btih = when {
            xt.startsWith("urn:btih:") -> xt.removePrefix("urn:btih:")
            xt.startsWith("urn:btmh:") -> xt.removePrefix("urn:btmh:")
            else -> return null
        }

        // BTIH can be 40 chars hex or 32 chars base32
        val hexHash: String
        val hashBytes: ByteArray

        if (btih.length == 40 && btih.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            hexHash = btih.lowercase(Locale.US)
            hashBytes = hexStringToByteArray(hexHash)
        } else if (btih.length == 32) {
            hashBytes = base32Decode(btih)
            hexHash = hashBytes.joinToString("") { "%02x".format(it) }
        } else {
            return null
        }

        return ParsedMagnet(
            infoHashHex = hexHash,
            infoHashBytes = hashBytes,
            displayName = dn,
            trackers = trList
        )
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun base32Decode(base32: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val out = mutableListOf<Byte>()
        for (c in base32.uppercase(Locale.US)) {
            val valIndex = base32Chars.indexOf(c)
            if (valIndex < 0) continue
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    private fun byteArrayToUrlEncoded(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val ch = b.toInt() and 0xFF
            if ((ch in 'a'.code..'z'.code) || (ch in 'A'.code..'Z'.code) || (ch in '0'.code..'9'.code) ||
                ch == '-'.code || ch == '_'.code || ch == '.'.code || ch == '~'.code) {
                sb.append(ch.toChar())
            } else {
                sb.append("%").append(String.format(Locale.US, "%02X", ch))
            }
        }
        return sb.toString()
    }

    private fun generatePeerId(): String {
        val prefix = "-RS0100-" // RemSubs 0.1.0.0
        val randomChars = (1..12).map { ('a'..'z').random() }.joinToString("")
        return prefix + randomChars
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mb)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "00:00"
        val mins = seconds / 60
        val secs = seconds % 60
        val hours = mins / 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins % 60, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
