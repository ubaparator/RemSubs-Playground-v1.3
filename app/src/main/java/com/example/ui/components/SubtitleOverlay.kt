package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        activeCues.forEach { cue ->
            val vAlign = if (cue.customPositionEnabled) cue.customVerticalAlign else style.verticalAlign
            val hAlign = if (cue.customPositionEnabled) cue.customHorizontalAlign else style.horizontalAlign
            val vOffset = if (cue.customPositionEnabled) cue.customVerticalOffsetDp.dp else style.verticalOffset
            val hOffset = if (cue.customPositionEnabled) cue.customHorizontalOffsetDp.dp else 0.dp

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
                    start = style.horizontalPadding,
                    end = style.horizontalPadding
                )
                SubtitleVerticalAlign.MIDDLE -> Modifier.padding(
                    horizontal = style.horizontalPadding
                )
                SubtitleVerticalAlign.BOTTOM -> Modifier.padding(
                    bottom = vOffset,
                    start = style.horizontalPadding,
                    end = style.horizontalPadding
                )
            }

            val offsetModifier = if (cue.customPositionEnabled && cue.customHorizontalOffsetDp != 0f) {
                Modifier.offset(x = hOffset)
            } else {
                Modifier
            }

            val cueStyle = if (cue.customPositionEnabled) {
                style.copy(
                    fontSizeSp = cue.customFontSizeSp ?: style.fontSizeSp,
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
                    fontFamily = fontFamily
                )
            }
        }
    }
}

@Composable
private fun SingleSubtitleView(
    cue: SubtitleCue,
    style: SubtitleStyle,
    fontFamily: FontFamily?
) {
    val boxModifier = if (style.hasBackgroundBox) {
        Modifier
            .background(style.backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    } else {
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }

    val annotatedText = remember(cue.rawText, cue.cleanText, style.isItalic, style.isBold, style.isUnderline) {
        buildAnnotatedSubtitle(cue.rawText, cue.cleanText, style)
    }

    Box(
        modifier = boxModifier.testTag("subtitle_cue_item"),
        contentAlignment = Alignment.Center
    ) {
        // Outline layer if enabled
        if (style.hasOutline && style.outlineWidth > 0) {
            Text(
                text = annotatedText,
                textAlign = style.textAlign,
                fontSize = style.fontSize,
                fontFamily = fontFamily,
                style = TextStyle(
                    color = style.outlineColor,
                    drawStyle = Stroke(
                        width = style.outlineWidth * 2.5f
                    )
                )
            )
        }

        // Main text layer (with subtle shadow for depth)
        Text(
            text = annotatedText,
            color = style.textColor,
            textAlign = style.textAlign,
            fontSize = style.fontSize,
            fontFamily = fontFamily,
            style = TextStyle(
                shadow = if (style.hasOutline) null else Shadow(
                    color = Color.Black,
                    blurRadius = 4f
                )
            )
        )
    }
}

/**
 * Parses ASS tags ({\i1}, {\b1}, {\u1}, \N, etc.) and HTML tags (<i>, <b>, <u>)
 * to build an AnnotatedString preserving styling, bold, italic, and underline.
 */
fun buildAnnotatedSubtitle(
    rawText: String,
    cleanText: String,
    style: SubtitleStyle
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

        for (match in matches) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                val plainPart = sourceText.substring(currentIndex, matchRange.first)
                if (plainPart.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontWeight = if (activeBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (activeItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (activeUnderline) TextDecoration.Underline else TextDecoration.None
                        )
                    ) {
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
                withStyle(
                    SpanStyle(
                        fontWeight = if (activeBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (activeItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (activeUnderline) TextDecoration.Underline else TextDecoration.None
                    )
                ) {
                    append(tail)
                }
            }
        }
    }
}
