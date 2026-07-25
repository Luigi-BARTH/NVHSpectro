package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.TelemetryData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    telemetry: TelemetryData,
    onExport: (String, String) -> Unit
) {
    var pedalPercent by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exporter les données NVH") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Vitesse actuelle : ${String.format("%.1f", telemetry.speedKmh)} km/h")
                
                OutlinedTextField(
                    value = pedalPercent,
                    onValueChange = { pedalPercent = it },
                    label = { Text("Enfoncement pédale (%)") },
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Commentaires / Conditions") },
                    modifier = Modifier.height(100.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(pedalPercent, comments) }
            ) {
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
