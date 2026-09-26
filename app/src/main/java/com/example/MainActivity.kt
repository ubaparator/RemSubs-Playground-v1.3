package com.example

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
import com.example.ui.components.AssExportDialog
import com.example.ui.components.EditSubtitleCueDialog
import com.example.ui.components.FontAndStyleSection
import com.example.ui.components.HardsubEncodeDialog
import com.example.ui.components.MainMenuScreen
import com.example.ui.components.MkvSubtitleExtractionDialog
import com.example.ui.components.SubtitleListSection
import com.example.ui.components.VideoInfoSection
import com.example.ui.components.VideoPlayerSection
import com.example.ui.theme.MyApplicationTheme
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
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingVideoToSave by remember { mutableStateOf<File?>(null) }
    var pendingExtractedSubtitleToSave by remember { mutableStateOf<File?>(null) }

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

    if (uiState.currentScreen == AppScreen.MAIN_MENU) {
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
            onExtractSubtitleFromVideo = {
                mkvVideoPickerLauncher.launch(arrayOf("video/*", "video/x-matroska", "*/*"))
            }
        )
    } else {
        // 2. Subtitle Editor Screen
        BackHandler {
            viewModel.navigateToMainMenu()
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!uiState.isFullscreen) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = viewModel::navigateToMainMenu,
                                modifier = Modifier.testTag("nav_back_to_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Ana Menüye Dön"
                                )
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_rem_logo),
                                    contentDescription = "Rem Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "remsubs playground",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = uiState.subtitleFileName ?: ".ass düzenleyici",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        actions = {
                            // "Encode Yap" Action Button (Hardsub Burn-in libass & CRF 23)
                            Button(
                                onClick = { viewModel.openEncodeDialog() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .testTag("top_bar_btn_encode")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Encode Al",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Quick .ASS Export Button in TopAppBar
                            FilledTonalButton(
                                onClick = { viewModel.openExportDialog() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .testTag("top_bar_export_ass")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = ".ASS Çıktı",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Quick Video Pick button in top bar
                            IconButton(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                modifier = Modifier.testTag("top_bar_pick_video")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoFile,
                                    contentDescription = "Video Seç",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Video Player & Subtitle Overlay Area
                VideoPlayerSection(
                    uiState = uiState,
                    seekEvent = viewModel.seekEvent,
                    onUpdatePosition = viewModel::updatePlaybackPosition,
                    onUpdateDuration = viewModel::updateDuration,
                    onSetPlaying = viewModel::setIsPlaying,
                    onSeekTo = viewModel::seekTo,
                    onSetSpeed = viewModel::setPlaybackSpeed,
                    onToggleFullscreen = viewModel::toggleFullscreen,
                    onPlayerError = viewModel::setErrorMessage,
                    onEditActiveCue = viewModel::startEditingCue,
                    modifier = if (uiState.isFullscreen) Modifier.weight(1f) else Modifier
                )

                // When in fullscreen mode, we only show video player
                if (!uiState.isFullscreen) {
                    // Navigation Tabs (Altyazılar, Font & Konum, Video & Bilgi)
                    TabRow(
                        selectedTabIndex = uiState.selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = uiState.selectedTab == 0,
                            onClick = { viewModel.setSelectedTab(0) },
                            icon = { Icon(Icons.Default.Subtitles, null, modifier = Modifier.size(20.dp)) },
                            text = { Text("Altyazılar", fontSize = 12.sp) },
                            modifier = Modifier.testTag("tab_subtitles")
                        )

                        Tab(
                            selected = uiState.selectedTab == 1,
                            onClick = { viewModel.setSelectedTab(1) },
                            icon = { Icon(Icons.Default.FontDownload, null, modifier = Modifier.size(20.dp)) },
                            text = { Text("Font & Stil (.ttf)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("tab_font_style")
                        )

                        Tab(
                            selected = uiState.selectedTab == 2,
                            onClick = { viewModel.setSelectedTab(2) },
                            icon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(20.dp)) },
                            text = { Text("Video & Bilgi", fontSize = 12.sp) },
                            modifier = Modifier.testTag("tab_video_info")
                        )
                    }

                    // Bottom Section according to selected tab
                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.selectedTab) {
                            0 -> SubtitleListSection(
                                uiState = uiState,
                                onPickSubtitle = {
                                    subtitlePickerLauncher.launch(arrayOf("*/*"))
                                },
                                onLoadDemoSubtitle = viewModel::loadDemoMedia,
                                onSeekToCue = viewModel::seekTo,
                                onAdjustTimeOffset = viewModel::adjustTimeOffset,
                                onResetTimeOffset = viewModel::resetTimeOffset,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onEditCue = viewModel::startEditingCue,
                                onAddNewCue = viewModel::addNewCueAtCurrentPosition,
                                onExportAss = viewModel::openExportDialog
                            )
                            1 -> FontAndStyleSection(
                                uiState = uiState,
                                onPickTtfFont = {
                                    fontPickerLauncher.launch(arrayOf("*/*"))
                                },
                                onSelectPresetFont = viewModel::selectPresetFont,
                                onUpdateStyle = viewModel::updateStyle,
                                onExportAss = viewModel::openExportDialog
                            )
                            2 -> VideoInfoSection(
                                uiState = uiState,
                                onPickVideo = {
                                    videoPickerLauncher.launch("video/*")
                                },
                                onLoadDemoMedia = viewModel::loadDemoMedia,
                                onOpenEncode = viewModel::openEncodeDialog
                            )
                        }
                    }
                }
            }
        }
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
            onOpenCompatibilityTest = viewModel::openCompatibilityTest
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
