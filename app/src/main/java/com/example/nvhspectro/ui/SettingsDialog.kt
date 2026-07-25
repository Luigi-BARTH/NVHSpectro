package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    minDb: Double,
    maxDb: Double,
    onMinDbChange: (Double) -> Unit,
    onMaxDbChange: (Double) -> Unit,
    fftSize: Int,
    onFftSizeChange: (Int) -> Unit,
    maxFreq: Int,
    onMaxFreqChange: (Int) -> Unit,
    timeWindowSec: Double,
    onTimeWindowChange: (Double) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres NVH") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Temps d'affichage
                Column {
                    Text("Temps d'affichage : ${String.format("%.1f", timeWindowSec)} s")
                    Slider(
                        value = timeWindowSec.toFloat(),
                        onValueChange = { onTimeWindowChange(it.toDouble()) },
                        valueRange = 3f..30f
                    )
                }

                // dB Min
                Column {
                    Text("Niveau Min (dB): ${minDb.toInt()}")
                    Slider(
                        value = minDb.toFloat(),
                        onValueChange = { onMinDbChange(it.toDouble()) },
                        valueRange = -120f..0f
                    )
                }
                
                // dB Max
                Column {
                    Text("Niveau Max (dB): ${maxDb.toInt()}")
                    Slider(
                        value = maxDb.toFloat(),
                        onValueChange = { onMaxDbChange(it.toDouble()) },
                        valueRange = -40f..50f
                    )
                }

                // Taille FFT
                Column {
                    Text("Résolution FFT (Taille du buffer)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(1024, 2048, 4096).forEach { size ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (fftSize == size),
                                    onClick = { onFftSizeChange(size) }
                                )
                                Text(size.toString())
                            }
                        }
                    }
                }
                
                // Fréquence Max
                Column {
                    Text("Fréquence Max (Hz): $maxFreq")
                    Slider(
                        value = maxFreq.toFloat(),
                        onValueChange = { onMaxFreqChange(it.toInt()) },
                        valueRange = 1000f..22050f
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Valider")
            }
        }
    )
}
