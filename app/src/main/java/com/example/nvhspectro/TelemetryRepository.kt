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

data class TelemetryData(
    val speedKmh: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accelerationG: Float = 0f
)

class TelemetryRepository(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    @SuppressLint("MissingPermission")
    fun startTelemetry(): Flow<TelemetryData> = callbackFlow {
        var currentData = TelemetryData()

        // Listener Capteurs (Accélération linéaire, sans gravité)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // On prend l'accélération longitudinale (axe Y du téléphone en portrait)
                    // Ou la magnitude globale pour simplifier : sqrt(x^2 + y^2 + z^2)
                    // Pour le NVH, l'axe Y est souvent celui de l'avancement si le tél est posé à plat
                    val y = it.values[1] 
                    val g = y / 9.81f // conversion en G
                    
                    currentData = currentData.copy(accelerationG = g)
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
                    val speed = if (loc.hasSpeed()) (loc.speed * 3.6f) else 0f // m/s -> km/h
                    currentData = currentData.copy(
                        speedKmh = speed,
                        altitude = loc.altitude,
                        latitude = loc.latitude,
                        longitude = loc.longitude
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
