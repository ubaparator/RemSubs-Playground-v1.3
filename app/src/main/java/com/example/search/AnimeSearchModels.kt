package com.example.search

enum class AnimeSource(val displayName: String, val description: String) {
    SUBSPLEASE("SubsPlease", "Haftalık yayınlar, her bölüm 480p / 720p / 1080p"),
    NYAA("Nyaa", "Tüm gruplar ve batch'ler, seeder sayılarıyla")
}

enum class VideoQuality(val label: String, val pixels: String) {
    P480("480p", "480"),
    P720("720p", "720"),
    P1080("1080p", "1080");

    companion object {
        private val P1080_PATTERN = Regex("""(?i)\b1080(?:p\d{0,3})?\b|1920x1080""")
        private val P720_PATTERN = Regex("""(?i)\b720(?:p\d{0,3})?\b|1280x720""")
        private val P480_PATTERN = Regex("""(?i)\b480(?:p\d{0,3})?\b|(?:640|848|854)x480""")

        /** Resolution named in a release title such as "[Group] Show - 01 (1080p) [ABCD1234].mkv". */
        fun detect(title: String): VideoQuality? = when {
            P1080_PATTERN.containsMatchIn(title) -> P1080
            P720_PATTERN.containsMatchIn(title) -> P720
            P480_PATTERN.containsMatchIn(title) -> P480
            else -> null
        }
    }
}

data class AnimeSearchResult(
    /** Info-hash (hex) when known; unique within one result list. */
    val id: String,
    val source: AnimeSource,
    val title: String,
    val magnetUri: String,
    val showName: String? = null,
    val episode: String? = null,
    val quality: VideoQuality? = null,
    val sizeBytes: Long? = null,
    val sizeText: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val completedDownloads: Int? = null,
    val publishedAtMillis: Long? = null,
    val isBatch: Boolean = false,
    val isTrusted: Boolean = false,
    val isRemake: Boolean = false,
    val pageUrl: String? = null
)

data class AnimeSearchUiState(
    val query: String = "",
    val source: AnimeSource = AnimeSource.SUBSPLEASE,
    val quality: VideoQuality = VideoQuality.P1080,
    val sortBySeeders: Boolean = false,
    val isLoading: Boolean = false,
    val results: List<AnimeSearchResult> = emptyList(),
    val errorMessage: String? = null,
    /** Query of the last finished search; null until the first search. */
    val lastSearchedQuery: String? = null
)

class AnimeSearchException(message: String, cause: Throwable? = null) : Exception(message, cause)
