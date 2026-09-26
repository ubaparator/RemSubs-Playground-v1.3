package com.example.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SubtitleVerticalAlign {
    TOP,
    MIDDLE,
    BOTTOM
}

enum class SubtitleHorizontalAlign {
    LEFT,
    CENTER,
    RIGHT
}

data class SubtitleStyle(
    val fontName: String = "Varsayılan (System)",
    val customFontPath: String? = null,
    val fontSizeSp: Float = 22f,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColor: Color = Color(0xFFFFFFFF),
    val outlineColor: Color = Color(0xFF000000),
    val hasOutline: Boolean = true,
    val outlineWidth: Float = 2.5f,
    val hasBackgroundBox: Boolean = false,
    val backgroundColor: Color = Color(0x99000000),
    val verticalAlign: SubtitleVerticalAlign = SubtitleVerticalAlign.BOTTOM,
    val horizontalAlign: SubtitleHorizontalAlign = SubtitleHorizontalAlign.CENTER,
    val verticalOffsetDp: Float = 32f, // distance from edge
    val horizontalPaddingDp: Float = 16f,
    val timeOffsetMs: Long = 0L // +/- sync offset
) {
    val fontSize: TextUnit get() = fontSizeSp.sp
    val verticalOffset: Dp get() = verticalOffsetDp.dp
    val horizontalPadding: Dp get() = horizontalPaddingDp.dp

    val textAlign: TextAlign
        get() = when (horizontalAlign) {
            SubtitleHorizontalAlign.LEFT -> TextAlign.Start
            SubtitleHorizontalAlign.CENTER -> TextAlign.Center
            SubtitleHorizontalAlign.RIGHT -> TextAlign.End
        }

    val fontWeight: FontWeight
        get() = if (isBold) FontWeight.Bold else FontWeight.Normal

    val textDecoration: TextDecoration
        get() = if (isUnderline) TextDecoration.Underline else TextDecoration.None
}
