package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.KinematicsConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergenceReportDialog(
    entries: List<EmergenceReportEntry>,
    kinematicsConfig: KinematicsConfig,
    onDismiss: () -> Unit,
    onClearReport: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Titre d'en-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📊 Rapport d'Émergence Harmonique NVH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (kinematicsConfig.isEnabled && (kinematicsConfig.vehicleName.isNotEmpty() || kinematicsConfig.motorName.isNotEmpty())) {
                            Text(
                                text = "🚘 ${kinematicsConfig.vehicleName} ${if (kinematicsConfig.motorName.isNotEmpty()) "| ${kinematicsConfig.motorName}" else ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TextButton(onClick = onClearReport) {
                        Text("Effacer", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                Divider()

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!kinematicsConfig.isEnabled) 
                                "⚠️ Activez la kinématique GMPe pour caractériser les rangs d'harmoniques H_k." 
                            else 
                                "Aucune émergence harmonique significative détectée pour le moment.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    // En-tête de tableau
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ordre", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                        Text("Vitesse (km/h)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                        Text("Régime (RPM)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                        Text("Fréq. (Hz)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                        Text("Émergence", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                    }

                    // Liste scrollable des entrées
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(entries.sortedByDescending { it.maxEmergenceDb }) { item ->
                            EmergenceReportRow(item)
                        }
                    }
                }

                Divider()

                // Boutons d'action : Réinitialiser & Fermer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onClearReport,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("🔄 Réinitialiser le rapport", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(onClick = onDismiss) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
fun EmergenceReportRow(entry: EmergenceReportEntry) {
    val isCritical = entry.maxEmergenceDb >= 6.0
    val badgeBg = if (isCritical) Color(0xFFFF1744) else Color(0xFFFFC107)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ordre H_k avec badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.9f)
            ) {
                Text(
                    text = entry.orderName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Plage de vitesse
            Text(
                text = "%.0f - %.0f".format(entry.minSpeedKmh, entry.maxSpeedKmh),
                fontSize = 11.sp,
                modifier = Modifier.weight(1.3f)
            )

            // Plage de régime RPM
            Text(
                text = "${entry.minRpm} - ${entry.maxRpm}",
                fontSize = 11.sp,
                modifier = Modifier.weight(1.3f)
            )

            // Plage de fréquence Hz
            Text(
                text = "${entry.minFreqHz} - ${entry.maxFreqHz}",
                fontSize = 11.sp,
                modifier = Modifier.weight(1.1f)
            )

            // Émergence max TTNR
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeBg,
                modifier = Modifier.weight(1.1f)
            ) {
                Text(
                    text = "+%.1f dB".format(entry.maxEmergenceDb),
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
