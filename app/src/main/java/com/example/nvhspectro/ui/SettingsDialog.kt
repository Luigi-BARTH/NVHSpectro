package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    minDb: Double,
    maxDb: Double,
    onMinDbChange: (Double) -> Unit,
    onMaxDbChange: (Double) -> Unit,
    fftSize: Int,
    onFftSizeChange: (Int) -> Unit,
    minFreq: Int = 0,
    onMinFreqChange: (Int) -> Unit = {},
    maxFreq: Int,
    onMaxFreqChange: (Int) -> Unit = {},
    timeWindowSec: Double,
    onTimeWindowChange: (Double) -> Unit,
    isDetectorEnabled: Boolean = true,
    onDetectorEnabledChange: (Boolean) -> Unit = {},
    emergenceThresholdDb: Double = 2.5,
    onEmergenceThresholdChange: (Double) -> Unit = {},
    magnitudeGateDbFS: Double = -90.0,
    onMagnitudeGateChange: (Double) -> Unit = {}
) {
    val sampleRate = 44100.0
    val stepSize = fftSize / 2.0
    val dtStepMs = (stepSize / sampleRate) * 1000.0
    val tBlockMs = (fftSize / sampleRate) * 1000.0
    val fps = sampleRate / stepSize
    val dfHz = sampleRate / fftSize

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres NVH & DSP (v8.0.0 - Version Avancée)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // SECTION 1: DÉTECTEUR DE BALISES CLIGNOTANTES (POINTS 1 ET 4)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1914)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ DÉTECTEUR DE BALISES CLIGNOTANTES",
                                color = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = isDetectorEnabled,
                                onCheckedChange = onDetectorEnabledChange
                            )
                        }

                        if (isDetectorEnabled) {
                            Column {
                                Text(
                                    text = "Seuil d'Émergence (TTNR) : ${String.format("%.1f", emergenceThresholdDb)} dB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Slider(
                                    value = emergenceThresholdDb.toFloat(),
                                    onValueChange = { onEmergenceThresholdChange(it.toDouble()) },
                                    valueRange = 2f..10f
                                )
                            }

                            Column {
                                Text(
                                    text = "Porte d'Amplitude Absolue : ${magnitudeGateDbFS.toInt()} dBFS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Slider(
                                    value = magnitudeGateDbFS.toFloat(),
                                    onValueChange = { onMagnitudeGateChange(it.toDouble()) },
                                    valueRange = -100f..-30f
                                )
                            }
                        }
                    }
                }

                // Temps d'affichage
                Column {
                    Text("Temps d'affichage : ${String.format("%.1f", timeWindowSec)} s", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = timeWindowSec.toFloat(),
                        onValueChange = { onTimeWindowChange(it.toDouble()) },
                        valueRange = 3f..30f
                    )
                }

                // dB Min / Max
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Min : ${minDb.toInt()} dBFS", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = minDb.toFloat(),
                            onValueChange = { onMinDbChange(it.toDouble()) },
                            valueRange = -120f..0f
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Max : ${maxDb.toInt()} dBFS", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = maxDb.toFloat(),
                            onValueChange = { onMaxDbChange(it.toDouble()) },
                            valueRange = -40f..50f
                        )
                    }
                }

                // Taille FFT N (Affichage aéré sur 2 lignes)
                Column {
                    Text("Résolution FFT (Taille N)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(512, 1024).forEach { size ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (fftSize == size),
                                    onClick = { onFftSizeChange(size) }
                                )
                                Text("$size pts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(2048, 4096).forEach { size ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (fftSize == size),
                                    onClick = { onFftSizeChange(size) }
                                )
                                Text("$size pts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // TABLEAU RÉCAPITULATIF DSP EXPERT
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101827)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "INDICATEURS SIGNAL (N = $fftSize)",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), thickness = 0.5.dp)

                        DspInfoRow("Recouvrement (Overlap)", "50 %")
                        DspInfoRow("Incrément (Pas Δt)", String.format("%.1f ms", dtStepMs))
                        DspInfoRow("Bloc Temporel (1/Δf)", String.format("%.1f ms", tBlockMs))
                        DspInfoRow("Cadence d'affichage", String.format("%.1f trames/s", fps))
                        DspInfoRow("Résolution Fréq (Δf)", String.format("%.1f Hz", dfHz))
                    }
                }
                
                // Plage de Fréquences d'analyse (Min & Max)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fréq Min : $minFreq Hz", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = minFreq.toFloat(),
                            onValueChange = { onMinFreqChange(it.toInt()) },
                            valueRange = 0f..(maxFreq - 100).toFloat().coerceAtLeast(100f)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fréq Max : $maxFreq Hz", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = maxFreq.toFloat(),
                            onValueChange = { onMaxFreqChange(it.toInt()) },
                            valueRange = (minFreq + 100).toFloat().coerceAtMost(22000f)..22050f
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Valider", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DspInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
