package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.search.AnimeSearchResult
import com.example.search.AnimeSearchUiState
import com.example.search.AnimeSource
import com.example.search.VideoQuality
import com.example.torrent.TorrentDownloadInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Anime search over SubsPlease and Nyaa. Picking a result starts a magnet download in the
 * built-in torrent client and returns to the main menu, where its progress is shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimeSearchScreen(
    state: AnimeSearchUiState,
    torrentDownloadInfo: TorrentDownloadInfo,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectSource: (AnimeSource) -> Unit,
    onSelectQuality: (VideoQuality) -> Unit,
    onToggleSortBySeeders: () -> Unit,
    onDownload: (AnimeSearchResult) -> Unit,
    snackbarHostState: SnackbarHostState? = null
) {
    var pendingResult by remember { mutableStateOf<AnimeSearchResult?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val sortBySeeders = state.sortBySeeders && state.source == AnimeSource.NYAA
    val visibleResults = remember(state.results, sortBySeeders) {
        if (sortBySeeders) state.results.sortedByDescending { it.seeders ?: -1 } else state.results
    }
    val submitSearch = {
        keyboard?.hide()
        onSearch()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Anime Ara", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "SubsPlease • Nyaa • Magnet ile indir",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("anime_search_back")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "controls") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = state.query,
                                    onValueChange = onQueryChange,
                                    label = { Text("Anime adı", fontSize = 12.sp) },
                                    placeholder = { Text("örn. Frieren, One Piece", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (state.query.isNotEmpty()) {
                                            IconButton(onClick = { onQueryChange("") }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Temizle")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_anime_search")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = submitSearch,
                                    enabled = !state.isLoading,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                    modifier = Modifier.testTag("btn_anime_search")
                                ) {
                                    Text("Ara", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Kaynak",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AnimeSource.entries.forEach { source ->
                                    FilterChip(
                                        selected = state.source == source,
                                        onClick = { onSelectSource(source) },
                                        label = {
                                            Text(
                                                text = source.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (state.source == source) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.testTag("chip_source_${source.name.lowercase(Locale.ROOT)}")
                                    )
                                }
                            }
                            Text(
                                text = state.source.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Çözünürlük",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VideoQuality.entries.forEach { quality ->
                                    FilterChip(
                                        selected = state.quality == quality,
                                        onClick = { onSelectQuality(quality) },
                                        label = {
                                            Text(
                                                text = quality.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (state.quality == quality) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.testTag("chip_quality_${quality.pixels}")
                                    )
                                }
                            }
                        }
                    }
                }

                if (torrentDownloadInfo.isActive) {
                    item(key = "active_download") {
                        ActiveDownloadCard(torrentDownloadInfo, onClick = onBack)
                    }
                }

                when {
                    state.isLoading -> item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "${state.source.displayName} aranıyor...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    state.errorMessage != null -> item(key = "error") {
                        SearchErrorCard(
                            message = state.errorMessage,
                            canRetry = state.query.trim().length >= 2,
                            onRetry = submitSearch
                        )
                    }

                    state.lastSearchedQuery == null -> item(key = "hint") {
                        SearchHintCard()
                    }

                    visibleResults.isEmpty() -> item(key = "empty") {
                        InfoCard(
                            text = "\"${state.lastSearchedQuery}\" için ${state.source.displayName} üzerinde ${state.quality.label} sonuç bulunamadı. " +
                                "Farklı bir yazım (Romaji / İngilizce ad) ya da diğer kaynağı dene."
                        )
                    }

                    else -> {
                        item(key = "results_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${visibleResults.size} sonuç • ${state.quality.label}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (state.source == AnimeSource.NYAA) {
                                    FilterChip(
                                        selected = state.sortBySeeders,
                                        onClick = onToggleSortBySeeders,
                                        label = { Text("Seed'e göre", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("chip_sort_seeders")
                                    )
                                }
                            }
                        }
                        items(visibleResults, key = { it.id }) { result ->
                            SearchResultCard(result = result, onClick = { pendingResult = result })
                        }
                    }
                }
            }
        }
    }

    pendingResult?.let { result ->
        ConfirmDownloadDialog(
            result = result,
            activeDownloadName = torrentDownloadInfo.takeIf { it.isActive }?.torrentName,
            onConfirm = {
                pendingResult = null
                onDownload(result)
            },
            onDismiss = { pendingResult = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultCard(result: AnimeSearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("anime_result_${result.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Badge(result.source.displayName, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    result.quality?.let { Badge(it.label, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                    if (result.isBatch) Badge("Batch", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    if (result.source == AnimeSource.NYAA && result.isTrusted) Badge("Güvenilir", Color(0xFF1B5E20), Color.White)
                    if (result.isRemake) Badge("Remake", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (result.source == AnimeSource.SUBSPLEASE && result.showName != null) {
                    Text(
                        text = result.showName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    result.episode?.let {
                        Text(
                            text = if (result.isBatch) "Bölüm $it (toplu)" else "Bölüm $it",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = result.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = listOfNotNull(result.sizeText, relativeTime(result.publishedAtMillis)).joinToString("  •  ")
                    if (meta.isNotEmpty()) {
                        Text(text = meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    result.seeders?.let { seeders ->
                        if (meta.isNotEmpty()) Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "▲ $seeders",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = seederColor(seeders)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "▼ ${result.leechers ?: 0}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "İndir",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmDownloadDialog(
    result: AnimeSearchResult,
    activeDownloadName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
        title = { Text("İndirilsin mi?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = result.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(result.source.displayName, result.quality?.label, result.sizeText).joinToString("  •  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.seeders == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bu torrent'i şu an paylaşan (seeder) görünmüyor; indirme başlamayabilir.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (result.isBatch) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bu bir toplu (batch) yayın. Dosya listesi gelince sadece istediğin bölümü seçip indirebilirsin.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (activeDownloadName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = "Devam eden indirme durdurulacak: $activeDownloadName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag("btn_confirm_anime_download")) {
                Text("İndir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
private fun ActiveDownloadCard(info: TorrentDownloadInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Şu an indiriliyor: ${info.torrentName}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { info.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%${info.progressPercentage} • ${info.speedText} • Ayrıntılar için dokun",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SearchErrorCard(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Arama yapılamadı", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            if (canRetry) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tekrar Dene", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchHintCard() {
    InfoCard(
        text = "• SubsPlease: Haftalık simulcast bölümleri. Her bölüm 480p / 720p / 1080p gelir; İngilizce altyazı MKV'nin içinde softsub olarak bulunur, \"Videodan Altyazı Ayıkla\" ile çıkarabilirsin.\n" +
            "• Nyaa: Tüm fansub grupları ve toplu (batch) yayınlar, seeder sayılarıyla.\n" +
            "• İpucu: Romaji ya da İngilizce adla ara (örn. \"Frieren\", \"Kusuriya no Hitorigoto\")."
    )
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = container) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun seederColor(seeders: Int): Color = when {
    seeders >= 10 -> Color(0xFF4CAF50)
    seeders > 0 -> Color(0xFFFF9800)
    else -> MaterialTheme.colorScheme.error
}

private fun relativeTime(millis: Long?): String? {
    if (millis == null) return null
    val minutes = (System.currentTimeMillis() - millis) / 60_000L
    return when {
        minutes < 0 -> SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr")).format(Date(millis))
        minutes < 1 -> "az önce"
        minutes < 60 -> "$minutes dk önce"
        minutes < 24 * 60 -> "${minutes / 60} saat önce"
        minutes < 2 * 24 * 60 -> "dün"
        minutes < 30 * 24 * 60 -> "${minutes / (24 * 60)} gün önce"
        else -> SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr")).format(Date(millis))
    }
}
