package com.rescue.sos.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

class BleAdvertiser(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null

    private var advertiseCallback: AdvertiseCallback? = null
    var isAdvertising = false
        private set

    companion object {
        private const val TAG = "BleAdvertiser"
        val SOS_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    }

    fun isBleSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun enableBluetoothIfDisabled(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return if (!adapter.isEnabled) {
            try {
                Log.d(TAG, "Bluetooth apagado. Activando Bluetooth automáticamente por emergencia...")
                adapter.enable() // Enciende el Bluetooth automáticamente en caso de socorro/emergencia
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error al encender Bluetooth automáticamente", e)
                false
            }
        } else {
            true
        }
    }

    fun isMultipleAdvertisementSupported(): Boolean {
        return bluetoothAdapter?.isMultipleAdvertisementSupported == true
    }

    @SuppressLint("MissingPermission")
    fun startSOS(victimId: String, onStatusChanged: (Boolean, String?) -> Unit) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onStatusChanged(false, "Este dispositivo no soporta Bluetooth.")
            return
        }

        // Si el Bluetooth está apagado, la app lo enciende automáticamente por emergencia
        if (!adapter.isEnabled) {
            enableBluetoothIfDisabled()
            // Breve retardo para permitir que la radio de Bluetooth encienda
            Thread.sleep(1000)
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            onStatusChanged(false, "Este dispositivo no soporta BLE Multiple Advertisement.")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onStatusChanged(false, "No se pudo obtener el BluetoothLeAdvertiser.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val payload = victimId.toByteArray(Charsets.UTF_8)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(SOS_SERVICE_UUID)
            .addServiceData(SOS_SERVICE_UUID, payload)
            .build()

        stopSOS()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Log.d(TAG, "Beacon SOS iniciado exitosamente con ID: $victimId")
                onStatusChanged(true, null)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising = false
                val errorMsg = when (errorCode) {
                    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos demasiado grandes"
                    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados anunciantes activos"
                    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "El anuncio ya está corriendo"
                    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno del chip Bluetooth"
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                    else -> "Error de anuncio desconocido: $errorCode"
                }
                Log.e(TAG, "Fallo al iniciar beacon SOS: $errorMsg")
                onStatusChanged(false, errorMsg)
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisos insuficientes para iniciar BLE Advertising", e)
            isAdvertising = false
            onStatusChanged(false, "Faltan permisos de Bluetooth (BLUETOOTH_ADVERTISE).")
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al iniciar BLE Advertising", e)
            isAdvertising = false
            onStatusChanged(false, e.localizedMessage ?: "Error desconocido")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopSOS() {
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener publicidad BLE", e)
            }
        }
        advertiseCallback = null
        isAdvertising = false
    }
}
