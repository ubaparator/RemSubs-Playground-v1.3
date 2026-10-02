package com.example.torrent

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Pure Kotlin Bencode parser and Torrent file inspector.
 * Provides offline, native-independent validation and inspection of .torrent files:
 * - Reads info_hash (SHA-1 of the exact raw bencoded 'info' dict bytes)
 * - Reads single-file or multi-file structure
 * - Reads pieces, piece length, total length, announce URLs
 * - Guarantees error diagnosis without relying solely on JNI.
 */
object BencodeParser {

    data class TorrentMetadata(
        val name: String,
        val infoHashHex: String,
        val totalSize: Long,
        val pieceLength: Int,
        val pieceCount: Int,
        val pieceHashes: List<String>,
        val isMultiFile: Boolean,
        val files: List<TorrentFileInfo>,
        val announce: String?
    )

    data class TorrentFileInfo(
        val path: String,
        val length: Long
    )

    fun parseTorrent(file: File): TorrentMetadata {
        return parseTorrent(file.readBytes())
    }

    fun parseTorrent(bytes: ByteArray): TorrentMetadata {
        val stream = ByteArrayInputStream(bytes)
        val root = decode(stream) as? Map<String, Any?>
            ?: throw IllegalArgumentException("Bencode kök yapısı bir sözlük (dictionary) değil.")

        val announce = (root["announce"] as? ByteArray)?.toString(Charsets.UTF_8)

        // Find raw bytes of 'info' dictionary to compute true info_hash
        val infoBytes = extractInfoDictBytes(bytes)
            ?: throw IllegalArgumentException(".torrent dosyası geçerli bir 'info' sözlüğü içermiyor.")

        val infoHash = MessageDigest.getInstance("SHA-1").digest(infoBytes)
        val infoHashHex = infoHash.joinToString("") { "%02x".format(it) }

        val info = root["info"] as? Map<String, Any?>
            ?: throw IllegalArgumentException("Eksik 'info' sözlüğü.")

        val name = (info["name"] as? ByteArray)?.toString(Charsets.UTF_8)
            ?: (info["name.utf-8"] as? ByteArray)?.toString(Charsets.UTF_8)
            ?: "Adsız Torrent"

        val pieceLength = (info["piece length"] as? Long)?.toInt() ?: 0
        val rawPieces = info["pieces"] as? ByteArray ?: ByteArray(0)
        val pieceCount = rawPieces.size / 20

        val pieceHashes = mutableListOf<String>()
        for (i in 0 until pieceCount) {
            val hash = rawPieces.copyOfRange(i * 20, (i + 1) * 20)
            pieceHashes.add(hash.joinToString("") { "%02x".format(it) })
        }

        val rawFiles = info["files"] as? List<*>
        val files = mutableListOf<TorrentFileInfo>()
        var totalSize = 0L
        val isMultiFile = rawFiles != null && rawFiles.isNotEmpty()

        if (isMultiFile) {
            for (item in rawFiles!!) {
                val fMap = item as? Map<*, *> ?: continue
                val length = (fMap["length"] as? Long) ?: 0L
                val pathList = fMap["path"] as? List<*>
                val path = pathList?.mapNotNull { (it as? ByteArray)?.toString(Charsets.UTF_8) }
                    ?.joinToString(File.separator) ?: "dosya"
                files.add(TorrentFileInfo(path = path, length = length))
                totalSize += length
            }
        } else {
            val length = (info["length"] as? Long) ?: 0L
            files.add(TorrentFileInfo(path = name, length = length))
            totalSize = length
        }

        return TorrentMetadata(
            name = name,
            infoHashHex = infoHashHex,
            totalSize = totalSize,
            pieceLength = pieceLength,
            pieceCount = pieceCount,
            pieceHashes = pieceHashes,
            isMultiFile = isMultiFile,
            files = files,
            announce = announce
        )
    }

    private fun extractInfoDictBytes(bytes: ByteArray): ByteArray? {
        // Search for "4:info"
        val pattern = "4:info".toByteArray(Charsets.ISO_8859_1)
        var startIndex = -1
        for (i in 0 until (bytes.size - pattern.size)) {
            var match = true
            for (j in pattern.indices) {
                if (bytes[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                startIndex = i + pattern.size
                break
            }
        }

        if (startIndex == -1 || startIndex >= bytes.size) return null

        // Decode the info element starting at startIndex
        val stream = ByteArrayInputStream(bytes, startIndex, bytes.size - startIndex)
        val length = measureElementLength(stream)
        if (length <= 0) return null

        return bytes.copyOfRange(startIndex, startIndex + length)
    }

    private fun measureElementLength(stream: InputStream): Int {
        var count = 0
        fun readByte(): Int {
            val b = stream.read()
            if (b != -1) count++
            return b
        }

        fun skipBencode(): Boolean {
            val b = readByte()
            if (b == -1) return false
            when (b.toChar()) {
                'i' -> {
                    while (true) {
                        val c = readByte()
                        if (c == -1) return false
                        if (c.toChar() == 'e') break
                    }
                    return true
                }
                'l', 'd' -> {
                    while (true) {
                        stream.mark(1)
                        val next = readByte()
                        if (next == -1) return false
                        if (next.toChar() == 'e') break
                        stream.reset()
                        count--
                        if (!skipBencode()) return false
                    }
                    return true
                }
                in '0'..'9' -> {
                    val sb = StringBuilder()
                    sb.append(b.toChar())
                    while (true) {
                        val c = readByte()
                        if (c == -1) return false
                        if (c.toChar() == ':') break
                        sb.append(c.toChar())
                    }
                    val strLen = sb.toString().toIntOrNull() ?: return false
                    for (i in 0 until strLen) {
                        if (readByte() == -1) return false
                    }
                    return true
                }
                else -> return false
            }
        }

        val success = skipBencode()
        return if (success) count else -1
    }

    fun decode(stream: InputStream): Any? {
        val b = stream.read()
        if (b == -1) return null

        return when (val ch = b.toChar()) {
            'i' -> {
                val sb = StringBuilder()
                while (true) {
                    val c = stream.read()
                    if (c == -1 || c.toChar() == 'e') break
                    sb.append(c.toChar())
                }
                sb.toString().toLongOrNull() ?: 0L
            }
            'l' -> {
                val list = mutableListOf<Any?>()
                while (true) {
                    stream.mark(1)
                    val next = stream.read()
                    if (next == -1 || next.toChar() == 'e') break
                    stream.reset()
                    list.add(decode(stream))
                }
                list
            }
            'd' -> {
                val map = LinkedHashMap<String, Any?>()
                while (true) {
                    stream.mark(1)
                    val next = stream.read()
                    if (next == -1 || next.toChar() == 'e') break
                    stream.reset()
                    val keyBytes = decode(stream) as? ByteArray ?: break
                    val key = keyBytes.toString(Charsets.UTF_8)
                    val value = decode(stream)
                    map[key] = value
                }
                map
            }
            in '0'..'9' -> {
                val sb = StringBuilder()
                sb.append(ch)
                while (true) {
                    val c = stream.read()
                    if (c == -1 || c.toChar() == ':') break
                    sb.append(c.toChar())
                }
                val length = sb.toString().toIntOrNull() ?: 0
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val r = stream.read(bytes, read, length - read)
                    if (r == -1) break
                    read += r
                }
                bytes
            }
            else -> null
        }
    }
}
