package com.rescue.sos.data.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlin.concurrent.thread

enum class BuildingMaterial(val displayName: String, val attenuationFactor: Double) {
    CONCRETE("Concreto Armado (Fuerte Atenuación)", 3.8),
    BRICK_TABLAROCA("Tablaroca / Ladrillo (Atenuación Media)", 2.8),
    WOOD_OPEN("Madera / Campo Abierto (Baja Atenuación)", 2.0)
}

class MetalDetectorAudioTracker {

    private var toneGenerator: ToneGenerator? = null
    private var isTracking = false
    private var trackingThread: Thread? = null

    @Volatile
    private var currentRssi: Int = -95

    @Volatile
    private var selectedMaterial: BuildingMaterial = BuildingMaterial.CONCRETE

    companion object {
        private const val TAG = "MetalDetectorAudio"
    }

    fun startTracking() {
        if (isTracking) return
        isTracking = true

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando ToneGenerator", e)
        }

        trackingThread = thread {
            while (isTracking) {
                val rssi = currentRssi

                // Calcular intervalo entre beeps en milisegundos (Lejos: 1200ms, Muy cerca: 100ms)
                val clampedRssi = rssi.coerceIn(-95, -45)
                val progress = (clampedRssi - (-95)).toFloat() / ((-45) - (-95)).toFloat() // 0.0 a 1.0

                val beepIntervalMs = (1200 - (progress * 1100)).toLong().coerceAtLeast(90L)
                val toneType = when {
                    progress > 0.8f -> ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE
                    progress > 0.5f -> ToneGenerator.TONE_PROP_BEEP
                    else -> ToneGenerator.TONE_PROP_BEEP2
                }

                try {
                    toneGenerator?.startTone(toneType, 60)
                } catch (e: Exception) {
                    Log.e(TAG, "Error emitiendo tono", e)
                }

                try {
                    Thread.sleep(beepIntervalMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    fun updateRssiAndMaterial(rssi: Int, material: BuildingMaterial) {
        this.currentRssi = rssi
        this.selectedMaterial = material
    }

    fun stopTracking() {
        isTracking = false
        trackingThread?.interrupt()
        trackingThread = null

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando ToneGenerator", e)
        }
    }
}
