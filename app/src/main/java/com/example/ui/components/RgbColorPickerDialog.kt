package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Modern, user-friendly RGB & HEX Color Picker Dialog.
 * Allows choosing any custom color via:
 * 1. Direct HEX string input (e.g., #FFFFFF or FF5500)
 * 2. Independent Red (0-255), Green (0-255), Blue (0-255) sliders and value inputs
 * 3. Quick high-visibility curated color presets
 * 4. Real-time preview against light and dark backgrounds with subtitle text simulation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RgbColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val initialR = (initialColor.red * 255).roundToInt().coerceIn(0, 255)
    val initialG = (initialColor.green * 255).roundToInt().coerceIn(0, 255)
    val initialB = (initialColor.blue * 255).roundToInt().coerceIn(0, 255)

    var redVal by remember { mutableFloatStateOf(initialR.toFloat()) }
    var greenVal by remember { mutableFloatStateOf(initialG.toFloat()) }
    var blueVal by remember { mutableFloatStateOf(initialB.toFloat()) }

    var hexInput by remember {
        mutableStateOf(String.format("%02X%02X%02X", initialR, initialG, initialB))
    }
    var hexError by remember { mutableStateOf(false) }

    val currentColor = Color(
        red = (redVal / 255f).coerceIn(0f, 1f),
        green = (greenVal / 255f).coerceIn(0f, 1f),
        blue = (blueVal / 255f).coerceIn(0f, 1f)
    )

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        val cr = r.coerceIn(0, 255)
        val cg = g.coerceIn(0, 255)
        val cb = b.coerceIn(0, 255)
        redVal = cr.toFloat()
        greenVal = cg.toFloat()
        blueVal = cb.toFloat()
        hexInput = String.format("%02X%02X%02X", cr, cg, cb)
        hexError = false
    }

    fun tryParseHex(input: String) {
        val clean = input.trim().removePrefix("#").trim()
        hexInput = clean
        if (clean.length == 6) {
            try {
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                redVal = r.toFloat()
                greenVal = g.toFloat()
                blueVal = b.toFloat()
                hexError = false
            } catch (e: NumberFormatException) {
                hexError = true
            }
        } else {
            hexError = clean.isNotEmpty() && clean.length > 6
        }
    }

    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Live Color Preview Box with Subtitle Mockup
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "CANLI ÖNİZLEME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Dark video background sample
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF12151D))
                                .border(1.dp, Color(0xFF2A2E3D), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Altyazı Örneği (Koyu Arka Plan)",
                                color = currentColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Light video background sample
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE2E8F0))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Altyazı Örneği (Açık Arka Plan)",
                                color = currentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Color Info & Copy Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(currentColor)
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "#${hexInput.uppercase()}",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "RGB(${redVal.roundToInt()}, ${greenVal.roundToInt()}, ${blueVal.roundToInt()})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("#${hexInput.uppercase()}"))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Kodu Kopyala",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Direct HEX Code Input Field
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { tryParseHex(it) },
                    label = { Text("HEX Renk Kodu (Örn: FFFFFF)") },
                    leadingIcon = {
                        Text(
                            text = "#",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                    },
                    isError = hexError,
                    supportingText = {
                        if (hexError) {
                            Text("Geçerli bir 6 haneli HEX renk kodu girin (0-9, A-F)", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("İstediğiniz renk kodunu doğrudan yazabilir veya yapıştırabilirsiniz")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hex_color_input_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. RGB Sliders (Red, Green, Blue)
                Text(
                    text = "RGB RENK KAYDIRICILARI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Red Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kırmızı (R): ${redVal.roundToInt()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF5350)
                    )
                }
                Slider(
                    value = redVal,
                    onValueChange = {
                        redVal = it
                        hexInput = String.format("%02X%02X%02X", it.roundToInt(), greenVal.roundToInt(), blueVal.roundToInt())
                        hexError = false
                    },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE53935),
                        activeTrackColor = Color(0xFFEF5350)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_red")
                )

                // Green Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Yeşil (G): ${greenVal.roundToInt()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF43A047)
                    )
                }
                Slider(
                    value = greenVal,
                    onValueChange = {
                        greenVal = it
                        hexInput = String.format("%02X%02X%02X", redVal.roundToInt(), it.roundToInt(), blueVal.roundToInt())
                        hexError = false
                    },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2E7D32),
                        activeTrackColor = Color(0xFF43A047)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_green")
                )

                // Blue Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mavi (B): ${blueVal.roundToInt()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E88E5)
                    )
                }
                Slider(
                    value = blueVal,
                    onValueChange = {
                        blueVal = it
                        hexInput = String.format("%02X%02X%02X", redVal.roundToInt(), greenVal.roundToInt(), it.roundToInt())
                        hexError = false
                    },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1565C0),
                        activeTrackColor = Color(0xFF1E88E5)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_blue")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Quick Preset Palettes
                Text(
                    text = "HIZLI RENK PALETİ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                val presetColors = listOf(
                    Triple(255, 255, 255) to "Beyaz",
                    Triple(0, 0, 0) to "Siyah",
                    Triple(255, 235, 59) to "Sarı",
                    Triple(255, 179, 0) to "Altın",
                    Triple(255, 112, 67) to "Turuncu",
                    Triple(229, 57, 53) to "Kırmızı",
                    Triple(216, 27, 96) to "Pembe",
                    Triple(142, 36, 170) to "Mor",
                    Triple(26, 35, 126) to "Lacivert",
                    Triple(30, 136, 229) to "Mavi",
                    Triple(0, 172, 193) to "Camgöbeği",
                    Triple(0, 137, 123) to "Turkuaz",
                    Triple(67, 160, 71) to "Yeşil",
                    Triple(124, 179, 66) to "Limon",
                    Triple(38, 50, 56) to "Koyu Gri",
                    Triple(120, 144, 156) to "Gümüş"
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetColors.forEach { (rgb, name) ->
                        val color = Color(rgb.first, rgb.second, rgb.third)
                        val isSelected = redVal.roundToInt() == rgb.first &&
                                greenVal.roundToInt() == rgb.second &&
                                blueVal.roundToInt() == rgb.third

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    updateFromRgb(rgb.first, rgb.second, rgb.third)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = name,
                                    tint = if (rgb.first > 200 && rgb.second > 200 && rgb.third > 200) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(currentColor)
                    onDismiss()
                },
                modifier = Modifier.testTag("color_picker_apply_button")
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Uygula (Seç)")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
