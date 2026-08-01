package com.example.nvhspectro.data

enum class KinematicsInputMode(val label: String) {
    V1000("V1000 Direct"),
    GEAR_RATIO("Rapport Global"),
    DETAILED_CHAIN("Chaîne Détaillée")
}

data class KinematicsConfig(
    val isEnabled: Boolean = false,
    val inputMode: KinematicsInputMode = KinematicsInputMode.V1000,
    val v1000Kmh: Double = 10.0,            // Vitesse en km/h pour 1000 RPM
    val globalGearRatio: Double = 9.5,        // Rapport total de réduction
    val gearReductionRatio: Double = 3.2,     // Rapport réducteur / descente
    val axleRatio: Double = 3.0,              // Rapport de pont
    val tireWidthMm: Int = 205,               // Largeur du pneu en mm (ex: 205)
    val tireAspectRatio: Int = 55,            // Hauteur du flanc en % (ex: 55)
    val rimDiameterInches: Int = 16,          // Diamètre de jante en pouces (ex: 16)
    val wheelRadiusMeters: Double = 0.31,     // Rayon sous charge de secours si renseigné directement
    val vehicleName: String = "",             // Identification du véhicule
    val motorName: String = "",               // Identification du moteur / GMPe
    val comments: String = "",                // Notes d'essai
    val holdTimeSec: Double = 3.0,            // Durée de rémanence visuelle des étiquettes (secondes)
    val selectedTrackedOrder: Double = 18.0   // Ordre spécifique sélectionné pour traçage 2D (ex: H18)
) {
    /**
     * Calcule le rayon dynamique sous charge de la roue (en mètres) à partir des dimensions pneu vendeur.
     */
    fun calculateWheelRadiusMeters(): Double {
        if (tireWidthMm <= 0 || tireAspectRatio <= 0 || rimDiameterInches <= 0) {
            return wheelRadiusMeters.coerceAtLeast(0.1)
        }
        val rimDiameterMeters = rimDiameterInches * 0.0254
        val sidewallHeightMeters = (tireWidthMm * (tireAspectRatio / 100.0)) / 1000.0
        val totalWheelDiameterMeters = rimDiameterMeters + (2.0 * sidewallHeightMeters)
        val freeRadiusMeters = totalWheelDiameterMeters / 2.0
        // Rayon efficace sous charge avec affaissement moyen du flanc (~1.5%)
        return freeRadiusMeters * 0.985
    }

    /**
     * Calcule la V1000 équivalente en km/h pour 1000 RPM selon le mode de saisie sélectionné.
     */
    fun getEffectiveV1000(): Double {
        val effectiveRadius = calculateWheelRadiusMeters()
        return when (inputMode) {
            KinematicsInputMode.V1000 -> v1000Kmh.coerceAtLeast(0.1)
            KinematicsInputMode.GEAR_RATIO -> {
                val wheelRpm = 1000.0 / globalGearRatio.coerceAtLeast(0.01)
                val wheelSpeedMs = (wheelRpm * 2.0 * Math.PI * effectiveRadius) / 60.0
                wheelSpeedMs * 3.6
            }
            KinematicsInputMode.DETAILED_CHAIN -> {
                val totalRatio = (gearReductionRatio * axleRatio).coerceAtLeast(0.01)
                val wheelRpm = 1000.0 / totalRatio
                val wheelSpeedMs = (wheelRpm * 2.0 * Math.PI * effectiveRadius) / 60.0
                wheelSpeedMs * 3.6
            }
        }
    }

    /**
     * Calcule le régime moteur (RPM) pour une vitesse donnée en km/h.
     */
    fun calculateRpm(speedKmh: Float): Double {
        val v1000 = getEffectiveV1000()
        if (v1000 <= 0.0) return 0.0
        return (speedKmh.toDouble() / v1000) * 1000.0
    }

    /**
     * Calcule la fréquence fondamentale H1 en Hz (RPM / 60).
     */
    fun calculateH1FreqHz(speedKmh: Float): Double {
        val rpm = calculateRpm(speedKmh)
        return rpm / 60.0
    }
}

/**
 * Balise d'harmonique active avec timestamp de persistance pour rémanence visuelle.
 */
data class TrackedHarmonicTag(
    val orderName: String,         // ex: "H18", "H36"
    val orderValue: Double,        // ex: 18.0
    val freqHz: Int,
    val ttnrDb: Double,
    val absDbFS: Double,
    val speedKmh: Float,
    val rpm: Double,
    val binIndex: Int,
    val lastSeenTimestampMs: Long
)

/**
 Entrée accumulée pour le rapport synthétique d'émergences.
 */
data class EmergenceReportEntry(
    val orderName: String,         // ex: "H18", "H22.2", "H14.8"
    val orderValue: Double,        // ex: 18.0, 22.2, 14.8
    var minSpeedKmh: Float,
    var maxSpeedKmh: Float,
    var minRpm: Int,
    var maxRpm: Int,
    var minFreqHz: Int,
    var maxFreqHz: Int,
    var maxEmergenceDb: Double,
    var countDetections: Int = 1,
    var lastTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Suivi dynamique d'un candidat d'ordre continu au dixième avec tolérance +-0.10.
 */
data class CandidateHarmonicTracker(
    var orderSum: Double,
    var count: Int,
    val firstSeenTimestampMs: Long,
    var lastSeenTimestampMs: Long,
    var lastFreqHz: Int,
    var maxTtnrDb: Double,
    var maxAbsDbFS: Double,
    var minSpeedKmh: Float,
    var maxSpeedKmh: Float,
    var minRpm: Int,
    var maxRpm: Int,
    var minFreqHz: Int,
    var maxFreqHz: Int,
    var binIndex: Int
) {
    val currentMeanOrder: Double
        get() = orderSum / count.coerceAtLeast(1)

    val formattedOrderName: String
        get() {
            val roundedOneDec = Math.round(currentMeanOrder * 10.0) / 10.0
            return if (roundedOneDec % 1.0 == 0.0) "H${roundedOneDec.toInt()}" else "H%.1f".format(roundedOneDec)
        }
}
