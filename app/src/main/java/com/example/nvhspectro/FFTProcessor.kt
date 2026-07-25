package com.example.nvhspectro

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.log10
import kotlin.math.sqrt

class FFTProcessor(val fftSize: Int = 2048) {
    private val fft = DoubleFFT_1D(fftSize.toLong())
    
    // Fenêtrage de Hanning pour réduire le "leakage"
    private val window = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1)))
    }

    /**
     * Calcule la FFT sur un bloc audio
     * @param audioData : bloc de taille >= fftSize
     * @return DoubleArray contenant les magnitudes (moitié du tableau car signal réel)
     */
    fun processFFT(audioData: ShortArray): DoubleArray {
        val size = minOf(audioData.size, fftSize)
        val fftData = DoubleArray(fftSize * 2)

        for (i in 0 until size) {
            // Normalisation 16-bit [-1.0, 1.0] et application de la fenêtre Hanning
            fftData[i * 2] = (audioData[i].toDouble() / 32768.0) * window[i]
            fftData[i * 2 + 1] = 0.0
        }

        // Calcul de la FFT
        fft.complexForward(fftData)

        // Calcul des magnitudes (échelle dBFS)
        val magnitudes = DoubleArray(fftSize / 2)
        // Gain cohérent de la fenêtre de Hanning = 0.5
        // Pour une sinusoïde d'amplitude 1.0, l'énergie se divise sur N/2.
        // La normalisation standard pour retrouver l'amplitude est : mag / (N/2) * (1/0.5) = mag / (N/4)
        val normFactor = fftSize / 4.0 

        for (i in 0 until fftSize / 2) {
            val re = fftData[i * 2]
            val im = fftData[i * 2 + 1]
            val mag = sqrt(re * re + im * im)
            
            val magNormalized = mag / normFactor
            // Plancher à 1e-6 pour éviter log10(0) = -Inf (-120 dBFS)
            magnitudes[i] = if (magNormalized > 1e-6) 20 * log10(magNormalized) else -120.0
        }

        return magnitudes
    }
}
