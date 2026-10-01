package com.example.parser

import androidx.compose.ui.graphics.Color
import com.example.model.SubtitleCue
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleStyle
import com.example.model.SubtitleVerticalAlign
import java.io.InputStream
import java.util.regex.Pattern

object SubtitleParser {

    private val SRT_TIME_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
    )

    private val ASS_TIME_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})\\.(\\d{1,3})"
    )

    fun parse(inputStream: InputStream, fileName: String?): List<SubtitleCue> {
        val rawContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .removePrefix("\uFEFF") // Remove UTF-8 BOM if present

        val lowerName = fileName?.lowercase().orEmpty()
        return if (lowerName.endsWith(".ass") || lowerName.endsWith(".ssa") || rawContent.contains("[Events]") || rawContent.contains("Dialogue:")) {
            parseAss(rawContent)
        } else {
            parseSrt(rawContent)
        }
    }

    fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.lines()
        var i = 0
        var cueIndex = 1

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            // Next line might be number index, or directly timestamp
            val timeMatcher = SRT_TIME_PATTERN.matcher(line)
            if (timeMatcher.find()) {
                val startMs = parseSrtTimestamp(
                    timeMatcher.group(1),
                    timeMatcher.group(2),
                    timeMatcher.group(3),
                    timeMatcher.group(4)
                )
                val endMs = parseSrtTimestamp(
                    timeMatcher.group(5),
                    timeMatcher.group(6),
                    timeMatcher.group(7),
                    timeMatcher.group(8)
                )

                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(lines[i].trim())
                    i++
                }

                val rawText = textBuilder.toString()
                val assConverted = HtmlSubtitleParser.htmlToAss(rawText)
                val cleanText = HtmlSubtitleParser.cleanToPlainText(rawText)

                if (cleanText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            id = cueIndex++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            rawText = assConverted,
                            cleanText = cleanText
                        )
                    )
                }
            } else {
                // Check if the next line has the timestamp
                if (i + 1 < lines.size) {
                    val nextMatcher = SRT_TIME_PATTERN.matcher(lines[i + 1].trim())
                    if (nextMatcher.find()) {
                        val startMs = parseSrtTimestamp(
                            nextMatcher.group(1),
                            nextMatcher.group(2),
                            nextMatcher.group(3),
                            nextMatcher.group(4)
                        )
                        val endMs = parseSrtTimestamp(
                            nextMatcher.group(5),
                            nextMatcher.group(6),
                            nextMatcher.group(7),
                            nextMatcher.group(8)
                        )

                        i += 2
                        val textBuilder = StringBuilder()
                        while (i < lines.size && lines[i].trim().isNotEmpty()) {
                            if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                            textBuilder.append(lines[i].trim())
                            i++
                        }

                        val rawText = textBuilder.toString()
                        val assConverted = HtmlSubtitleParser.htmlToAss(rawText)
                        val cleanText = HtmlSubtitleParser.cleanToPlainText(rawText)

                        if (cleanText.isNotBlank()) {
                            cues.add(
                                SubtitleCue(
                                    id = cueIndex++,
                                    startTimeMs = startMs,
                                    endTimeMs = endMs,
                                    rawText = assConverted,
                                    cleanText = cleanText
                                )
                            )
                        }
                        continue
                    }
                }
                i++
            }
        }

        return cues.sortedBy { it.startTimeMs }
    }

    fun parseAss(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.lines()
        var isInEvents = false
        var formatColumns = listOf(
            "Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text"
        )
        var cueIndex = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[Events]", ignoreCase = true)) {
                isInEvents = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                isInEvents = false
            }

            if (!isInEvents) {
                // If not explicitly in [Events], still look for Dialogue lines
                if (!trimmed.startsWith("Dialogue:", ignoreCase = true) &&
                    !trimmed.startsWith("Format:", ignoreCase = true)
                ) {
                    continue
                }
            }

            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                val formatStr = trimmed.substringAfter("Format:").trim()
                formatColumns = formatStr.split(",").map { it.trim() }
                continue
            }

            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val valueStr = trimmed.substringAfter("Dialogue:").trim()
                // Format usually has 9 commas separating 10 columns; last column is Text which may contain commas
                val maxSplits = formatColumns.size
                val parts = valueStr.split(",", limit = maxSplits).map { it.trim() }

                val colMap = mutableMapOf<String, String>()
                for (c in formatColumns.indices) {
                    if (c < parts.size) {
                        colMap[formatColumns[c].lowercase()] = parts[c]
                    }
                }

                val startStr = colMap["start"] ?: ""
                val endStr = colMap["end"] ?: ""
                val textStr = colMap["text"] ?: if (parts.size >= maxSplits) parts.last() else ""
                val styleStr = colMap["style"] ?: "Default"
                val actorStr = colMap["name"] ?: ""
                val layerStr = colMap["layer"] ?: "0"
                val marginVStr = colMap["marginv"] ?: "0"

                val startMs = parseAssTimestamp(startStr)
                val endMs = parseAssTimestamp(endStr)

                if (startMs >= 0 && endMs >= startMs) {
                    val clean = cleanAssText(textStr)
                    if (clean.isNotBlank()) {
                        cues.add(
                            SubtitleCue(
                                id = cueIndex++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                rawText = textStr,
                                cleanText = clean,
                                styleName = styleStr,
                                layer = layerStr.toIntOrNull() ?: 0,
                                marginV = marginVStr.toIntOrNull() ?: 0,
                                actor = actorStr
                            )
                        )
                    }
                }
            }
        }

        return cues.sortedBy { it.startTimeMs }
    }

    /**
     * Extracts existing Style definitions from an .ASS script content.
     */
    fun extractAssStyles(content: String): List<String> {
        val styles = mutableListOf<String>()
        var inStyles = false
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[V4+ Styles]", ignoreCase = true) || trimmed.startsWith("[V4 Styles]", ignoreCase = true)) {
                inStyles = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inStyles = false
            }
            if (inStyles && trimmed.startsWith("Style:", ignoreCase = true)) {
                styles.add(trimmed)
            }
        }
        return styles
    }

    /**
     * Extracts PlayResX and PlayResY resolution from ASS script if declared.
     */
    fun extractScriptResolution(content: String): Pair<Int, Int>? {
        var rx: Int? = null
        var ry: Int? = null
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("PlayResX:", ignoreCase = true)) {
                rx = trimmed.substringAfter(":").trim().toIntOrNull()
            } else if (trimmed.startsWith("PlayResY:", ignoreCase = true)) {
                ry = trimmed.substringAfter(":").trim().toIntOrNull()
            }
            if (trimmed.startsWith("[Events]", ignoreCase = true)) break
        }
        return if (rx != null || ry != null) {
            Pair(rx ?: 1920, ry ?: 1080)
        } else null
    }

    /**
     * Parses the primary Default style from an ASS script into a SubtitleStyle object.
     * Scales font size and margins proportionally based on PlayResY (relative to standard 216dp mobile preview height).
     */
    fun parseAssDefaultStyle(content: String, targetPlayResY: Int = 1080): SubtitleStyle? {
        val scriptRes = extractScriptResolution(content)
        val playResY = scriptRes?.second ?: targetPlayResY

        var formatColumns = listOf(
            "Name", "Fontname", "Fontsize", "PrimaryColour", "SecondaryColour",
            "OutlineColour", "BackColour", "Bold", "Italic", "Underline",
            "StrikeOut", "ScaleX", "ScaleY", "Spacing", "Angle",
            "BorderStyle", "Outline", "Shadow", "Alignment", "MarginL", "MarginR", "MarginV", "Encoding"
        )

        var defaultStyleLine: String? = null
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Format:", ignoreCase = true) &&
                (trimmed.contains("Fontname", ignoreCase = true) || trimmed.contains("PrimaryColour", ignoreCase = true))
            ) {
                formatColumns = trimmed.substringAfter("Format:").trim().split(",").map { it.trim().lowercase() }
            }
            if (trimmed.startsWith("Style:", ignoreCase = true)) {
                val parts = trimmed.substringAfter("Style:").trim().split(",")
                val sName = parts.firstOrNull()?.trim() ?: ""
                if (sName.equals("Default", ignoreCase = true) || defaultStyleLine == null) {
                    defaultStyleLine = trimmed
                    if (sName.equals("Default", ignoreCase = true)) break
                }
            }
        }

        if (defaultStyleLine == null) return null

        try {
            val parts = defaultStyleLine.substringAfter("Style:").trim().split(",").map { it.trim() }
            val colMap = mutableMapOf<String, String>()
            for (c in formatColumns.indices) {
                if (c < parts.size) {
                    colMap[formatColumns[c]] = parts[c]
                }
            }

            val fontName = colMap["fontname"] ?: "Arial"
            val rawFontSize = colMap["fontsize"]?.toFloatOrNull() ?: 48f
            val fontSizeSp = rawFontSize.coerceIn(12f, 160f)

            val primaryColStr = colMap["primarycolour"] ?: "&H00FFFFFF"
            val textColor = parseAssColor(primaryColStr) ?: Color.White

            val outlineColStr = colMap["outlinecolour"] ?: "&H00000000"
            val outlineColor = parseAssColor(outlineColStr) ?: Color.Black

            val backColStr = colMap["backcolour"] ?: "&H80000000"
            val backgroundColor = parseAssColor(backColStr) ?: Color(0x99000000)

            val bold = colMap["bold"]?.toIntOrNull() ?: 0
            val italic = colMap["italic"]?.toIntOrNull() ?: 0
            val underline = colMap["underline"]?.toIntOrNull() ?: 0
            val borderStyle = colMap["borderstyle"]?.toIntOrNull() ?: 1

            val rawOutline = colMap["outline"]?.toFloatOrNull() ?: 2.5f
            val outlineWidth = rawOutline.coerceIn(0f, 20f)

            val alignNum = colMap["alignment"]?.toIntOrNull() ?: 2
            val (vAlign, hAlign) = when (alignNum) {
                7 -> Pair(SubtitleVerticalAlign.TOP, SubtitleHorizontalAlign.LEFT)
                8 -> Pair(SubtitleVerticalAlign.TOP, SubtitleHorizontalAlign.CENTER)
                9 -> Pair(SubtitleVerticalAlign.TOP, SubtitleHorizontalAlign.RIGHT)
                4 -> Pair(SubtitleVerticalAlign.MIDDLE, SubtitleHorizontalAlign.LEFT)
                5 -> Pair(SubtitleVerticalAlign.MIDDLE, SubtitleHorizontalAlign.CENTER)
                6 -> Pair(SubtitleVerticalAlign.MIDDLE, SubtitleHorizontalAlign.RIGHT)
                1 -> Pair(SubtitleVerticalAlign.BOTTOM, SubtitleHorizontalAlign.LEFT)
                3 -> Pair(SubtitleVerticalAlign.BOTTOM, SubtitleHorizontalAlign.RIGHT)
                else -> Pair(SubtitleVerticalAlign.BOTTOM, SubtitleHorizontalAlign.CENTER)
            }

            val rawMarginV = colMap["marginv"]?.toFloatOrNull() ?: 40f
            val verticalOffsetDp = rawMarginV.coerceIn(0f, 300f)

            val rawMarginL = colMap["marginl"]?.toFloatOrNull() ?: 20f
            val horizontalPaddingDp = rawMarginL.coerceIn(0f, 200f)

            return SubtitleStyle(
                fontName = fontName,
                fontSizeSp = fontSizeSp,
                isBold = bold != 0,
                isItalic = italic != 0,
                isUnderline = underline != 0,
                textColor = textColor,
                outlineColor = outlineColor,
                hasOutline = rawOutline > 0,
                outlineWidth = outlineWidth,
                hasBackgroundBox = borderStyle == 3,
                backgroundColor = backgroundColor,
                verticalAlign = vAlign,
                horizontalAlign = hAlign,
                verticalOffsetDp = verticalOffsetDp,
                horizontalPaddingDp = horizontalPaddingDp
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Parses ASS hex color format: &HAABBGGRR, &HBBGGRR, or decimal integer.
     * Note: In ASS, alpha is inverted (00 = opaque, FF = transparent).
     */
    fun parseAssColor(colorStr: String): Color? {
        val clean = colorStr.trim().removePrefix("&H").removePrefix("&h").removeSuffix("&")
        val hexVal = clean.toLongOrNull(16) ?: clean.toLongOrNull() ?: return null

        return try {
            if (clean.length > 6) {
                val a = (hexVal shr 24 and 0xFF).toInt()
                val b = (hexVal shr 16 and 0xFF).toInt()
                val g = (hexVal shr 8 and 0xFF).toInt()
                val r = (hexVal and 0xFF).toInt()
                val alpha = (255 - a).coerceIn(0, 255)
                Color(r, g, b, alpha)
            } else {
                val b = (hexVal shr 16 and 0xFF).toInt()
                val g = (hexVal shr 8 and 0xFF).toInt()
                val r = (hexVal and 0xFF).toInt()
                Color(r, g, b, 255)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSrtTimestamp(h: String?, m: String?, s: String?, ms: String?): Long {
        val hours = h?.toLongOrNull() ?: 0L
        val minutes = m?.toLongOrNull() ?: 0L
        val seconds = s?.toLongOrNull() ?: 0L
        val millisStr = ms ?: "0"
        val millis = when (millisStr.length) {
            1 -> (millisStr.toLongOrNull() ?: 0L) * 100
            2 -> (millisStr.toLongOrNull() ?: 0L) * 10
            else -> millisStr.take(3).toLongOrNull() ?: 0L
        }
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
    }

    private fun parseAssTimestamp(timestamp: String): Long {
        val matcher = ASS_TIME_PATTERN.matcher(timestamp.trim())
        if (matcher.find()) {
            val hours = matcher.group(1)?.toLongOrNull() ?: 0L
            val minutes = matcher.group(2)?.toLongOrNull() ?: 0L
            val seconds = matcher.group(3)?.toLongOrNull() ?: 0L
            val frac = matcher.group(4) ?: "0"
            val millis = when (frac.length) {
                1 -> (frac.toLongOrNull() ?: 0L) * 100
                2 -> (frac.toLongOrNull() ?: 0L) * 10 // centiseconds
                else -> frac.take(3).toLongOrNull() ?: 0L
            }
            return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
        }
        return -1L
    }

    private fun cleanHtmlTags(text: String): String {
        return HtmlSubtitleParser.cleanToPlainText(text)
    }

    fun cleanAssText(text: String): String {
        return HtmlSubtitleParser.cleanToPlainText(text)
    }

    /**
     * Built-in sample subtitles for immediate testing in preview/emulator
     */
    fun getSampleAssContent(): String {
        return """
[Script Info]
Title: remsubs playground Sample Demo
ScriptType: v4.00+
WrapStyle: 0
ScaledBorderAndShadow: yes
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,50,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,3,2,2,30,30,40,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:04.50,Default,,0,0,0,,{\b1}remsubs playground{\b0}\NVideo ve Altyazı Önizleme Sistemi 💙
Dialogue: 0,0:00:05.00,0:00:08.50,Default,,0,0,0,,Bu bir {\i1}Advanced SubStation Alpha (.ass){\i0} altyazı örneğidir.
Dialogue: 0,0:00:09.00,0:00:13.00,Default,,0,0,0,,Lokal videonuz seçildiğinde doğrudan eş zamanlı akar.
Dialogue: 0,0:00:13.50,0:00:18.00,Default,,0,0,0,,İstediğiniz {\b1}.TTF font dosyasını{\b0} yükleyip anında uygulayabilirsiniz!
Dialogue: 0,0:00:18.50,0:00:23.50,Default,,0,0,0,,Altyazının font boyutunu, konumunu ve rengini kolayca ayarlayın.
Dialogue: 0,0:00:24.00,0:00:29.00,Default,,0,0,0,,Diyalog listesinden satırlara tıklayarak videoda gezinebilirsiniz.
Dialogue: 0,0:00:30.00,0:00:35.00,Default,,0,0,0,,remsubs playground ile kusursuz senkronizasyon ve altyazı tasarımı!
        """.trimIndent()
    }

    fun getSampleSrtContent(): String {
        return """
1
00:00:01,000 --> 00:00:04,500
remsubs playground - SRT Altyazı Desteği

2
00:00:05,000 --> 00:00:08,500
Seçtiğiniz video lokalden önizlenir.

3
00:00:09,000 --> 00:00:13,000
Özel TTF font yükleyebilir ve yerleşimini değiştirebilirsiniz.

4
00:00:13,500 --> 00:00:18,000
Eş zamanlı altyazı önizlemesi ve zamanlama ayarı!
        """.trimIndent()
    }

    /**
     * Exports a list of SubtitleCue elements to standard SubRip (.srt) format.
     */
    fun exportToSrt(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        cues.sortedBy { it.startTimeMs }.forEachIndexed { index, cue ->
            sb.append(index + 1).append("\n")
            sb.append(formatSrtTimestamp(cue.startTimeMs))
            sb.append(" --> ")
            sb.append(formatSrtTimestamp(cue.endTimeMs))
            sb.append("\n")
            sb.append(cue.cleanText)
            sb.append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun formatSrtTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
