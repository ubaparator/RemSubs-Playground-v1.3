package com.example.mkv

/**
 * Metadata for a softsub subtitle stream inside an MKV container.
 */
data class MkvSubtitleTrack(
    val streamIndex: Int,          // Stream index in container (e.g. 0, 1, 2)
    val subtitleTrackNumber: Int,  // Sequential subtitle index (1, 2, 3...)
    val codecName: String,         // "ass", "ssa", "subrip", "srt", "webvtt", "mov_text", etc.
    val languageCode: String,      // "tur", "eng", "jpn", "und", etc.
    val languageDisplayName: String,// "Türkçe", "English", "Japonca", "Belirtilmemiş"
    val trackTitle: String,        // e.g. "Türkçe Çeviri", "Dialogue", or default title
    val isDefault: Boolean,
    val isForced: Boolean,
    val isSupportedTextFormat: Boolean = true // True for ASS/SSA, SRT/SubRip, WebVTT
) {
    val fileExtension: String
        get() = when (codecName.lowercase()) {
            "ass", "ssa" -> "ass"
            "subrip", "srt" -> "srt"
            "webvtt", "vtt" -> "vtt"
            else -> "ass"
        }

    val displaySummary: String
        get() {
            val codecLabel = codecName.uppercase()
            val defaultBadge = if (isDefault) " [Varsayılan]" else ""
            val forcedBadge = if (isForced) " [Zorunlu]" else ""
            return "Altyazı $subtitleTrackNumber — $languageDisplayName — $codecLabel$defaultBadge$forcedBadge"
        }
}
