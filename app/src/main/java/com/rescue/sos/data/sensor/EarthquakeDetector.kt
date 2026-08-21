package com.rescue.sos.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class EarthquakeDetector(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onEarthquakeDetected: (() -> Unit)? = null

    // Un sismo fuerte produce movimiento brusco > 5.0 m/s² descartando la aceleración de la gravedad terrestre
    private val EARTHQUAKE_THRESHOLD = 5.0f
    private val accelerationHistory = ArrayList<Float>()
    var isListening = false
        private set

    companion object {
        private const val TAG = "EarthquakeDetector"
    }

    fun startDetection(onDetected: () -> Unit): Boolean {
        if (sensorManager == null || accelerometer == null) {
            Log.e(TAG, "El acelerómetro no está disponible en este dispositivo.")
            return false
        }

        onEarthquakeDetected = onDetected
        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
        isListening = registered
        Log.d(TAG, "Detección de sismo iniciada: $registered")
        return registered
    }

    fun stopDetection() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
            Log.d(TAG, "Detección de sismo detenida")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculamos la aceleración bruta
        val rawAcceleration = sqrt(x * x + y * y + z * z)
        // Restamos la gravedad terrestre (~9.81 m/s²)
        val netAcceleration = abs(rawAcceleration - SensorManager.GRAVITY_EARTH)

        accelerationHistory.add(netAcceleration)
        if (accelerationHistory.size > 100) {
            accelerationHistory.removeAt(0)
        }

        if (netAcceleration > EARTHQUAKE_THRESHOLD) {
            Log.w(TAG, "¡ALERTA DE SISMO DETECTADA! Aceleración: $netAcceleration m/s²")
            onEarthquakeDetected?.invoke()
            stopDetection() // Detener para prevenir falsos positivos repetidos
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
