package com.example.torrent

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Pure-Kotlin magnet link helpers. They don't touch the native BitTorrent engine, so they
 * also work in unit tests and before the session starts.
 *
 * Both info-hash encodings seen in the wild are supported: 40-char hex (Nyaa) and
 * 32-char Base32 (SubsPlease).
 */
object MagnetLinks {

    /** Trackers Nyaa embeds in its own magnets, plus two long-lived public ones. */
    val PUBLIC_TRACKERS: List<String> = listOf(
        "http://nyaa.tracker.wf:7777/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://explodie.org:6969/announce"
    )

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val BTIH_PATTERN = Regex("""urn:btih:([A-Za-z0-9]+)""")

    fun isMagnet(text: String): Boolean = text.trim().startsWith("magnet:?", ignoreCase = true)

    /** Cleans a magnet copied from a web page or chat: surrounding/embedded whitespace and HTML-escaped '&'. */
    fun normalize(raw: String): String = raw.trim()
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), "")

    /** The v1 info-hash as 40 lowercase hex chars, or null when the magnet has none. */
    fun infoHashHex(magnet: String): String? {
        val value = BTIH_PATTERN.find(magnet)?.groupValues?.get(1) ?: return null
        return when (value.length) {
            40 -> value.takeIf { v -> v.all { it.isHexDigit() } }?.lowercase(Locale.ROOT)
            32 -> base32ToHex(value)
            else -> null
        }
    }

    fun displayName(magnet: String): String? = parameter(magnet, "dn")?.takeIf { it.isNotBlank() }

    /** Exact payload size from the optional `xl` parameter. */
    fun exactLength(magnet: String): Long? = parameter(magnet, "xl")?.toLongOrNull()?.takeIf { it > 0L }

    fun build(infoHashHex: String, name: String?, trackers: List<String> = PUBLIC_TRACKERS): String {
        val sb = StringBuilder("magnet:?xt=urn:btih:").append(infoHashHex.lowercase(Locale.ROOT))
        if (!name.isNullOrBlank()) sb.append("&dn=").append(encode(name))
        trackers.forEach { sb.append("&tr=").append(encode(it)) }
        return sb.toString()
    }

    /** RFC 4648 Base32 (A–Z, 2–7) to lowercase hex; null for invalid input. */
    fun base32ToHex(base32: String): String? {
        val clean = base32.trim().trimEnd('=').uppercase(Locale.ROOT)
        if (clean.isEmpty()) return null
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = BASE32_ALPHABET.indexOf(c)
            if (value < 0) return null
            buffer = ((buffer shl 5) or value) and 0xFFFF
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private fun parameter(magnet: String, key: String): String? {
        val query = magnet.substringAfter('?', "")
        for (part in query.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0 || !part.substring(0, eq).equals(key, ignoreCase = true)) continue
            val raw = part.substring(eq + 1)
            return try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: Exception) {
                raw
            }
        }
        return null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
