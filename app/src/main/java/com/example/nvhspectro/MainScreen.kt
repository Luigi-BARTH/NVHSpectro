package com.example.nvhspectro

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val isRecording by viewModel.isRecording.collectAsState()
    
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val fftSize by viewModel.fftSize.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val timeWindowSec by viewModel.timeWindowSec.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

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
                            .padding(end = 8.dp),
                        contentScale = ContentScale.Fit
                    )
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
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { viewModel.toggleRecording() }) {
                        Text(if (isRecording) "Stop" else "Lecture")
                    }
                    Button(onClick = { viewModel.toggleFreeze() }) {
                        Text(if (isFrozen) "Dégeler" else "Figer")
                    }
                    Button(onClick = { showExportDialog = true }) {
                        Text("Exporter")
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
                    minDb = minDb,
                    maxDb = maxDb,
                    maxFreq = maxFreq,
                    fftSize = fftSize,
                    sampleRate = 44100,
                    historySize = viewModel.historySize,
                    displayMode = displayMode
                )
                
                // Sélecteur de Mode (Absolue vs TTNR) en haut à gauche du Spectrogramme
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 8.dp),
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
                
                if (fftHistory.isEmpty()) {
                    Text(if (isRecording) "Analyse audio en cours..." else "Appuyez sur Lecture", color = Color.White)
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
                            historySize = viewModel.historySize
                        )
                    }
                }
            }
        }
        
        if (showSettingsDialog) {
            com.example.nvhspectro.ui.SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                minDb = minDb,
                maxDb = maxDb,
                onMinDbChange = { viewModel.updateSettings(it, maxDb, fftSize, maxFreq, timeWindowSec) },
                onMaxDbChange = { viewModel.updateSettings(minDb, it, fftSize, maxFreq, timeWindowSec) },
                fftSize = fftSize,
                onFftSizeChange = { viewModel.updateSettings(minDb, maxDb, it, maxFreq, timeWindowSec) },
                maxFreq = maxFreq,
                onMaxFreqChange = { viewModel.updateSettings(minDb, maxDb, fftSize, it, timeWindowSec) },
                timeWindowSec = timeWindowSec,
                onTimeWindowChange = { viewModel.updateSettings(minDb, maxDb, fftSize, maxFreq, it) }
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
