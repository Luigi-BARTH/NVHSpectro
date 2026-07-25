package com.example.nvhspectro

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    val fftHistory by viewModel.fftHistory.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val fftSize by viewModel.fftSize.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val timeWindowSec by viewModel.timeWindowSec.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NVH Spectro") },
                actions = {
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
            // Zone 1: Spectrogramme
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
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
                    historySize = viewModel.historySize
                )
                
                if (fftHistory.isEmpty()) {
                    Text(if (isRecording) "Analyse audio en cours..." else "Appuyez sur Lecture", color = Color.White)
                }
            }

            // Zone 2: Dashboard Capteurs / GPS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Données Véhicule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DashboardItem("Vitesse", String.format("%.1f km/h", telemetry.speedKmh))
                        DashboardItem("Altitude", String.format("%.0f m", telemetry.altitude))
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DashboardItem("Accélération", String.format("%.2f g", telemetry.accelerationG))
                        DashboardItem("Position", if (telemetry.speedKmh >= 0) "Signal OK" else "GPS en attente")
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
                    // Appel de la sauvegarde
                    showExportDialog = false
                    viewModel.exportData(pedalPercent, comments)
                }
            )
        }
    }
}

@Composable
fun DashboardItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
