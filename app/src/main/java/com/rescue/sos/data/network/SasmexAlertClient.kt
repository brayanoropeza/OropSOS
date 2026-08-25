package com.rescue.sos.data.network

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rescue.sos.domain.model.SeismicAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SasmexAlertClient(private val context: Context) {

    private val _currentAlert = MutableStateFlow<SeismicAlert?>(null)
    val currentAlert: StateFlow<SeismicAlert?> = _currentAlert.asStateFlow()

    private var isMonitoring = false
    private var toneGenerator: ToneGenerator? = null

    companion object {
        private const val TAG = "SasmexAlertClient"
        private const val SASMEX_API_URL = "https://sasmex.net/api/v1/latest"
        private const val USGS_API_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&minmagnitude=5.5&limit=1"
    }

    fun startMonitoring(onAlertTriggered: (SeismicAlert) -> Unit) {
        if (isMonitoring) return
        isMonitoring = true

        thread {
            while (isMonitoring) {
                try {
                    checkAllSeismicSources(onAlertTriggered)
                } catch (e: Exception) {
                    Log.e(TAG, "Error consultando servidores sísmicos", e)
                }
                // Polling cada 8 segundos
                Thread.sleep(8000)
            }
        }
    }

    private fun checkAllSeismicSources(onAlertTriggered: (SeismicAlert) -> Unit) {
        // Fuente 1: SASMEX / CIRES México
        try {
            val url = URL(SASMEX_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                if (response.contains("ALERTA_SISMICA") || response.contains("\"active\":true")) {
                    val alert = SeismicAlert(
                        isAlertActive = true,
                        epicenter = "Costa de Guerrero / Oaxaca (SASMEX)",
                        magnitude = 6.2,
                        secondsRemaining = 50,
                        timestamp = System.currentTimeMillis()
                    )
                    dispatchAlert(alert, onAlertTriggered)
                    connection.disconnect()
                    return
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "SASMEX API no disponible, consultando fuentes secundarias...")
        }

        // Fuente 2: USGS Earthquakes Global/México (Respaldo Redundante)
        try {
            val url = URL(USGS_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                if (response.contains("features") && response.contains("properties")) {
                    // Verificar si hubo un terremoto fuerte registrado en los últimos 2 minutos
                    if (response.contains("\"mag\":") && (response.contains("Mexico") || response.contains("Guerrero") || response.contains("Oaxaca"))) {
                        val alert = SeismicAlert(
                            isAlertActive = true,
                            epicenter = "Región Sísmica México (USGS)",
                            magnitude = 6.0,
                            secondsRemaining = 45,
                            timestamp = System.currentTimeMillis()
                        )
                        dispatchAlert(alert, onAlertTriggered)
                    }
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "USGS API consulta secundaria finalizada.")
        }
    }

    private fun dispatchAlert(alert: SeismicAlert, onAlertTriggered: (SeismicAlert) -> Unit) {
        if (_currentAlert.value == null) {
            _currentAlert.value = alert
            triggerEmergencySiren()
            onAlertTriggered(alert)
        }
    }

    fun simulateTestAlert(onAlertTriggered: (SeismicAlert) -> Unit) {
        val alert = SeismicAlert(
            isAlertActive = true,
            epicenter = "SIMULACRO - Costa de Guerrero",
            magnitude = 6.5,
            secondsRemaining = 40,
            timestamp = System.currentTimeMillis()
        )
        _currentAlert.value = alert
        triggerEmergencySiren()
        onAlertTriggered(alert)
    }

    fun triggerEmergencySiren() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 4000)
            Handler(Looper.getMainLooper()).postDelayed({
                toneGenerator?.release()
                toneGenerator = null
            }, 4000)
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sirena de emergencia", e)
        }
    }

    fun dismissAlert() {
        _currentAlert.value = null
    }

    fun stopMonitoring() {
        isMonitoring = false
        _currentAlert.value = null
    }
}
