package com.rescue.sos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class RescueApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SOS_CHANNEL_ID,
                "Señal de Socorro (SOS)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación activa mientras se emite la señal de socorro BLE"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SOS_CHANNEL_ID = "sos_emergency_channel"
    }
}
