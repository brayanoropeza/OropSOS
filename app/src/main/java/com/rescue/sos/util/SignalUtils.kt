package com.rescue.sos.util

import kotlin.math.pow

object SignalUtils {

    /**
     * Estima la distancia aproximada en metros basándose en el RSSI y la potencia de transmisión (txPower).
     * Fórmula Log-Distance Path Loss model:
     * Distance = 10 ^ ((Measured Power - RSSI) / (10 * N))
     * N es la constante de atenuación ambiental (2.0 a 4.0 bajo escombros).
     */
    fun calculateEstimatedDistance(rssi: Int, measuredPower: Int = -59, environmentalFactor: Double = 3.0): Double {
        if (rssi == 0) return -1.0
        val ratio = (measuredPower - rssi) / (10.0 * environmentalFactor)
        return 10.0.pow(ratio)
    }
}
