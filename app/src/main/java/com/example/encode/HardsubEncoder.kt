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
 * HardsubEncoder serves as the bridge delegating "Encode Al" execution to TermuxEncodeManager.
 *
 * The previous unreliable FFmpegKit hardsub execution has been completely disconnected.
 * Real encoding is executed inside Termux with FFmpeg libass.
 */
object HardsubEncoder {
    private const val TAG = "HardsubEncoder"

    val encodeState: StateFlow<EncodeState> = TermuxEncodeManager.encodeState

    fun isFFmpegAvailable(): Boolean {
        return try {
            val version = FFmpegKitConfig.getVersion()
            Log.d(TAG, "Native library inspection: $version")
            version != null
        } catch (t: Throwable) {
            Log.w(TAG, "FFmpeg native library check: ${t.localizedMessage}")
            false
        }
    }

    fun resetState() {
        TermuxEncodeManager.resetState()
    }

    fun cancelEncoding(context: Context? = null) {
        if (context != null) {
            TermuxEncodeManager.cancelEncode(context)
        } else {
            TermuxEncodeManager.resetState()
        }
    }

    /**
     * Entry point to REAL HARDSUB encoding via Termux FFmpeg.
     */
    suspend fun startHardsubEncode(
        context: Context,
        videoUri: Uri,
        cues: List<SubtitleCue>,
        style: SubtitleStyle,
        customFontFile: File? = null,
        additionalStyles: List<String> = emptyList(),
        settings: EncodingSettings = EncodingSettings(),
        sourceMetadata: SourceVideoMetadata = SourceVideoMetadata()
    ) {
        TermuxEncodeManager.startEncode(
            context = context,
            videoUri = videoUri,
            cues = cues,
            style = style,
            customFontFile = customFontFile,
            additionalStyles = additionalStyles,
            settings = settings,
            sourceMetadata = sourceMetadata
        )
    }

    fun formatTimeSeconds(totalSeconds: Long): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
