package com.example.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.FileOutputStream

object FontManager {
    private const val TAG = "FontManager"
    private const val FONTS_DIR = "custom_fonts"

    data class CustomFontDetails(
        val file: File,
        val displayName: String,
        val familyName: String,
        val allNames: Set<String>,
        val extension: String,
        val sizeBytes: Long
    )

    data class FontUploadResult(
        val displayName: String,
        val familyName: String,
        val file: File,
        val allNames: Set<String> = emptySet()
    )

    fun isFontFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")
    }

    fun copyTtfToInternalStorage(context: Context, uri: Uri): Pair<String, File>? {
        val res = copyFontToInternalStorage(context, uri) ?: return null
        return Pair(res.displayName, res.file)
    }

    fun copyFontToInternalStorage(context: Context, uri: Uri): FontUploadResult? {
        return try {
            val originalName = getFileName(context, uri) ?: "custom_font.ttf"
            val dir = File(context.filesDir, FONTS_DIR).apply { mkdirs() }
            val destinationFile = File(dir, originalName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (!destinationFile.exists() || destinationFile.length() <= 0L) {
                return null
            }

            val meta = FontMetadataParser.extractFontNames(destinationFile)
            val familyName = meta.familyName ?: destinationFile.nameWithoutExtension

            Log.i(TAG, "Özel font kaydedildi: ${destinationFile.name} (Ailesi: $familyName)")
            FontUploadResult(
                displayName = familyName,
                familyName = familyName,
                file = destinationFile,
                allNames = meta.allDistinctNames
            )
        } catch (e: Exception) {
            Log.e(TAG, "Font yükleme hatası: ${e.localizedMessage}", e)
            null
        }
    }

    fun copyMultipleFontsToInternalStorage(context: Context, uris: List<Uri>): List<FontUploadResult> {
        val results = mutableListOf<FontUploadResult>()
        for (uri in uris) {
            val res = copyFontToInternalStorage(context, uri)
            if (res != null) {
                results.add(res)
            }
        }
        return results
    }

    fun getAllCustomFonts(context: Context): List<File> {
        val dir = File(context.filesDir, FONTS_DIR)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && isFontFile(f.name) }?.toList() ?: emptyList()
    }

    fun getAllCustomFontDetails(context: Context): List<CustomFontDetails> {
        val files = getAllCustomFonts(context)
        return files.map { file ->
            val meta = FontMetadataParser.extractFontNames(file)
            val fam = meta.familyName ?: file.nameWithoutExtension
            CustomFontDetails(
                file = file,
                displayName = fam,
                familyName = fam,
                allNames = meta.allDistinctNames,
                extension = file.extension.lowercase(),
                sizeBytes = file.length()
            )
        }
    }

    fun deleteCustomFont(context: Context, fontFile: File): Boolean {
        return try {
            if (fontFile.exists() && fontFile.parentFile?.name == FONTS_DIR) {
                fontFile.delete()
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "Font silinemedi: ${e.localizedMessage}")
            false
        }
    }

    fun createFontFamilyFromFile(file: File): FontFamily? {
        return try {
            if (file.exists() && file.length() > 0L) {
                val typeface = Typeface.createFromFile(file)
                FontFamily(typeface)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Typeface oluşturulamadı (${file.name}): ${e.localizedMessage}")
            null
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return name
    }
}
