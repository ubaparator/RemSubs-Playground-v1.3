package com.example.encode

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.model.SubtitleCue
import com.example.model.SubtitleStyle
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * HardsubEncoder serves as the primary API bridge delegating "Encode Al" execution
 * to FfmpegEncodeManager using the app's internal FFmpeg + libass engine.
 *
 * No external terminal, Termux, or third-party apps are used.
 */
object HardsubEncoder {
    private const val TAG = "HardsubEncoder"

    val encodeState: StateFlow<EncodeState> = FfmpegEncodeManager.encodeState

    fun isFFmpegAvailable(): Boolean {
        return try {
            val version = FFmpegKitConfig.getVersion()
            Log.d(TAG, "Dahili FFmpeg sürümü: $version")
            version != null
        } catch (t: Throwable) {
            Log.w(TAG, "Dahili FFmpeg kontrolü: ${t.localizedMessage}")
            false
        }
    }

    fun resetState() {
        FfmpegEncodeManager.resetState()
    }

    fun cancelEncoding(context: Context? = null) {
        FfmpegEncodeManager.cancelEncode()
    }

    /**
     * Entry point to internal FFmpeg + libass hardsub encoding.
     */
    suspend fun startHardsubEncode(
        context: Context,
        videoUri: Uri,
        cues: List<SubtitleCue>,
        style: SubtitleStyle,
        customFontFile: File? = null,
        additionalStyles: List<String> = emptyList(),
        settings: EncodingSettings = EncodingSettings(),
        sourceMetadata: SourceVideoMetadata = SourceVideoMetadata(),
        introVideoUri: Uri? = null,
        introDurationMs: Long = 0L,
        introKeepAudio: Boolean = false
    ) {
        FfmpegEncodeManager.startEncode(
            context = context,
            videoUri = videoUri,
            cues = cues,
            style = style,
            customFontFile = customFontFile,
            additionalStyles = additionalStyles,
            settings = settings,
            sourceMetadata = sourceMetadata,
            introVideoUri = introVideoUri,
            introDurationMs = introDurationMs,
            introKeepAudio = introKeepAudio
        )
    }

    fun formatTimeSeconds(totalSeconds: Long): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}

