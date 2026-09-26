package com.example.model

data class SubtitleCue(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rawText: String,
    val cleanText: String,
    val styleName: String = "Default",
    val layer: Int = 0,
    val marginV: Int = 0,
    val actor: String = "",
    // Individual single-cue custom positioning & styling:
    val customPositionEnabled: Boolean = false,
    val customVerticalAlign: SubtitleVerticalAlign = SubtitleVerticalAlign.BOTTOM,
    val customHorizontalAlign: SubtitleHorizontalAlign = SubtitleHorizontalAlign.CENTER,
    val customVerticalOffsetDp: Float = 32f,
    val customHorizontalOffsetDp: Float = 0f,
    val customFontSizeSp: Float? = null,
    val customTextColorArgb: Long? = null,
    val customOutlineColorArgb: Long? = null,
    val customIsItalic: Boolean? = null,
    val customIsBold: Boolean? = null,
    val customIsUnderline: Boolean? = null
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0)

    fun formatStartTime(): String = formatTimestamp(startTimeMs)
    fun formatEndTime(): String = formatTimestamp(endTimeMs)

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val millis = ms % 1000
            return if (hours > 0) {
                String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
            } else {
                String.format("%02d:%02d.%03d", minutes, seconds, millis)
            }
        }

        fun parseTimestamp(input: String): Long? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            // Check if user entered raw milliseconds
            trimmed.toLongOrNull()?.let { if (it >= 0) return it }

            val normalized = trimmed.replace(',', '.')
            val parts = normalized.split(':')
            return try {
                when (parts.size) {
                    3 -> {
                        val hours = parts[0].toLong()
                        val minutes = parts[1].toLong()
                        val secParts = parts[2].split('.')
                        val seconds = secParts[0].toLong()
                        val millis = if (secParts.size > 1) {
                            secParts[1].padEnd(3, '0').take(3).toLong()
                        } else 0L
                        (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
                    }
                    2 -> {
                        val minutes = parts[0].toLong()
                        val secParts = parts[1].split('.')
                        val seconds = secParts[0].toLong()
                        val millis = if (secParts.size > 1) {
                            secParts[1].padEnd(3, '0').take(3).toLong()
                        } else 0L
                        (minutes * 60 + seconds) * 1000 + millis
                    }
                    1 -> {
                        val secParts = parts[0].split('.')
                        val seconds = secParts[0].toLong()
                        val millis = if (secParts.size > 1) {
                            secParts[1].padEnd(3, '0').take(3).toLong()
                        } else 0L
                        seconds * 1000 + millis
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
