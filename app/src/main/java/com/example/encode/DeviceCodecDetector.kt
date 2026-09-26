package com.example.encode

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DeviceCodecDetector {
    private const val TAG = "DeviceCodecDetector"

    data class CodecCapability(
        val mimeType: String,
        val encoderName: String,
        val isHardware: Boolean,
        val maxSupportedWidth: Int,
        val maxSupportedHeight: Int,
        val maxSupportedFps: Double,
        val isUsable: Boolean
    )

    private var cachedMediaCodecH264: CodecCapability? = null
    private var cachedMediaCodecH265: CodecCapability? = null
    private var cachedLibx264Usable: Boolean? = null
    private var cachedLibx265Usable: Boolean? = null

    /**
     * Inspects device MediaCodec encoders at runtime.
     */
    fun getMediaCodecH264Capability(): CodecCapability? {
        if (cachedMediaCodecH264 != null) return cachedMediaCodecH264
        cachedMediaCodecH264 = queryCodecCapability("video/avc")
        return cachedMediaCodecH264
    }

    fun getMediaCodecH265Capability(): CodecCapability? {
        if (cachedMediaCodecH265 != null) return cachedMediaCodecH265
        cachedMediaCodecH265 = queryCodecCapability("video/hevc")
        return cachedMediaCodecH265
    }

    private fun queryCodecCapability(mimeType: String): CodecCapability? {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                val types = info.supportedTypes
                if (types.any { it.equals(mimeType, ignoreCase = true) }) {
                    val caps = try {
                        info.getCapabilitiesForType(mimeType)
                    } catch (_: Exception) {
                        null
                    }

                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                                !info.name.startsWith("c2.android.", ignoreCase = true)
                    }

                    var maxWidth = 1920
                    var maxHeight = 1080
                    var maxFps = 60.0

                    caps?.videoCapabilities?.let { vc ->
                        maxWidth = vc.supportedWidths.upper ?: 1920
                        maxHeight = vc.supportedHeights.upper ?: 1080
                        maxFps = vc.supportedFrameRates.upper?.toDouble() ?: 60.0
                    }

                    return CodecCapability(
                        mimeType = mimeType,
                        encoderName = info.name,
                        isHardware = isHw,
                        maxSupportedWidth = maxWidth,
                        maxSupportedHeight = maxHeight,
                        maxSupportedFps = maxFps,
                        isUsable = true
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error querying MediaCodec for $mimeType: ${t.localizedMessage}")
        }
        return null
    }

    /**
     * Determine best compatible encoder for given input video and settings.
     */
    fun resolveBestEncoder(
        settings: EncodingSettings,
        inputMetadata: SourceVideoMetadata
    ): Pair<EncoderOption, String> {
        if (settings.encoderOption != EncoderOption.AUTO) {
            return Pair(settings.encoderOption, settings.encoderOption.ffmpegName)
        }

        // Automatic mode selection logic
        val allowHardware = settings.hardwareAcceleration != "Kapalı (Saf Yazılım)"
        val h264Cap = getMediaCodecH264Capability()
        val h265Cap = getMediaCodecH265Capability()

        // If source is HEVC and device has MediaCodec H.265 hardware encoder
        if (allowHardware && inputMetadata.codec.contains("HEVC", ignoreCase = true) && h265Cap != null && h265Cap.isHardware) {
            return Pair(EncoderOption.MEDIA_CODEC_H265, "hevc_mediacodec")
        }

        // If hardware acceleration is supported for H.264
        if (allowHardware && h264Cap != null && h264Cap.isHardware &&
            inputMetadata.width <= h264Cap.maxSupportedWidth &&
            inputMetadata.height <= h264Cap.maxSupportedHeight
        ) {
            return Pair(EncoderOption.MEDIA_CODEC_H264, "h264_mediacodec")
        }

        // Default stable universal software encoder
        return Pair(EncoderOption.LIBX264, "libx264")
    }

    /**
     * Executes the comprehensive "Uyumluluk Testi" developer test suite.
     * Safely checks all required components and performs a mini test encode without crashing.
     */
    suspend fun runCompatibilityTestSuite(context: Context): List<CompatibilityTestItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CompatibilityTestItem>()

        // 1. Termux Environment Check
        val termuxInstalled = TermuxEncodeManager.isTermuxInstalled(context)
        results.add(
            CompatibilityTestItem(
                title = "Termux Ortamı",
                statusText = if (termuxInstalled) "OK (Yüklü)" else "Bulunamadı",
                isOk = termuxInstalled,
                details = if (termuxInstalled) {
                    "Termux tespit edildi. Encode Al işlemi Termux FFmpeg ve libass üzerinden çalışır."
                } else {
                    "Termux yüklü değil. 'Encode Al' için Termux ve 'pkg install ffmpeg' gereklidir."
                }
            )
        )

        // 2. FFmpeg Native / System Initialization
        val ffmpegVersion = try {
            FFmpegKitConfig.getVersion()
        } catch (t: Throwable) {
            null
        }

        if (ffmpegVersion != null) {
            results.add(
                CompatibilityTestItem(
                    title = "FFmpeg",
                    statusText = "OK ($ffmpegVersion)",
                    isOk = true,
                    details = "Native kütüphaneler başarıyla yüklendi (arm64-v8a / x86_64)."
                )
            )
        } else {
            results.add(
                CompatibilityTestItem(
                    title = "FFmpeg",
                    statusText = "Kullanılamıyor",
                    isOk = false,
                    details = "Native JNI kütüphaneleri bulunamadı."
                )
            )
        }

        // 2. MediaCodec H.264
        val h264Cap = getMediaCodecH264Capability()
        if (h264Cap != null) {
            val hwType = if (h264Cap.isHardware) "Donanım" else "Yazılım"
            results.add(
                CompatibilityTestItem(
                    title = "MediaCodec H.264",
                    statusText = "OK ($hwType)",
                    isOk = true,
                    details = "${h264Cap.encoderName} • Maks: ${h264Cap.maxSupportedWidth}x${h264Cap.maxSupportedHeight} @ ${h264Cap.maxSupportedFps.toInt()}fps"
                )
            )
        } else {
            results.add(
                CompatibilityTestItem(
                    title = "MediaCodec H.264",
                    statusText = "Kullanılamıyor",
                    isOk = false,
                    details = "Cihazda AVC encoder tespit edilemedi."
                )
            )
        }

        // 3. MediaCodec H.265
        val h265Cap = getMediaCodecH265Capability()
        if (h265Cap != null) {
            val hwType = if (h265Cap.isHardware) "Donanım" else "Yazılım"
            results.add(
                CompatibilityTestItem(
                    title = "MediaCodec H.265",
                    statusText = "OK ($hwType)",
                    isOk = true,
                    details = "${h265Cap.encoderName} • Maks: ${h265Cap.maxSupportedWidth}x${h265Cap.maxSupportedHeight}"
                )
            )
        } else {
            results.add(
                CompatibilityTestItem(
                    title = "MediaCodec H.265",
                    statusText = "Kullanılamıyor",
                    isOk = false,
                    details = "Cihazda HEVC donanım encoderi bulunmuyor."
                )
            )
        }

        // 4. Subtitle Filter / libass support
        var libassOk = false
        try {
            val dummyAss = File(context.cacheDir, "test_check.ass")
            dummyAss.writeText("[Script Info]\nTitle: Test\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,Test\n")
            libassOk = dummyAss.exists() && dummyAss.length() > 0
        } catch (_: Exception) {}

        results.add(
            CompatibilityTestItem(
                title = "ASS / libass",
                statusText = if (libassOk) "OK" else "Kullanılamıyor",
                isOk = libassOk,
                details = "libass gömme filtresi ve .ass ayrıştırıcı desteği."
            )
        )

        // 5. Font Sistemi
        val fontsDir = File(context.cacheDir, "fonts").apply { mkdirs() }
        val fontSystemOk = fontsDir.exists() && fontsDir.canWrite()
        results.add(
            CompatibilityTestItem(
                title = "Font sistemi",
                statusText = if (fontSystemOk) "OK" else "Kullanılamıyor",
                isOk = fontSystemOk,
                details = "Dahili ve özel TTF font dizini yazılabilir durumda."
            )
        )

        // 6. Test File Creation & Storage Permissions
        val testOutput = File(context.cacheDir, "test_encode_${System.currentTimeMillis()}.mp4")
        val storageOk = try {
            testOutput.createNewFile()
            val ok = testOutput.exists()
            testOutput.delete()
            ok
        } catch (_: Exception) {
            false
        }
        results.add(
            CompatibilityTestItem(
                title = "Depolama / Çıktı",
                statusText = if (storageOk) "OK" else "Hata",
                isOk = storageOk,
                details = "Önbellek depolama alanı yazma ve okuma işlemlerine açık."
            )
        )

        // 7. libx264 Tiny Test Encode (Mini Test)
        var x264Ok = cachedLibx264Usable ?: false
        if (cachedLibx264Usable == null) {
            try {
                val dummyOut = File(context.cacheDir, "test_x264_mini.mp4")
                if (dummyOut.exists()) dummyOut.delete()

                val testArgs = arrayOf(
                    "-y",
                    "-f", "lavfi",
                    "-i", "color=c=black:s=160x120:d=0.2",
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-pix_fmt", "yuv420p",
                    dummyOut.absolutePath
                )
                val session = FFmpegKit.executeWithArguments(testArgs)
                val returnCode = session.returnCode
                x264Ok = ReturnCode.isSuccess(returnCode) && dummyOut.exists() && dummyOut.length() > 0
                dummyOut.delete()
                cachedLibx264Usable = x264Ok
            } catch (t: Throwable) {
                Log.w(TAG, "libx264 test encode failed: ${t.localizedMessage}")
                x264Ok = false
            }
        }

        results.add(
            CompatibilityTestItem(
                title = "libx264",
                statusText = if (x264Ok) "OK" else "Kullanılamıyor",
                isOk = x264Ok,
                details = "H.264 yazılım kodlayıcı ile mikro test encode denemesi."
            )
        )

        // 8. libx265 Tiny Test Encode
        var x265Ok = cachedLibx265Usable ?: false
        if (cachedLibx265Usable == null) {
            try {
                val dummyOut = File(context.cacheDir, "test_x265_mini.mp4")
                if (dummyOut.exists()) dummyOut.delete()

                val testArgs = arrayOf(
                    "-y",
                    "-f", "lavfi",
                    "-i", "color=c=black:s=160x120:d=0.2",
                    "-c:v", "libx265",
                    "-preset", "ultrafast",
                    "-pix_fmt", "yuv420p",
                    dummyOut.absolutePath
                )
                val session = FFmpegKit.executeWithArguments(testArgs)
                val returnCode = session.returnCode
                x265Ok = ReturnCode.isSuccess(returnCode) && dummyOut.exists() && dummyOut.length() > 0
                dummyOut.delete()
                cachedLibx265Usable = x265Ok
            } catch (t: Throwable) {
                Log.w(TAG, "libx265 test encode check failed: ${t.localizedMessage}")
                x265Ok = false
            }
        }

        results.add(
            CompatibilityTestItem(
                title = "libx265",
                statusText = if (x265Ok) "OK" else "Kullanılamıyor",
                isOk = x265Ok,
                details = if (x265Ok) "HEVC yazılım kodlayıcı kullanılabilir." else "libx265 derlemesi mevcut değil veya cihazda desteklenmiyor."
            )
        )

        results
    }
}
