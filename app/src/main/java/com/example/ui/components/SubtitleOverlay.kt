package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleCue
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleStyle
import com.example.model.SubtitleVerticalAlign

@Composable
fun SubtitleOverlay(
    activeCues: List<SubtitleCue>,
    style: SubtitleStyle,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier
) {
    if (activeCues.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val containerHeight = maxHeight
        // 216dp is the reference preview height (16:9 on ~384dp wide standard mobile screen).
        // This guarantees mathematical 1:1 proportionality with ASS PlayResY and hardsub video.
        val scale = (containerHeight.value / 216f).coerceIn(0.5f, 4.0f)

        val scaledFontSize = (style.fontSizeSp * scale).sp
        val scaledVerticalOffset = (style.verticalOffsetDp * scale).dp
        val scaledHorizontalPadding = (style.horizontalPaddingDp * scale).dp
        val scaledOutlineWidth = (style.outlineWidth * scale).coerceAtLeast(1f)

        activeCues.forEach { cue ->
            val overrideAlign = extractAssAlignmentFromText(cue.rawText)
            val vAlign = if (cue.customPositionEnabled) cue.customVerticalAlign
                else (overrideAlign?.first ?: style.verticalAlign)
            val hAlign = if (cue.customPositionEnabled) cue.customHorizontalAlign
                else (overrideAlign?.second ?: style.horizontalAlign)

            val vOffset = if (cue.customPositionEnabled) (cue.customVerticalOffsetDp * scale).dp else scaledVerticalOffset
            val hOffset = if (cue.customPositionEnabled) (cue.customHorizontalOffsetDp * scale).dp else 0.dp

            val alignment = when (vAlign) {
                SubtitleVerticalAlign.TOP -> when (hAlign) {
                    SubtitleHorizontalAlign.LEFT -> Alignment.TopStart
                    SubtitleHorizontalAlign.CENTER -> Alignment.TopCenter
                    SubtitleHorizontalAlign.RIGHT -> Alignment.TopEnd
                }
                SubtitleVerticalAlign.MIDDLE -> when (hAlign) {
                    SubtitleHorizontalAlign.LEFT -> Alignment.CenterStart
                    SubtitleHorizontalAlign.CENTER -> Alignment.Center
                    SubtitleHorizontalAlign.RIGHT -> Alignment.CenterEnd
                }
                SubtitleVerticalAlign.BOTTOM -> when (hAlign) {
                    SubtitleHorizontalAlign.LEFT -> Alignment.BottomStart
                    SubtitleHorizontalAlign.CENTER -> Alignment.BottomCenter
                    SubtitleHorizontalAlign.RIGHT -> Alignment.BottomEnd
                }
            }

            val paddingModifier = when (vAlign) {
                SubtitleVerticalAlign.TOP -> Modifier.padding(
                    top = vOffset,
                    start = scaledHorizontalPadding,
                    end = scaledHorizontalPadding
                )
                SubtitleVerticalAlign.MIDDLE -> Modifier.padding(
                    horizontal = scaledHorizontalPadding
                )
                SubtitleVerticalAlign.BOTTOM -> Modifier.padding(
                    bottom = vOffset,
                    start = scaledHorizontalPadding,
                    end = scaledHorizontalPadding
                )
            }

            val offsetModifier = if (cue.customPositionEnabled && cue.customHorizontalOffsetDp != 0f) {
                Modifier.offset(x = hOffset)
            } else {
                Modifier
            }

            val cueFontSize = if (cue.customPositionEnabled && cue.customFontSizeSp != null) {
                (cue.customFontSizeSp * scale).sp
            } else {
                scaledFontSize
            }

            val cueStyle = if (cue.customPositionEnabled) {
                style.copy(
                    textColor = cue.customTextColorArgb?.let { Color(it) } ?: style.textColor,
                    outlineColor = cue.customOutlineColorArgb?.let { Color(it) } ?: style.outlineColor,
                    hasOutline = if (cue.customOutlineColorArgb != null) true else style.hasOutline,
                    isItalic = cue.customIsItalic ?: style.isItalic,
                    isBold = cue.customIsBold ?: style.isBold,
                    isUnderline = cue.customIsUnderline ?: style.isUnderline,
                    horizontalAlign = cue.customHorizontalAlign,
                    verticalAlign = cue.customVerticalAlign
                )
            } else {
                style
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(paddingModifier)
                    .then(offsetModifier),
                contentAlignment = alignment
            ) {
                SingleSubtitleView(
                    cue = cue,
                    style = cueStyle,
                    effectiveFontSize = cueFontSize,
                    effectiveOutlineWidth = scaledOutlineWidth,
                    fontFamily = fontFamily,
                    previewScale = scale
                )
            }
        }
    }
}

@Composable
private fun SingleSubtitleView(
    cue: SubtitleCue,
    style: SubtitleStyle,
    effectiveFontSize: TextUnit,
    effectiveOutlineWidth: Float,
    fontFamily: FontFamily?,
    previewScale: Float
) {
    val boxModifier = if (style.hasBackgroundBox) {
        Modifier
            .background(style.backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = (10 * previewScale).dp, vertical = (4 * previewScale).dp)
    } else {
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }

    val annotatedText = remember(cue.rawText, cue.cleanText, style.isItalic, style.isBold, style.isUnderline, previewScale) {
        buildAnnotatedSubtitle(cue.rawText, cue.cleanText, style, previewScale)
    }

    Box(
        modifier = boxModifier.testTag("subtitle_cue_item"),
        contentAlignment = Alignment.Center
    ) {
        // Outline layer if enabled
        if (style.hasOutline && effectiveOutlineWidth > 0) {
            Text(
                text = annotatedText,
                textAlign = style.textAlign,
                fontSize = effectiveFontSize,
                fontFamily = fontFamily,
                style = TextStyle(
                    color = style.outlineColor,
                    drawStyle = Stroke(
                        width = effectiveOutlineWidth * 2.5f
                    )
                )
            )
        }

        // Main text layer (with subtle shadow for depth)
        Text(
            text = annotatedText,
            color = style.textColor,
            textAlign = style.textAlign,
            fontSize = effectiveFontSize,
            fontFamily = fontFamily,
            style = TextStyle(
                shadow = if (style.hasOutline) null else Shadow(
                    color = Color.Black,
                    blurRadius = 4f * previewScale
                )
            )
        )
    }
}

/**
 * Extracts ASS alignment tag \an1 to \an9 from dialogue text:
 * 7: TopLeft, 8: TopCenter, 9: TopRight
 * 4: MidLeft, 5: MidCenter, 6: MidRight
 * 1: BotLeft, 2: BotCenter, 3: BotRight
 */
fun extractAssAlignmentFromText(text: String): Pair<SubtitleVerticalAlign, SubtitleHorizontalAlign>? {
    if (!text.contains("\\an")) return null
    val match = Regex("""\\an([1-9])""").find(text) ?: return null
    val num = match.groupValues[1].toIntOrNull() ?: return null
    return when (num) {
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
}

/**
 * Parses ASS hex color override like \c&HBBGGRR& or \1c&HBBGGRR&
 */
fun parseAssInlineColor(token: String): Color? {
    val clean = token.replace("{", "").replace("}", "")
        .replace("&", "")
        .replace("\\1c", "")
        .replace("\\c", "")
        .trim()
    val hex = clean.removePrefix("H").removePrefix("h")
    if (hex.length >= 6) {
        return try {
            val b = hex.substring(0, 2).toInt(16)
            val g = hex.substring(2, 4).toInt(16)
            val r = hex.substring(4, 6).toInt(16)
            Color(r, g, b)
        } catch (_: Exception) { null }
    }
    return null
}

/**
 * Parses ASS tags ({\i1}, {\b1}, {\u1}, {\c...}, {\fs...}, \N, etc.) and HTML tags (<i>, <b>, <u>)
 * to build an AnnotatedString preserving styling, bold, italic, underline, inline colors, and font sizes.
 */
fun buildAnnotatedSubtitle(
    rawText: String,
    cleanText: String,
    style: SubtitleStyle,
    previewScale: Float = 1.0f
): AnnotatedString {
    val sourceText = if (rawText.isNotBlank() && (rawText.contains("{") || rawText.contains("<") || rawText.contains("\\N"))) {
        rawText
    } else {
        cleanText
    }

    // Pattern to match ASS tags like {...}, HTML tags like <...>, and ASS newlines \N, \n, \h
    val tokenPattern = Regex("""(\{[^}]*\}|<[^>]+>|\\N|\\n|\\h)""")
    val matches = tokenPattern.findAll(sourceText).toList()

    if (matches.isEmpty()) {
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (style.isUnderline) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(cleanText)
            }
        }
    }

    return buildAnnotatedString {
        var currentIndex = 0
        var activeBold = style.isBold
        var activeItalic = style.isItalic
        var activeUnderline = style.isUnderline
        var activeColor: Color? = null
        var activeFontSize: TextUnit? = null

        fun currentSpanStyle(): SpanStyle {
            return SpanStyle(
                color = activeColor ?: Color.Unspecified,
                fontSize = activeFontSize ?: TextUnit.Unspecified,
                fontWeight = if (activeBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (activeItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (activeUnderline) TextDecoration.Underline else TextDecoration.None
            )
        }

        for (match in matches) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                val plainPart = sourceText.substring(currentIndex, matchRange.first)
                if (plainPart.isNotEmpty()) {
                    withStyle(currentSpanStyle()) {
                        append(plainPart)
                    }
                }
            }

            val token = match.value
            when {
                token == "\\N" || token == "\\n" -> {
                    append("\n")
                }
                token == "\\h" -> {
                    append(" ")
                }
                token.startsWith("{") && token.endsWith("}") -> {
                    val inner = token.substring(1, token.length - 1)
                    if (inner.contains("\\i1")) activeItalic = true
                    if (inner.contains("\\i0")) activeItalic = false
                    if (inner.contains("\\b1") || inner.contains("\\b700")) activeBold = true
                    if (inner.contains("\\b0")) activeBold = false
                    if (inner.contains("\\u1")) activeUnderline = true
                    if (inner.contains("\\u0")) activeUnderline = false

                    // Inline text color \c&HBBGGRR& or \1c&HBBGGRR&
                    if (inner.contains("\\c") || inner.contains("\\1c")) {
                        val colMatch = Regex("""\\(?:1c|c)(&?H?[0-9a-fA-F]{6}&?)""").find(inner)
                        if (colMatch != null) {
                            activeColor = parseAssInlineColor(colMatch.value)
                        }
                    }

                    // Inline font size \fsXX
                    if (inner.contains("\\fs")) {
                        val fsMatch = Regex("""\\fs([0-9]+)""").find(inner)
                        val rawFs = fsMatch?.groupValues?.get(1)?.toFloatOrNull()
                        if (rawFs != null) {
                            // Scale font size according to preview scale
                            val fsInPreview = (rawFs / (1080f / 216f)) * previewScale
                            activeFontSize = fsInPreview.sp
                        }
                    }
                }
                token.startsWith("<") && token.endsWith(">") -> {
                    val lower = token.lowercase()
                    when {
                        lower.startsWith("<i") && !lower.startsWith("</") -> activeItalic = true
                        lower.startsWith("</i") -> activeItalic = style.isItalic
                        lower.startsWith("<b") && !lower.startsWith("</") -> activeBold = true
                        lower.startsWith("</b") -> activeBold = style.isBold
                        lower.startsWith("<u") && !lower.startsWith("</") -> activeUnderline = true
                        lower.startsWith("</u") -> activeUnderline = style.isUnderline
                        lower == "<br>" || lower == "<br/>" || lower == "<br />" -> append("\n")
                    }
                }
            }

            currentIndex = matchRange.last + 1
        }

        if (currentIndex < sourceText.length) {
            val tail = sourceText.substring(currentIndex)
            if (tail.isNotEmpty()) {
                withStyle(currentSpanStyle()) {
                    append(tail)
                }
            }
        }
    }
}
