package com.example.encode

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

object InputVideoAnalyzer {
    private const val TAG = "InputVideoAnalyzer"

    fun analyzeVideo(context: Context, videoFile: File?, videoUri: Uri?): SourceVideoMetadata {
        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var durationMs = 0L
        var fps = 30.0
        var bitrateKbps = 0L
        var codecName = "H.264 / AVC"
        var audioCodecName = "AAC"
        var isHdr = false
        var pixelFormat = "yuv420p"

        try {
            if (videoFile != null && videoFile.exists()) {
                retriever.setDataSource(videoFile.absolutePath)
            } else if (videoUri != null) {
                retriever.setDataSource(context, videoUri)
            }

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            width = widthStr?.toIntOrNull() ?: 1920
            height = heightStr?.toIntOrNull() ?: 1080
            durationMs = durationStr?.toLongOrNull() ?: 0L
            bitrateKbps = (bitrateStr?.toLongOrNull() ?: 0L) / 1000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                fps = fpsStr?.toDoubleOrNull() ?: 30.0
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val colorStandard = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
                val colorTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                if (colorTransfer == "6" || colorTransfer == "7" || colorStandard == "6") {
                    isHdr = true
                }
            }

            // Inspect mime types with MediaExtractor for true codec names
            val extractor = MediaExtractor()
            try {
                if (videoFile != null && videoFile.exists()) {
                    extractor.setDataSource(videoFile.absolutePath)
                } else if (videoUri != null) {
                    extractor.setDataSource(context, videoUri, null)
                }

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/")) {
                        codecName = when {
                            mime.contains("avc") || mime.contains("h264") -> "H.264 / AVC"
                            mime.contains("hevc") || mime.contains("h265") -> "H.265 / HEVC"
                            mime.contains("vp9") -> "VP9"
                            mime.contains("av01") || mime.contains("av1") -> "AV1"
                            mime.contains("mp4v") -> "MPEG-4"
                            else -> mime.removePrefix("video/").uppercase()
                        }
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            try {
                                fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                            } catch (_: Exception) {}
                        }
                    } else if (mime.startsWith("audio/")) {
                        audioCodecName = when {
                            mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                            mime.contains("opus") -> "Opus"
                            mime.contains("mp3") || mime.contains("mpeg") -> "MP3"
                            mime.contains("ac3") -> "AC3"
                            mime.contains("flac") -> "FLAC"
                            else -> mime.removePrefix("audio/").uppercase()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaExtractor inspection fallback: ${e.localizedMessage}")
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }

        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever error: ${e.localizedMessage}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val resolutionStr = if (width > 0 && height > 0) "${width}x${height}" else "1920x1080"

        return SourceVideoMetadata(
            codec = codecName,
            resolution = resolutionStr,
            width = width,
            height = height,
            fps = if (fps > 0.0) fps else 30.0,
            bitrateKbps = bitrateKbps,
            durationMs = durationMs,
            audioCodec = audioCodecName,
            pixelFormat = pixelFormat,
            isHdr = isHdr
        )
    }
}
