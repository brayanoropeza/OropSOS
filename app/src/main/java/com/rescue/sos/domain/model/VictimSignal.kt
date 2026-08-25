package com.rescue.sos.domain.model

data class VictimSignal(
    val victimId: String,
    val rssi: Int,
    val timestamp: Long,
    val locationCoordinates: String = "SIN_GPS",
    val distanceCategory: DistanceCategory = getDistanceCategory(rssi)
)

enum class DistanceCategory(val description: String) {
    VERY_CLOSE("Muy cerca (1 - 3 metros) - Debajo o al lado"),
    CLOSE("Cerca (3 - 10 metros) - Moverse en esta dirección"),
    FAR("Lejos (> 10 metros) - Atenuada por escombros/distancia")
}

fun getDistanceCategory(rssi: Int): DistanceCategory {
    return when {
        rssi >= -65 -> DistanceCategory.VERY_CLOSE
        rssi >= -82 -> DistanceCategory.CLOSE
        else -> DistanceCategory.FAR
    }
}
