package com.example.nvhspectro.data

enum class KinematicsInputMode(val label: String) {
    V1000("V1000 Direct"),
    GEAR_RATIO("Rapport Global"),
    DETAILED_CHAIN("Chaîne Détaillée")
}

data class KinematicsConfig(
    val isEnabled: Boolean = false,
    val inputMode: KinematicsInputMode = KinematicsInputMode.V1000,
    val v1000Kmh: Double = 35.0,            // Vitesse en km/h pour 1000 RPM
    val globalGearRatio: Double = 9.5,        // Rapport total de réduction
    val gearReductionRatio: Double = 3.2,     // Rapport réducteur / descente
    val axleRatio: Double = 3.0,              // Rapport de pont
    val wheelRadiusMeters: Double = 0.31,     // Rayon sous charge du pneu (ex: ~0.31m pour 205/55 R16)
    val vehicleName: String = "",             // Identification du véhicule
    val motorName: String = "",               // Identification du moteur / GMPe
    val comments: String = "",                // Notes d'essai
    val holdTimeSec: Double = 3.0             // Durée de rémanence visuelle des étiquettes (secondes)
) {
    /**
     * Calcule la V1000 équivalente en km/h pour 1000 RPM selon le mode de saisie sélectionné.
     */
    fun getEffectiveV1000(): Double {
        return when (inputMode) {
            KinematicsInputMode.V1000 -> v1000Kmh.coerceAtLeast(0.1)
            KinematicsInputMode.GEAR_RATIO -> {
                val wheelRpm = 1000.0 / globalGearRatio.coerceAtLeast(0.01)
                val wheelSpeedMs = (wheelRpm * 2.0 * Math.PI * wheelRadiusMeters) / 60.0
                wheelSpeedMs * 3.6
            }
            KinematicsInputMode.DETAILED_CHAIN -> {
                val totalRatio = (gearReductionRatio * axleRatio).coerceAtLeast(0.01)
                val wheelRpm = 1000.0 / totalRatio
                val wheelSpeedMs = (wheelRpm * 2.0 * Math.PI * wheelRadiusMeters) / 60.0
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
 * Entrée accumulée pour le rapport synthétique d'émergences.
 */
data class EmergenceReportEntry(
    val orderName: String,         // ex: "H18"
    val orderValue: Double,        // ex: 18.0
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
