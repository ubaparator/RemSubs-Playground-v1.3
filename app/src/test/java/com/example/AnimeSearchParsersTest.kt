package com.example

import com.example.search.AnimeSource
import com.example.search.NyaaRssParser
import com.example.search.SearchFormat
import com.example.search.SubsPleaseParser
import com.example.search.VideoQuality
import com.example.torrent.MagnetLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are trimmed copies of real nyaa.si / subsplease.org responses (October 2026). */
class AnimeSearchParsersTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/search/$name")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun nyaaRssItemsBecomeMagnetResults() {
        val results = NyaaRssParser.parse(fixture("nyaa_rss_sample.xml"))
        assertEquals(3, results.size)

        val first = results[0]
        assertEquals("ef3e7ad1b12bdd9fc341691d8866cd1fa8374a4b", first.id)
        assertEquals(AnimeSource.NYAA, first.source)
        assertEquals(VideoQuality.P1080, first.quality)
        assertEquals(29, first.seeders)
        assertEquals(3, first.leechers)
        assertEquals(839, first.completedDownloads)
        assertEquals("6.6 GiB", first.sizeText)
        assertEquals(6.6 * 1024 * 1024 * 1024, first.sizeBytes!!.toDouble(), 1024.0)
        assertEquals(1789227900000L, first.publishedAtMillis)
        assertTrue(first.isBatch)
        assertFalse(first.isTrusted)
        assertEquals("https://nyaa.si/view/2160092", first.pageUrl)
        assertEquals(first.id, MagnetLinks.infoHashHex(first.magnetUri))
        assertEquals(first.title, MagnetLinks.displayName(first.magnetUri))
    }

    @Test
    fun nyaaCharacterReferencesInTitlesAreDecoded() {
        val titles = NyaaRssParser.parse(fixture("nyaa_rss_sample.xml")).map { it.title }
        assertTrue(titles.any { it.contains("Beyond Journey's End") })
        assertFalse(titles.any { it.contains("&#39;") })
    }

    @Test
    fun subsPleaseResultsUseTheSelectedResolution() {
        val results = SubsPleaseParser.parse(fixture("subsplease_search_sample.json"), VideoQuality.P1080)
        assertEquals(3, results.size)

        // Newest first: the batch (29 May) before episodes 10 (27 Mar) and 09 (20 Mar)
        assertEquals(listOf("01-10", "10", "09"), results.map { it.episode })
        assertTrue(results[0].isBatch)

        val episode10 = results[1]
        assertEquals(AnimeSource.SUBSPLEASE, episode10.source)
        assertEquals("67e65563abf71184990a54c8d3a111e349dd672f", episode10.id)
        assertEquals("[SubsPlease] Sousou no Frieren S2 - 10 (1080p) [7D35515E].mkv", episode10.title)
        assertEquals("Sousou no Frieren S2", episode10.showName)
        assertEquals(VideoQuality.P1080, episode10.quality)
        assertEquals(1457009569L, episode10.sizeBytes)
        assertEquals(1774623759000L, episode10.publishedAtMillis)
        assertFalse(episode10.isBatch)
        assertTrue(episode10.magnetUri.contains("urn:btih:M7TFKY5L64IYJGIKKTENHIIR4NE52ZZP"))
    }

    @Test
    fun subsPleaseQualitiesPointAtDifferentTorrents() {
        val json = fixture("subsplease_search_sample.json")
        val p1080 = SubsPleaseParser.parse(json, VideoQuality.P1080).map { it.id }.toSet()
        val p480 = SubsPleaseParser.parse(json, VideoQuality.P480)
        assertEquals(3, p480.size)
        assertTrue(p480.all { it.quality == VideoQuality.P480 && it.title.contains("480p") })
        assertTrue(p480.none { it.id in p1080 })
    }

    @Test
    fun subsPleaseNoMatchIsAnEmptyJsonArray() {
        assertTrue(SubsPleaseParser.parse("[]", VideoQuality.P1080).isEmpty())
        assertTrue(SubsPleaseParser.parse("", VideoQuality.P720).isEmpty())
    }

    @Test
    fun resolutionIsDetectedFromReleaseTitles() {
        assertEquals(VideoQuality.P1080, VideoQuality.detect("[SubsPlease] Show - 01 (1080p) [ABCD1234].mkv"))
        assertEquals(VideoQuality.P1080, VideoQuality.detect("Show S01 (BD 1920x1080 HEVC)"))
        assertEquals(VideoQuality.P1080, VideoQuality.detect("Show 1080p60 WEB-DL"))
        assertEquals(VideoQuality.P720, VideoQuality.detect("[Erai-raws] Show - 05 [720p][Multiple Subtitle]"))
        assertEquals(VideoQuality.P480, VideoQuality.detect("Show - 12 [480p]"))
        assertNull(VideoQuality.detect("Show - 10800 Special"))
        assertNull(VideoQuality.detect("Show - 01 [x264]"))
    }

    @Test
    fun batchReleasesAreRecognised() {
        assertTrue(SearchFormat.looksLikeBatch("[Group] Show (01-12) [1080p]"))
        assertTrue(SearchFormat.looksLikeBatch("[Group] Show [Batch]"))
        assertFalse(SearchFormat.looksLikeBatch("[SubsPlease] Show - 05 (1080p) [ABCD-1234].mkv"))
        assertFalse(SearchFormat.looksLikeBatch("[Erai-raws] Show - 05 [1080p HEVC]"))
    }

    @Test
    fun sizesAreParsed() {
        assertEquals(1536L * 1024 * 1024, SearchFormat.parseSize("1.5 GiB"))
        assertEquals(700L * 1024 * 1024, SearchFormat.parseSize("700.0 MiB"))
        assertEquals(512L, SearchFormat.parseSize("512 Bytes"))
        assertNull(SearchFormat.parseSize("unknown"))
    }
}
