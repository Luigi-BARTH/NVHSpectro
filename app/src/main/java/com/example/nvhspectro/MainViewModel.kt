package com.example.nvhspectro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioRepository = AudioRepository()
    private val telemetryRepository = TelemetryRepository(application)
    private var fftProcessor = FFTProcessor(2048)
    
    // Etats de l'UI
    private val _telemetryState = MutableStateFlow(TelemetryData())
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()
    
    private val _fftHistory = MutableStateFlow<List<DoubleArray>>(emptyList())
    val fftHistory: StateFlow<List<DoubleArray>> = _fftHistory.asStateFlow()
    
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
            if (history.isEmpty()) return@launch
            
            val bitmapWidth = history.size
            val binCount = history.first().size
            val maxF = _maxFreq.value
            val nyquistFreq = 44100 / 2
            val displayedBinCount = kotlin.math.min(binCount, (maxF * binCount) / nyquistFreq)
            val bitmapHeight = displayedBinCount
            
            // 1. Générer le bitmap de base du spectrogramme
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(bitmapWidth * bitmapHeight) { android.graphics.Color.BLACK }
            val minDbVal = _minDb.value
            val maxDbVal = _maxDb.value
            
            for (x in 0 until bitmapWidth) {
                val frameData = history[x]
                for (y in 0 until bitmapHeight) {
                    val b = bitmapHeight - 1 - y
                    val magnitude = if (b < frameData.size) frameData[b] else minDbVal
                    val normalized = ((magnitude - minDbVal) / (maxDbVal - minDbVal)).toFloat()
                    pixels[y * bitmapWidth + (bitmapWidth - 1 - x)] = com.example.nvhspectro.getJetColorInt(normalized)
                }
            }
            bitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
            
            // 2. Dessiner le spectrogramme redimensionné et ajouter les annotations
            val outWidth = 1200
            val outHeight = 800
            val outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBitmap)
            canvas.drawColor(android.graphics.Color.DKGRAY)
            
            val paint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                isAntiAlias = true
            }
            
            // Dessiner l'image redimensionnée (laissant 200px en bas pour les commentaires)
            val dstRect = android.graphics.Rect(50, 50, outWidth - 50, outHeight - 200)
            canvas.drawBitmap(bitmap, null, dstRect, paint)
            
            // Ajouter les textes
            val telemetry = _telemetryState.value
            var textY = outHeight - 160f
            canvas.drawText("Vitesse: ${String.format("%.1f", telemetry.speedKmh)} km/h | Altitude: ${String.format("%.0f", telemetry.altitude)} m", 50f, textY, paint)
            textY += 40f
            canvas.drawText("Pédale: $pedalPercent % | Accélération: ${String.format("%.2f", telemetry.accelerationG)} g", 50f, textY, paint)
            textY += 40f
            canvas.drawText("Commentaire: $comments", 50f, textY, paint)
            
            // 3. Sauvegarder dans le MediaStore (Pictures)
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
