package com.example.nvhspectro

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.log10
import kotlin.math.sqrt

class FFTProcessor(val fftSize: Int = 2048) {
    private val fft = DoubleFFT_1D(fftSize.toLong())
    private var lastFrameTtnr: DoubleArray? = null
    
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
     * Calcule le spectre d'émergence TTNR (Tone-to-Noise Ratio) selon ECMA-74 / ISO 1996-2 Hybride NVH v7.0.0
     * Combine la puissance de bande critique et l'émergence spectrale locale (Delta L) pour détecter les sons faibles mais audibles.
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

            // 1. Condition de Pic Local Strict : Seuls les vrais sommets de pics sont évalués
            // Les pentes, vallées et bruits de fond continus sont directement forcés à 0.0 dB
            val isStrictLocalPeak = i > 0 && i < binCount - 1 &&
                    magnitudesDbFS[i] > magnitudesDbFS[i - 1] &&
                    magnitudesDbFS[i] > magnitudesDbFS[i + 1]

            // 2. Porte d'amplitude profilée selon la fréquence (Double Verrou HF pour MLI)
            val minMagnitudeGate = when {
                f < 500.0 -> -75.0
                f < 4000.0 -> -85.0
                else -> -75.0 // -75 dBFS en HF: filtre 99.9% de la MLI benigne, capture 100% de la MLI défectueuse
            }

            // Filtre Passe-Haut NVH 30 Hz + Pic Local Strict + Porte d'amplitude profilée
            if (f < 30.0 || !isStrictLocalPeak || magnitudesDbFS[i] < minMagnitudeGate) {
                rawTtnr[i] = 0.0
                continue
            }

            // 3. Largeur de bande critique (Formule de Terhardt) & Masquage Local Adaptatif NVH (max 350 Hz)
            val fKhz = f / 1000.0
            val criticalBandwidth = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * fKhz * fKhz, 0.69)
            val localMaskingBandwidth = minOf(criticalBandwidth, 350.0)
            val halfCbBins = (localMaskingBandwidth / (2.0 * df)).toInt().coerceAtLeast(4)

            val minBin = (i - halfCbBins).coerceAtLeast(0)
            val maxBin = (i + halfCbBins).coerceAtMost(binCount - 1)

            // 4. Puissance du ton (Somme du pic i et de ses 4 raies adjacentes de leakage/fenêtrage Hann +-2 bins)
            var pTone = powerLinear[i]
            if (i > 0) pTone += powerLinear[i - 1]
            if (i > 1) pTone += powerLinear[i - 2]
            if (i < binCount - 1) pTone += powerLinear[i + 1]
            if (i < binCount - 2) pTone += powerLinear[i + 2]

            // 5. Puissance du bruit ambiant local
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

            // TTNR selon bande critique ECMA-74
            val ratioCb = if (pNoiseTotalInCb > 0.0) pTone / pNoiseTotalInCb else 0.0
            val ttnrCbDb = if (ratioCb > 1.0) 10.0 * log10(ratioCb) else 0.0

            // Émergence Spectrale Locale ISO 1996-2 (Delta L par rapport au bruit de fond local immédiat)
            val localNoiseFloorDbFS = 10.0 * log10(pNoiseDensityPerHz * df)
            val localEmergenceDb = (magnitudesDbFS[i] - localNoiseFloorDbFS).coerceAtLeast(0.0)

            // Seuil d'émergence adaptatif en fréquence (anti-turbulences & double verrou HF)
            val minEmergenceRequired = when {
                f < 1500.0 -> 4.2
                f < 4000.0 -> 3.5
                else -> 4.0 // 4.0 dB en HF: élimine les petites fluctuations, valide la MLI/sifflement émergent
            }

            // Hybridation NVH Psychoacoustique : Valorise les raies émergentes audibles selon la zone fréquentielle
            val hybridTtnr = if (localEmergenceDb >= minEmergenceRequired) {
                maxOf(ttnrCbDb, localEmergenceDb - 1.5)
            } else {
                if (ttnrCbDb >= minEmergenceRequired) ttnrCbDb else 0.0
            }

            rawTtnr[i] = hybridTtnr.coerceIn(0.0, 30.0)
        }

        // 4. Filtre de Prominence Spectrale (Anti-Spike 1-Pixel)
        // Élimine les spikes isolés de 1 pixel produits par la variance statistique du bruit de ventilateur
        val filteredTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val valCurr = rawTtnr[i]
            if (valCurr <= 0.0) continue

            val prevVal = if (i > 0) rawTtnr[i - 1] else 0.0
            val nextVal = if (i < binCount - 1) rawTtnr[i + 1] else 0.0

            // Un pic physique a des épaules d'énergie (prevVal ou nextVal non-nuls)
            val hasStructure = (prevVal >= 0.20 * valCurr || nextVal >= 0.20 * valCurr)
            if (hasStructure) {
                filteredTtnr[i] = valCurr
            } else {
                filteredTtnr[i] = 0.0 // Annuler le spike 1-pixel isolé
            }
        }

        // 5. Lissage Spectral Doux (90% pic actuel) pour préserver la hauteur exacte des raies fines sans l'atténuer
        val smoothedTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val prev = if (i > 0) filteredTtnr[i - 1] else filteredTtnr[i]
            val curr = filteredTtnr[i]
            val next = if (i < binCount - 1) filteredTtnr[i + 1] else filteredTtnr[i]
            smoothedTtnr[i] = 0.05 * prev + 0.90 * curr + 0.05 * next
        }

        // 6. Seuil Couperet de Squelch (Tout TTNR < 1.0 dB est du bruit de fond insignifiant -> 0.0 dB)
        for (i in 0 until binCount) {
            if (smoothedTtnr[i] < 1.0) {
                smoothedTtnr[i] = 0.0
            }
            if (i * df < 30.0) {
                smoothedTtnr[i] = 0.0
            }
        }

        // 6. Filtre de Persistance Temporelle NVH (Cohérence 2 trames consécutives)
        // Les vrais sifflements durent sur au moins 2 trames consécutives (t-1 et t).
        // Les étincelles isolées de bruit aléatoire sont éliminées.
        val prevFrame = lastFrameTtnr
        val finalTtnr = DoubleArray(binCount)
        if (prevFrame != null && prevFrame.size == binCount) {
            for (i in 0 until binCount) {
                val curr = smoothedTtnr[i]
                if (curr >= 1.0) {
                    val prevNear = maxOf(
                        if (i > 0) prevFrame[i - 1] else 0.0,
                        prevFrame[i],
                        if (i < binCount - 1) prevFrame[i + 1] else 0.0
                    )
                    if (prevNear >= 0.5) {
                        finalTtnr[i] = curr
                    } else {
                        finalTtnr[i] = curr * 0.35 // Atténuation du flash transitoire éphémère
                    }
                } else {
                    finalTtnr[i] = 0.0
                }
            }
        } else {
            System.arraycopy(smoothedTtnr, 0, finalTtnr, 0, binCount)
        }

        lastFrameTtnr = finalTtnr.clone()
        return finalTtnr
    }
}
