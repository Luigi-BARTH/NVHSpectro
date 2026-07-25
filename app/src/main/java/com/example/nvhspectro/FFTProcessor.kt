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
        val normFactor = fftSize / 4.0 

        for (i in 0 until fftSize / 2) {
            val re = fftData[i * 2]
            val im = fftData[i * 2 + 1]
            val mag = sqrt(re * re + im * im)
            
            val magNormalized = mag / normFactor
            magnitudes[i] = if (magNormalized > 1e-6) 20 * log10(magNormalized) else -120.0
        }

        return magnitudes
    }

    /**
     * Calcule le spectre d'émergence TTNR (Tone-to-Noise Ratio) selon ECMA-74 / ISO 1996-2
     * Avec Porte de Bruit Psychoacoustique (-70 dBFS) et Lissage Spectral Intelligent.
     * @param magnitudesDbFS : Tableau de magnitudes en dBFS
     * @param sampleRate : Fréquence d'échantillonnage (ex: 44100 Hz)
     * @return DoubleArray contenant les valeurs TTNR en dB d'émergence filtrées [0..30 dB]
     */
    fun computeTTNR(magnitudesDbFS: DoubleArray, sampleRate: Int = 44100): DoubleArray {
        val binCount = magnitudesDbFS.size
        val df = sampleRate.toDouble() / fftSize
        val rawTtnr = DoubleArray(binCount)

        // Convertir dBFS en puissance linéaire P = 10^(dBFS / 10)
        val powerLinear = DoubleArray(binCount) { i ->
            Math.pow(10.0, magnitudesDbFS[i] / 10.0)
        }

        for (i in 0 until binCount) {
            val f = i * df
            // Porte de bruit : en dessous de -70 dBFS, ignorer les fluctuations statistiques de bruit
            if (f < 50.0 || magnitudesDbFS[i] < -70.0) {
                rawTtnr[i] = 0.0
                continue
            }

            // 1. Largeur de bande critique (Bande de Bark selon Terhardt)
            val fKhz = f / 1000.0
            val criticalBandwidth = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * fKhz * fKhz, 0.69)
            val halfCbBins = (criticalBandwidth / (2.0 * df)).toInt().coerceAtLeast(3)

            val minBin = (i - halfCbBins).coerceAtLeast(0)
            val maxBin = (i + halfCbBins).coerceAtMost(binCount - 1)

            // 2. Puissance du ton (Somme du pic i et de ses raies adjacentes de leakage)
            var pTone = powerLinear[i]
            if (i > 0) pTone += powerLinear[i - 1]
            if (i < binCount - 1) pTone += powerLinear[i + 1]

            // 3. Puissance du bruit de masque ambiant (excluant le pic et ses voisins immédiats)
            var pNoiseSum = 0.0
            var noiseCount = 0

            for (j in minBin..maxBin) {
                if (Math.abs(j - i) > 2) {
                    pNoiseSum += powerLinear[j]
                    noiseCount++
                }
            }

            if (noiseCount == 0 || pNoiseSum <= 0.0) {
                rawTtnr[i] = 0.0
                continue
            }

            val pNoiseDensityPerHz = pNoiseSum / (noiseCount * df)
            val pNoiseTotalInCb = pNoiseDensityPerHz * criticalBandwidth

            if (pNoiseTotalInCb > 0.0) {
                val ratio = pTone / pNoiseTotalInCb
                val ttnrDb = if (ratio > 1.0) 10.0 * log10(ratio) else 0.0
                rawTtnr[i] = ttnrDb.coerceIn(0.0, 30.0)
            } else {
                rawTtnr[i] = 0.0
            }
        }

        // 4. Lissage Spectral Gaußien 3 raies pour éliminer les micro-pointes isolées d'une seule raie
        val smoothedTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val prev = if (i > 0) rawTtnr[i - 1] else rawTtnr[i]
            val curr = rawTtnr[i]
            val next = if (i < binCount - 1) rawTtnr[i + 1] else rawTtnr[i]
            smoothedTtnr[i] = 0.2 * prev + 0.6 * curr + 0.2 * next
        }

        return smoothedTtnr
    }
}
