package com.example.nvhspectro

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioRepository = AudioRepository()
    private val telemetryRepository = TelemetryRepository(application)
    private var fftProcessor = FFTProcessor(2048)
    
    // Etats de l'UI
    private val _telemetryState = MutableStateFlow(TelemetryData())
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()
    
    private val _fftHistory = MutableStateFlow<List<DoubleArray>>(emptyList())
    val fftHistory: StateFlow<List<DoubleArray>> = _fftHistory.asStateFlow()
    
    private val _telemetryHistory = MutableStateFlow<List<TelemetryData>>(emptyList())
    val telemetryHistory: StateFlow<List<TelemetryData>> = _telemetryHistory.asStateFlow()

    private val _selectedMetric = MutableStateFlow(com.example.nvhspectro.ui.TelemetryMetric.SPEED)
    val selectedMetric: StateFlow<com.example.nvhspectro.ui.TelemetryMetric> = _selectedMetric.asStateFlow()

    fun selectMetric(metric: com.example.nvhspectro.ui.TelemetryMetric) {
        _selectedMetric.value = metric
    }
    
    // Paramètres réglables
    private val _minDb = MutableStateFlow(-120.0)
    val minDb: StateFlow<Double> = _minDb.asStateFlow()

    private val _maxDb = MutableStateFlow(0.0)
    val maxDb: StateFlow<Double> = _maxDb.asStateFlow()

    private val _fftSize = MutableStateFlow(2048)
    val fftSize: StateFlow<Int> = _fftSize.asStateFlow()

    private val _maxFreq = MutableStateFlow(10000)
    val maxFreq: StateFlow<Int> = _maxFreq.asStateFlow()

    private val _timeWindowSec = MutableStateFlow(5.0)
    val timeWindowSec: StateFlow<Double> = _timeWindowSec.asStateFlow()

    val historySize: Int
        get() {
            val dt = (_fftSize.value / 2.0) / 44100.0
            return (_timeWindowSec.value / dt).toInt().coerceAtLeast(10)
        }

    fun updateSettings(newMinDb: Double, newMaxDb: Double, newFftSize: Int, newMaxFreq: Int, newTimeWindow: Double) {
        _minDb.value = newMinDb
        _maxDb.value = newMaxDb
        _maxFreq.value = newMaxFreq
        _timeWindowSec.value = newTimeWindow
        if (_fftSize.value != newFftSize) {
            _fftSize.value = newFftSize
            fftProcessor = FFTProcessor(newFftSize)
            _fftHistory.value = emptyList() // Reset history on size change
            if (_isRecording.value) {
                stopRecording()
                startRecording()
            }
        }
    }
    
    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    fun toggleFreeze() {
        _isFrozen.value = !_isFrozen.value
    }
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        
        // Lancer la télémétrie GPS et Capteurs
        viewModelScope.launch {
            telemetryRepository.startTelemetry().collect { data ->
                _telemetryState.value = data
                if (!_isFrozen.value) {
                    val currentList = _telemetryHistory.value.toMutableList()
                    currentList.add(0, data)
                    if (currentList.size > historySize) {
                        currentList.removeLast()
                    }
                    _telemetryHistory.value = currentList
                }
            }
        }
        
        // Lancer la capture Audio et FFT
        viewModelScope.launch {
            audioRepository.startAudioCapture(_fftSize.value).collect { audioBuffer ->
                if (!_isFrozen.value) {
                    // Traitement FFT
                    val magnitudes = fftProcessor.processFFT(audioBuffer)
                    
                    // Mettre à jour l'historique
                    val currentList = _fftHistory.value.toMutableList()
                    currentList.add(0, magnitudes) // Ajouter en tête
                    if (currentList.size > historySize) {
                        currentList.removeLast()
                    }
                    _fftHistory.value = currentList
                }
            }
        }
    }

    private fun stopRecording() {
        _isRecording.value = false
        audioRepository.stopAudioCapture()
    }
    
    fun exportData(pedalPercent: String, comments: String) {
        viewModelScope.launch {
            val history = _fftHistory.value
            val telemHistory = _telemetryHistory.value
            if (history.isEmpty()) return@launch
            
            val bitmapWidth = history.size
            val binCount = history.first().size
            val maxF = _maxFreq.value
            val nyquistFreq = 44100 / 2
            val displayedBinCount = min(binCount, (maxF * binCount) / nyquistFreq)
            val bitmapHeight = displayedBinCount
            
            // 1. Générer le bitmap brut du spectrogramme
            val spectroBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(bitmapWidth * bitmapHeight) { android.graphics.Color.BLACK }
            val minDbVal = _minDb.value
            val maxDbVal = _maxDb.value
            
            for (x in 0 until bitmapWidth) {
                val frameData = history[x]
                for (y in 0 until bitmapHeight) {
                    val b = bitmapHeight - 1 - y
                    val magnitude = if (b < frameData.size) frameData[b] else minDbVal
                    val normalized = ((magnitude - minDbVal) / (maxDbVal - minDbVal)).toFloat()
                    pixels[y * bitmapWidth + (bitmapWidth - 1 - x)] = getJetColorInt(normalized)
                }
            }
            spectroBitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
            
            // 2. Préparation du canvas de rendu global d'exportation
            val outWidth = 1400
            val outHeight = 1850
            val outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBitmap)
            canvas.drawColor(android.graphics.Color.parseColor("#121212")) // Fond sombre pro
            
            val paintTitle = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 42f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val paintText = Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = 28f
                isAntiAlias = true
            }
            val paintAxis = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val paintLine = Paint().apply {
                color = android.graphics.Color.GRAY
                strokeWidth = 2f
                isAntiAlias = true
            }
            
            // --- EN-TÊTE ---
            var curY = 60f
            canvas.drawText("NVH SPECTRO - RAPPORT D'ANALYSE ROULAGE", 60f, curY, paintTitle)
            
            // Dessin du logo Vibratec officiel en haut à droite
            try {
                val logoBitmap = android.graphics.BitmapFactory.decodeResource(
                    getApplication<Application>().resources,
                    R.drawable.logo_vibratec
                )
                if (logoBitmap != null) {
                    val logoW = 280f
                    val logoH = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * logoW
                    val logoRect = android.graphics.RectF(outWidth - logoW - 60f, 30f, outWidth - 60f, 30f + logoH)
                    canvas.drawBitmap(logoBitmap, null, logoRect, null)
                }
            } catch (e: Exception) {
                // Ignore if logo decoding fails
            }

            curY += 45f
            
            val telemetry = _telemetryState.value
            val metadataStr = "Vitesse: ${String.format("%.1f", telemetry.speedKmh)} km/h | Pédale: ${if (pedalPercent.isBlank()) "-" else pedalPercent}% | Accél: ${String.format("%.2f", telemetry.accelerationG)}g | Altitude: ${String.format("%.0f", telemetry.altitude)}m"
            canvas.drawText(metadataStr, 60f, curY, paintText)
            curY += 40f
            
            if (comments.isNotBlank()) {
                canvas.drawText("Commentaires: $comments", 60f, curY, paintText)
                curY += 40f
            }
            
            curY += 20f
            
            // Zone réservée pour l'alignement strict de tous les axes X (Abscisses)
            val marginLeft = 200f
            val marginRight = 60f
            val plotWidth = outWidth - marginLeft - marginRight
            
            // --- 1. SPECTROGRAMME (Hauteur 500px) ---
            val spectroHeight = 500f
            val dstRect = android.graphics.RectF(marginLeft, curY, marginLeft + plotWidth, curY + spectroHeight)
            canvas.drawBitmap(spectroBitmap, null, dstRect, null)
            
            // Axes Y Spectrogramme
            val actualMaxFreq = (displayedBinCount * nyquistFreq) / binCount
            canvas.drawLine(marginLeft, curY, marginLeft, curY + spectroHeight, paintLine)
            canvas.drawText("${actualMaxFreq} Hz", 40f, curY + 30f, paintAxis)
            canvas.drawText("0 Hz", 40f, curY + spectroHeight, paintAxis)
            
            curY += spectroHeight + 60f
            
            // --- 2. LES 3 COURBES ÉPILÉES (Vitesse, Accélération, Altitude) ---
            val graphHeight = 220f
            val graphGap = 60f
            val timeWindow = _timeWindowSec.value
            
            // Fonction locale pour tracer un graphe 2D
            fun drawStackedGraph(
                title: String,
                unit: String,
                colorInt: Int,
                values: List<Double>
            ) {
                // Fond du graphe
                val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#1E1E1E") }
                canvas.drawRect(marginLeft, curY, marginLeft + plotWidth, curY + graphHeight, bgPaint)
                canvas.drawLine(marginLeft, curY, marginLeft, curY + graphHeight, paintLine)
                canvas.drawLine(marginLeft, curY + graphHeight, marginLeft + plotWidth, curY + graphHeight, paintLine)
                
                val minV = if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0
                val maxV = if (values.isNotEmpty()) values.maxOrNull() ?: 1.0 else 1.0
                val rangeV = if (maxV > minV) maxV - minV else 1.0
                
                // Labels Y
                canvas.drawText(String.format("%.1f %s", maxV, unit), 20f, curY + 30f, paintAxis)
                canvas.drawText(String.format("%.1f %s", minV, unit), 20f, curY + graphHeight, paintAxis)
                
                // Titre Graphe
                val titlePaint = Paint().apply {
                    color = colorInt
                    textSize = 26f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                canvas.drawText(title, marginLeft + 20f, curY + 35f, titlePaint)
                
                // Courbe
                if (values.size > 1) {
                    val path = Path()
                    val pCount = values.size
                    val linePaint = Paint().apply {
                        color = colorInt
                        strokeWidth = 3.5f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    
                    for (i in 0 until pCount) {
                        val fractionX = (pCount - 1 - i).toFloat() / max(1, pCount - 1)
                        val x = marginLeft + (1f - fractionX) * plotWidth
                        val normY = ((values[i] - minV) / rangeV).toFloat()
                        val y = (curY + graphHeight) - (normY * graphHeight)
                        
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, linePaint)
                }
                
                // Label Temps en bas du 3ème graphe
                curY += graphHeight + graphGap
            }
            
            // Graphe 1: Vitesse
            val speedValues = telemHistory.map { it.speedKmh.toDouble() }
            drawStackedGraph("Vitesse (km/h)", "km/h", android.graphics.Color.parseColor("#00E676"), speedValues)
            
            // Graphe 2: Accélération
            val accelValues = telemHistory.map { it.accelerationG.toDouble() }
            drawStackedGraph("Accélération (g)", "g", android.graphics.Color.parseColor("#FF9100"), accelValues)
            
            // Graphe 3: Altitude
            val altValues = telemHistory.map { it.altitude }
            drawStackedGraph("Altitude (m)", "m", android.graphics.Color.parseColor("#00B0FF"), altValues)
            
            // Graduations de l'Axe X (Temps en secondes) en bas du dernier graphe
            val xBottomY = curY - graphGap + 35f
            val xSteps = 5
            for (i in 0..xSteps) {
                val fraction = i.toFloat() / xSteps
                val x = marginLeft + fraction * plotWidth
                val tSec = -timeWindow * (1f - fraction)
                canvas.drawText(String.format("%.1fs", tSec), x - 25f, xBottomY, paintAxis)
            }
            canvas.drawText("Temps (s)", marginLeft + plotWidth / 2f - 40f, xBottomY + 35f, paintAxis)
            
            // 3. Sauvegarder dans le MediaStore (Pictures/NVHSpectro)
            val resolver = getApplication<Application>().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "NVHSpectro_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NVHSpectro")
            }
            
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outStream ->
                    outBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }
            }
        }
    }
}
