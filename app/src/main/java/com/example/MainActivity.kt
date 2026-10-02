package com.example

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.AxiSubViewModel
import com.example.ui.components.AnimeSearchScreen
import com.example.ui.components.AssExportDialog
import com.example.ui.components.EditSubtitleCueDialog
import com.example.ui.components.FontAndStyleSection
import com.example.ui.components.HardsubEncodeDialog
import com.example.ui.components.MainMenuScreen
import com.example.ui.components.MkvSubtitleExtractionDialog
import com.example.ui.components.SubtitleListSection
import com.example.ui.components.ModernSubtitleEditorScreen
import com.example.ui.components.UpdateReadyDialog
import com.example.ui.components.VideoInfoSection
import com.example.ui.components.VideoPlayerSection
import com.example.ui.theme.MyApplicationTheme
import com.example.update.AppUpdater
import com.example.update.UpdateState
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AxiSubMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxiSubMainScreen(
    viewModel: AxiSubViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val encodeState by viewModel.encodeState.collectAsState()
    val torrentDownloadInfo by viewModel.torrentDownloadInfo.collectAsState()
    val animeSearchState by viewModel.animeSearchState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingVideoToSave by remember { mutableStateOf<File?>(null) }
    var pendingExtractedSubtitleToSave by remember { mutableStateOf<File?>(null) }
    var updateDialogDismissed by rememberSaveable { mutableStateOf(false) }

    val launchUpdateInstaller: () -> Unit = {
        (viewModel.updateState.value as? UpdateState.ReadyToInstall)?.let { ready ->
            try {
                context.startActivity(AppUpdater.installIntent(context, ready.apk))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Sistem yükleyicisi açılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Returning from the "install unknown apps" settings page continues straight to the installer
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (AppUpdater.canRequestInstalls(context)) launchUpdateInstaller()
    }

    val installUpdate: () -> Unit = {
        updateDialogDismissed = true
        if (AppUpdater.canRequestInstalls(context)) {
            launchUpdateInstaller()
        } else {
            Toast.makeText(
                context,
                "Güncellemeyi kurabilmek için RemSubs'a \"Bilinmeyen uygulamaları yükle\" izni ver, sonra geri dön.",
                Toast.LENGTH_LONG
            ).show()
            installPermissionLauncher.launch(AppUpdater.installPermissionIntent(context))
        }
    }

    // Save Extracted Subtitle to Device
    val saveExtractedSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fileToSave = pendingExtractedSubtitleToSave
        if (uri != null && fileToSave != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    fileToSave.inputStream().use { input ->
                        input.copyTo(os)
                    }
                    os.flush()
                }
                Toast.makeText(context, "Altyazı başarıyla cihazınıza kaydedildi!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Kayıt hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // MKV Video Picker Launcher (for Softsub Extraction)
    val mkvVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectMkvForSoftsubs(it) }
    }

    // Torrent File (.torrent) Picker
    val torrentFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.startTorrentFileDownload(it) }
    }

    // Save Encoded Video to Device (Downloads / Movies)
    val exportVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val fileToSave = pendingVideoToSave
        if (uri != null && fileToSave != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    fileToSave.inputStream().use { input ->
                        input.copyTo(os)
                    }
                    os.flush()
                }
                Toast.makeText(context, "Hardsub video başarıyla cihazınıza kaydedildi!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Kayıt hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Video Picker Launcher (Plays directly without internal copying)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadLocalVideo(it)
            viewModel.navigateToEditor()
        }
    }

    // Subtitle Picker Launcher (.ass or .srt)
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.loadSubtitleFromUri(it)
            viewModel.navigateToEditor()
        }
    }

    // Font Picker Launcher (.ttf)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadTtfFontFromUri(it) }
    }

    // Export .ASS File Launcher
    val exportAssLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-ssa")
    ) { uri ->
        uri?.let {
            viewModel.saveExportedAssToUri(it) { _, _ -> }
        }
    }

    // Show transient status messages
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    if (uiState.currentScreen == AppScreen.ANIME_SEARCH) {
        BackHandler {
            viewModel.navigateToMainMenu()
        }

        AnimeSearchScreen(
            state = animeSearchState,
            torrentDownloadInfo = torrentDownloadInfo,
            onBack = viewModel::navigateToMainMenu,
            onQueryChange = viewModel::updateAnimeSearchQuery,
            onSearch = viewModel::runAnimeSearch,
            onSelectSource = viewModel::selectAnimeSource,
            onSelectQuality = viewModel::selectAnimeQuality,
            onToggleSortBySeeders = viewModel::toggleAnimeSortBySeeders,
            onDownload = viewModel::downloadAnimeSearchResult,
            snackbarHostState = snackbarHostState
        )
    } else if (uiState.currentScreen == AppScreen.MAIN_MENU) {
        // 1. Initial Main Menu on startup (with Torrent/Magnet Downloader & option: video ile altyazı düzenleme)
        MainMenuScreen(
            uiState = uiState,
            torrentDownloadInfo = torrentDownloadInfo,
            onOpenEditor = viewModel::navigateToEditor,
            onLoadDemoAndOpen = {
                viewModel.loadDemoMedia()
                viewModel.navigateToEditor()
            },
            onStartBlankProject = viewModel::startBlankProject,
            onPickSubtitle = {
                subtitlePickerLauncher.launch(arrayOf("*/*"))
            },
            onPickVideo = {
                videoPickerLauncher.launch("video/*")
            },
            onOpenExport = viewModel::openExportDialog,
            onStartMagnetDownload = viewModel::startMagnetDownload,
            onPickTorrentFile = {
                torrentFilePickerLauncher.launch(arrayOf("*/*"))
            },
            onPauseTorrentDownload = viewModel::pauseTorrentDownload,
            onResumeTorrentDownload = viewModel::resumeTorrentDownload,
            onCancelTorrentDownload = viewModel::cancelTorrentDownload,
            onOpenDownloadedVideo = viewModel::openDownloadedVideoInEditor,
            onSelectTorrentVideoFile = viewModel::selectTorrentVideoFile,
            onExtractSubtitleFromVideo = {
                mkvVideoPickerLauncher.launch(arrayOf("video/*", "video/x-matroska", "*/*"))
            },
            onOpenAnimeSearch = viewModel::openAnimeSearch,
            onRetryTorrentDownload = viewModel::retryTorrentDownload,
            onSaveTorrentToGallery = viewModel::saveTorrentDownloadToGallery,
            updateState = updateState,
            onInstallUpdate = installUpdate,
            onRetryUpdate = viewModel::checkForUpdates,
            versionName = BuildConfig.VERSION_NAME,
            snackbarHostState = snackbarHostState
        )

        (updateState as? UpdateState.ReadyToInstall)?.let { ready ->
            if (!updateDialogDismissed) {
                UpdateReadyDialog(
                    info = ready.info,
                    activeDownloadName = torrentDownloadInfo.takeIf { it.isActive }?.torrentName,
                    onInstall = installUpdate,
                    onLater = { updateDialogDismissed = true }
                )
            }
        }
    } else {
        // 2. Redesigned Modern Subtitle Editor Screen
        BackHandler {
            viewModel.navigateToMainMenu()
        }

        ModernSubtitleEditorScreen(
            uiState = uiState,
            seekEvent = viewModel.seekEvent,
            onNavigateBack = viewModel::navigateToMainMenu,
            onUpdatePosition = viewModel::updatePlaybackPosition,
            onUpdateDuration = viewModel::updateDuration,
            onSetPlaying = viewModel::setIsPlaying,
            onSeekTo = viewModel::seekTo,
            onSetSpeed = viewModel::setPlaybackSpeed,
            onToggleFullscreen = viewModel::toggleFullscreen,
            onSaveCue = viewModel::saveEditedCue,
            onAddNewCue = viewModel::addNewCueAtCurrentPosition,
            onDeleteCue = viewModel::deleteCue,
            onSelectCue = viewModel::selectCue,
            onDuplicateCue = viewModel::duplicateCue,
            onSplitCue = viewModel::splitCue,
            onAdjustTimeOffset = viewModel::adjustTimeOffset,
            onResetTimeOffset = viewModel::resetTimeOffset,
            onUpdateStyle = viewModel::updateStyle,
            onPickTtfFont = { fontPickerLauncher.launch(arrayOf("*/*")) },
            onSelectPresetFont = viewModel::selectPresetFont,
            onOpenExportDialog = viewModel::openExportDialog,
            onOpenEncodeDialog = viewModel::openEncodeDialog,
            onPickVideo = { videoPickerLauncher.launch("video/*") },
            onPickSubtitle = { subtitlePickerLauncher.launch(arrayOf("*/*")) },
            onExtractMkvSubtitle = { mkvVideoPickerLauncher.launch(arrayOf("video/*", "video/x-matroska", "*/*")) },
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onReplaceAllText = viewModel::replaceTextInAllCues,
            onVideoDimensionsDetected = viewModel::updateVideoDimensions,
            snackbarHostState = snackbarHostState
        )
    }

    // Subtitle Cue Edit Dialog / BottomSheet (Edit timing, text & custom positioning)
    uiState.editingCue?.let { cueToEdit ->
        EditSubtitleCueDialog(
            cue = cueToEdit,
            currentVideoPositionMs = uiState.currentPositionMs,
            onDismiss = viewModel::dismissEditingCue,
            onSave = viewModel::saveEditedCue,
            onDelete = viewModel::deleteCue
        )
    }

    // ASS Export Dialog (Review & Save / Share .ass file)
    if (uiState.showExportDialog) {
        AssExportDialog(
            uiState = uiState,
            onDismiss = viewModel::dismissExportDialog,
            onRequestSaveFile = { suggestedName ->
                exportAssLauncher.launch(suggestedName)
            }
        )
    }

    // Hardsub Encoding Dialog (libass burn-in, adaptive codecs, FPS / Total Frames ratio, Estimated Finish Time)
    if (uiState.showEncodeDialog) {
        HardsubEncodeDialog(
            uiState = uiState,
            encodeState = encodeState,
            onDismiss = viewModel::dismissEncodeDialog,
            onStartEncode = viewModel::startHardsubEncode,
            onCancelEncode = viewModel::cancelHardsubEncode,
            onPlayEncodedVideo = viewModel::playEncodedVideo,
            onSaveToDevice = { file ->
                pendingVideoToSave = file
                exportVideoLauncher.launch(file.name)
            },
            onUpdateSettings = viewModel::updateEncodingSettings,
            onOpenCompatibilityTest = viewModel::openCompatibilityTest,
            onSelectIntroVideo = viewModel::setIntroVideo,
            onRemoveIntroVideo = viewModel::removeIntroVideo,
            onSetIntroKeepAudio = viewModel::setIntroKeepAudio,
            onToggleIntroPreview = viewModel::setShowIntroPreview
        )
    }

    // Compatibility Test Dialog (Device hardware & FFmpeg checks)
    if (uiState.showCompatibilityDialog) {
        com.example.ui.components.CompatibilityTestDialog(
            isRunning = uiState.isCompatibilityTestRunning,
            testResults = uiState.compatibilityTestResults,
            onRerunTest = viewModel::runCompatibilityTest,
            onDismiss = viewModel::dismissCompatibilityTest
        )
    }

    // MKV Softsub Extraction Dialog
    if (uiState.showMkvExtractionDialog) {
        MkvSubtitleExtractionDialog(
            isOpen = uiState.showMkvExtractionDialog,
            isInspecting = uiState.isInspectingMkv,
            isExtracting = uiState.isExtractingMkvSubtitle,
            mkvFileName = uiState.mkvFileName,
            tracks = uiState.mkvSubtitleTracks,
            selectedTrack = uiState.selectedMkvTrack,
            extractedFile = uiState.extractedSubtitleFile,
            errorMessage = uiState.mkvExtractionErrorMessage,
            onTrackSelect = viewModel::selectMkvTrack,
            onStartExtraction = viewModel::extractSelectedMkvSubtitle,
            onOpenInEditor = viewModel::openExtractedSubtitleInEditor,
            onSaveToDevice = { file ->
                pendingExtractedSubtitleToSave = file
                saveExtractedSubtitleLauncher.launch(file.name)
            },
            onDismiss = viewModel::dismissMkvExtractionDialog
        )
    }
}
