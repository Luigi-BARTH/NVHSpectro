package com.example.nvhspectro

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class GpsStatus {
    NONE,  // Rouge : pas de fix ou mauvaise précision (>30m)
    POOR,  // Orange : précision moyenne (10m - 30m)
    GOOD   // Vert : excellente précision (<10m)
}

data class TelemetryData(
    val speedKmh: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accelerationG: Float = 0f,
    val gpsStatus: GpsStatus = GpsStatus.NONE,
    val timestampMs: Long = System.currentTimeMillis()
)

class TelemetryRepository(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun startTelemetry(): Flow<TelemetryData> = callbackFlow {
        var currentData = TelemetryData()
        var lastSpeedMs = 0f
        var lastTimeMs = 0L

        // Configuration GPS haute précision (Fréquence d'échantillonnage élevée)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 200)
            .setMinUpdateIntervalMillis(100)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val nowMs = loc.time
                    val speedMs = if (loc.hasSpeed()) loc.speed else 0f // m/s
                    val speedKmh = speedMs * 3.6f // km/h
                    val accuracy = if (loc.hasAccuracy()) loc.accuracy else 999f
                    
                    // Calcul 100% GPS de l'accélération (dérivée de la vitesse GPS par rapport au temps GPS)
                    var accelG = 0f
                    if (lastTimeMs > 0 && nowMs > lastTimeMs) {
                        val dt = (nowMs - lastTimeMs) / 1000.0f // delta temps en secondes
                        val dv = speedMs - lastSpeedMs // delta vitesse en m/s
                        if (dt > 0.05f) {
                            val accelMss = dv / dt // accélération en m/s²
                            accelG = accelMss / 9.81f // conversion pure en G
                        }
                    }
                    
                    lastSpeedMs = speedMs
                    lastTimeMs = nowMs

                    val status = when {
                        accuracy <= 10f -> GpsStatus.GOOD
                        accuracy <= 30f -> GpsStatus.POOR
                        else -> GpsStatus.NONE
                    }

                    currentData = TelemetryData(
                        speedKmh = speedKmh,
                        altitude = loc.altitude,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accelerationG = accelG,
                        gpsStatus = status,
                        timestampMs = nowMs
                    )
                    trySend(currentData)
                }
            }
        }

        // Démarrage GPS 100% pur
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
