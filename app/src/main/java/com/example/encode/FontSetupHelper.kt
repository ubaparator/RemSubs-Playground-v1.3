package com.example.encode

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.R
import java.io.File
import java.io.FileOutputStream

object FontSetupHelper {
    private const val TAG = "FontSetupHelper"

    /**
     * Prepares the fonts directory with bundled Google fonts (Roboto, Montserrat),
     * creates standard fallback font aliases (Arial, sans-serif, Default),
     * copies any user-provided custom font, and registers everything with FFmpegKitConfig.
     *
     * @return the prepared fonts directory File.
     */
    fun setupFonts(context: Context, customFontFile: File? = null): File {
        val fontsDir = File(context.cacheDir, "fonts").apply { mkdirs() }

        // 1. Copy bundled fonts from APK resources
        val robotoDest = File(fontsDir, "roboto.ttf")
        copyRawFontResourceIfNeeded(context, R.font.roboto, robotoDest)

        val montserratDest = File(fontsDir, "montserrat.ttf")
        copyRawFontResourceIfNeeded(context, R.font.montserrat, montserratDest)

        // 2. Create font aliases so any common ASS font name resolves to a real font file
        if (robotoDest.exists() && robotoDest.length() > 0L) {
            copyFileIfMissing(robotoDest, File(fontsDir, "Roboto.ttf"))
            copyFileIfMissing(robotoDest, File(fontsDir, "Arial.ttf"))
            copyFileIfMissing(robotoDest, File(fontsDir, "arial.ttf"))
            copyFileIfMissing(robotoDest, File(fontsDir, "sans-serif.ttf"))
            copyFileIfMissing(robotoDest, File(fontsDir, "Default.ttf"))
            copyFileIfMissing(robotoDest, File(fontsDir, "default.ttf"))
        }

        if (montserratDest.exists() && montserratDest.length() > 0L) {
            copyFileIfMissing(montserratDest, File(fontsDir, "Montserrat.ttf"))
        }

        // 3. User's custom font if provided
        if (customFontFile != null && customFontFile.exists() && customFontFile.canRead() && customFontFile.length() > 0L) {
            try {
                val dest = File(fontsDir, customFontFile.name)
                if (!dest.exists() || dest.length() != customFontFile.length()) {
                    customFontFile.copyTo(dest, overwrite = true)
                }
                Log.i(TAG, "Özel font başarıyla yüklendi: ${customFontFile.name} (${customFontFile.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Özel font kopyalanamadı: ${e.localizedMessage}")
            }
        }

        // 4. Configure FFmpegKit Font Directories & Font Mapping
        val dirList = mutableListOf<String>()
        dirList.add(fontsDir.absolutePath)
        val sysFonts = File("/system/fonts")
        if (sysFonts.exists() && sysFonts.canRead()) {
            dirList.add(sysFonts.absolutePath)
        }

        val fontMapping = mutableMapOf<String, String>()
        fontMapping["Default"] = "Roboto"
        fontMapping["Varsayılan (System)"] = "Roboto"
        fontMapping["Varsayılan"] = "Roboto"
        fontMapping["System"] = "Roboto"
        fontMapping["Arial"] = "Roboto"
        fontMapping["arial"] = "Roboto"
        fontMapping["sans-serif"] = "Roboto"
        fontMapping["Roboto"] = "Roboto"
        fontMapping["Montserrat"] = "Montserrat"

        if (customFontFile != null) {
            val baseName = customFontFile.nameWithoutExtension
            fontMapping[baseName] = baseName
            fontMapping[customFontFile.name] = baseName
        }

        try {
            FFmpegKitConfig.setFontDirectoryList(context, dirList, fontMapping)
            Log.i(TAG, "FFmpegKit font yapılandırması başarıyla tamamlandı. Dizinler: $dirList, Eşlemeler: ${fontMapping.keys}")
        } catch (t: Throwable) {
            Log.w(TAG, "FFmpegKitConfig.setFontDirectoryList çağrısı atlandı (JVM/Native): ${t.localizedMessage}")
        }

        return fontsDir
    }

    private fun copyRawFontResourceIfNeeded(context: Context, resId: Int, destFile: File) {
        if (destFile.exists() && destFile.length() > 0L) {
            return
        }
        try {
            context.resources.openRawResource(resId).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Font kaynağı çıkarıldı: ${destFile.name} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            Log.w(TAG, "Font kaynağı çıkarılamadı (resId=$resId): ${e.localizedMessage}")
        }
    }

    private fun copyFileIfMissing(source: File, dest: File) {
        if (!dest.exists() || dest.length() != source.length()) {
            try {
                source.copyTo(dest, overwrite = true)
            } catch (e: Exception) {
                Log.w(TAG, "Font alias kopyalanamadı (${dest.name}): ${e.localizedMessage}")
            }
        }
    }
}
