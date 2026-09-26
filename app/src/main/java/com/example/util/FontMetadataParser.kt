package com.example.util

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin TrueType (.ttf), OpenType (.otf), and TrueType Collection (.ttc) metadata parser.
 * Extracts the real Font Family Name (nameID 1 & 16), Full Font Name (nameID 4),
 * and PostScript Name (nameID 6) directly from the SFNT 'name' table.
 */
object FontMetadataParser {
    private const val TAG = "FontMetadataParser"

    private const val TAG_NAME = 0x6E616D65 // 'name'
    private const val TAG_TTCF = 0x74746366 // 'ttcf'

    data class FontNames(
        val familyName: String? = null,
        val fullName: String? = null,
        val typographicFamily: String? = null,
        val postScriptName: String? = null,
        val allDistinctNames: Set<String> = emptySet()
    )

    fun extractFontNames(file: File): FontNames {
        if (!file.exists() || file.length() < 12L) {
            val base = file.nameWithoutExtension
            return FontNames(familyName = base, fullName = base, allDistinctNames = setOf(base))
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = raf.readInt()
                val offsetList = mutableListOf<Long>()

                if (magic == TAG_TTCF) {
                    // TrueType Collection
                    val ttcVersion = raf.readInt()
                    val numFonts = raf.readInt()
                    for (i in 0 until minOf(numFonts, 8)) {
                        offsetList.add(raf.readInt().toLong() and 0xFFFFFFFFL)
                    }
                } else {
                    offsetList.add(0L)
                }

                val allFamilies = mutableSetOf<String>()
                val allFullNames = mutableSetOf<String>()
                val allPostScript = mutableSetOf<String>()
                val allTypographic = mutableSetOf<String>()

                for (fontOffset in offsetList) {
                    raf.seek(fontOffset)
                    val sfntVersion = raf.readInt()
                    val numTables = raf.readUnsignedShort()
                    val searchRange = raf.readUnsignedShort()
                    val entrySelector = raf.readUnsignedShort()
                    val rangeShift = raf.readUnsignedShort()

                    var nameTableOffset = -1L
                    var nameTableLength = 0L

                    for (i in 0 until numTables) {
                        val tableTag = raf.readInt()
                        val checkSum = raf.readInt()
                        val tableOffset = raf.readInt().toLong() and 0xFFFFFFFFL
                        val tableLength = raf.readInt().toLong() and 0xFFFFFFFFL

                        if (tableTag == TAG_NAME) {
                            nameTableOffset = tableOffset
                            nameTableLength = tableLength
                            break
                        }
                    }

                    if (nameTableOffset > 0L) {
                        parseNameTable(
                            raf,
                            nameTableOffset,
                            allFamilies,
                            allFullNames,
                            allTypographic,
                            allPostScript
                        )
                    }
                }

                val primaryFamily = allTypographic.firstOrNull() ?: allFamilies.firstOrNull()
                val primaryFull = allFullNames.firstOrNull()
                val primaryPostScript = allPostScript.firstOrNull()

                val distinctNames = mutableSetOf<String>()
                primaryFamily?.let { distinctNames.add(it) }
                primaryFull?.let { distinctNames.add(it) }
                primaryPostScript?.let { distinctNames.add(it) }
                distinctNames.addAll(allFamilies)
                distinctNames.addAll(allFullNames)
                distinctNames.addAll(allTypographic)
                distinctNames.addAll(allPostScript)
                distinctNames.add(file.nameWithoutExtension)

                Log.d(TAG, "Parsed font '${file.name}': Family=$primaryFamily, Full=$primaryFull, All=$distinctNames")

                return FontNames(
                    familyName = primaryFamily ?: file.nameWithoutExtension,
                    fullName = primaryFull ?: file.nameWithoutExtension,
                    typographicFamily = allTypographic.firstOrNull(),
                    postScriptName = primaryPostScript,
                    allDistinctNames = distinctNames.filter { it.isNotBlank() }.toSet()
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not parse font metadata from ${file.name}: ${t.localizedMessage}")
            val base = file.nameWithoutExtension
            return FontNames(familyName = base, fullName = base, allDistinctNames = setOf(base))
        }
    }

    private fun parseNameTable(
        raf: RandomAccessFile,
        nameOffset: Long,
        families: MutableSet<String>,
        fullNames: MutableSet<String>,
        typographic: MutableSet<String>,
        postScript: MutableSet<String>
    ) {
        raf.seek(nameOffset)
        val format = raf.readUnsignedShort()
        val numRecords = raf.readUnsignedShort()
        val stringStorageOffset = nameOffset + raf.readUnsignedShort()

        data class Record(
            val platformID: Int,
            val encodingID: Int,
            val languageID: Int,
            val nameID: Int,
            val length: Int,
            val offset: Int
        )

        val records = mutableListOf<Record>()
        for (i in 0 until numRecords) {
            records.add(
                Record(
                    platformID = raf.readUnsignedShort(),
                    encodingID = raf.readUnsignedShort(),
                    languageID = raf.readUnsignedShort(),
                    nameID = raf.readUnsignedShort(),
                    length = raf.readUnsignedShort(),
                    offset = raf.readUnsignedShort()
                )
            )
        }

        // Sort by preference: Unicode / Windows English first, then Macintosh
        val sortedRecords = records.sortedWith(
            compareByDescending<Record> { it.platformID == 3 && it.languageID == 0x0409 } // Windows US English
                .thenByDescending { it.platformID == 3 } // Windows
                .thenByDescending { it.platformID == 0 } // Unicode
                .thenByDescending { it.platformID == 1 } // Mac
        )

        for (rec in sortedRecords) {
            if (rec.nameID !in listOf(1, 2, 4, 6, 16)) continue
            if (rec.length <= 0) continue

            val strOffset = stringStorageOffset + rec.offset
            raf.seek(strOffset)
            val bytes = ByteArray(rec.length)
            raf.readFully(bytes)

            val parsedString = try {
                if (rec.platformID == 3 || rec.platformID == 0) {
                    String(bytes, StandardCharsets.UTF_16BE).trim()
                } else if (rec.platformID == 1) {
                    String(bytes, StandardCharsets.ISO_8859_1).trim()
                } else {
                    String(bytes, StandardCharsets.UTF_8).trim()
                }
            } catch (_: Exception) { "" }

            val cleanStr = parsedString.replace("\u0000", "").trim()
            if (cleanStr.isNotBlank()) {
                when (rec.nameID) {
                    1 -> families.add(cleanStr)
                    4 -> fullNames.add(cleanStr)
                    6 -> postScript.add(cleanStr)
                    16 -> typographic.add(cleanStr)
                }
            }
        }
    }
}
