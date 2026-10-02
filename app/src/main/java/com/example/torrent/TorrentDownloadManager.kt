package com.example.torrent

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.util.FontManager
import com.example.util.MediaStorageManager
import com.frostwire.jlibtorrent.AddTorrentParams
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionHandle
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.FileErrorAlert
import com.frostwire.jlibtorrent.alerts.TorrentAlert
import com.frostwire.jlibtorrent.alerts.TorrentErrorAlert
import com.frostwire.jlibtorrent.swig.error_code
import com.frostwire.jlibtorrent.swig.settings_pack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * BitTorrent client on FrostWire jlibtorrent 2.0 (libtorrent 2.0).
 *
 * jlibtorrent 2.0.11.0's SessionManager.download(...) helpers are broken and must not be used:
 * - download(magnet, dir, flags) calls flags.or_(null), a NullPointerException for every magnet;
 * - download(ti, dir, resume, priorities, ...) calls prioritize_files on the invalid handle
 *   returned by find_torrent, an "invalid torrent handle used" error for every .torrent file.
 * Torrents are added with session.add_torrent() instead, which also hands back the handle
 * synchronously, so no info-hash lookup (hex vs. SubsPlease's Base32) is needed.
 *
 * Only the chosen video file is downloaded; when it completes the torrent is removed from the
 * session (no seeding on mobile) and the file stays in app storage for the editor.
 */
object TorrentDownloadManager {
    private const val TAG = "TorrentDownloadManager"
    private const val MONITOR_INTERVAL_MS = 1_000L
    private const val NO_PEER_HINT_AFTER_MS = 45_000L
    private const val METADATA_TIMEOUT_MS = 4 * 60_000L
    private const val MAX_TORRENT_FILE_BYTES = 16 * 1024 * 1024

    private val _downloadInfo = MutableStateFlow(TorrentDownloadInfo())
    val downloadInfo: StateFlow<TorrentDownloadInfo> = _downloadInfo.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    /**
     * Bumped by every start/cancel; work belonging to an older generation must not touch the
     * visible state. Bumping and publishing both happen under [stateLock], so a cancel can never
     * be overwritten by a monitor tick that was already running.
     */
    private val generation = AtomicInteger(0)
    private val stateLock = Any()

    @Volatile private var sessionManager: SessionManager? = null
    @Volatile private var active: ActiveDownload? = null
    @Volatile private var monitorJob: Job? = null
    @Volatile private var lastRequest: DownloadRequest? = null

    private sealed class DownloadRequest {
        abstract val label: String?
        abstract val sourceLabel: String

        class Magnet(val uri: String, override val label: String?, override val sourceLabel: String) : DownloadRequest()
        class TorrentFile(val bytes: ByteArray, override val label: String?) : DownloadRequest() {
            override val sourceLabel: String = ".torrent"
        }
    }

    private class ActiveDownload(
        val generation: Int,
        val handle: TorrentHandle,
        val infoHashHex: String,
        val saveDir: File,
        val startedAtMs: Long
    ) {
        @Volatile var files: List<TorrentFileEntry> = emptyList()
        @Volatile var totalFileCount: Int = 0
        @Volatile var selectedIndex: Int = -1
        @Volatile var metadataApplied = false
        @Volatile var awaitingSelection = false
        @Volatile var pausedByUser = false
        @Volatile var sawPeer = false

        /** Set once the download completed, failed or was cancelled; nothing may touch it afterwards. */
        val finished = AtomicBoolean(false)
    }

    /**
     * Replaces the visible state and detaches the current download. Returns the new generation
     * and the detached download, which the caller must remove from the session.
     */
    private fun beginGeneration(state: TorrentDownloadInfo, request: DownloadRequest?): Pair<Int, ActiveDownload?> {
        val result = synchronized(stateLock) {
            val gen = generation.incrementAndGet()
            lastRequest = request
            val previous = active
            active = null
            _downloadInfo.value = state
            gen to previous
        }
        monitorJob?.cancel()
        return result
    }

    /** Updates the visible state only while [download] is still the one the user is looking at. */
    private fun publish(
        download: ActiveDownload,
        allowFinished: Boolean = false,
        transform: (TorrentDownloadInfo) -> TorrentDownloadInfo
    ) {
        synchronized(stateLock) {
            if (download.generation == generation.get() && (allowFinished || !download.finished.get())) {
                _downloadInfo.update(transform)
            }
        }
    }

    /** Updates the visible state only if no start/cancel happened since generation [gen]. */
    private fun publishIf(gen: Int, transform: (TorrentDownloadInfo) -> TorrentDownloadInfo) {
        synchronized(stateLock) {
            if (gen == generation.get()) _downloadInfo.update(transform)
        }
    }

    private fun publishError(gen: Int, type: TorrentErrorType, message: String, canRetry: Boolean) {
        publishIf(gen) { errorState(it, type, message, canRetry) }
    }

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.FILE_ERROR.swig()
        )

        override fun alert(alert: Alert<*>) {
            val download = active ?: return
            val alertHandle = (alert as? TorrentAlert<*>)?.handle() ?: return
            if (!alertHandle.isValid || alertHandle.infoHash().toHex() != download.infoHashHex) return

            when (alert.type()) {
                AlertType.METADATA_RECEIVED -> scope.launch { applyMetadata(download) }
                AlertType.TORRENT_FINISHED -> scope.launch { completeIfDone(download) }
                AlertType.TORRENT_ERROR -> {
                    val message = (alert as TorrentErrorAlert).error().message()
                    Log.e(TAG, "torrent_error: $message")
                    scope.launch { failDownload(download, classifyErrorMessage(message)) }
                }
                AlertType.FILE_ERROR -> {
                    val fileAlert = alert as FileErrorAlert
                    val message = "${fileAlert.error().message()} (${fileAlert.filename()})"
                    Log.e(TAG, "file_error: $message")
                    scope.launch { failDownload(download, classifyErrorMessage(message)) }
                }
                else -> Unit
            }
        }
    }

    /**
     * Starts downloading a magnet link. [displayName] and [sourceLabel] only affect what the
     * UI shows until the torrent's metadata arrives.
     */
    fun startMagnetDownload(
        context: Context,
        magnetUri: String,
        displayName: String? = null,
        sourceLabel: String = "Magnet"
    ) {
        val magnet = MagnetLinks.normalize(magnetUri)
        if (!MagnetLinks.isMagnet(magnet)) {
            val (_, previous) = beginGeneration(
                errorState(
                    TorrentDownloadInfo(),
                    TorrentErrorType.INVALID_MAGNET,
                    "Bu bir magnet bağlantısı değil. Bağlantı \"magnet:?xt=urn:btih:...\" ile başlamalı.",
                    canRetry = false
                ),
                request = null
            )
            detach(previous)
            return
        }
        start(context, DownloadRequest.Magnet(magnet, displayName ?: MagnetLinks.displayName(magnet), sourceLabel))
    }

    /** Starts downloading from a user-picked .torrent file. */
    fun startTorrentFileDownload(context: Context, torrentUri: Uri) {
        val appContext = context.applicationContext
        val (gen, previous) = beginGeneration(
            TorrentDownloadInfo(
                state = TorrentState.RESOLVING_METADATA,
                sourceLabel = ".torrent",
                statusMessage = ".torrent dosyası okunuyor..."
            ),
            request = null
        )
        detach(previous)
        scope.launch {
            val bytes = try {
                readTorrentBytes(appContext, torrentUri)
            } catch (t: Throwable) {
                Log.e(TAG, ".torrent okunamadı", t)
                publishError(gen, TorrentErrorType.STORAGE_ERROR, t.message ?: "Seçilen .torrent dosyası okunamadı.", canRetry = false)
                return@launch
            }

            val head = String(bytes, 0, minOf(bytes.size, 32), Charsets.ISO_8859_1).trimStart().lowercase(Locale.ROOT)
            if (head.startsWith("<!doc") || head.startsWith("<html")) {
                publishError(
                    gen,
                    TorrentErrorType.INVALID_TORRENT,
                    "Seçilen dosya .torrent değil, bir web sayfası (HTML). Torrent dosyasını tarayıcıdan doğrudan indirip tekrar seçin.",
                    canRetry = false
                )
                return@launch
            }
            if (!head.startsWith("d")) {
                publishError(gen, TorrentErrorType.INVALID_TORRENT, "Seçilen dosya geçerli bir .torrent dosyası değil.", canRetry = false)
                return@launch
            }
            if (gen != generation.get()) return@launch
            val name = FontManager.getFileName(appContext, torrentUri)?.removeSuffix(".torrent")
            start(appContext, DownloadRequest.TorrentFile(bytes, name))
        }
    }

    /** Starts the last magnet/.torrent again; partial data on disk is reused. */
    fun retryLastDownload(context: Context) {
        val request = lastRequest ?: return
        start(context, request)
    }

    fun selectVideoFile(fileName: String) {
        val download = active ?: return
        val entry = download.files.firstOrNull { it.path == fileName } ?: return
        if (entry.index == download.selectedIndex && !download.awaitingSelection) return
        scope.launch {
            try {
                selectFile(download, entry.index)
                Log.i(TAG, "Seçili video değişti: ${entry.path}")
            } catch (t: Throwable) {
                Log.w(TAG, "Dosya seçilemedi: ${t.message}")
            }
        }
    }

    fun pauseDownload() {
        val download = active ?: return
        download.pausedByUser = true
        scope.launch { runCatching { download.handle.pause() } }
        publish(download) {
            it.copy(state = TorrentState.PAUSED, statusMessage = "Duraklatıldı", speedBytesPerSec = 0L, speedText = "0 KB/s", etaText = "--:--")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun resumeDownload(context: Context? = null) {
        val download = active ?: return
        download.pausedByUser = false
        if (!download.awaitingSelection) scope.launch { runCatching { download.handle.resume() } }
        publish(download) {
            it.copy(
                state = if (download.metadataApplied) TorrentState.DOWNLOADING else TorrentState.RESOLVING_METADATA,
                statusMessage = "Devam ediliyor..."
            )
        }
        if (monitorJob?.isActive != true) startMonitor(download)
    }

    /**
     * Stops the current download and resets the card. Partial data of an unfinished download is
     * deleted; a completed file is never touched (the torrent is already out of the session then).
     */
    fun cancelDownload() {
        val (_, download) = beginGeneration(TorrentDownloadInfo(), request = null)
        if (download != null && download.finished.compareAndSet(false, true)) {
            scope.launch {
                // While libtorrent re-checks existing data the file may be a finished earlier download
                val checking = runCatching { download.handle.status(true).state() }.getOrNull()?.let {
                    it == TorrentStatus.State.CHECKING_FILES || it == TorrentStatus.State.CHECKING_RESUME_DATA
                } ?: true
                removeTorrent(download, deleteFiles = !checking)
            }
        }
    }

    /** Takes a replaced download out of the session, keeping its data so it can be resumed later. */
    private fun detach(previous: ActiveDownload?) {
        if (previous != null && previous.finished.compareAndSet(false, true)) {
            scope.launch { removeTorrent(previous, deleteFiles = false) }
        }
    }

    /** Copies the downloaded video to Movies/RemSubs (one MediaStore copy). */
    fun saveDownloadedVideoToGallery(context: Context) {
        val info = _downloadInfo.value
        val file = info.downloadedFile ?: return
        if (info.isSavingToGallery || info.permanentUri != null) return
        val appContext = context.applicationContext
        val gen = generation.get()
        publishIf(gen) { it.copy(isSavingToGallery = true, gallerySaveProgress = 0f, galleryError = null) }
        scope.launch {
            var lastPercent = -1
            val result = MediaStorageManager.publishToGallery(appContext, file) { progress, _, _ ->
                val percent = (progress * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    publishIf(gen) { it.copy(gallerySaveProgress = progress) }
                }
            }
            publishIf(gen) {
                when (result) {
                    is StorageSaveResult.Success -> it.copy(
                        isSavingToGallery = false,
                        gallerySaveProgress = 1f,
                        permanentUri = result.permanentUri
                    )
                    is StorageSaveResult.Failure -> it.copy(isSavingToGallery = false, galleryError = result.reason)
                }
            }
        }
    }

    private fun start(context: Context, request: DownloadRequest) {
        val appContext = context.applicationContext
        val (gen, previous) = beginGeneration(
            TorrentDownloadInfo(
                magnetUri = (request as? DownloadRequest.Magnet)?.uri.orEmpty(),
                torrentName = request.label ?: "Torrent İndirmesi",
                sourceLabel = request.sourceLabel,
                state = TorrentState.RESOLVING_METADATA,
                statusMessage = "BitTorrent motoru başlatılıyor..."
            ),
            request = request
        )

        scope.launch {
            startMutex.withLock {
                // Removed before adding: re-adding the same torrent must not get the old handle back
                if (previous != null && previous.finished.compareAndSet(false, true)) {
                    removeTorrent(previous, deleteFiles = false)
                }
                if (gen != generation.get()) return@withLock

                try {
                    val session = ensureSession()
                    val saveDir = downloadDirectory(appContext)
                    val params = when (request) {
                        is DownloadRequest.Magnet -> magnetParams(request.uri)
                        is DownloadRequest.TorrentFile -> torrentFileParams(request.bytes)
                    }
                    params.savePath(saveDir.absolutePath)
                    // Not auto-managed: the session queue must never pause it on its own
                    params.flags(
                        params.flags()
                            .and_(TorrentFlags.AUTO_MANAGED.inv())
                            .and_(TorrentFlags.PAUSED.inv())
                    )

                    val ec = error_code()
                    val th = session.swig().add_torrent(params.swig(), ec)
                    if (ec.value() != 0 || !th.is_valid()) {
                        throw IllegalStateException("Torrent oturuma eklenemedi: ${ec.message()}")
                    }
                    val handle = TorrentHandle(th)
                    handle.resume()

                    val download = ActiveDownload(gen, handle, handle.infoHash().toHex(), saveDir, System.currentTimeMillis())
                    val stillCurrent = synchronized(stateLock) {
                        (gen == generation.get()).also { current -> if (current) active = download }
                    }
                    if (!stillCurrent) {
                        removeTorrent(download, deleteFiles = false)
                        return@withLock
                    }
                    Log.i(TAG, "Torrent eklendi: ${download.infoHashHex} -> ${saveDir.absolutePath}")

                    publish(download) {
                        it.copy(infoHashHex = download.infoHashHex, statusMessage = "Meta veri aranıyor (izleyiciler + DHT)...")
                    }
                    if (handle.torrentFile() != null) applyMetadata(download)
                    startMonitor(download)
                } catch (t: Throwable) {
                    Log.e(TAG, "Torrent başlatılamadı", t)
                    val (type, message) = classifyError(t)
                    publishError(
                        gen,
                        type,
                        message,
                        canRetry = type != TorrentErrorType.INVALID_MAGNET && type != TorrentErrorType.INVALID_TORRENT
                    )
                }
            }
        }
    }

    private fun ensureSession(): SessionManager {
        sessionManager?.let { if (it.isRunning) return it }

        val settings = SettingsPack()
        settings.setEnableDht(true)
        settings.setEnableLsd(true)
        // Magnets put every tracker in its own tier; announce to all of them in parallel
        // instead of walking the list one dead tracker at a time.
        settings.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
        // Announces carry nothing secret; skipping certificate checks avoids HTTPS trackers
        // failing where libtorrent can't locate a CA store (Android).
        settings.validateHttpsTrackers(false)

        val params = SessionParams(settings)
        // libtorrent 2.0's default mmap storage turns a full disk into SIGBUS and misbehaves on
        // some Android file systems; plain read/write I/O fails with a normal error instead.
        params.setPosixDiskIO()

        val manager = SessionManager(false)
        manager.addListener(alertListener)
        manager.start(params)
        if (!manager.isRunning) {
            throw IllegalStateException("engine init failure: jlibtorrent session could not start")
        }
        sessionManager = manager
        Log.i(TAG, "jlibtorrent oturumu başladı (DHT + LSD, POSIX disk I/O)")
        return manager
    }

    private fun magnetParams(magnet: String): AddTorrentParams {
        val params = try {
            AddTorrentParams.parseMagnetUri(magnet)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("invalid magnet link: ${e.message}", e)
        }
        val infoHash = params.getInfoHashes().getBest()
        if (infoHash.isAllZeros) {
            throw IllegalArgumentException("invalid magnet link: info-hash (urn:btih) bulunamadı")
        }
        if (params.name().isNullOrBlank()) params.name(infoHash.toHex())
        mergeTrackers(params)
        return params
    }

    private fun torrentFileParams(bytes: ByteArray): AddTorrentParams {
        val info = TorrentInfo.bdecode(bytes)
        if (!info.isValid) throw IllegalArgumentException("invalid torrent: meta veri doğrulanamadı")
        val params = AddTorrentParams.createInstance()
        params.torrentInfo(info)
        if (!info.isPrivate) mergeTrackers(params)
        return params
    }

    /** Adds [MagnetLinks.PUBLIC_TRACKERS] and drops WebTorrent (ws/wss) trackers libtorrent can't use. */
    private fun mergeTrackers(params: AddTorrentParams) {
        val existing = params.trackers()
        val existingTiers = params.trackerTiers()
        val trackers = ArrayList<String>()
        val tiers = ArrayList<Int>()
        existing.forEachIndexed { i, url ->
            if (!url.startsWith("ws", ignoreCase = true)) {
                trackers.add(url)
                tiers.add(existingTiers.getOrNull(i) ?: 0)
            }
        }
        var nextTier = (tiers.maxOrNull() ?: -1) + 1
        for (url in MagnetLinks.PUBLIC_TRACKERS) {
            if (trackers.none { it.equals(url, ignoreCase = true) }) {
                trackers.add(url)
                tiers.add(nextTier++)
            }
        }
        params.trackers(trackers)
        params.trackerTiers(tiers)
    }

    private fun applyMetadata(download: ActiveDownload) {
        if (download.finished.get()) return
        val info = download.handle.torrentFile() ?: return
        synchronized(download) {
            if (download.metadataApplied) return
            download.metadataApplied = true
        }

        val storage = info.files()
        val files = (0 until storage.numFiles())
            .filterNot { storage.padFileAt(it) }
            .map { i ->
                val path = storage.filePath(i)
                TorrentFileEntry(index = i, path = path, sizeBytes = storage.fileSize(i), isVideo = MediaStorageManager.isVideoFile(path))
            }
        download.files = files
        download.totalFileCount = storage.numFiles()

        val videos = files.filter { it.isVideo }
        val autoPick = when {
            videos.size == 1 -> videos.first()
            videos.isEmpty() -> files.maxByOrNull { it.sizeBytes }
            else -> null
        }
        Log.i(TAG, "Meta veri alındı: ${info.name()} (${files.size} dosya, ${videos.size} video)")

        publish(download) {
            it.copy(
                torrentName = info.name(),
                infoHashHex = download.infoHashHex,
                pieceCount = info.numPieces(),
                pieceSize = info.pieceLength(),
                files = files,
                state = if (download.pausedByUser) TorrentState.PAUSED else TorrentState.DOWNLOADING,
                statusMessage = "İndiriliyor"
            )
        }

        if (autoPick != null) {
            selectFile(download, autoPick.index)
        } else {
            // A batch: download nothing until the user picks the episode they want
            download.awaitingSelection = true
            download.handle.prioritizeFiles(Array(download.totalFileCount) { Priority.IGNORE })
            publish(download) {
                it.copy(
                    awaitingFileSelection = true,
                    statusMessage = "Bu torrent ${videos.size} video içeriyor. İndirmek istediğin bölümü seç."
                )
            }
        }
    }

    private fun selectFile(download: ActiveDownload, fileIndex: Int) {
        if (download.finished.get()) return
        val entry = download.files.firstOrNull { it.index == fileIndex } ?: return
        download.handle.prioritizeFiles(
            Array(download.totalFileCount) { i -> if (i == fileIndex) Priority.FOUR else Priority.IGNORE }
        )
        download.selectedIndex = fileIndex
        download.awaitingSelection = false
        if (!download.pausedByUser) download.handle.resume()
        publish(download) {
            it.copy(
                selectedVideoFileName = entry.path,
                awaitingFileSelection = false,
                totalBytes = entry.sizeBytes,
                statusMessage = if (download.pausedByUser) "Duraklatıldı" else "İndiriliyor"
            )
        }
    }

    private fun startMonitor(download: ActiveDownload) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive && active === download && !download.finished.get()) {
                try {
                    refresh(download)
                } catch (t: Throwable) {
                    Log.w(TAG, "Torrent durumu okunamadı: ${t.message}")
                }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh(download: ActiveDownload) {
        val handle = download.handle
        if (!handle.isValid) {
            failDownload(download, TorrentErrorType.UNKNOWN to "Torrent oturumdan beklenmedik şekilde düştü.")
            return
        }
        val status = handle.status(true)
        val error = status.errorCode()
        if (error != null && error.isError) {
            failDownload(download, classifyErrorMessage(error.message()))
            return
        }
        if (!download.metadataApplied && status.hasMetadata()) applyMetadata(download)

        val peers = status.numPeers()
        if (peers > 0) download.sawPeer = true
        val seeders = maxOf(status.numSeeds(), status.numComplete())
        val leechers = maxOf(status.numPeers() - status.numSeeds(), status.numIncomplete())
        val dhtNodes = sessionManager?.dhtNodes() ?: 0L
        val elapsed = System.currentTimeMillis() - download.startedAtMs

        if (!download.metadataApplied) {
            if (!download.sawPeer && elapsed > METADATA_TIMEOUT_MS) {
                failDownload(
                    download,
                    TorrentErrorType.NO_PEERS to "4 dakikadır hiçbir eş (peer) bulunamadı. Bu torrent'i şu an paylaşan kimse olmayabilir; daha sonra tekrar dene."
                )
                return
            }
            val message = when {
                download.pausedByUser -> "Duraklatıldı"
                peers > 0 -> "Meta veri alınıyor... ($peers eş bağlı)"
                elapsed > NO_PEER_HINT_AFTER_MS -> "Henüz eş bulunamadı, aranmaya devam ediliyor (DHT: $dhtNodes düğüm)..."
                else -> "Magnet meta verisi aranıyor (izleyiciler + DHT)..."
            }
            publish(download) {
                it.copy(
                    state = TorrentState.RESOLVING_METADATA,
                    statusMessage = message,
                    connectedPeers = peers,
                    seeders = seeders,
                    leechers = leechers,
                    dhtNodes = dhtNodes
                )
            }
            return
        }

        if (download.awaitingSelection) {
            publish(download) { it.copy(connectedPeers = peers, seeders = seeders, leechers = leechers, dhtNodes = dhtNodes) }
            return
        }

        val wanted = status.totalWanted()
        val done = status.totalWantedDone().coerceAtMost(wanted)
        if (download.selectedIndex >= 0 && wanted > 0 && done >= wanted) {
            completeIfDone(download)
            return
        }

        val paused = download.pausedByUser
        val progress = if (wanted > 0) (done.toFloat() / wanted).coerceIn(0f, 1f) else 0f
        val rate = if (paused) 0L else status.downloadPayloadRate().toLong()
        val etaSeconds = if (rate > 1024L && wanted > done) (wanted - done) / rate else 0L
        val state = status.state()
        val message = when {
            paused -> "Duraklatıldı"
            state == TorrentStatus.State.CHECKING_FILES || state == TorrentStatus.State.CHECKING_RESUME_DATA ->
                "Daha önce inen veriler doğrulanıyor..."
            peers == 0 -> "Bağlı eş yok, kaynak bekleniyor..."
            else -> "İndiriliyor"
        }
        publish(download) {
            it.copy(
                state = if (paused) TorrentState.PAUSED else TorrentState.DOWNLOADING,
                statusMessage = message,
                totalBytes = wanted,
                downloadedBytes = done,
                progress = progress,
                progressPercentage = (progress * 100).toInt(),
                speedBytesPerSec = rate,
                speedText = formatSpeed(rate),
                etaText = if (etaSeconds > 0) formatEta(etaSeconds) else "--:--",
                connectedPeers = peers,
                seeders = seeders,
                leechers = leechers,
                dhtNodes = dhtNodes
            )
        }
    }

    private suspend fun completeIfDone(download: ActiveDownload) {
        if (download.selectedIndex < 0 || download.awaitingSelection || download.finished.get()) return
        if (!download.handle.isValid) return
        val status = download.handle.status(true)
        val wanted = status.totalWanted()
        if (wanted <= 0L || status.totalWantedDone() < wanted) return
        if (!download.finished.compareAndSet(false, true)) return

        val entry = download.files.first { it.index == download.selectedIndex }
        val file = File(download.saveDir, entry.path)
        publish(download, allowFinished = true) {
            it.copy(
                state = TorrentState.TRANSFERRING,
                statusMessage = "Dosya hazırlanıyor...",
                progress = 1f,
                progressPercentage = 100,
                downloadedBytes = wanted,
                speedBytesPerSec = 0L,
                speedText = "0 KB/s",
                etaText = "00:00"
            )
        }

        // Stop seeding and let libtorrent close the file before anyone else opens it
        removeTorrent(download, deleteFiles = false)
        var waitedMs = 0
        while (file.length() < entry.sizeBytes && waitedMs < 5_000) {
            delay(250)
            waitedMs += 250
        }
        synchronized(stateLock) {
            if (active === download) active = null
        }

        if (!file.isFile || file.length() < entry.sizeBytes) {
            Log.e(TAG, "Tamamlanan dosya eksik: ${file.absolutePath} (${file.length()} / ${entry.sizeBytes})")
            publishError(
                download.generation,
                TorrentErrorType.STORAGE_ERROR,
                "İndirme bitti ama dosya diskte eksik görünüyor: ${entry.displayName}",
                canRetry = true
            )
            return
        }

        Log.i(TAG, "İndirme tamamlandı: ${file.absolutePath} (${file.length()} bayt)")
        publish(download, allowFinished = true) {
            it.copy(
                state = TorrentState.COMPLETED,
                statusMessage = "İndirme tamamlandı",
                downloadedFile = file,
                downloadedBytes = entry.sizeBytes,
                totalBytes = entry.sizeBytes,
                progress = 1f,
                progressPercentage = 100,
                connectedPeers = 0,
                errorMessage = null,
                errorType = null
            )
        }
    }

    private fun failDownload(download: ActiveDownload, error: Pair<TorrentErrorType, String>) {
        if (!download.finished.compareAndSet(false, true)) return
        // Keep partial data so "Tekrar Dene" continues where it stopped
        removeTorrent(download, deleteFiles = false)
        synchronized(stateLock) {
            if (active === download) active = null
        }
        publishError(download.generation, error.first, error.second, canRetry = true)
    }

    private fun errorState(
        current: TorrentDownloadInfo,
        type: TorrentErrorType,
        message: String,
        canRetry: Boolean
    ): TorrentDownloadInfo = current.copy(
        state = TorrentState.ERROR,
        errorType = type,
        errorMessage = message,
        statusMessage = "Hata: $message",
        canRetry = canRetry && lastRequest != null,
        speedBytesPerSec = 0L,
        speedText = "0 KB/s",
        etaText = "--:--"
    )

    private fun removeTorrent(download: ActiveDownload, deleteFiles: Boolean) {
        try {
            val manager = sessionManager ?: return
            if (!download.handle.isValid) return
            if (deleteFiles) {
                manager.remove(download.handle, SessionHandle.DELETE_FILES)
            } else {
                manager.remove(download.handle)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Torrent oturumdan kaldırılamadı: ${t.message}")
        }
    }

    private fun downloadDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("torrent_downloads") ?: File(context.filesDir, "torrent_downloads")
        dir.mkdirs()
        return dir
    }

    private fun readTorrentBytes(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Seçilen .torrent dosyası açılamadı.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_TORRENT_FILE_BYTES) {
                    throw java.io.IOException("Seçilen dosya .torrent olamayacak kadar büyük (16 MB üstü).")
                }
            }
            if (out.size() == 0) throw java.io.IOException("Seçilen .torrent dosyası boş.")
            return out.toByteArray()
        }
    }

    fun classifyErrorMessage(message: String?): Pair<TorrentErrorType, String> {
        val msg = message ?: ""
        val lower = msg.lowercase(Locale.ROOT)
        return when {
            // 1. Native / JNI / ABI Linker errors
            lower.contains("unsatisfiedlink") || lower.contains("dlopen") || lower.contains("jlibtorrent.so") ||
            lower.contains(".so\" not found") || lower.contains("linkageerror") || lower.contains("noclassdef") ->
                Pair(TorrentErrorType.JNI_NATIVE_ERROR, "Native kütüphane (libjlibtorrent.so) hatası: ${msg.ifBlank { "ABI uyumsuzluğu veya JNI yükleme hatası" }}")

            // 2. Engine initialization failure
            lower.contains("engine init") || lower.contains("session could not start") ||
            (lower.contains("sessionmanager") && lower.contains("failed")) || lower.contains("engine failure") ->
                Pair(TorrentErrorType.ENGINE_INIT_FAILURE, "BitTorrent motoru başlatılamadı: ${msg.ifBlank { "SessionManager başlatılamadı" }}")

            // A stale handle is an engine-side problem, never a corrupt .torrent
            lower.contains("invalid torrent handle") ->
                Pair(TorrentErrorType.UNKNOWN, "Torrent oturumu geçersizleşti, tekrar deneyin.")

            // Input validation first, so words like "tracker" inside a parser message can't misroute it
            lower.contains("invalid magnet") || lower.contains("geçersiz magnet") || (lower.contains("magnet:") && lower.contains("invalid")) ->
                Pair(TorrentErrorType.INVALID_MAGNET, "Geçersiz veya desteklenmeyen magnet bağlantısı: ${msg.substringAfter(": ", msg)}")

            lower.contains("bencode") || lower.contains("invalid torrent") || lower.contains("bozuk torrent") ||
            lower.contains("can't decode") || lower.contains("unexpected end of file") ||
            lower.contains("not a valid bencode") || lower.contains("torrent file is corrupt") ->
                Pair(TorrentErrorType.INVALID_TORRENT, "Geçersiz veya bozuk .torrent içeriği: ${msg.ifBlank { "Bencode yapısı çözümlenemedi" }}")

            // 3. Storage and permissions
            lower.contains("permission") || lower.contains("denied") || lower.contains("securityexception") ->
                Pair(TorrentErrorType.PERMISSION, "Depolama izni reddedildi: $msg")

            lower.contains("no space") || (lower.contains("space") && lower.contains("full")) || lower.contains("disk full") ->
                Pair(TorrentErrorType.STORAGE_FULL, "Cihaz depolama alanı yetersiz")

            lower.contains("filenotfound") || lower.contains("ioexception") || lower.contains("read error") || lower.contains("write error") ->
                Pair(TorrentErrorType.STORAGE_ERROR, "Depolama veya dosya I/O hatası: $msg")

            // 4. Network and socket
            lower.contains("connection refused") || lower.contains("network unreachable") ||
            (lower.contains("timed out") && lower.contains("socket")) || lower.contains("unknownhost") ||
            lower.contains("connection reset") || lower.contains("econnreset") || lower.contains("econnrefused") ->
                Pair(TorrentErrorType.NETWORK_ERROR, "Ağ bağlantı hatası: $msg")

            // 5. Metadata timeout
            lower.contains("metadata") || lower.contains("meta verisi") ->
                Pair(TorrentErrorType.METADATA_TIMEOUT, "Metadata zaman aşımı (Tracker/Peer yanıt vermedi)")

            // 6. Peers
            lower.contains("no peer") || lower.contains("zero peer") || lower.contains("no seeder") || lower.contains("zero seed") ->
                Pair(TorrentErrorType.NO_PEERS, "Kullanılabilir eş (peer) veya seeder bulunamadı")

            // 7. Trackers
            lower.contains("tracker") ->
                Pair(TorrentErrorType.TRACKER_ERROR, "İzleyici (Tracker) bağlantı hatası: $msg")

            // 8. DHT
            lower.contains("dht") ->
                Pair(TorrentErrorType.DHT_ERROR, "DHT ağı yanıt vermedi: $msg")

            // 9. Generic fallback - preserve the real message
            else ->
                Pair(TorrentErrorType.UNKNOWN, msg.ifBlank { "Bilinmeyen indirme hatası" })
        }
    }

    fun classifyError(t: Throwable): Pair<TorrentErrorType, String> {
        val rawMsg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
        return when {
            t is UnsatisfiedLinkError || t is LinkageError || t is NoClassDefFoundError -> {
                Pair(TorrentErrorType.JNI_NATIVE_ERROR, "Native (JNI/libtorrent) kütüphane hatası: $rawMsg")
            }
            t is java.io.FileNotFoundException -> {
                Pair(TorrentErrorType.STORAGE_ERROR, "Dosya bulunamadı: $rawMsg")
            }
            t is java.net.SocketException || t is java.net.UnknownHostException || t is java.net.ConnectException -> {
                Pair(TorrentErrorType.NETWORK_ERROR, "Ağ veya bağlantı hatası: $rawMsg")
            }
            t is SecurityException -> {
                Pair(TorrentErrorType.PERMISSION, "Depolama veya dosya erişim izni reddedildi: $rawMsg")
            }
            else -> classifyErrorMessage(rawMsg)
        }
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
}
