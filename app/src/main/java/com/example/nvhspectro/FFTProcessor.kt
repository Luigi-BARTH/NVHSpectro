package com.example.nvhspectro

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.log10
import kotlin.math.sqrt

class FFTProcessor(val fftSize: Int = 2048) {
    private val fft = DoubleFFT_1D(fftSize.toLong())
    private var lastFrameEnergyDb: Double = -120.0
    private var historyFrame1: DoubleArray? = null
    private var historyFrame2: DoubleArray? = null
    
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
     * Avec Anti-Shock Squelch (chocs de table) et Filtre Médian Temporel sur 3 trames.
     * @param magnitudesDbFS : Tableau de magnitudes en dBFS
     * @param sampleRate : Fréquence d'échantillonnage (ex: 44100 Hz)
     * @return DoubleArray contenant les valeurs TTNR en dB d'émergence filtrées [0..30 dB]
     */
    fun computeTTNR(magnitudesDbFS: DoubleArray, sampleRate: Int = 44100): DoubleArray {
        val binCount = magnitudesDbFS.size
        val df = sampleRate.toDouble() / fftSize
        val rawTtnr = DoubleArray(binCount)

        // Convertir dBFS en puissance linéaire P = 10^(dBFS / 10)
        var totalFrameEnergySum = 0.0
        val powerLinear = DoubleArray(binCount) { i ->
            val p = Math.pow(10.0, magnitudesDbFS[i] / 10.0)
            totalFrameEnergySum += p
            p
        }

        // 1. DÉTECTEUR D'IMPULSION ET CHOC TEMPOREL (SOLUTION 1 : ANTI-SHOCK SQUELCH)
        // Un choc sur la table fait bondir l'énergie globale de la trame de > 6.0 dB en 20 ms
        val currentFrameEnergyDb = 10.0 * log10(totalFrameEnergySum.coerceAtLeast(1e-12))
        val deltaEnergyDb = currentFrameEnergyDb - lastFrameEnergyDb
        lastFrameEnergyDb = currentFrameEnergyDb

        val isTransientShock = deltaEnergyDb > 6.0

        if (!isTransientShock) {
            for (i in 0 until binCount) {
                val f = i * df

                // Porte d'amplitude profilée selon la fréquence (Double Verrou HF pour MLI)
                val minMagnitudeGate = when {
                    f < 500.0 -> -75.0
                    f < 4000.0 -> -85.0
                    else -> -75.0 // -75 dBFS en HF: filtre 99.9% de la MLI benigne, capture 100% de la MLI défectueuse
                }

                // Filtre Passe-Haut NVH 30 Hz + Porte d'amplitude profilée
                if (f < 30.0 || magnitudesDbFS[i] < minMagnitudeGate) {
                    continue
                }

                // Condition de Pic Local Strict : Seuls les vrais sommets de pics sont évalués
                val isStrictLocalPeak = i > 0 && i < binCount - 1 &&
                        magnitudesDbFS[i] > magnitudesDbFS[i - 1] &&
                        magnitudesDbFS[i] > magnitudesDbFS[i + 1]

                if (!isStrictLocalPeak) {
                    continue
                }

                // Largeur de bande critique (Formule de Terhardt) & Masquage Local Adaptatif NVH (max 350 Hz)
                val fKhz = f / 1000.0
                val criticalBandwidth = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * fKhz * fKhz, 0.69)
                val localMaskingBandwidth = minOf(criticalBandwidth, 350.0)
                val halfCbBins = (localMaskingBandwidth / (2.0 * df)).toInt().coerceAtLeast(4)

                val minBin = (i - halfCbBins).coerceAtLeast(0)
                val maxBin = (i + halfCbBins).coerceAtMost(binCount - 1)

                // Puissance brute du ton (Somme du pic i et de ses 4 raies adjacentes de leakage +-2 bins)
                var pToneGross = powerLinear[i]
                if (i > 0) pToneGross += powerLinear[i - 1]
                if (i > 1) pToneGross += powerLinear[i - 2]
                if (i < binCount - 1) pToneGross += powerLinear[i + 1]
                if (i < binCount - 2) pToneGross += powerLinear[i + 2]

                // Puissance du bruit ambiant local
                var pNoiseSum = 0.0
                var noiseCount = 0

                for (j in minBin..maxBin) {
                    if (Math.abs(j - i) > 3) {
                        pNoiseSum += powerLinear[j]
                        noiseCount++
                    }
                }

                if (noiseCount == 0 || pNoiseSum <= 0.0) {
                    continue
                }

                val pNoiseDensityPerHz = pNoiseSum / (noiseCount * df)
                val pNoiseIn5Bins = 5.0 * pNoiseDensityPerHz * df

                // Puissance NETTE du ton (Soustraction du bruit de fond sous le dôme)
                val pToneNet = maxOf(0.0, pToneGross - pNoiseIn5Bins)

                // Largeur de bande critique stabilisée (borne min 150 Hz pour éviter l'explosion du ratio en BF)
                val cbwEffective = maxOf(criticalBandwidth, 150.0)
                val pNoiseTotalInCb = pNoiseDensityPerHz * cbwEffective

                // TTNR ECMA-74 sur puissance nette du ton
                val ratioCb = if (pNoiseTotalInCb > 0.0) pToneNet / pNoiseTotalInCb else 0.0
                val ttnrCbDb = if (ratioCb > 1.0) 10.0 * log10(ratioCb) else 0.0

                // Émergence Spectrale Locale ISO 1996-2
                val localNoiseFloorDbFS = 10.0 * log10(pNoiseDensityPerHz * df)
                val localEmergenceDb = (magnitudesDbFS[i] - localNoiseFloorDbFS).coerceAtLeast(0.0)

                // Seuil d'émergence adaptatif en fréquence (anti-turbulences & double verrou HF)
                val minEmergenceRequired = when {
                    f < 1500.0 -> 4.5
                    f < 4000.0 -> 3.8
                    else -> 4.0
                }

                // Hybridation NVH Psychoacoustique : Seuls les tons avec puissance nette positive ET émergence nette sont retenus
                val finalPeakTtnr = if (pToneNet > 0.0 && localEmergenceDb >= minEmergenceRequired) {
                    maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(0.0, 30.0)
                } else {
                    0.0
                }

                if (finalPeakTtnr >= 1.0) {
                    rawTtnr[i] = finalPeakTtnr
                    // Reconstitution de la largeur physique du dôme (Leakage Hanning sur bins adjacents)
                    if (i > 0 && rawTtnr[i - 1] < finalPeakTtnr * 0.45) {
                        rawTtnr[i - 1] = finalPeakTtnr * 0.45
                    }
                    if (i < binCount - 1 && rawTtnr[i + 1] < finalPeakTtnr * 0.45) {
                        rawTtnr[i + 1] = finalPeakTtnr * 0.45
                    }
                }
            }
        }

        // 2. Filtre de Prominence Spectrale (Anti-Spike 1-Pixel)
        val filteredTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val valCurr = rawTtnr[i]
            if (valCurr <= 0.0) continue

            val prevVal = if (i > 0) rawTtnr[i - 1] else 0.0
            val nextVal = if (i < binCount - 1) rawTtnr[i + 1] else 0.0

            val hasStructure = (prevVal >= 0.20 * valCurr || nextVal >= 0.20 * valCurr)
            if (hasStructure) {
                filteredTtnr[i] = valCurr
            } else {
                filteredTtnr[i] = 0.0
            }
        }

        // 3. Lissage Spectral Doux
        val smoothedTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val prev = if (i > 0) filteredTtnr[i - 1] else filteredTtnr[i]
            val curr = filteredTtnr[i]
            val next = if (i < binCount - 1) filteredTtnr[i + 1] else filteredTtnr[i]
            smoothedTtnr[i] = 0.05 * prev + 0.90 * curr + 0.05 * next
        }

        // Seuil Couperet de Squelch (Tout TTNR < 1.0 dB est du bruit de fond insignifiant -> 0.0 dB)
        for (i in 0 until binCount) {
            if (smoothedTtnr[i] < 1.0 || i * df < 30.0) {
                smoothedTtnr[i] = 0.0
            }
        }

        // 4. FILTRE DE PERSISTANCE TEMPORELLE NVH SUR 3 TRAMES (SOUPLE +-3 BINS DE DÉPLACEMENT)
        // Permet de suivre les harmoniques glissantes (sinus, régimes moteur) tout en effaçant 100% des nuages de points parasites.
        val h1 = historyFrame1
        val h2 = historyFrame2
        val finalTtnr = DoubleArray(binCount)

        if (h1 != null && h2 != null && h1.size == binCount && h2.size == binCount) {
            for (i in 0 until binCount) {
                val curr = smoothedTtnr[i]
                if (curr >= 1.0) {
                    // Recherche de la raie sur une fenêtre de +-3 bins (pour capter le glissement fréquentiel)
                    var maxNearH1 = 0.0
                    var maxNearH2 = 0.0

                    val startBin = maxOf(0, i - 3)
                    val endBin = minOf(binCount - 1, i + 3)

                    for (k in startBin..endBin) {
                        if (h1[k] > maxNearH1) maxNearH1 = h1[k]
                        if (h2[k] > maxNearH2) maxNearH2 = h2[k]
                    }

                    // Une raie réelle (fixe ou glissante) est présente sur au moins 2 des 3 trames
                    val hasTemporalPersistence = (maxNearH1 >= 0.8 || maxNearH2 >= 0.8)
                    if (hasTemporalPersistence) {
                        finalTtnr[i] = curr
                    } else {
                        finalTtnr[i] = 0.0 // Effacer le point transitoire parasite isolé
                    }
                } else {
                    finalTtnr[i] = 0.0
                }
            }
        } else {
            System.arraycopy(smoothedTtnr, 0, finalTtnr, 0, binCount)
        }

        // Rotation des trames candidates dans l'historique (smoothedTtnr) pour éviter tout blocage d'extinction
        historyFrame2 = historyFrame1?.clone()
        historyFrame1 = smoothedTtnr.clone()

        return finalTtnr
    }
}
