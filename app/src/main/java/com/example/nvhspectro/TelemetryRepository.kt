package com.example.nvhspectro

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    @SuppressLint("MissingPermission")
    fun startTelemetry(): Flow<TelemetryData> = callbackFlow {
        var currentData = TelemetryData()

        // Listener Capteurs (Accélération linéaire)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val y = it.values[1] 
                    val g = y / 9.81f // conversion en G
                    
                    currentData = currentData.copy(
                        accelerationG = g,
                        timestampMs = System.currentTimeMillis()
                    )
                    trySend(currentData)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Configuration GPS haute précision
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(200)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val speed = if (loc.hasSpeed()) (loc.speed * 3.6f) else 0f
                    val accuracy = if (loc.hasAccuracy()) loc.accuracy else 999f
                    
                    val status = when {
                        accuracy <= 10f -> GpsStatus.GOOD
                        accuracy <= 30f -> GpsStatus.POOR
                        else -> GpsStatus.NONE
                    }

                    currentData = currentData.copy(
                        speedKmh = speed,
                        altitude = loc.altitude,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        gpsStatus = status,
                        timestampMs = System.currentTimeMillis()
                    )
                    trySend(currentData)
                }
            }
        }

        // Démarrage
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        awaitClose {
            sensorManager.unregisterListener(sensorListener)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
