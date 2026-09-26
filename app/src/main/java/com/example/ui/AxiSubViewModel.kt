package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.SubtitleCue
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleStyle
import com.example.model.SubtitleVerticalAlign
import com.example.parser.AssGenerator
import com.example.parser.SubtitleParser
import com.example.util.FontManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    MAIN_MENU,
    EDITOR
}

data class AxiSubUiState(
    val currentScreen: AppScreen = AppScreen.MAIN_MENU,
    val videoUri: Uri? = null,
    val videoTitle: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val subtitles: List<SubtitleCue> = emptyList(),
    val subtitleFileName: String? = null,
    val activeCues: List<SubtitleCue> = emptyList(),
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val loadedFontFamily: FontFamily? = null,
    val customFontName: String? = null,
    val selectedTab: Int = 0, // 0: Altyazı Listesi, 1: Font & Biçim, 2: Video Bilgisi
    val searchQuery: String = "",
    val isFullscreen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val editingCue: SubtitleCue? = null,
    val generatedAssContent: String = "",
    val showExportDialog: Boolean = false,
    val showEncodeDialog: Boolean = false,
    val wasConvertedFromSrt: Boolean = false,
    val encodingSettings: com.example.encode.EncodingSettings = com.example.encode.EncodingSettings(),
    val sourceVideoMetadata: com.example.encode.SourceVideoMetadata = com.example.encode.SourceVideoMetadata(),
    val showCompatibilityDialog: Boolean = false,
    val isCompatibilityTestRunning: Boolean = false,
    val compatibilityTestResults: List<com.example.encode.CompatibilityTestItem> = emptyList(),
    val showMkvExtractionDialog: Boolean = false,
    val isInspectingMkv: Boolean = false,
    val isExtractingMkvSubtitle: Boolean = false,
    val mkvFileName: String = "",
    val mkvFileReference: File? = null,
    val mkvSubtitleTracks: List<com.example.mkv.MkvSubtitleTrack> = emptyList(),
    val selectedMkvTrack: com.example.mkv.MkvSubtitleTrack? = null,
    val extractedSubtitleFile: File? = null,
    val mkvExtractionErrorMessage: String? = null,
    val isHardsubVideoPlaying: Boolean = false
)

class AxiSubViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AxiSubUiState())
    val uiState: StateFlow<AxiSubUiState> = _uiState.asStateFlow()

    // Event to signal player to seek to timestamp
    private val _seekEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val seekEvent: SharedFlow<Long> = _seekEvent.asSharedFlow()

    val torrentDownloadInfo: StateFlow<com.example.torrent.TorrentDownloadInfo> =
        com.example.torrent.TorrentDownloadManager.downloadInfo

    init {
        // Load default sample demo on first startup so emulator has immediate working preview
        loadDemoMedia()
    }

    private fun generateAssString(
        subtitles: List<SubtitleCue>,
        style: SubtitleStyle,
        title: String? = null
    ): String {
        val name = title ?: _uiState.value.subtitleFileName ?: "remsubs_altyazi.ass"
        return AssGenerator.generateAss(
            title = name,
            subtitles = subtitles,
            style = style,
            applyTimeOffset = false
        )
    }

    fun loadDemoMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val demoAss = SubtitleParser.getSampleAssContent()
            val parsedCues = SubtitleParser.parseAss(demoAss)

            // Use bundled local sample video in raw resources (offline & error-free)
            val context = getApplication<Application>()
            val demoVideoUri = Uri.parse("android.resource://${context.packageName}/raw/sample_demo")

            _uiState.update {
                it.copy(
                    videoUri = demoVideoUri,
                    videoTitle = "Örnek Video (remsubs demo.mp4)",
                    subtitles = parsedCues,
                    subtitleFileName = "ornek_demo.ass",
                    generatedAssContent = demoAss,
                    wasConvertedFromSrt = false,
                    isHardsubVideoPlaying = false,
                    isLoading = false,
                    errorMessage = null,
                    statusMessage = "Örnek video ve .ASS altyazı yüklendi (Demo Modu)"
                )
            }
            updateActiveCues(0L)
        }
    }

    fun loadLocalVideo(uri: Uri) {
        val context = getApplication<Application>()
        val title = FontManager.getFileName(context, uri) ?: "Yerel Video"
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoTitle = title,
                isHardsubVideoPlaying = false,
                errorMessage = null,
                statusMessage = "Lokal video önizlemeye alındı: $title"
            )
        }
        extractVideoMetadata(uri)
    }

    private fun extractVideoMetadata(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val metadata = com.example.encode.InputVideoAnalyzer.analyzeVideo(context, null, uri)
                _uiState.update { it.copy(sourceVideoMetadata = metadata) }
            } catch (e: Exception) {
                android.util.Log.e("AxiSubViewModel", "Error analyzing video metadata", e)
            }
        }
    }

    fun loadSubtitleFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            try {
                val fileName = FontManager.getFileName(context, uri) ?: "altyazi.ass"
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val cues = SubtitleParser.parse(inputStream, fileName)
                    val isSrt = fileName.lowercase().endsWith(".srt")
                    val targetFileName = if (isSrt) {
                        fileName.replace(Regex("(?i)\\.srt$"), ".ass")
                    } else {
                        fileName
                    }
                    val currentStyle = _uiState.value.subtitleStyle
                    val assContent = AssGenerator.generateAss(
                        title = targetFileName,
                        subtitles = cues,
                        style = currentStyle
                    )
                    _uiState.update {
                        it.copy(
                            subtitles = cues,
                            subtitleFileName = targetFileName,
                            generatedAssContent = assContent,
                            wasConvertedFromSrt = isSrt,
                            isLoading = false,
                            statusMessage = if (isSrt) {
                                "SRT altyazısı .ASS formatına dönüştürüldü ve editöre aktarıldı (${cues.size} satır)"
                            } else {
                                "$fileName yüklendi (${cues.size} satır altyazı)"
                            }
                        )
                    }
                    updateActiveCues(_uiState.value.currentPositionMs)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Altyazı dosyası okunamadı!"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Altyazı hatası: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun loadTtfFontFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val result = FontManager.copyTtfToInternalStorage(context, uri)
            if (result != null) {
                val (fileName, fontFile) = result
                val fontFamily = FontManager.createFontFamilyFromFile(fontFile)
                if (fontFamily != null) {
                    _uiState.update { state ->
                        val updatedStyle = state.subtitleStyle.copy(
                            fontName = fileName,
                            customFontPath = fontFile.absolutePath
                        )
                        state.copy(
                            loadedFontFamily = fontFamily,
                            customFontName = fileName,
                            subtitleStyle = updatedStyle,
                            generatedAssContent = generateAssString(state.subtitles, updatedStyle, state.subtitleFileName),
                            isLoading = false,
                            statusMessage = "Özel font yüklendi: $fileName"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Font dosyası ayrıştırılamadı (.ttf olduğundan emin olun)"
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Font kopyalama hatası!"
                    )
                }
            }
        }
    }

    fun selectPresetFont(name: String, family: FontFamily?) {
        _uiState.update { state ->
            val updatedStyle = state.subtitleStyle.copy(
                fontName = name,
                customFontPath = null
            )
            state.copy(
                loadedFontFamily = family,
                customFontName = null,
                subtitleStyle = updatedStyle,
                generatedAssContent = generateAssString(state.subtitles, updatedStyle, state.subtitleFileName),
                statusMessage = "Font değiştirildi: $name"
            )
        }
    }

    fun updatePlaybackPosition(posMs: Long) {
        _uiState.update { it.copy(currentPositionMs = posMs) }
        updateActiveCues(posMs)
    }

    fun updateDuration(durationMs: Long) {
        _uiState.update { it.copy(durationMs = durationMs) }
    }

    fun setIsPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(0L))
        _seekEvent.tryEmit(target)
        updatePlaybackPosition(target)
    }

    fun navigateToEditor() {
        _uiState.update { it.copy(currentScreen = AppScreen.EDITOR) }
    }

    fun navigateToMainMenu() {
        _uiState.update { it.copy(currentScreen = AppScreen.MAIN_MENU) }
    }

    fun startBlankProject() {
        _uiState.update {
            it.copy(
                subtitles = emptyList(),
                subtitleFileName = "yeni_proje.ass",
                generatedAssContent = "",
                wasConvertedFromSrt = false,
                currentScreen = AppScreen.EDITOR,
                statusMessage = "Yeni boş altyazı projesi oluşturuldu"
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update {
            it.copy(
                playbackSpeed = speed,
                statusMessage = "Oynatma hızı: ${speed}x"
            )
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun updateStyle(update: (SubtitleStyle) -> SubtitleStyle) {
        _uiState.update { state ->
            val newStyle = update(state.subtitleStyle)
            state.copy(
                subtitleStyle = newStyle,
                generatedAssContent = generateAssString(state.subtitles, newStyle, state.subtitleFileName)
            )
        }
        updateActiveCues(_uiState.value.currentPositionMs)
    }

    fun adjustTimeOffset(deltaMs: Long) {
        _uiState.update { state ->
            val newOffset = state.subtitleStyle.timeOffsetMs + deltaMs
            val newStyle = state.subtitleStyle.copy(timeOffsetMs = newOffset)
            state.copy(
                subtitleStyle = newStyle,
                generatedAssContent = generateAssString(state.subtitles, newStyle, state.subtitleFileName),
                statusMessage = "Senkron kayması: ${if (newOffset >= 0) "+$newOffset" else "$newOffset"} ms"
            )
        }
        updateActiveCues(_uiState.value.currentPositionMs)
    }

    fun resetTimeOffset() {
        _uiState.update { state ->
            val newStyle = state.subtitleStyle.copy(timeOffsetMs = 0L)
            state.copy(
                subtitleStyle = newStyle,
                generatedAssContent = generateAssString(state.subtitles, newStyle, state.subtitleFileName),
                statusMessage = "Senkron sıfırlandı (0 ms)"
            )
        }
        updateActiveCues(_uiState.value.currentPositionMs)
    }

    fun startEditingCue(cue: SubtitleCue) {
        _uiState.update { it.copy(editingCue = cue) }
    }

    fun dismissEditingCue() {
        _uiState.update { it.copy(editingCue = null) }
    }

    fun saveEditedCue(updatedCue: SubtitleCue) {
        _uiState.update { state ->
            val updatedList = state.subtitles.map { if (it.id == updatedCue.id) updatedCue else it }
                .sortedBy { it.startTimeMs }
            state.copy(
                subtitles = updatedList,
                editingCue = null,
                generatedAssContent = generateAssString(updatedList, state.subtitleStyle, state.subtitleFileName),
                statusMessage = "Altyazı #${updatedCue.id} güncellendi"
            )
        }
        updateActiveCues(_uiState.value.currentPositionMs)
    }

    fun addNewCueAtCurrentPosition() {
        val currentPos = _uiState.value.currentPositionMs
        val nextId = (_uiState.value.subtitles.maxOfOrNull { it.id } ?: 0) + 1
        val newCue = SubtitleCue(
            id = nextId,
            startTimeMs = currentPos,
            endTimeMs = currentPos + 3000L,
            rawText = "Yeni Altyazı Satırı",
            cleanText = "Yeni Altyazı Satırı"
        )
        _uiState.update { state ->
            val updated = (state.subtitles + newCue).sortedBy { it.startTimeMs }
            state.copy(
                subtitles = updated,
                editingCue = newCue,
                generatedAssContent = generateAssString(updated, state.subtitleStyle, state.subtitleFileName),
                statusMessage = "Yeni altyazı eklendi (#$nextId)"
            )
        }
        updateActiveCues(currentPos)
    }

    fun deleteCue(cueId: Int) {
        _uiState.update { state ->
            val updated = state.subtitles.filterNot { it.id == cueId }
            state.copy(
                subtitles = updated,
                editingCue = null,
                generatedAssContent = generateAssString(updated, state.subtitleStyle, state.subtitleFileName),
                statusMessage = "Altyazı #$cueId silindi"
            )
        }
        updateActiveCues(_uiState.value.currentPositionMs)
    }

    fun openExportDialog() {
        val currentAss = generateAssString(_uiState.value.subtitles, _uiState.value.subtitleStyle, _uiState.value.subtitleFileName)
        _uiState.update {
            it.copy(
                generatedAssContent = currentAss,
                showExportDialog = true
            )
        }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false) }
    }

    val encodeState: StateFlow<com.example.encode.EncodeState> = com.example.encode.HardsubEncoder.encodeState

    fun openEncodeDialog() {
        try {
            if (_uiState.value.videoUri == null) {
                // Load demo video if none selected so user has an immediate video ready to encode
                loadDemoMedia()
            } else {
                extractVideoMetadata(_uiState.value.videoUri)
            }
            com.example.encode.HardsubEncoder.resetState()
            _uiState.update { it.copy(showEncodeDialog = true, errorMessage = null) }
        } catch (t: Throwable) {
            android.util.Log.e("AxiSubViewModel", "Error opening encode dialog", t)
            _uiState.update {
                it.copy(
                    showEncodeDialog = true,
                    errorMessage = "Encode modülü hazırlanırken hata: ${t.localizedMessage ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    fun dismissEncodeDialog() {
        if (!encodeState.value.isEncoding && !encodeState.value.isPreparing) {
            _uiState.update { it.copy(showEncodeDialog = false) }
        }
    }

    fun updateEncodingSettings(settings: com.example.encode.EncodingSettings) {
        _uiState.update { it.copy(encodingSettings = settings) }
    }

    fun openCompatibilityTest() {
        _uiState.update { it.copy(showCompatibilityDialog = true) }
        if (_uiState.value.compatibilityTestResults.isEmpty()) {
            runCompatibilityTest()
        }
    }

    fun dismissCompatibilityTest() {
        _uiState.update { it.copy(showCompatibilityDialog = false) }
    }

    fun runCompatibilityTest() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCompatibilityTestRunning = true) }
            val results = com.example.encode.DeviceCodecDetector.runCompatibilityTestSuite(getApplication())
            _uiState.update {
                it.copy(
                    isCompatibilityTestRunning = false,
                    compatibilityTestResults = results
                )
            }
        }
    }

    fun startMagnetDownload(magnetUri: String) {
        com.example.torrent.TorrentDownloadManager.startMagnetDownload(getApplication(), magnetUri)
    }

    fun startTorrentFileDownload(torrentUri: Uri) {
        com.example.torrent.TorrentDownloadManager.startTorrentFileDownload(getApplication(), torrentUri)
    }

    fun pauseTorrentDownload() {
        com.example.torrent.TorrentDownloadManager.pauseDownload()
    }

    fun resumeTorrentDownload() {
        com.example.torrent.TorrentDownloadManager.resumeDownload(getApplication())
    }

    fun cancelTorrentDownload() {
        com.example.torrent.TorrentDownloadManager.cancelDownload()
    }

    fun openDownloadedVideoInEditor(file: File) {
        val permanentUri = com.example.torrent.TorrentDownloadManager.downloadInfo.value.permanentUri
        val uri = permanentUri ?: Uri.fromFile(file)
        loadLocalVideo(uri)
        navigateToEditor()
        _uiState.update {
            it.copy(
                isHardsubVideoPlaying = false,
                statusMessage = "İndirilen torrent videosu düzenleyiciye aktarıldı!"
            )
        }
    }

    fun startHardsubEncode() {
        val state = _uiState.value
        val videoUri = state.videoUri ?: run {
            _uiState.update { it.copy(errorMessage = "Lütfen önce bir video seçin veya örnek videoyu yükleyin.") }
            return
        }
        if (state.subtitles.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lütfen önce bir altyazı dosyası (.ass/.srt) yükleyin.") }
            return
        }
        val customFontFile = state.subtitleStyle.customFontPath?.let { path ->
            val f = File(path)
            if (f.exists()) f else null
        }
        val additionalStyles = SubtitleParser.extractAssStyles(state.generatedAssContent)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.encode.HardsubEncoder.startHardsubEncode(
                    context = getApplication(),
                    videoUri = videoUri,
                    cues = state.subtitles,
                    style = state.subtitleStyle,
                    customFontFile = customFontFile,
                    additionalStyles = additionalStyles,
                    settings = state.encodingSettings,
                    sourceMetadata = state.sourceVideoMetadata
                )
            } catch (t: Throwable) {
                android.util.Log.e("AxiSubViewModel", "Fatal error in startHardsubEncode", t)
                _uiState.update {
                    it.copy(errorMessage = "Encode başlatılamadı: ${t.localizedMessage ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    fun inspectMkvForSoftsubs(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val fileName = FontManager.getFileName(context, uri) ?: "video.mkv"
            _uiState.update {
                it.copy(
                    showMkvExtractionDialog = true,
                    isInspectingMkv = true,
                    isExtractingMkvSubtitle = false,
                    mkvFileName = fileName,
                    mkvFileReference = null,
                    mkvSubtitleTracks = emptyList(),
                    selectedMkvTrack = null,
                    extractedSubtitleFile = null,
                    mkvExtractionErrorMessage = null
                )
            }

            when (val result = com.example.mkv.MkvSubtitleExtractor.inspectMkv(context, uri)) {
                is com.example.mkv.MkvInspectionResult.Success -> {
                    val defaultTrack = result.tracks.firstOrNull { it.isDefault } ?: result.tracks.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isInspectingMkv = false,
                            mkvFileReference = result.mkvFile,
                            mkvSubtitleTracks = result.tracks,
                            selectedMkvTrack = defaultTrack
                        )
                    }
                }
                is com.example.mkv.MkvInspectionResult.NoSubtitlesFound -> {
                    _uiState.update {
                        it.copy(
                            isInspectingMkv = false,
                            mkvSubtitleTracks = emptyList(),
                            mkvExtractionErrorMessage = null
                        )
                    }
                }
                is com.example.mkv.MkvInspectionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isInspectingMkv = false,
                            mkvExtractionErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun selectMkvTrack(track: com.example.mkv.MkvSubtitleTrack) {
        _uiState.update { it.copy(selectedMkvTrack = track) }
    }

    fun extractSelectedMkvSubtitle() {
        val state = _uiState.value
        val mkvFile = state.mkvFileReference ?: run {
            _uiState.update { it.copy(mkvExtractionErrorMessage = "MKV dosyası bulunamadı.") }
            return
        }
        val track = state.selectedMkvTrack ?: run {
            _uiState.update { it.copy(mkvExtractionErrorMessage = "Lütfen bir altyazı parçası seçin.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExtractingMkvSubtitle = true,
                    mkvExtractionErrorMessage = null
                )
            }
            val context = getApplication<Application>()
            when (val result = com.example.mkv.MkvSubtitleExtractor.extractTrack(context, mkvFile, track)) {
                is com.example.mkv.SubtitleExtractionResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isExtractingMkvSubtitle = false,
                            extractedSubtitleFile = result.extractedFile,
                            statusMessage = "Altyazı başarıyla ayıklandı: ${result.extractedFile.name}"
                        )
                    }
                }
                is com.example.mkv.SubtitleExtractionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isExtractingMkvSubtitle = false,
                            mkvExtractionErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun dismissMkvExtractionDialog() {
        _uiState.update {
            it.copy(
                showMkvExtractionDialog = false,
                isInspectingMkv = false,
                isExtractingMkvSubtitle = false,
                mkvExtractionErrorMessage = null
            )
        }
    }

    fun openExtractedSubtitleInEditor(file: File) {
        val uri = Uri.fromFile(file)
        loadSubtitleFromUri(uri)
        val mkvFile = _uiState.value.mkvFileReference
        if (mkvFile != null && mkvFile.exists()) {
            val videoUri = Uri.fromFile(mkvFile)
            loadLocalVideo(videoUri)
        }
        navigateToEditor()
        _uiState.update {
            it.copy(
                showMkvExtractionDialog = false,
                statusMessage = "Ayıklanan altyazı (${file.name}) editöre yüklendi!"
            )
        }
    }

    fun cancelHardsubEncode() {
        com.example.encode.HardsubEncoder.cancelEncoding(getApplication())
    }

    fun playEncodedVideo(file: File) {
        val uri = Uri.fromFile(file)
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoTitle = "Hardsub Video (${file.name})",
                showEncodeDialog = false,
                isHardsubVideoPlaying = true,
                statusMessage = "Hardsub gömülü video oynatıcıya yüklendi!"
            )
        }
    }

    fun saveExportedAssToUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val content = _uiState.value.generatedAssContent.ifBlank {
                    generateAssString(_uiState.value.subtitles, _uiState.value.subtitleStyle, _uiState.value.subtitleFileName)
                }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                _uiState.update {
                    it.copy(
                        statusMessage = "Düzenlenmiş .ASS dosyası başarıyla kaydedildi!",
                        showExportDialog = false
                    )
                }
                onResult(true, "Kaydedildi")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(statusMessage = "Kaydetme hatası: ${e.localizedMessage}")
                }
                onResult(false, e.localizedMessage ?: "Bilinmeyen hata")
            }
        }
    }

    fun setErrorMessage(message: String?) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun updateActiveCues(currentPos: Long) {
        val offset = _uiState.value.subtitleStyle.timeOffsetMs
        val effectiveTime = currentPos - offset
        val active = _uiState.value.subtitles.filter { cue ->
            effectiveTime in cue.startTimeMs..cue.endTimeMs
        }
        _uiState.update { it.copy(activeCues = active) }
    }
}
