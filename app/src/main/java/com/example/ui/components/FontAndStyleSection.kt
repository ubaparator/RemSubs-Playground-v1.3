package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubtitleHorizontalAlign
import com.example.model.SubtitleStyle
import com.example.model.SubtitleVerticalAlign
import com.example.ui.AxiSubUiState

@Composable
fun FontAndStyleSection(
    uiState: AxiSubUiState,
    onPickTtfFont: () -> Unit,
    onSelectPresetFont: (String, FontFamily?) -> Unit,
    onUpdateStyle: ((SubtitleStyle) -> SubtitleStyle) -> Unit,
    onExportAss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val style = uiState.subtitleStyle

    var showTextColorPicker by remember { mutableStateOf(false) }
    var showOutlineColorPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Real-time ASS synchronization & Export banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eşzamanlı .ASS Çıktısı",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Seçtiğiniz font (${style.fontName}) ve renkler anında arka plandaki .ASS çıktısına aktarılıyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onExportAss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("export_ass_button_font_tab")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Çıktı Al", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // 1. TTF Font Upload Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Özel .TTF Font Dosyası",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (uiState.customFontName != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "ÖZEL FONT",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Aktif Font: ${uiState.customFontName ?: style.fontName}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickTtfFont,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("upload_ttf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cihazdan .TTF Yükle", fontSize = 12.sp)
                    }

                    if (uiState.customFontName != null) {
                        OutlinedButton(
                            onClick = { onSelectPresetFont("Varsayılan (System)", null) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Sıfırla",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sıfırla", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Built-in Font Presets
                Text(
                    text = "Hazır Sistem Fontları:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Sans-Serif" to FontFamily.SansSerif,
                        "Serif" to FontFamily.Serif,
                        "Monospace" to FontFamily.Monospace,
                        "Cursive" to FontFamily.Cursive
                    ).forEach { (name, family) ->
                        val isSelected = style.fontName == name && uiState.customFontName == null
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectPresetFont(name, family) },
                            label = { Text(name, fontSize = 11.sp) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        // 2. Live Subtitle Preview Box
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181A20)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "CANLI ALTYAZI ÖNİZLEME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SubtitleOverlay(
                        activeCues = listOf(
                            com.example.model.SubtitleCue(
                                id = 999,
                                startTimeMs = 0L,
                                endTimeMs = 1000L,
                                rawText = "remsubs playground Canlı Font ve Yerleşim Testi\n(Örnek İkinci Satır)",
                                cleanText = "remsubs playground Canlı Font ve Yerleşim Testi\n(Örnek İkinci Satır)"
                            )
                        ),
                        style = style.copy(verticalOffsetDp = 4f),
                        fontFamily = uiState.loadedFontFamily,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 3. Font Boyutu & Tipografi Ayarları
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Font Boyutu: ${style.fontSizeSp.toInt()} sp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Bold, Italic & Underline Toggles
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onUpdateStyle { it.copy(isBold = !it.isBold) } },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (style.isBold) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .testTag("toggle_bold_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatBold,
                                contentDescription = "Kalın",
                                tint = if (style.isBold) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onUpdateStyle { it.copy(isItalic = !it.isItalic) } },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (style.isItalic) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .testTag("toggle_italic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatItalic,
                                contentDescription = "İtalik",
                                tint = if (style.isItalic) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onUpdateStyle { it.copy(isUnderline = !it.isUnderline) } },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (style.isUnderline) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .testTag("toggle_underline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatUnderlined,
                                contentDescription = "Altı Çizili",
                                tint = if (style.isUnderline) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Slider(
                    value = style.fontSizeSp,
                    onValueChange = { onUpdateStyle { s -> s.copy(fontSizeSp = it) } },
                    valueRange = 14f..44f,
                    steps = 15,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("font_size_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Text Color Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatColorText,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Metin Rengi:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = style.textColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = String.format("#%02X%02X%02X", (style.textColor.red * 255).toInt(), (style.textColor.green * 255).toInt(), (style.textColor.blue * 255).toInt()),
                            color = if (isColorDark(style.textColor)) Color.White else Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Color(0xFFFFFFFF) to "Beyaz",
                        Color(0xFFFFEA00) to "Sarı",
                        Color(0xFF00E5FF) to "Camgöbeği",
                        Color(0xFF69F0AE) to "Açık Yeşil",
                        Color(0xFFFF80AB) to "Pembe",
                        Color(0xFFFFAB40) to "Turuncu"
                    ).forEach { (col, _) ->
                        val isSelected = style.textColor == col
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .clickable { onUpdateStyle { it.copy(textColor = col) } }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (col == Color.White || col == Color(0xFFFFEA00)) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = { showTextColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("pick_text_color_rgb_btn")
                    ) {
                        Icon(Icons.Default.ColorLens, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RGB ile Seç", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Kenarlık / Gölge & Arka Plan Kutusu
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Metin Kenarlık Çizgisi (Outline)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = style.hasOutline,
                        onCheckedChange = { onUpdateStyle { s -> s.copy(hasOutline = it) } }
                    )
                }

                if (style.hasOutline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Kenarlık Kalınlığı", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "${style.outlineWidth.toInt()} px", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = style.outlineWidth,
                        onValueChange = { onUpdateStyle { s -> s.copy(outlineWidth = it) } },
                        valueRange = 1f..6f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dış Çizgi Rengi (Font Outline Color) Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dış Çizgi (Kenarlık) Rengi:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Current color preview chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = style.outlineColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = getOutlineColorLabel(style.outlineColor),
                                color = if (isColorDark(style.outlineColor)) Color.White else Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset outline color buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val outlineColorPresets = listOf(
                            Color(0xFF000000) to "Siyah",
                            Color(0xFFFFFFFF) to "Beyaz",
                            Color(0xFF263238) to "Koyu Gri",
                            Color(0xFF0D47A1) to "Lacivert",
                            Color(0xFFB71C1C) to "Kırmızı",
                            Color(0xFF4A148C) to "Mor",
                            Color(0xFFFF6F00) to "Turuncu",
                            Color(0xFF004D40) to "Yeşil"
                        )
                        outlineColorPresets.forEach { (col, _) ->
                            val isSelected = style.outlineColor == col
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable { onUpdateStyle { it.copy(outlineColor = col) } }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (col == Color.White) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // RGB selection button for outline color
                    OutlinedButton(
                        onClick = { showOutlineColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_outline_color_rgb_btn")
                    ) {
                        Icon(Icons.Default.ColorLens, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kenarlık Rengini RGB Kodu ile Seç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Arka Plan Gölge Kutusu",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Okunabilirliği artırmak için koyu kutu ekler",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = style.hasBackgroundBox,
                        onCheckedChange = { onUpdateStyle { s -> s.copy(hasBackgroundBox = it) } }
                    )
                }
            }
        }

        // 5. Konum ve Yerleşim Ayarları (Alignments & Offset)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Altyazı Ekran Konumu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Dikey Konum (Üst / Orta / Alt)
                Text(text = "Dikey Hizalama:", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlignOptionButton(
                        title = "Üst",
                        icon = Icons.Default.AlignVerticalTop,
                        selected = style.verticalAlign == SubtitleVerticalAlign.TOP,
                        onClick = { onUpdateStyle { it.copy(verticalAlign = SubtitleVerticalAlign.TOP) } },
                        modifier = Modifier.weight(1f)
                    )
                    AlignOptionButton(
                        title = "Orta",
                        icon = Icons.Default.AlignVerticalCenter,
                        selected = style.verticalAlign == SubtitleVerticalAlign.MIDDLE,
                        onClick = { onUpdateStyle { it.copy(verticalAlign = SubtitleVerticalAlign.MIDDLE) } },
                        modifier = Modifier.weight(1f)
                    )
                    AlignOptionButton(
                        title = "Alt",
                        icon = Icons.Default.AlignVerticalBottom,
                        selected = style.verticalAlign == SubtitleVerticalAlign.BOTTOM,
                        onClick = { onUpdateStyle { it.copy(verticalAlign = SubtitleVerticalAlign.BOTTOM) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Yatay Konum (Sol / Orta / Sağ)
                Text(text = "Yatay Hizalama:", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlignOptionButton(
                        title = "Sol",
                        icon = Icons.AutoMirrored.Filled.AlignHorizontalLeft,
                        selected = style.horizontalAlign == SubtitleHorizontalAlign.LEFT,
                        onClick = { onUpdateStyle { it.copy(horizontalAlign = SubtitleHorizontalAlign.LEFT) } },
                        modifier = Modifier.weight(1f)
                    )
                    AlignOptionButton(
                        title = "Orta",
                        icon = Icons.Default.AlignHorizontalCenter,
                        selected = style.horizontalAlign == SubtitleHorizontalAlign.CENTER,
                        onClick = { onUpdateStyle { it.copy(horizontalAlign = SubtitleHorizontalAlign.CENTER) } },
                        modifier = Modifier.weight(1f)
                    )
                    AlignOptionButton(
                        title = "Sağ",
                        icon = Icons.AutoMirrored.Filled.AlignHorizontalRight,
                        selected = style.horizontalAlign == SubtitleHorizontalAlign.RIGHT,
                        onClick = { onUpdateStyle { it.copy(horizontalAlign = SubtitleHorizontalAlign.RIGHT) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Kenar Mesafesi (Y-Offset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Dikey Kenar Mesafesi (Y-Offset)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(text = "${style.verticalOffsetDp.toInt()} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = style.verticalOffsetDp,
                    onValueChange = { onUpdateStyle { s -> s.copy(verticalOffsetDp = it) } },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vertical_offset_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showTextColorPicker) {
        RgbColorPickerDialog(
            title = "Metin Rengi Seç (RGB & HEX)",
            initialColor = style.textColor,
            onDismiss = { showTextColorPicker = false },
            onColorSelected = { selectedColor ->
                onUpdateStyle { it.copy(textColor = selectedColor) }
            }
        )
    }

    if (showOutlineColorPicker) {
        RgbColorPickerDialog(
            title = "Kenarlık (Dış Çizgi) Rengi Seç (RGB & HEX)",
            initialColor = style.outlineColor,
            onDismiss = { showOutlineColorPicker = false },
            onColorSelected = { selectedColor ->
                onUpdateStyle { it.copy(outlineColor = selectedColor, hasOutline = true) }
            }
        )
    }
}

@Composable
private fun AlignOptionButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .height(38.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun isColorDark(color: Color): Boolean {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance < 0.5f
}

private fun getOutlineColorLabel(color: Color): String {
    return when (color) {
        Color(0xFF000000) -> "Siyah"
        Color(0xFFFFFFFF) -> "Beyaz"
        Color(0xFF263238) -> "Koyu Gri"
        Color(0xFF0D47A1) -> "Lacivert"
        Color(0xFFB71C1C) -> "Kırmızı"
        Color(0xFF4A148C) -> "Mor"
        Color(0xFFFF6F00) -> "Turuncu"
        Color(0xFF004D40) -> "Yeşil"
        else -> {
            val r = (color.red * 255).toInt()
            val g = (color.green * 255).toInt()
            val b = (color.blue * 255).toInt()
            String.format("#%02X%02X%02X", r, g, b)
        }
    }
}

