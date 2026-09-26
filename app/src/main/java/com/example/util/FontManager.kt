package com.example.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.FileOutputStream

object FontManager {

    private const val FONTS_DIR = "custom_fonts"

    fun copyTtfToInternalStorage(context: Context, uri: Uri): Pair<String, File>? {
        return try {
            val fileName = getFileName(context, uri) ?: "custom_font.ttf"
            val dir = File(context.filesDir, FONTS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val destinationFile = File(dir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            Pair(fileName, destinationFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createFontFamilyFromFile(file: File): FontFamily? {
        return try {
            if (file.exists()) {
                val typeface = Typeface.createFromFile(file)
                FontFamily(typeface)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
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
