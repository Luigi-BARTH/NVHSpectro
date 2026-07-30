package com.example.nvhspectro

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.ui.InfoDialog
import com.example.nvhspectro.ui.TelemetryGraph
import com.example.nvhspectro.ui.TelemetryMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    if (permissionsGranted) {
        val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        AppScreen(viewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("En attente des permissions (Microphone, GPS)...")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: MainViewModel) {
    val telemetry by viewModel.telemetryState.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()

    val fftHistory by viewModel.fftHistory.collectAsState()
    val fftHistoryAbsolute by viewModel.fftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by viewModel.fftHistoryTTNR.collectAsState()
    val isDetectorEnabled by viewModel.isDetectorEnabled.collectAsState()
    val emergenceThresholdDb by viewModel.emergenceThresholdDb.collectAsState()
    val magnitudeGateDbFS by viewModel.magnitudeGateDbFS.collectAsState()
    val latestTTNRSpectrum by viewModel.latestTTNRSpectrum.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val fftSize by viewModel.fftSize.collectAsState()
    val minFreq by viewModel.minFreq.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val timeWindowSec by viewModel.timeWindowSec.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()

    val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()
    val trackedHarmonicTags by viewModel.trackedHarmonicTags.collectAsState()
    val emergenceReportEntries by viewModel.emergenceReportEntries.collectAsState()
    
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showKinematicsDialog by remember { mutableStateOf(false) }
    var showEmergenceReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NVH Spectro", fontWeight = FontWeight.Bold) },
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.logo_vibratec),
                        contentDescription = "Logo Vibratec",
                        modifier = Modifier
                            .height(28.dp)
                            .padding(end = 6.dp),
                        contentScale = ContentScale.Fit
                    )
                    TextButton(onClick = { showInfoDialog = true }) {
                        Text("Informations", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = { showSettingsDialog = true }) {
                        Text("Réglages", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Bouton Analyse GMPe
                    Button(
                        onClick = { showKinematicsDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (kinematicsConfig.isEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (kinematicsConfig.isEnabled) "⚙️ GMPe (Actif)" else "⚙️ GMPe",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // 2. Bouton Rapport d'émergence
                    Button(
                        onClick = { showEmergenceReportDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📊 Rapport (${emergenceReportEntries.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // 3. Bouton Figer / Dégeler
                    Button(
                        onClick = { viewModel.toggleFreeze() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFrozen) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = if (isFrozen) "▶ Dégeler" else "⏸ Figer",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // 4. Bouton Exporter (GARANTI VISIBLE !)
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0275D8)
                        )
                    ) {
                        Text(
                            text = "📸 Exporter",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Zone 1: Spectrogramme (55% hauteur)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                SpectrogramCanvas(
                    history = fftHistory,
                    absHistory = fftHistoryAbsolute,
                    ttnrHistory = fftHistoryTTNR,
                    minDb = minDb,
                    maxDb = maxDb,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    fftSize = fftSize,
                    sampleRate = 44100,
                    historySize = viewModel.historySize,
                    displayMode = displayMode,
                    isDetectorEnabled = isDetectorEnabled,
                    emergenceThresholdDb = emergenceThresholdDb,
                    magnitudeGateDbFS = magnitudeGateDbFS,
                    trackedHarmonicTags = trackedHarmonicTags,
                    kinematicsConfig = kinematicsConfig
                )

                // Superposition d'éléments en haut à gauche (Sélecteur de Mode + Bannière Cinématique GMPe en dessous)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Sélecteur de Mode (Absolue vs TTNR)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DisplayMode.values().forEach { mode ->
                            FilterChip(
                                selected = (displayMode == mode),
                                onClick = { viewModel.setDisplayMode(mode) },
                                label = { Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    // Bannière Cinématique GMPe (Positionnée sous les chips pour éviter tout chevauchement)
                    if (kinematicsConfig.isEnabled) {
                        val effV1000 = kinematicsConfig.getEffectiveV1000()
                        val curSpeed = telemetry.speedKmh
                        val isActiveSpeed = curSpeed > 1.0f

                        Surface(
                            color = Color(0xCC121212),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // LED d'état agrandie (12dp) : ROUGE si Vitesse <= 1 km/h (Inactif), VERTE si > 1 km/h (Actif)
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (isActiveSpeed) Color(0xFF00E676) else Color(0xFFFF1744),
                                            CircleShape
                                        )
                                )
                                val titleText = if (kinematicsConfig.vehicleName.isNotEmpty()) kinematicsConfig.vehicleName else "GMPe"
                                
                                if (isActiveSpeed) {
                                    val h1Hz = kinematicsConfig.calculateH1FreqHz(curSpeed)
                                    val curRpm = kinematicsConfig.calculateRpm(curSpeed).toInt()
                                    Text(
                                        text = "🚘 $titleText | V1000: %.1f km/h | H1: %.1fHz (%d RPM)".format(effV1000, h1Hz, curRpm),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "🚘 $titleText | Inactif (< 1 km/h) | V1000: %.1f km/h".format(effV1000),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFCDD2)
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (fftHistory.isEmpty()) {
                    Text("Analyse audio & sonogramme en cours...", color = Color.White)
                }
            }

            // Zone 2: Données Véhicule (45% hauteur)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .padding(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // En-tête : Titre + LED Signal GPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DONNÉES GPS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // LED GPS
                        GpsLedIndicator(status = telemetry.gpsStatus)
                    }

                    // Encart des valeurs instantanées (Vitesse & Accélération)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        KpiItem("Vitesse", String.format("%.1f km/h", telemetry.speedKmh))
                        KpiItem("Accélération", String.format("%.2f g", telemetry.accelerationG))
                        KpiItem("Altitude", String.format("%.0f m", telemetry.altitude))
                    }

                    Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                    // Onglets Sélecteurs de métrique pour le graphique 2D
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryMetric.values().forEach { metric ->
                            FilterChip(
                                selected = (selectedMetric == metric),
                                onClick = { viewModel.selectMetric(metric) },
                                label = { Text(metric.label) }
                            )
                        }
                    }

                    // Zone Graphique 2D synchronisé 1-to-1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black.copy(alpha = 0.7f))
                    ) {
                        TelemetryGraph(
                            history = telemetryHistory,
                            metric = selectedMetric,
                            timeWindowSec = timeWindowSec,
                            historySize = viewModel.historySize,
                            ttnrSpectrum = latestTTNRSpectrum,
                            minFreq = minFreq,
                            maxFreq = maxFreq
                        )
                    }
                }
            }
        }
        
        if (showInfoDialog) {
            InfoDialog(
                onDismiss = { showInfoDialog = false }
            )
        }

        if (showSettingsDialog) {
            com.example.nvhspectro.ui.SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                minDb = minDb,
                maxDb = maxDb,
                onMinDbChange = { viewModel.updateSettings(it, maxDb, fftSize, minFreq, maxFreq, timeWindowSec) },
                onMaxDbChange = { viewModel.updateSettings(minDb, it, fftSize, minFreq, maxFreq, timeWindowSec) },
                fftSize = fftSize,
                onFftSizeChange = { viewModel.updateSettings(minDb, maxDb, it, minFreq, maxFreq, timeWindowSec) },
                minFreq = minFreq,
                onMinFreqChange = { viewModel.updateSettings(minDb, maxDb, fftSize, it, maxFreq, timeWindowSec) },
                maxFreq = maxFreq,
                onMaxFreqChange = { viewModel.updateSettings(minDb, maxDb, fftSize, minFreq, it, timeWindowSec) },
                timeWindowSec = timeWindowSec,
                onTimeWindowChange = { viewModel.updateSettings(minDb, maxDb, fftSize, minFreq, maxFreq, it) },
                isDetectorEnabled = isDetectorEnabled,
                onDetectorEnabledChange = { enabled ->
                    viewModel.updateDetectorSettings(enabled, emergenceThresholdDb, magnitudeGateDbFS)
                },
                emergenceThresholdDb = emergenceThresholdDb,
                onEmergenceThresholdChange = { threshold ->
                    viewModel.updateDetectorSettings(isDetectorEnabled, threshold, magnitudeGateDbFS)
                },
                magnitudeGateDbFS = magnitudeGateDbFS,
                onMagnitudeGateChange = { gate ->
                    viewModel.updateDetectorSettings(isDetectorEnabled, emergenceThresholdDb, gate)
                }
            )
        }
        
        if (showExportDialog) {
            com.example.nvhspectro.ui.ExportDialog(
                onDismiss = { showExportDialog = false },
                telemetry = telemetry,
                onExport = { pedalPercent, comments ->
                    showExportDialog = false
                    viewModel.exportData(pedalPercent, comments)
                }
            )
        }

        if (showKinematicsDialog) {
            com.example.nvhspectro.ui.KinematicsDialog(
                currentConfig = kinematicsConfig,
                onDismiss = { showKinematicsDialog = false },
                onSave = { newConfig ->
                    showKinematicsDialog = false
                    viewModel.updateKinematicsConfig(newConfig)
                }
            )
        }

        if (showEmergenceReportDialog) {
            com.example.nvhspectro.ui.EmergenceReportDialog(
                entries = emergenceReportEntries,
                kinematicsConfig = kinematicsConfig,
                onDismiss = { showEmergenceReportDialog = false },
                onClearReport = { viewModel.clearEmergenceReport() }
            )
        }
    }
}

@Composable
fun GpsLedIndicator(status: GpsStatus) {
    val (ledColor, textLabel) = when (status) {
        GpsStatus.GOOD -> Color(0xFF00E676) to "Signal OK"
        GpsStatus.POOR -> Color(0xFFFF9100) to "Signal Médiocre"
        GpsStatus.NONE -> Color(0xFFFF5252) to "Signal Perdu"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Signal GPS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = ledColor, shape = CircleShape)
        )
        Text(text = textLabel, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun KpiItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
