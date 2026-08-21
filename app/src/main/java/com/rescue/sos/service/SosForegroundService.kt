package com.rescue.sos.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rescue.sos.RescueApp
import com.rescue.sos.data.ble.BleAdvertiser
import com.rescue.sos.presentation.MainActivity

class SosForegroundService : Service() {

    private lateinit var advertiser: BleAdvertiser
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "SosForegroundService"
        private const val NOTIFICATION_ID = 9110
        const val ACTION_START_SOS = "ACTION_START_SOS"
        const val ACTION_STOP_SOS = "ACTION_STOP_SOS"
        const val EXTRA_VICTIM_ID = "EXTRA_VICTIM_ID"

        fun startService(context: Context, victimId: String) {
            val intent = Intent(context, SosForegroundService::class.java).apply {
                action = ACTION_START_SOS
                putExtra(EXTRA_VICTIM_ID, victimId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SosForegroundService::class.java).apply {
                action = ACTION_STOP_SOS
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        advertiser = BleAdvertiser(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OropSOS::ServiceWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SOS) {
            stopSosBeacon()
            stopSelf()
            return START_NOT_STICKY
        }

        val victimId = intent?.getStringExtra(EXTRA_VICTIM_ID)
            ?: "SOS_VICTIMA_${System.currentTimeMillis() % 10000}"

        startForegroundWithNotification()
        acquireWakeLock()

        // Encender Bluetooth automáticamente si está apagado antes de transmitir SOS
        advertiser.enableBluetoothIfDisabled()

        advertiser.startSOS(victimId) { success, error ->
            if (!success) {
                Log.e(TAG, "Error al iniciar transmisión SOS en Service: $error")
            } else {
                Log.d(TAG, "Transmisión SOS activa en primer plano con Bluetooth ON")
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, RescueApp.SOS_CHANNEL_ID)
            .setContentTitle("EMISIÓN SOS ACTIVA - MODO VÍCTIMA")
            .setContentText("OropSOS transmitiendo socorro BLE. Bluetooth Activado.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 horas max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al adquirir WakeLock", e)
        }
    }

    private fun stopSosBeacon() {
        advertiser.stopSOS()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar WakeLock", e)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopSosBeacon()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
