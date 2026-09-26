package com.example.encode

import java.io.File

/**
 * State representing current hardsub encoding phase, progress, and output.
 */
data class EncodeState(
    val isPreparing: Boolean = false,
    val isEncoding: Boolean = false,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val currentPhaseText: String = "Hazır",
    val currentEncoderName: String = "libx264 (Termux FFmpeg)",
    val progress: Float = 0f, // 0.0 to 1.0
    val progressPercentage: Int = 0, // 0 to 100
    val currentFrame: Long = 0L,
    val totalFrames: Long = 0L,
    val currentFps: Double = 0.0,
    val fpsToTotalFramesRatio: Double = 0.0,
    val estimatedRemainingSeconds: Long = 0L,
    val averageEstimatedFinishText: String = "--:--",
    val elapsedSeconds: Long = 0L,
    val outputVideoFile: File? = null,
    val errorMessage: String? = null,
    val lastLogLine: String = "",
    val fullLogs: String = "",
    val sourceMetadata: SourceVideoMetadata = SourceVideoMetadata()
)

/**
 * Supported video encoder types.
 * Hardware encoders (MediaCodec) use target bitrates; software encoders (libx264/libx265) use CRF & presets.
 */
enum class EncoderOption(
    val id: String,
    val displayName: String,
    val ffmpegName: String,
    val isHardware: Boolean,
    val supportsCrf: Boolean,
    val supportsPreset: Boolean,
    val defaultBitrate: String = "4000k"
) {
    AUTO(
        id = "auto",
        displayName = "En Uyumlu (Otomatik)",
        ffmpegName = "auto",
        isHardware = false,
        supportsCrf = true,
        supportsPreset = true
    ),
    MEDIA_CODEC_H264(
        id = "h264_mediacodec",
        displayName = "MediaCodec H.264 (Donanım)",
        ffmpegName = "h264_mediacodec",
        isHardware = true,
        supportsCrf = false,
        supportsPreset = false,
        defaultBitrate = "4500k"
    ),
    MEDIA_CODEC_H265(
        id = "hevc_mediacodec",
        displayName = "MediaCodec H.265 / HEVC (Donanım)",
        ffmpegName = "hevc_mediacodec",
        isHardware = true,
        supportsCrf = false,
        supportsPreset = false,
        defaultBitrate = "3200k"
    ),
    LIBX264(
        id = "libx264",
        displayName = "libx264 (Yazılım / Evrensel)",
        ffmpegName = "libx264",
        isHardware = false,
        supportsCrf = true,
        supportsPreset = true
    ),
    LIBX265(
        id = "libx265",
        displayName = "libx265 (Yazılım / Yüksek Sıkıştırma)",
        ffmpegName = "libx265",
        isHardware = false,
        supportsCrf = true,
        supportsPreset = true
    ),
    VP9(
        id = "vp9",
        displayName = "VP9 (Google)",
        ffmpegName = "libvpx-vp9",
        isHardware = false,
        supportsCrf = true,
        supportsPreset = false,
        defaultBitrate = "3000k"
    ),
    AV1(
        id = "av1",
        displayName = "AV1 (AOMedia)",
        ffmpegName = "libaom-av1",
        isHardware = false,
        supportsCrf = true,
        supportsPreset = false,
        defaultBitrate = "2500k"
    )
}

/**
 * User-selected or detected encoding settings.
 */
data class EncodingSettings(
    val encoderOption: EncoderOption = EncoderOption.AUTO,
    val videoCodec: String = "Otomatik", // "Otomatik", "H.264 / AVC", "H.265 / HEVC", "VP9", "AV1"
    val crf: Int = 18, // 16 to 28 (User requested 18)
    val preset: String = "veryfast", // ultrafast, superfast, veryfast, faster, fast, medium
    val bitrate: String = "Otomatik", // "Otomatik", "2000k", "4000k", "6000k", "8000k", "12000k"
    val fps: String = "Kaynakla Aynı", // "Kaynakla Aynı", "24", "30", "60"
    val resolution: String = "Kaynakla Aynı", // "Kaynakla Aynı", "1080p", "720p", "480p"
    val pixelFormat: String = "yuv420p", // "yuv420p", "nv12", "nv21", "Otomatik"
    val hardwareAcceleration: String = "Otomatik" // "Otomatik", "Zorunlu Açık", "Kapalı"
)

/**
 * Metadata extracted from input video.
 */
data class SourceVideoMetadata(
    val codec: String = "Bilinmiyor",
    val resolution: String = "Bilinmiyor",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 30.0,
    val bitrateKbps: Long = 0L,
    val durationMs: Long = 0L,
    val audioCodec: String = "AAC",
    val pixelFormat: String = "yuv420p",
    val isHdr: Boolean = false
) {
    fun toDisplayLines(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add("Codec" to codec)
        list.add("Çözünürlük" to if (width > 0 && height > 0) "${width}x${height}" else resolution)
        list.add("FPS" to String.format(java.util.Locale.US, "%.1f", fps))
        list.add("Ses" to audioCodec)
        if (bitrateKbps > 0) {
            list.add("Bitrate" to "${bitrateKbps} kbps")
        }
        if (durationMs > 0) {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            list.add("Süre" to String.format(java.util.Locale.US, "%02d:%02d", min, sec))
        }
        if (isHdr) {
            list.add("HDR/SDR" to "HDR Destekli")
        }
        return list
    }
}

/**
 * Result of individual compatibility check during "Uyumluluk Testi".
 */
data class CompatibilityTestItem(
    val title: String,
    val statusText: String,
    val isOk: Boolean,
    val details: String = ""
)
