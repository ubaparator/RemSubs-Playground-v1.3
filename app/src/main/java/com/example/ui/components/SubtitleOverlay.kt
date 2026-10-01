package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleCue
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleStyle
import com.example.model.SubtitleVerticalAlign
import com.example.parser.AssGenerator
import com.example.parser.HtmlSubtitleParser
import java.util.Locale

@Composable
fun SubtitleOverlay(
    activeCues: List<SubtitleCue>,
    style: SubtitleStyle,
    fontFamily: FontFamily?,
    sourceVideoWidth: Int = 1920,
    sourceVideoHeight: Int = 1080,
    modifier: Modifier = Modifier
) {
    if (activeCues.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val vWidth = if (sourceVideoWidth > 0) sourceVideoWidth.toFloat() else 1920f
        val vHeight = if (sourceVideoHeight > 0) sourceVideoHeight.toFloat() else 1080f
        val videoAspect = vWidth / vHeight
        val containerAspect = if (containerHeight.value > 0) containerWidth.value / containerHeight.value else videoAspect

        // Compute exact video content rectangle inside letterboxed container
        val contentWidth: Dp
        val contentHeight: Dp
        val contentOffsetX: Dp
        val contentOffsetY: Dp

        if (containerAspect > videoAspect) {
            // Container is wider: pillarbox (black bars left and right)
            contentHeight = containerHeight
            contentWidth = (contentHeight.value * videoAspect).dp
            contentOffsetX = (containerWidth - contentWidth) / 2f
            contentOffsetY = 0.dp
        } else {
            // Container is taller: letterbox (black bars top and bottom)
            contentWidth = containerWidth
            contentHeight = (contentWidth.value / videoAspect).dp
            contentOffsetX = 0.dp
            contentOffsetY = (containerHeight - contentHeight) / 2f
        }

        // Direct 1:1 scale factor from ASS PlayRes coordinates to screen preview pixels
        // Eliminates arbitrary 216dp constant completely!
        val assScale = contentHeight.value / vHeight
        val playResX = vWidth
        val playResY = vHeight

        val resolvedBaseFontSize = AssGenerator.resolveAssFontSize(style.fontSizeSp, vHeight.toInt()).toFloat()
        val scaledFontSize = (resolvedBaseFontSize * assScale).sp
        val scaledVerticalOffset = (style.verticalOffsetDp * assScale).dp
        val scaledHorizontalPadding = (style.horizontalPaddingDp * assScale).dp
        val scaledOutlineWidth = (style.outlineWidth * assScale).coerceAtLeast(1f)

        // Subtitle content Box strictly aligned with video content bounds
        Box(
            modifier = Modifier
                .offset(x = contentOffsetX, y = contentOffsetY)
                .size(contentWidth, contentHeight)
        ) {
            activeCues.forEach { cue ->
                // Check if cue has an explicit \pos(x, y) tag in ASS PlayRes coordinate system
                val posOverride = extractAssPos(cue.rawText)

                if (posOverride != null) {
                    val (rawX, rawY) = posOverride
                    // Map ASS PlayRes coordinates directly to preview content rectangle
                    val posX = (rawX / playResX) * contentWidth.value
                    val posY = (rawY / playResY) * contentHeight.value

                    Box(
                        modifier = Modifier
                            .offset(x = posX.dp, y = posY.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SingleSubtitleView(
                            cue = cue,
                            style = style,
                            effectiveFontSize = scaledFontSize,
                            effectiveOutlineWidth = scaledOutlineWidth,
                            fontFamily = fontFamily,
                            assScale = assScale,
                            playResY = playResY
                        )
                    }
                } else {
                    val overrideAlign = extractAssAlignmentFromText(cue.rawText)
                    val vAlign = if (cue.customPositionEnabled) cue.customVerticalAlign
                    else (overrideAlign?.first ?: style.verticalAlign)
                    val hAlign = if (cue.customPositionEnabled) cue.customHorizontalAlign
                    else (overrideAlign?.second ?: style.horizontalAlign)

                    val vOffset = if (cue.customPositionEnabled) (cue.customVerticalOffsetDp * assScale).dp else scaledVerticalOffset
                    val hOffset = if (cue.customPositionEnabled) (cue.customHorizontalOffsetDp * assScale).dp else 0.dp

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
                        val resolvedCueSize = AssGenerator.resolveAssFontSize(cue.customFontSizeSp, playResY.toInt()).toFloat()
                        (resolvedCueSize * assScale).sp
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
                            assScale = assScale,
                            playResY = playResY
                        )
                    }
                }
            }
        }
    }
}

private fun extractAssPos(rawText: String): Pair<Float, Float>? {
    if (!rawText.contains("\\pos")) return null
    val match = Regex("""\\pos\s*\(\s*([0-9.-]+)\s*,\s*([0-9.-]+)\s*\)""").find(rawText) ?: return null
    val x = match.groupValues[1].toFloatOrNull() ?: return null
    val y = match.groupValues[2].toFloatOrNull() ?: return null
    return Pair(x, y)
}

@Composable
private fun SingleSubtitleView(
    cue: SubtitleCue,
    style: SubtitleStyle,
    effectiveFontSize: TextUnit,
    effectiveOutlineWidth: Float,
    fontFamily: FontFamily?,
    assScale: Float,
    playResY: Float
) {
    val boxModifier = if (style.hasBackgroundBox) {
        Modifier
            .background(style.backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        Modifier
    }

    Box(
        modifier = boxModifier.testTag("subtitle_cue_${cue.id}")
    ) {
        val annotatedText = buildAnnotatedSubtitle(
            rawText = cue.rawText,
            cleanText = cue.cleanText,
            style = style,
            assScale = assScale,
            playResY = playResY
        )

        // Draw shadow/outline layer behind text
        if (style.hasOutline && !style.hasBackgroundBox) {
            Text(
                text = annotatedText,
                style = TextStyle(
                    fontSize = effectiveFontSize,
                    textAlign = style.textAlign,
                    fontFamily = fontFamily,
                    fontWeight = style.fontWeight,
                    fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = style.textDecoration,
                    drawStyle = Stroke(width = effectiveOutlineWidth * 2.2f),
                    color = style.outlineColor
                )
            )

            // Optional subtle drop shadow for depth
            Text(
                text = annotatedText,
                style = TextStyle(
                    fontSize = effectiveFontSize,
                    textAlign = style.textAlign,
                    fontFamily = fontFamily,
                    fontWeight = style.fontWeight,
                    fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = style.textDecoration,
                    color = style.outlineColor.copy(alpha = 0.6f),
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        blurRadius = effectiveOutlineWidth * 1.5f
                    )
                )
            )
        }

        // Foreground filled text
        Text(
            text = annotatedText,
            style = TextStyle(
                fontSize = effectiveFontSize,
                textAlign = style.textAlign,
                fontFamily = fontFamily,
                fontWeight = style.fontWeight,
                fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = style.textDecoration,
                color = style.textColor
            )
        )
    }
}

/**
 * Parses ASS tags ({\i1}, {\b1}, {\u1}, {\s1}, {\c...}, {\fs...}, \N, etc.)
 * and HTML tags (via HtmlSubtitleParser) to build an AnnotatedString preserving styling,
 * without ever showing raw tag syntax.
 */
fun buildAnnotatedSubtitle(
    rawText: String,
    cleanText: String,
    style: SubtitleStyle,
    assScale: Float = 1.0f,
    playResY: Float = 1080f
): AnnotatedString {
    val sourceRaw = if (rawText.isNotBlank()) rawText else cleanText
    // Step 1: Decode entities and translate all HTML tags (<i>, <b>, <u>, <br>, <font>) to ASS overrides
    val assText = HtmlSubtitleParser.htmlToAss(sourceRaw)

    // Tokenize ASS override blocks {...} and newline tags \N, \n, \h
    val tokenPattern = Regex("""(\{[^}]*\}|\\N|\\n|\\h)""")
    val matches = tokenPattern.findAll(assText).toList()

    if (matches.isEmpty()) {
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (style.isUnderline) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(HtmlSubtitleParser.cleanToPlainText(cleanText))
            }
        }
    }

    return buildAnnotatedString {
        var currentIndex = 0
        var activeBold = style.isBold
        var activeItalic = style.isItalic
        var activeUnderline = style.isUnderline
        var activeStrike = false
        var activeColor: Color? = null
        var activeFontSize: TextUnit? = null

        fun currentSpanStyle(): SpanStyle {
            val decoration = when {
                activeUnderline && activeStrike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                activeUnderline -> TextDecoration.Underline
                activeStrike -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }
            return SpanStyle(
                color = activeColor ?: Color.Unspecified,
                fontSize = activeFontSize ?: TextUnit.Unspecified,
                fontWeight = if (activeBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (activeItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = decoration
            )
        }

        for (match in matches) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                val plainPart = assText.substring(currentIndex, matchRange.first)
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
                    if (inner.contains("\\s1")) activeStrike = true
                    if (inner.contains("\\s0")) activeStrike = false

                    // Inline text color \c&HBBGGRR& or \1c&HBBGGRR&
                    if (inner.contains("\\c") || inner.contains("\\1c")) {
                        val colMatch = Regex("""\\(?:1c|c)(&?H?[0-9a-fA-F]{6}&?)""").find(inner)
                        if (colMatch != null) {
                            activeColor = parseAssInlineColor(colMatch.value)
                        } else if (inner.contains("\\c") && !inner.contains("&H")) {
                            activeColor = null // reset
                        }
                    }

                    // Inline font size \fsXX
                    if (inner.contains("\\fs")) {
                        val fsMatch = Regex("""\\fs([0-9.]+)""").find(inner)
                        val rawFs = fsMatch?.groupValues?.get(1)?.toFloatOrNull()
                        if (rawFs != null) {
                            // Scale font size directly with ASS coordinate scale
                            val fsInPreview = rawFs * assScale
                            activeFontSize = fsInPreview.sp
                        } else {
                            activeFontSize = null
                        }
                    }
                }
            }

            currentIndex = matchRange.last + 1
        }

        if (currentIndex < assText.length) {
            val tail = assText.substring(currentIndex)
            if (tail.isNotEmpty()) {
                withStyle(currentSpanStyle()) {
                    append(tail)
                }
            }
        }
    }
}

private fun parseAssInlineColor(colorTag: String): Color? {
    val clean = colorTag.replace(Regex("""[\\c1&H]"""), "").trim()
    val hex = clean.padStart(6, '0').takeLast(6)
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

fun extractAssAlignmentFromText(text: String): Pair<SubtitleVerticalAlign, SubtitleHorizontalAlign>? {
    val match = Regex("""\\an([1-9])""").find(text) ?: return null
    return when (match.groupValues[1].toIntOrNull()) {
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
