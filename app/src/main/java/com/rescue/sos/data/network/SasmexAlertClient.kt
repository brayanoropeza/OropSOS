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
        // API pública de monitoreo de sismos SASMEX / CIRES
        private const val SASMEX_API_URL = "https://sasmex.net/api/v1/latest"
    }

    fun startMonitoring(onAlertTriggered: (SeismicAlert) -> Unit) {
        if (isMonitoring) return
        isMonitoring = true

        thread {
            while (isMonitoring) {
                try {
                    checkSasmexApi(onAlertTriggered)
                } catch (e: Exception) {
                    Log.e(TAG, "Error consultando servidor SASMEX/CIRES", e)
                }
                // Polling cada 10 segundos
                Thread.sleep(10000)
            }
        }
    }

    private fun checkSasmexApi(onAlertTriggered: (SeismicAlert) -> Unit) {
        val url = URL(SASMEX_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"

        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()

            if (response.contains("ALERTA_SISMICA") || response.contains("\"active\":true")) {
                val alert = SeismicAlert(
                    isAlertActive = true,
                    epicenter = "Costa de Guerrero / Oaxaca",
                    magnitude = 6.2,
                    secondsRemaining = 50,
                    timestamp = System.currentTimeMillis()
                )
                _currentAlert.value = alert
                triggerEmergencySiren()
                onAlertTriggered(alert)
            }
        }
        connection.disconnect()
    }

    fun simulateTestAlert(onAlertTriggered: (SeismicAlert) -> Unit) {
        val alert = SeismicAlert(
            isAlertActive = true,
            epicenter = "SIMULACRO - Costa de Guerrero",
            magnitude = 6.5,
            secondsRemaining = 60,
            timestamp = System.currentTimeMillis()
        )
        _currentAlert.value = alert
        triggerEmergencySiren()
        onAlertTriggered(alert)
    }

    fun triggerEmergencySiren() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 5000) // 5 segundos de sirena de emergencia
            Handler(Looper.getMainLooper()).postDelayed({
                toneGenerator?.release()
                toneGenerator = null
            }, 5000)
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sirena de emergencia", e)
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        _currentAlert.value = null
    }
}
