package com.rescue.sos.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import android.util.Log

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    companion object {
        private const val TAG = "LocationHelper"
    }

    fun isLocationEnabled(): Boolean {
        if (locationManager == null) return false
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return isGpsEnabled || isNetworkEnabled
    }

    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir ajustes de ubicación", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): String {
        if (locationManager == null) return "SIN_GPS"

        try {
            val gpsLocation: Location? = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val networkLocation: Location? = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            val bestLocation = when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }

            return if (bestLocation != null) {
                // Formato compacto para caber en paquete BLE: "19.4326,-99.1332"
                String.format(java.util.Locale.US, "%.4f,%.4f", bestLocation.latitude, bestLocation.longitude)
            } else {
                "SIN_GPS"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo última ubicación GPS", e)
            return "SIN_GPS"
        }
    }
}
