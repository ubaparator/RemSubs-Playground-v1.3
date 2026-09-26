package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.media3.common.PlaybackException
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.model.SubtitleCue
import com.example.ui.AxiSubUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSection(
    uiState: AxiSubUiState,
    seekEvent: SharedFlow<Long>,
    onUpdatePosition: (Long) -> Unit,
    onUpdateDuration: (Long) -> Unit,
    onSetPlaying: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onPlayerError: (String?) -> Unit = {},
    onEditActiveCue: ((SubtitleCue) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderTempPosition by remember { mutableFloatStateOf(0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    // Auto hide controls after 3.5 seconds
    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying && !isDraggingSlider) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Set playback speed
    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
    }

    // Handle incoming seek events
    LaunchedEffect(seekEvent) {
        seekEvent.collectLatest { targetMs ->
            exoPlayer.seekTo(targetMs)
            onUpdatePosition(targetMs)
        }
    }

    // Load media item when uri changes
    LaunchedEffect(uiState.videoUri) {
        uiState.videoUri?.let { uri ->
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    // Position ticker coroutine
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (exoPlayer.isPlaying && !isDraggingSlider) {
                val current = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                onUpdatePosition(current)
                if (dur > 0L) {
                    onUpdateDuration(dur)
                }
            }
            delay(40) // ~25 FPS position updates for ultra-smooth subtitle display
        }
    }

    // Listener for state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onSetPlaying(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    onUpdateDuration(dur)
                    onPlayerError(null)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorDesc = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "Video akışı yüklenemedi (Ağ / Kaynak Hatası: ${error.message ?: error.errorCodeName})"
                    else ->
                        "Video oynatma hatası: ${error.localizedMessage ?: error.errorCodeName}"
                }
                onPlayerError(errorDesc)
                onSetPlaying(false)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val containerModifier = if (uiState.isFullscreen) {
        modifier.fillMaxSize().background(Color.Black)
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    }

    Box(
        modifier = containerModifier
            .testTag("video_player_container")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtitle Overlay: Only rendered for soft preview.
        // When playing an encoded hardsub video, the subtitles are directly in the video pixels,
        // so we hide the software overlay to guarantee the user sees real burned-in hardsubs!
        if (!uiState.isHardsubVideoPlaying) {
            SubtitleOverlay(
                activeCues = uiState.activeCues,
                style = uiState.subtitleStyle,
                fontFamily = uiState.loadedFontFamily,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Hardsub badge in top-left to inform user the subtitles are burned into frames
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hardsub (Piksellere Gömülü)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Error overlay indicator
        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Video Hatası",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Bar: Video Title & Active Subtitle Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = uiState.videoTitle ?: "Video Seçilmedi",
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        uiState.subtitleFileName?.let { subName ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$subName (${uiState.subtitles.size} satır)",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick Edit Active Cue button
                        uiState.activeCues.firstOrNull()?.let { activeCue ->
                            if (onEditActiveCue != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .clickable { onEditActiveCue(activeCue) }
                                        .padding(end = 8.dp)
                                        .testTag("edit_active_cue_player_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Altyazıyı Düzenle",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Altyazıyı Düzenle",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Speed Selector Menu
                        Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .clickable { speedMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Hız",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${uiState.playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        onSetSpeed(speed)
                                        speedMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

                // Center Play / Pause & Quick Jump Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Jump to previous subtitle cue
                    IconButton(
                        onClick = {
                            val current = uiState.currentPositionMs
                            val prevCue = uiState.subtitles.lastOrNull { it.startTimeMs < current - 300 }
                            if (prevCue != null) {
                                onSeekTo(prevCue.startTimeMs)
                            } else {
                                onSeekTo((current - 5000L).coerceAtLeast(0L))
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("prev_cue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Önceki Altyazı",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // -5 seconds
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 5000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            onUpdatePosition(target)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("rewind_5s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "-5 Saniye",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play / Pause main button
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Durdur" else "Oynat",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // +5 seconds
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 5000L).coerceAtMost(uiState.durationMs)
                            exoPlayer.seekTo(target)
                            onUpdatePosition(target)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("forward_5s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "+5 Saniye",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Jump to next subtitle cue
                    IconButton(
                        onClick = {
                            val current = uiState.currentPositionMs
                            val nextCue = uiState.subtitles.firstOrNull { it.startTimeMs > current + 100 }
                            if (nextCue != null) {
                                onSeekTo(nextCue.startTimeMs)
                            } else {
                                onSeekTo((current + 5000L).coerceAtMost(uiState.durationMs))
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("next_cue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Sonraki Altyazı",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Bottom Bar: Time slider and duration
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    val currentPos = if (isDraggingSlider) sliderTempPosition.toLong() else uiState.currentPositionMs
                    val totalDur = uiState.durationMs.coerceAtLeast(1L)

                    Slider(
                        value = currentPos.toFloat().coerceIn(0f, totalDur.toFloat()),
                        onValueChange = {
                            isDraggingSlider = true
                            sliderTempPosition = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            val seekTarget = sliderTempPosition.toLong()
                            exoPlayer.seekTo(seekTarget)
                            onUpdatePosition(seekTarget)
                        },
                        valueRange = 0f..totalDur.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("video_timeline_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${SubtitleCue.formatTimestamp(currentPos)} / ${SubtitleCue.formatTimestamp(uiState.durationMs)}",
                            color = Color.White,
                            fontSize = 11.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.subtitleStyle.timeOffsetMs != 0L) {
                                Text(
                                    text = "Offset: ${if (uiState.subtitleStyle.timeOffsetMs > 0) "+" else ""}${uiState.subtitleStyle.timeOffsetMs}ms",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier.size(32.dp).testTag("fullscreen_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Tam Ekran",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
