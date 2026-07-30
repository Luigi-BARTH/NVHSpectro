package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.KinematicsInputMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinematicsDialog(
    currentConfig: KinematicsConfig,
    onDismiss: () -> Unit,
    onSave: (KinematicsConfig) -> Unit
) {
    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var selectedMode by remember { mutableStateOf(currentConfig.inputMode) }
    
    var v1000Text by remember { mutableStateOf(currentConfig.v1000Kmh.toString()) }
    var globalRatioText by remember { mutableStateOf(currentConfig.globalGearRatio.toString()) }
    var reductionRatioText by remember { mutableStateOf(currentConfig.gearReductionRatio.toString()) }
    var axleRatioText by remember { mutableStateOf(currentConfig.axleRatio.toString()) }
    var wheelRadiusText by remember { mutableStateOf(currentConfig.wheelRadiusMeters.toString()) }
    
    var vehicleName by remember { mutableStateOf(currentConfig.vehicleName) }
    var motorName by remember { mutableStateOf(currentConfig.motorName) }
    var comments by remember { mutableStateOf(currentConfig.comments) }
    var holdTimeText by remember { mutableStateOf(currentConfig.holdTimeSec.toString()) }

    // Construction de la configuration temporaire pour calculs en temps réel
    val tempConfig = remember(
        isEnabled, selectedMode, v1000Text, globalRatioText, 
        reductionRatioText, axleRatioText, wheelRadiusText, 
        vehicleName, motorName, comments, holdTimeText
    ) {
        KinematicsConfig(
            isEnabled = isEnabled,
            inputMode = selectedMode,
            v1000Kmh = v1000Text.toDoubleOrNull() ?: 10.0,
            globalGearRatio = globalRatioText.toDoubleOrNull() ?: 9.5,
            gearReductionRatio = reductionRatioText.toDoubleOrNull() ?: 3.2,
            axleRatio = axleRatioText.toDoubleOrNull() ?: 3.0,
            wheelRadiusMeters = wheelRadiusText.toDoubleOrNull() ?: 0.31,
            vehicleName = vehicleName,
            motorName = motorName,
            comments = comments,
            holdTimeSec = holdTimeText.toDoubleOrNull() ?: 3.0
        )
    }

    val effectiveV1000 = tempConfig.getEffectiveV1000()
    val h1At50KmhHz = tempConfig.calculateH1FreqHz(50f)
    val rpmAt50Kmh = tempConfig.calculateRpm(50f).toInt()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Titre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ Paramètres GMPe & Kinématique",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }

                Divider()

                // Section Identifiants Véhicule & Moteur
                Text("🚘 Identification du Test Véhicule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = vehicleName,
                    onValueChange = { vehicleName = it },
                    label = { Text("Nom / Modèle Véhicule") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = motorName,
                    onValueChange = { motorName = it },
                    label = { Text("Nom Moteur / GMPe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()

                // Section Mode de saisie de la V1000
                Text("🔗 Correspondance Vitesse (km/h) ↔ Régime (RPM)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    KinematicsInputMode.values().forEach { mode ->
                        FilterChip(
                            selected = (selectedMode == mode),
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Champs dynamiques selon le mode sélectionné
                when (selectedMode) {
                    KinematicsInputMode.V1000 -> {
                        OutlinedTextField(
                            value = v1000Text,
                            onValueChange = { v1000Text = it },
                            label = { Text("V1000 (km/h pour 1000 RPM)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    KinematicsInputMode.GEAR_RATIO -> {
                        OutlinedTextField(
                            value = globalRatioText,
                            onValueChange = { globalRatioText = it },
                            label = { Text("Rapport Global de Démultiplication Total") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = wheelRadiusText,
                            onValueChange = { wheelRadiusText = it },
                            label = { Text("Rayon sous charge du pneu (mètres, ex: 0.31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    KinematicsInputMode.DETAILED_CHAIN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reductionRatioText,
                                onValueChange = { reductionRatioText = it },
                                label = { Text("Réducteur / Descente") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = axleRatioText,
                                onValueChange = { axleRatioText = it },
                                label = { Text("Rapport Pont") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = wheelRadiusText,
                            onValueChange = { wheelRadiusText = it },
                            label = { Text("Rayon pneu (mètres, ex: 0.31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Carte récapitulative des valeurs calculées
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "📐 Synthèse V1000 & Fondamental H1 :",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "• V1000 équivalente : %.2f km/h / 1000 RPM".format(effectiveV1000),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "• À 50 km/h : %d RPM ➔ H1 = %.2f Hz".format(rpmAt50Kmh, h1At50KmhHz),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Divider()

                // Rémanence visuelle & Commentaires
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = holdTimeText,
                        onValueChange = { holdTimeText = it },
                        label = { Text("Rémanence Tags (sec)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Commentaires libres d'essai") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(tempConfig) }) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}
