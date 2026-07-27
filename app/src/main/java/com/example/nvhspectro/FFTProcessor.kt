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

        val df = 44100.0 / fftSize
        for (i in 0 until fftSize / 2) {
            val f = i * df
            if (f < 30.0) {
                magnitudes[i] = -120.0
                continue
            }
            val re = fftData[i * 2]
            val im = fftData[i * 2 + 1]
            val mag = sqrt(re * re + im * im)
            
            val magNormalized = mag / normFactor
            magnitudes[i] = if (magNormalized > 1e-6) 20 * log10(magNormalized) else -120.0
        }

        return magnitudes
    }

    /**
     * Calcule le spectre d'émergence TTNR (Tone-to-Noise Ratio) selon ECMA-74 / ISO 1996-2 (Optimisé NVH Véhicule v7.0.0)
     * Avec Porte de Bruit Psychoacoustique (-85 dBFS), Bande de Masquage Locale Adaptative et Lissage Dynamique.
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
            // Filtre Passe-Haut NVH 30 Hz (rien sous 30 Hz) + Porte d'amplitude -85 dBFS
            if (f < 30.0 || magnitudesDbFS[i] < -85.0) {
                rawTtnr[i] = 0.0
                continue
            }

            // 1. Largeur de bande critique (Formule de Terhardt) & Masquage Local Adaptatif NVH (max 400 Hz)
            val fKhz = f / 1000.0
            val criticalBandwidth = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * fKhz * fKhz, 0.69)
            
            // Pour l'estimation de la densité de bruit locale en environnement automobile non-plat,
            // on borne la fenêtre de calcul du bruit à max 400 Hz pour éviter d'intégrer le bruit broadband HF.
            val localMaskingBandwidth = minOf(criticalBandwidth, 400.0)
            val halfCbBins = (localMaskingBandwidth / (2.0 * df)).toInt().coerceAtLeast(4)

            val minBin = (i - halfCbBins).coerceAtLeast(0)
            val maxBin = (i + halfCbBins).coerceAtMost(binCount - 1)

            // 2. Puissance du ton (Somme du pic i et de ses 4 raies adjacentes de leakage/fenêtrage Hann +-2 bins)
            var pTone = powerLinear[i]
            if (i > 0) pTone += powerLinear[i - 1]
            if (i > 1) pTone += powerLinear[i - 2]
            if (i < binCount - 1) pTone += powerLinear[i + 1]
            if (i < binCount - 2) pTone += powerLinear[i + 2]

            // 3. Puissance du bruit de masque ambiant (excluant le pic et ses voisins immédiats +-3 bins)
            var pNoiseSum = 0.0
            var noiseCount = 0

            for (j in minBin..maxBin) {
                if (Math.abs(j - i) > 3) {
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

        // 4. Lissage Spectral Doux (90% pic actuel) pour préserver la hauteur exacte des raies fines sans l'atténuer
        val smoothedTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val prev = if (i > 0) rawTtnr[i - 1] else rawTtnr[i]
            val curr = rawTtnr[i]
            val next = if (i < binCount - 1) rawTtnr[i + 1] else rawTtnr[i]
            smoothedTtnr[i] = 0.05 * prev + 0.90 * curr + 0.05 * next
        }

        return smoothedTtnr
    }
}
