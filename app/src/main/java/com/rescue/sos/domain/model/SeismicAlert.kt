package com.rescue.sos.domain.model

data class SeismicAlert(
    val isAlertActive: Boolean,
    val epicenter: String,
    val magnitude: Double,
    val secondsRemaining: Int,
    val timestamp: Long
)
