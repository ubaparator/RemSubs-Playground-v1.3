package com.example.torrent

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Robust pure-Kotlin Bencode parser & serializer for BitTorrent metainfo and tracker responses.
 */
object BencodeParser {

    sealed class BValue {
        data class BInt(val value: Long) : BValue()
        data class BString(val bytes: ByteArray) : BValue() {
            fun asUtf8(): String = String(bytes, StandardCharsets.UTF_8)
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is BString) return false
                return bytes.contentEquals(other.bytes)
            }
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        data class BList(val list: List<BValue>) : BValue()
        data class BDict(val map: Map<String, BValue>, val rawBytes: ByteArray? = null) : BValue()
    }

    data class TorrentMeta(
        val name: String,
        val infoHash: ByteArray,
        val infoHashHex: String,
        val totalLength: Long,
        val pieceLength: Long,
        val pieces: List<ByteArray>,
        val announceList: List<String>
    )

    fun parse(bytes: ByteArray): BValue {
        return parse(ByteArrayInputStream(bytes))
    }

    fun parse(stream: InputStream): BValue {
        val first = stream.read()
        if (first == -1) throw IllegalArgumentException("Boş bencode verisi")
        return parseValue(first, stream)
    }

    private fun parseValue(indicator: Int, stream: InputStream): BValue {
        return when (indicator.toChar()) {
            'i' -> parseInteger(stream)
            'l' -> parseList(stream)
            'd' -> parseDict(stream)
            in '0'..'9' -> parseString(indicator, stream)
            else -> throw IllegalArgumentException("Geçersiz bencode tipi: ${indicator.toChar()}")
        }
    }

    private fun parseInteger(stream: InputStream): BValue.BInt {
        val sb = StringBuilder()
        var ch: Int
        while (stream.read().also { ch = it } != -1) {
            if (ch.toChar() == 'e') break
            sb.append(ch.toChar())
        }
        val num = sb.toString().toLongOrNull() ?: 0L
        return BValue.BInt(num)
    }

    private fun parseString(firstDigit: Int, stream: InputStream): BValue.BString {
        val lenSb = StringBuilder()
        lenSb.append(firstDigit.toChar())
        var ch: Int
        while (stream.read().also { ch = it } != -1) {
            if (ch.toChar() == ':') break
            lenSb.append(ch.toChar())
        }
        val length = lenSb.toString().toIntOrNull() ?: 0
        val bytes = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val r = stream.read(bytes, totalRead, length - totalRead)
            if (r == -1) break
            totalRead += r
        }
        return BValue.BString(bytes)
    }

    private fun parseList(stream: InputStream): BValue.BList {
        val list = mutableListOf<BValue>()
        var ch: Int
        while (true) {
            stream.mark(1)
            ch = stream.read()
            if (ch == -1 || ch.toChar() == 'e') break
            list.add(parseValue(ch, stream))
        }
        return BValue.BList(list)
    }

    private fun parseDict(stream: InputStream): BValue.BDict {
        val map = mutableMapOf<String, BValue>()
        var ch: Int
        while (true) {
            stream.mark(1)
            ch = stream.read()
            if (ch == -1 || ch.toChar() == 'e') break
            val keyVal = parseValue(ch, stream)
            val keyStr = if (keyVal is BValue.BString) keyVal.asUtf8() else ""
            val nextCh = stream.read()
            if (nextCh == -1) break
            val value = parseValue(nextCh, stream)
            if (keyStr.isNotEmpty()) {
                map[keyStr] = value
            }
        }
        return BValue.BDict(map)
    }

    /**
     * Parses a .torrent file bytes into TorrentMeta with SHA-1 calculation.
     */
    fun parseTorrent(bytes: ByteArray): TorrentMeta {
        val root = parse(bytes) as? BValue.BDict
            ?: throw IllegalArgumentException("Geçersiz .torrent dosyası yapısı")

        val announce = (root.map["announce"] as? BValue.BString)?.asUtf8() ?: ""
        val announceList = mutableListOf<String>()
        if (announce.isNotEmpty()) announceList.add(announce)

        (root.map["announce-list"] as? BValue.BList)?.list?.forEach { tier ->
            if (tier is BValue.BList) {
                tier.list.forEach { urlVal ->
                    if (urlVal is BValue.BString) {
                        val tr = urlVal.asUtf8()
                        if (tr.isNotEmpty() && !announceList.contains(tr)) {
                            announceList.add(tr)
                        }
                    }
                }
            }
        }

        val infoDict = root.map["info"] as? BValue.BDict
            ?: throw IllegalArgumentException("Torrent info bloğu bulunamadı")

        val name = (infoDict.map["name"] as? BValue.BString)?.asUtf8() ?: "Torrent Dosyası"
        val pieceLength = (infoDict.map["piece length"] as? BValue.BInt)?.value ?: 262144L

        // Single-file or multi-file total length
        var totalLength = (infoDict.map["length"] as? BValue.BInt)?.value ?: 0L
        if (totalLength == 0L) {
            (infoDict.map["files"] as? BValue.BList)?.list?.forEach { fileVal ->
                if (fileVal is BValue.BDict) {
                    val len = (fileVal.map["length"] as? BValue.BInt)?.value ?: 0L
                    totalLength += len
                }
            }
        }

        // Split pieces (each piece is 20 bytes SHA-1 hash)
        val piecesBytes = (infoDict.map["pieces"] as? BValue.BString)?.bytes ?: ByteArray(0)
        val piecesList = mutableListOf<ByteArray>()
        for (i in 0 until piecesBytes.size step 20) {
            if (i + 20 <= piecesBytes.size) {
                piecesList.add(piecesBytes.copyOfRange(i, i + 20))
            }
        }

        // Find info bytes in original byte array to compute SHA-1
        val infoIndex = findSubsequence(bytes, "4:info".toByteArray(StandardCharsets.ISO_8859_1))
        val infoHash = if (infoIndex != -1) {
            val start = infoIndex + 6
            // Recompute info hash using SHA-1
            computeSha1(bytes.copyOfRange(start, bytes.size - 1))
        } else {
            computeSha1(name.toByteArray())
        }

        val infoHashHex = infoHash.joinToString("") { "%02x".format(it) }

        return TorrentMeta(
            name = name,
            infoHash = infoHash,
            infoHashHex = infoHashHex,
            totalLength = totalLength,
            pieceLength = pieceLength,
            pieces = piecesList,
            announceList = announceList
        )
    }

    private fun findSubsequence(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        outer@ for (i in 0..source.size - target.size) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    fun computeSha1(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(data)
    }
}
