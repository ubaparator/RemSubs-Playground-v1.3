package com.example.parser

import java.util.ArrayDeque
import java.util.Locale
import java.util.regex.Pattern

object HtmlSubtitleParser {

    private val ENTITY_PATTERN = Pattern.compile("&(#?[a-zA-Z0-9]+);")
    private val TAG_PATTERN = Pattern.compile("<(/?[a-zA-Z0-9]+)([^>]*)>")

    /**
     * Decodes HTML entities including named and numeric entities:
     * &lt; -> <, &gt; -> >, &amp; -> &, &quot; -> ", &apos; -> ', &nbsp; -> space,
     * &#60; -> <, &#x3C; -> <, etc.
     */
    fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        var current = input
        for (i in 0 until 2) {
            if (!current.contains('&')) break
            val matcher = ENTITY_PATTERN.matcher(current)
            if (!matcher.find()) break
            matcher.reset()
            val sb = StringBuffer()
            while (matcher.find()) {
                val entity = matcher.group(1) ?: ""
                val replacement = when {
                    entity.equals("lt", ignoreCase = true) -> "<"
                    entity.equals("gt", ignoreCase = true) -> ">"
                    entity.equals("amp", ignoreCase = true) -> "&"
                    entity.equals("quot", ignoreCase = true) -> "\""
                    entity.equals("apos", ignoreCase = true) -> "'"
                    entity.equals("nbsp", ignoreCase = true) -> " "
                    entity.startsWith("#x", ignoreCase = true) -> {
                        try {
                            val hex = entity.substring(2)
                            hex.toInt(16).toChar().toString()
                        } catch (_: Exception) {
                            matcher.group(0) ?: ""
                        }
                    }
                    entity.startsWith("#") -> {
                        try {
                            val dec = entity.substring(1)
                            dec.toInt(10).toChar().toString()
                        } catch (_: Exception) {
                            matcher.group(0) ?: ""
                        }
                    }
                    else -> matcher.group(0) ?: ""
                }
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
            }
            matcher.appendTail(sb)
            current = sb.toString()
        }
        return current
    }

    private data class FontState(
        val colorAss: String? = null,
        val face: String? = null,
        val size: String? = null
    )

    /**
     * Converts HTML formatting tags (<i>, <b>, <u>, <br>, <font color=... face=... size=...>)
     * into standard ASS override tags ({\i1}, {\b1}, {\u1}, \N, {\c&H...&}, {\fn...}, {\fs...})
     * with proper nested tag tracking so closing tags accurately restore parent state.
     */
    fun htmlToAss(input: String): String {
        // Step 1: Decode entities (&lt;i&gt; -> <i>)
        val decoded = decodeHtmlEntities(input)
        if (!decoded.contains('<') && !decoded.contains('&')) {
            return decoded
        }

        val sb = StringBuilder()
        val matcher = TAG_PATTERN.matcher(decoded)
        var lastEnd = 0

        var italicDepth = 0
        var boldDepth = 0
        var underlineDepth = 0
        val fontStack = ArrayDeque<FontState>()

        while (matcher.find()) {
            val textBefore = decoded.substring(lastEnd, matcher.start())
            sb.append(textBefore)

            val rawTagName = matcher.group(1) ?: ""
            val isClosing = rawTagName.startsWith("/")
            val tagName = if (isClosing) rawTagName.substring(1).lowercase(Locale.ROOT) else rawTagName.lowercase(Locale.ROOT)
            val attributes = matcher.group(2) ?: ""

            when (tagName) {
                "i", "em" -> {
                    if (isClosing) {
                        italicDepth = (italicDepth - 1).coerceAtLeast(0)
                        if (italicDepth == 0) {
                            sb.append("{\\i0}")
                        }
                    } else {
                        if (italicDepth == 0) {
                            sb.append("{\\i1}")
                        }
                        italicDepth++
                    }
                }
                "b", "strong" -> {
                    if (isClosing) {
                        boldDepth = (boldDepth - 1).coerceAtLeast(0)
                        if (boldDepth == 0) {
                            sb.append("{\\b0}")
                        }
                    } else {
                        if (boldDepth == 0) {
                            sb.append("{\\b1}")
                        }
                        boldDepth++
                    }
                }
                "u" -> {
                    if (isClosing) {
                        underlineDepth = (underlineDepth - 1).coerceAtLeast(0)
                        if (underlineDepth == 0) {
                            sb.append("{\\u0}")
                        }
                    } else {
                        if (underlineDepth == 0) {
                            sb.append("{\\u1}")
                        }
                        underlineDepth++
                    }
                }
                "br" -> {
                    sb.append("\\N")
                }
                "font" -> {
                    if (isClosing) {
                        if (fontStack.isNotEmpty()) {
                            fontStack.pop()
                            val parentFont = fontStack.peek()
                            val restoreTags = StringBuilder()
                            if (parentFont != null) {
                                if (parentFont.colorAss != null) restoreTags.append("\\c${parentFont.colorAss}") else restoreTags.append("\\c")
                                if (parentFont.face != null) restoreTags.append("\\fn${parentFont.face}") else restoreTags.append("\\fn")
                                if (parentFont.size != null) restoreTags.append("\\fs${parentFont.size}") else restoreTags.append("\\fs")
                            } else {
                                restoreTags.append("\\c\\fn\\fs")
                            }
                            if (restoreTags.isNotEmpty()) {
                                sb.append("{$restoreTags}")
                            }
                        }
                    } else {
                        val parsedColor = parseHtmlColorToAss(attributes)
                        val parsedFace = parseAttributeValue(attributes, "face")
                        val parsedSize = parseAttributeValue(attributes, "size")

                        val fontState = FontState(
                            colorAss = parsedColor,
                            face = parsedFace,
                            size = parsedSize
                        )
                        fontStack.push(fontState)

                        val fontTags = StringBuilder()
                        if (parsedColor != null) fontTags.append("\\c$parsedColor")
                        if (parsedFace != null) fontTags.append("\\fn$parsedFace")
                        if (parsedSize != null) fontTags.append("\\fs$parsedSize")

                        if (fontTags.isNotEmpty()) {
                            sb.append("{$fontTags}")
                        }
                    }
                }
            }

            lastEnd = matcher.end()
        }

        if (lastEnd < decoded.length) {
            sb.append(decoded.substring(lastEnd))
        }

        return sb.toString()
    }

    /**
     * Strips both HTML tags and ASS override tags to obtain plain human-readable text.
     * Converts HTML <br> and ASS \N to newline, then decodes remaining HTML entities.
     */
    fun cleanToPlainText(input: String): String {
        return input
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
            .let { decodeHtmlEntities(it) }
            .trim()
    }

    private fun parseAttributeValue(attributes: String, attrName: String): String? {
        val pattern = Pattern.compile("(?i)$attrName\\s*=\\s*[\"']?([^\"'\\s>]+)[\"']?")
        val matcher = pattern.matcher(attributes)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun parseHtmlColorToAss(attributes: String): String? {
        val rawColor = parseAttributeValue(attributes, "color") ?: return null
        return colorNameToAssHex(rawColor)
    }

    fun colorNameToAssHex(colorStr: String): String? {
        val trimmed = colorStr.trim().removePrefix("#")
        // Check 6-digit or 8-digit hex
        if (trimmed.length == 6 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            val r = trimmed.substring(0, 2)
            val g = trimmed.substring(2, 4)
            val b = trimmed.substring(4, 6)
            // ASS hex color is &HBBGGRR&
            return "&H$b$g$r&"
        }
        if (trimmed.length == 3 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            val r = "${trimmed[0]}${trimmed[0]}"
            val g = "${trimmed[1]}${trimmed[1]}"
            val b = "${trimmed[2]}${trimmed[2]}"
            return "&H$b$g$r&"
        }

        // Common named colors
        return when (colorStr.lowercase(Locale.ROOT)) {
            "white" -> "&HFFFFFF&"
            "black" -> "&H000000&"
            "red" -> "&H0000FF&"
            "green" -> "&H008000&"
            "lime" -> "&H00FF00&"
            "blue" -> "&HFF0000&"
            "yellow" -> "&H00FFFF&"
            "cyan" -> "&HFFFF00&"
            "magenta", "fuchsia" -> "&HFF00FF&"
            "gray", "grey" -> "&H808080&"
            "orange" -> "&H00A5FF&"
            "purple" -> "&H800080&"
            else -> null
        }
    }
}
