package com.example.search

import com.example.torrent.MagnetLinks
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Nyaa's search RSS (`https://nyaa.si/?page=rss&q=...`). Nyaa's RSS ignores sort
 * parameters and returns at most 75 items, newest first.
 */
object NyaaRssParser {

    fun parse(xml: String): List<AnimeSearchResult> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Element names keep their "nyaa:" prefix when namespaces are off
            isNamespaceAware = false
        }
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
            // Not every platform parser knows this feature
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val items = document.getElementsByTagName("item")
        val results = ArrayList<AnimeSearchResult>(items.length)
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val title = item.childText("title") ?: continue
            val infoHash = item.childText("nyaa:infoHash")?.lowercase(Locale.ROOT)
                ?.takeIf { it.length == 40 } ?: continue
            val sizeText = item.childText("nyaa:size")
            results += AnimeSearchResult(
                id = infoHash,
                source = AnimeSource.NYAA,
                title = title,
                magnetUri = MagnetLinks.build(infoHash, title),
                quality = VideoQuality.detect(title),
                sizeBytes = sizeText?.let { SearchFormat.parseSize(it) },
                sizeText = sizeText,
                seeders = item.childText("nyaa:seeders")?.toIntOrNull(),
                leechers = item.childText("nyaa:leechers")?.toIntOrNull(),
                completedDownloads = item.childText("nyaa:downloads")?.toIntOrNull(),
                publishedAtMillis = item.childText("pubDate")?.let { SearchFormat.parseRfc822(it) },
                isBatch = SearchFormat.looksLikeBatch(title),
                isTrusted = item.childText("nyaa:trusted").equals("Yes", ignoreCase = true),
                isRemake = item.childText("nyaa:remake").equals("Yes", ignoreCase = true),
                pageUrl = item.childText("guid")
            )
        }
        return results
    }

    private fun Element.childText(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Parses SubsPlease's search API (`https://subsplease.org/api/?f=search&s=...`): a JSON object
 * keyed by "Show - Episode", or an empty JSON array when nothing matched.
 */
object SubsPleaseParser {

    fun parse(json: String, quality: VideoQuality): List<AnimeSearchResult> {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("[")) return emptyList()

        val root = JSONObject(trimmed)
        val results = ArrayList<AnimeSearchResult>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = root.optJSONObject(key) ?: continue
            val downloads = entry.optJSONArray("downloads") ?: continue

            var magnet: String? = null
            for (i in 0 until downloads.length()) {
                val download = downloads.optJSONObject(i) ?: continue
                if (download.optString("res") == quality.pixels) {
                    magnet = download.optString("magnet").takeIf { MagnetLinks.isMagnet(it) }
                    break
                }
            }
            if (magnet == null) continue

            val show = entry.optString("show").ifBlank { key.substringBeforeLast(" - ") }
            val episode = entry.optString("episode").ifBlank { key.substringAfterLast(" - ", "") }
            val sizeBytes = MagnetLinks.exactLength(magnet)
            results += AnimeSearchResult(
                id = MagnetLinks.infoHashHex(magnet) ?: "$key/${quality.pixels}",
                source = AnimeSource.SUBSPLEASE,
                title = MagnetLinks.displayName(magnet) ?: "[SubsPlease] $show - $episode (${quality.label})",
                magnetUri = magnet,
                showName = show,
                episode = episode.ifBlank { null },
                quality = quality,
                sizeBytes = sizeBytes,
                sizeText = sizeBytes?.let { SearchFormat.formatSize(it) },
                publishedAtMillis = SearchFormat.parseRfc822(entry.optString("release_date")),
                isBatch = episode.contains('-'),
                isTrusted = true,
                pageUrl = entry.optString("page").takeIf { it.isNotBlank() }?.let { "https://subsplease.org/shows/$it/" }
            )
        }
        return results.sortedByDescending { it.publishedAtMillis ?: 0L }
    }
}

internal object SearchFormat {
    private val BATCH_PATTERN = Regex("""(?i)\bbatch\b|\b\d{1,3}\s?[-~]\s?\d{1,3}\b""")
    private val SIZE_PATTERN = Regex("""(?i)([0-9]+(?:\.[0-9]+)?)\s*(bytes?|b|kib|kb|mib|mb|gib|gb|tib|tb)""")

    fun looksLikeBatch(title: String): Boolean = BATCH_PATTERN.containsMatchIn(title)

    /** "6.6 GiB" → bytes. */
    fun parseSize(text: String): Long? {
        val match = SIZE_PATTERN.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "kib", "kb" -> 1024.0
            "mib", "mb" -> 1024.0 * 1024
            "gib", "gb" -> 1024.0 * 1024 * 1024
            "tib", "tb" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    fun formatSize(bytes: Long): String {
        val gib = bytes / (1024.0 * 1024 * 1024)
        val mib = bytes / (1024.0 * 1024)
        return when {
            gib >= 1.0 -> String.format(Locale.US, "%.1f GiB", gib)
            mib >= 1.0 -> String.format(Locale.US, "%.0f MiB", mib)
            else -> String.format(Locale.US, "%.0f KiB", bytes / 1024.0)
        }
    }

    /** RSS/HTTP dates like "Sat, 12 Sep 2026 15:45:00 -0000". */
    fun parseRfc822(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(text.trim())?.time
        } catch (_: Exception) {
            null
        }
    }
}
