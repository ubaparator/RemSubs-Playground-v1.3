package com.example.encode

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.R
import com.example.util.FontManager
import com.example.util.FontMetadataParser
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object FontSetupHelper {
    private const val TAG = "FontSetupHelper"

    /**
     * Extracts all font names referenced across ASS content:
     * 1. Fontname column in [V4+ Styles] / [V4 Styles] (Format: Name, Fontname, ...)
     * 2. Inline {\fnFontName} override tags in dialogue events
     */
    fun extractAllReferencedFontNames(assContent: String, additionalStyles: List<String> = emptyList()): Set<String> {
        val names = mutableSetOf<String>()

        // 1. From additionalStyles list
        for (styleLine in additionalStyles) {
            val trimmed = styleLine.trim()
            if (trimmed.startsWith("Style:", ignoreCase = true)) {
                val parts = trimmed.substring(6).trim().split(",")
                if (parts.size >= 2) {
                    val fontName = parts[1].trim()
                    if (fontName.isNotBlank()) names.add(fontName)
                }
            }
        }

        // 2. From ASS content styles
        var inStyles = false
        for (line in assContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[V4+ Styles]", ignoreCase = true) || trimmed.startsWith("[V4 Styles]", ignoreCase = true)) {
                inStyles = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inStyles = false
            }
            if (inStyles && trimmed.startsWith("Style:", ignoreCase = true)) {
                val parts = trimmed.substring(6).trim().split(",")
                if (parts.size >= 2) {
                    val fontName = parts[1].trim()
                    if (fontName.isNotBlank()) names.add(fontName)
                }
            }

            // 3. Inline {\fnFontName} tags in events
            if (trimmed.contains("\\fn", ignoreCase = true)) {
                val regex = Regex("""\\fn([^\\}]+)""", RegexOption.IGNORE_CASE)
                regex.findAll(trimmed).forEach { match ->
                    val fn = match.groupValues[1].trim()
                    if (fn.isNotBlank()) names.add(fn)
                }
            }
        }

        return names
    }

    /**
     * Prepares the fonts directory with:
     * 1. Priority #1: All user-provided custom fonts (.ttf, .otf, .ttc) with real metadata extraction & aliases
     * 2. Priority #2: Bundled Google fonts (Roboto, Montserrat) only as missing fallbacks
     * 3. Priority #3: System font directories fallback
     * 4. Registers everything with FFmpegKitConfig.setFontDirectoryList
     *
     * @param targetFontsDir The directory to place fonts in (e.g. cacheDir/encode/fonts)
     * @return the prepared fonts directory File.
     */
    fun setupFonts(
        context: Context,
        targetFontsDir: File? = null,
        customFontFile: File? = null,
        additionalCustomFonts: List<File> = emptyList(),
        referencedFontNames: Set<String> = emptySet()
    ): File {
        val fontsDir = targetFontsDir ?: File(File(context.cacheDir, "encode"), "fonts")
        fontsDir.mkdirs()

        val fontMapping = mutableMapOf<String, String>()
        val claimedFontNames = mutableSetOf<String>()

        // 1. Collect ALL custom fonts (passed explicitly + stored in app custom_fonts directory)
        val allCustomFontFiles = mutableListOf<File>()
        if (customFontFile != null && customFontFile.exists() && customFontFile.length() > 0L) {
            allCustomFontFiles.add(customFontFile)
        }
        allCustomFontFiles.addAll(additionalCustomFonts.filter { it.exists() && it.length() > 0L })
        allCustomFontFiles.addAll(FontManager.getAllCustomFonts(context))

        val uniqueCustomFonts = allCustomFontFiles.distinctBy { it.absolutePath }

        // 2. PRIORITY #1: Process each user-provided custom font file FIRST!
        for (fontFile in uniqueCustomFonts) {
            try {
                val ext = fontFile.extension.ifBlank { "ttf" }
                val destOriginal = File(fontsDir, fontFile.name)
                if (!destOriginal.exists() || destOriginal.length() != fontFile.length()) {
                    fontFile.copyTo(destOriginal, overwrite = true)
                }

                // Parse real internal font family, full name, and PostScript names
                val meta = FontMetadataParser.extractFontNames(fontFile)
                val familyName = meta.familyName ?: fontFile.nameWithoutExtension

                // All candidate names for this font
                val candidateNames = mutableSetOf<String>()
                candidateNames.addAll(meta.allDistinctNames)
                candidateNames.add(familyName)
                candidateNames.add(fontFile.nameWithoutExtension)
                meta.fullName?.let { candidateNames.add(it) }
                meta.postScriptName?.let { candidateNames.add(it) }
                meta.typographicFamily?.let { candidateNames.add(it) }

                // Also generate no-space versions (e.g. "Anime Font" -> "AnimeFont")
                val noSpaceNames = candidateNames.map { it.replace(" ", "") }.filter { it.isNotBlank() }
                candidateNames.addAll(noSpaceNames)

                for (name in candidateNames) {
                    val safeAliasName = name.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
                    if (safeAliasName.isNotBlank()) {
                        val aliasFile = File(fontsDir, "$safeAliasName.$ext")
                        copyFileIfMissing(fontFile, aliasFile)
                        fontMapping[safeAliasName] = familyName
                        fontMapping[safeAliasName.lowercase(Locale.ROOT)] = familyName
                        claimedFontNames.add(safeAliasName.lowercase(Locale.ROOT))
                    }
                }

                // If ASS references a specific font name that resembles this file or family, link it!
                for (refName in referencedFontNames) {
                    val cleanRef = refName.trim()
                    if (cleanRef.isBlank()) continue
                    val cleanRefNoSpace = cleanRef.replace(" ", "").lowercase(Locale.ROOT)
                    val matches = candidateNames.any { cand ->
                        cand.equals(cleanRef, ignoreCase = true) ||
                                cand.replace(" ", "").equals(cleanRefNoSpace, ignoreCase = true) ||
                                cand.contains(cleanRef, ignoreCase = true) ||
                                cleanRef.contains(cand, ignoreCase = true)
                    }
                    if (matches) {
                        val safeRefName = cleanRef.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
                        if (safeRefName.isNotBlank()) {
                            val safeRefFile = File(fontsDir, "$safeRefName.$ext")
                            copyFileIfMissing(fontFile, safeRefFile)
                            fontMapping[safeRefName] = familyName
                            fontMapping[safeRefName.lowercase(Locale.ROOT)] = familyName
                            claimedFontNames.add(safeRefName.lowercase(Locale.ROOT))
                        }
                    }
                }

                Log.i(TAG, "Özel font başarıyla yüklendi: ${fontFile.name} (Ailesi: $familyName, İsimler: $candidateNames)")
            } catch (e: Exception) {
                Log.w(TAG, "Özel font işlenirken hata (${fontFile.name}): ${e.localizedMessage}")
            }
        }

        // 3. PRIORITY #2: Copy bundled fonts (Roboto, Montserrat) as fallback ONLY if not claimed by user font
        val robotoDest = File(fontsDir, "roboto.ttf")
        copyRawFontResourceIfNeeded(context, R.font.roboto, robotoDest)

        val montserratDest = File(fontsDir, "montserrat.ttf")
        copyRawFontResourceIfNeeded(context, R.font.montserrat, montserratDest)

        if (robotoDest.exists() && robotoDest.length() > 0L) {
            val standardFallbacks = listOf("Roboto", "Arial", "arial", "sans-serif", "Default", "default", "System")
            for (fallbackName in standardFallbacks) {
                if (!claimedFontNames.contains(fallbackName.lowercase(Locale.ROOT))) {
                    copyFileIfMissing(robotoDest, File(fontsDir, "$fallbackName.ttf"))
                    fontMapping.putIfAbsent(fallbackName, "Roboto")
                    fontMapping.putIfAbsent(fallbackName.lowercase(Locale.ROOT), "Roboto")
                }
            }
        }

        if (montserratDest.exists() && montserratDest.length() > 0L) {
            if (!claimedFontNames.contains("montserrat")) {
                copyFileIfMissing(montserratDest, File(fontsDir, "Montserrat.ttf"))
                fontMapping.putIfAbsent("Montserrat", "Montserrat")
                fontMapping.putIfAbsent("montserrat", "Montserrat")
            }
        }

        // 4. PRIORITY #3: System font directories fallback
        val dirList = mutableListOf<String>()
        dirList.add(fontsDir.absolutePath)

        val sysFonts = File("/system/fonts")
        if (sysFonts.exists() && sysFonts.canRead()) {
            dirList.add(sysFonts.absolutePath)
        }

        try {
            FFmpegKitConfig.setFontDirectoryList(context, dirList, fontMapping)
            Log.i(TAG, "FFmpegKit font yapılandırması tamamlandı. Dizin: ${fontsDir.absolutePath}, Toplam Eşleme: ${fontMapping.size}")
        } catch (t: Throwable) {
            Log.w(TAG, "FFmpegKitConfig.setFontDirectoryList çağrısı atlandı: ${t.localizedMessage}")
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
                    output.flush()
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
