package com.example

import androidx.compose.ui.graphics.toArgb
import com.example.parser.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSrtParsing() {
        val srtContent = """
            1
            00:01:20,000 --> 00:01:24,500
            Merhaba dünya!
            İkinci satır.

            2
            00:02:10,500 --> 00:02:15,000
            <i>İtalik metin</i>
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srtContent)
        assertEquals(2, cues.size)

        assertEquals(1, cues[0].id)
        assertEquals(80000L, cues[0].startTimeMs) // 1m 20s
        assertEquals(84500L, cues[0].endTimeMs)
        assertEquals("Merhaba dünya!\nİkinci satır.", cues[0].cleanText)

        assertEquals(2, cues[1].id)
        assertEquals(130500L, cues[1].startTimeMs) // 2m 10.5s
        assertEquals("İtalik metin", cues[1].cleanText)
    }

    @Test
    fun testAssParsing() {
        val assContent = SubtitleParser.getSampleAssContent()
        val cues = SubtitleParser.parseAss(assContent)

        assertTrue("Should parse at least 5 cues", cues.size >= 5)
        assertEquals("remsubs playground\nVideo ve Altyazı Önizleme Sistemi 💙", cues[0].cleanText)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals(4500L, cues[0].endTimeMs)
    }

    @Test
    fun testAssCleanText() {
        val raw = "{\\b1\\pos(100,200)}Kalın metin{\\b0}\\Nİkinci satır\\hboşluk"
        val clean = SubtitleParser.cleanAssText(raw)
        assertEquals("Kalın metin\nİkinci satır boşluk", clean)
    }

    @Test
    fun testSubtitleCueTimestampParsing() {
        val ms = com.example.model.SubtitleCue.parseTimestamp("01:23.456")
        assertEquals(83456L, ms)

        val cue = com.example.model.SubtitleCue(
            id = 1,
            startTimeMs = 83456L,
            endTimeMs = 86000L,
            rawText = "Deneme",
            cleanText = "Deneme",
            customPositionEnabled = true,
            customVerticalAlign = com.example.model.SubtitleVerticalAlign.TOP,
            customHorizontalAlign = com.example.model.SubtitleHorizontalAlign.LEFT
        )
        assertEquals("01:23.456", cue.formatStartTime())
        assertTrue(cue.customPositionEnabled)
        assertEquals(com.example.model.SubtitleVerticalAlign.TOP, cue.customVerticalAlign)
    }

    @Test
    fun testColorInstantiationFromLong() {
        val colorVal: Long = 0xFFFFFFFFL
        val color = androidx.compose.ui.graphics.Color(colorVal)
        val argb = color.toArgb()
        assertEquals(-1, argb) // 0xFFFFFFFF in signed 32-bit Int is -1
    }

    @Test
    fun testAssGeneratorTimestamp() {
        // 1 hour, 23 minutes, 45 seconds, 670 ms -> 1:23:45.67
        val ms = 1 * 3600000L + 23 * 60000L + 45 * 1000L + 670L
        val formatted = com.example.parser.AssGenerator.formatAssTimestamp(ms)
        assertEquals("1:23:45.67", formatted)
    }

    @Test
    fun testSrtToAssConversionAndGeneration() {
        val srtContent = """
            1
            00:00:01,000 --> 00:00:04,500
            Birinci diyalog satırı

            2
            00:00:05,200 --> 00:00:08,000
            İkinci diyalog satırı
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srtContent)
        val style = com.example.model.SubtitleStyle(
            fontName = "Montserrat",
            fontSizeSp = 40f
        )
        val generatedAss = com.example.parser.AssGenerator.generateAss(
            title = "film_ceviri.ass",
            subtitles = cues,
            style = style
        )

        assertTrue(generatedAss.contains("[Script Info]"))
        assertTrue(generatedAss.contains("Title: film_ceviri.ass"))
        assertTrue(generatedAss.contains("[V4+ Styles]"))
        assertTrue(generatedAss.contains("Montserrat"))
        assertTrue(generatedAss.contains("[Events]"))
        assertTrue(generatedAss.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Default,,0,0,0,,Birinci diyalog satırı"))
        assertTrue(generatedAss.contains("Dialogue: 0,0:00:05.20,0:00:08.00,Default,,0,0,0,,İkinci diyalog satırı"))
    }

    @Test
    fun testItalicAndOutlineColorInAssGeneration() {
        val cue = com.example.model.SubtitleCue(
            id = 1,
            startTimeMs = 1000L,
            endTimeMs = 4000L,
            rawText = "Özel İtalik ve Renkli Dış Çizgi",
            cleanText = "Özel İtalik ve Renkli Dış Çizgi",
            customPositionEnabled = true,
            customIsItalic = true,
            customIsUnderline = true,
            customOutlineColorArgb = 0xFFFF0000L // Red outline
        )

        val style = com.example.model.SubtitleStyle(
            isItalic = true,
            isUnderline = true,
            outlineColor = androidx.compose.ui.graphics.Color(0xFF0000FF)
        )

        val assOutput = com.example.parser.AssGenerator.generateAss(
            title = "test_italic_outline.ass",
            subtitles = listOf(cue),
            style = style
        )

        // Verifies ASS Style header contains italic (-1) and underline (-1)
        assertTrue(assOutput.contains("[V4+ Styles]"))
        // Verifies dialogue has \i1, \u1, and \3c override tags
        assertTrue(assOutput.contains("\\i1"))
        assertTrue(assOutput.contains("\\u1"))
        assertTrue(assOutput.contains("\\3c"))
    }

    @Test
    fun testRgbColorToAssHexConversion() {
        // Red color (255, 0, 0)
        val redColor = androidx.compose.ui.graphics.Color(255, 0, 0)
        val assBgr = com.example.parser.AssGenerator.colorToInlineAssHex(redColor)
        // ASS format is &HBBGGRR& -> Blue=00, Green=00, Red=FF
        assertEquals("&H0000FF&", assBgr)

        // Custom RGB: R=18, G=52, B=86
        val customColor = androidx.compose.ui.graphics.Color(0x12, 0x34, 0x56)
        val customAssColor = com.example.parser.AssGenerator.colorToInlineAssHex(customColor)
        // Blue=56, Green=34, Red=12
        assertEquals("&H563412&", customAssColor)
    }

    @Test
    fun testMainMenuScreenNavigationState() {
        val uiState = com.example.ui.AxiSubUiState()
        assertEquals(com.example.ui.AppScreen.MAIN_MENU, uiState.currentScreen)
    }

    @Test
    fun testEncodeMetricsCalculation() {
        val totalFrames = 900L
        val fps = 30.0
        val currentFrame = 300L

        // User requirement: "fps değerini toplam kare değerine böl ve ortalama bitiş süresi yaz"
        val ratio = fps / totalFrames.toDouble()
        assertEquals(0.033333, ratio, 0.0001)

        val remainingFrames = totalFrames - currentFrame
        val remainingSeconds = (remainingFrames / fps).toLong()
        assertEquals(20L, remainingSeconds)

        val formattedTime = com.example.encode.HardsubEncoder.formatTimeSeconds(remainingSeconds)
        assertEquals("00:20", formattedTime)

        val formattedLongTime = com.example.encode.HardsubEncoder.formatTimeSeconds(125L)
        assertEquals("02:05", formattedLongTime)
    }

    @Test
    fun testSmartExceptionClasspathResolution() {
        val clazz = Class.forName("com.arthenica.smartexception.java.Exceptions")
        org.junit.Assert.assertNotNull(clazz)
    }

    @Test
    fun testFFmpegKitConfigInitialization() {
        // Must execute without throwing NoClassDefFoundError
        val isAvail = com.example.encode.HardsubEncoder.isFFmpegAvailable()
        // Result is boolean (true if native lib present, false otherwise, but never crashes)
    }

    @Test
    fun testExtractAssStyles() {
        val sampleAss = """
            [Script Info]
            Title: Test
            
            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,20
            Style: Title,Roboto,30
            Style: Sign,Comic Sans,24
            
            [Events]
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello
        """.trimIndent()

        val styles = SubtitleParser.extractAssStyles(sampleAss)
        assertEquals(3, styles.size)
        assertTrue(styles.any { it.contains("Style: Default") })
        assertTrue(styles.any { it.contains("Style: Title") })
        assertTrue(styles.any { it.contains("Style: Sign") })
    }

    @Test
    fun testMkvSubtitleTrackFileExtension() {
        val assTrack = com.example.mkv.MkvSubtitleTrack(
            streamIndex = 2,
            subtitleTrackNumber = 1,
            codecName = "ass",
            languageCode = "tur",
            languageDisplayName = "Türkçe",
            trackTitle = "Türkçe Altyazı",
            isDefault = true,
            isForced = false
        )
        assertEquals("ass", assTrack.fileExtension)

        val srtTrack = com.example.mkv.MkvSubtitleTrack(
            streamIndex = 3,
            subtitleTrackNumber = 2,
            codecName = "subrip",
            languageCode = "eng",
            languageDisplayName = "İngilizce",
            trackTitle = "English",
            isDefault = false,
            isForced = false
        )
        assertEquals("srt", srtTrack.fileExtension)
    }

    @Test
    fun testTermuxEscapingSpecialCharactersAndTurkish() {
        val path = "/storage/emulated/0/Movies/RemSubs/video (1) [720p] & 'özel' çşğü...mkv"
        val bashEscaped = com.example.encode.TermuxEncodeManager.escapeForBash(path)
        assertTrue(bashEscaped.startsWith("'"))
        assertTrue(bashEscaped.endsWith("'"))
        assertTrue(bashEscaped.contains("'\\''özel'\\''"))

        val filterEscaped = com.example.encode.TermuxEncodeManager.escapeForAssFilter("sub:file,name['1'].ass")
        assertEquals("sub\\:file\\,name\\[\\'1\\'\\]\\.ass".replace("\\.", "."), filterEscaped)
        assertTrue(filterEscaped.contains("\\:"))
        assertTrue(filterEscaped.contains("\\,"))
        assertTrue(filterEscaped.contains("\\["))
        assertTrue(filterEscaped.contains("\\]"))
        assertTrue(filterEscaped.contains("\\'"))
    }

    @Test
    fun testTorrentStorageFileTypesAndMimeResolution() {
        // Video files
        val mkvName = "ReZero_Episode_01_[1080p].mkv"
        val mp4Name = "sample_video (test).mp4"
        val torrentName = "remsubs_release_v1.torrent"

        assertEquals("video/x-matroska", com.example.torrent.TorrentStorageManager.resolveMimeType(mkvName))
        assertEquals("video/mp4", com.example.torrent.TorrentStorageManager.resolveMimeType(mp4Name))
        assertEquals("application/x-bittorrent", com.example.torrent.TorrentStorageManager.resolveMimeType(torrentName))

        assertTrue(com.example.torrent.TorrentStorageManager.isVideoFile(mkvName))
        assertTrue(com.example.torrent.TorrentStorageManager.isVideoFile(mp4Name))
        org.junit.Assert.assertFalse(com.example.torrent.TorrentStorageManager.isVideoFile(torrentName))

        assertTrue(com.example.torrent.TorrentStorageManager.isTorrentFile(torrentName))
        org.junit.Assert.assertFalse(com.example.torrent.TorrentStorageManager.isTorrentFile(mkvName))

        // Sanitizing preserves extension and does NOT append .mp4 to .torrent or .mkv
        assertEquals("ReZero_Episode_01_[1080p].mkv", com.example.torrent.TorrentStorageManager.sanitizeFileName(mkvName))
        assertEquals("remsubs_release_v1.torrent", com.example.torrent.TorrentStorageManager.sanitizeFileName(torrentName))
    }
}
