package com.rescue.sos.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.rescue.sos.data.location.LocationHelper

class BleAdvertiser(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private val locationHelper = LocationHelper(context)

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
                adapter.enable()
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

        if (!adapter.isEnabled) {
            enableBluetoothIfDisabled()
            try {
                Thread.sleep(800)
            } catch (e: Exception) {}
        }

        // 1. Iniciar Servidor GATT activo (al igual que un Galaxy Fit 3 / Smartwatch)
        try {
            if (gattServer == null && bluetoothManager != null) {
                gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {})
                val sosGattService = BluetoothGattService(
                    SOS_SERVICE_UUID.uuid,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )
                gattServer?.addService(sosGattService)
                Log.d(TAG, "Servidor GATT de socorro iniciado como periférico activo estilo Smartwatch/GalaxyFit3.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir Servidor GATT periférico", e)
        }

        // 2. Asignar nombre visible del dispositivo
        try {
            adapter.name = "SOS_VICTIMA"
        } catch (e: Exception) {}

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onStatusChanged(false, "No se pudo obtener el transmisor BluetoothLeAdvertiser.")
            return
        }

        // Configuración de emisión en MÁXIMA FRECUENCIA Y POTENCIA DE RADIO (LOW_LATENCY)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true) // Periférico conectable estilo Galaxy Fit 3
            .setTimeout(0)
            .build()

        // Capturar la última ubicación GPS conocida en el instante del SOS
        val gpsCoords = locationHelper.getLastKnownLocation()
        val shortId = if (victimId.length > 10) victimId.take(10) else victimId
        val fullDataString = "$shortId|$gpsCoords"
        val payload = fullDataString.toByteArray(Charsets.UTF_8)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Transmitir nombre del teléfono al igual que el Galaxy Fit 3
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(SOS_SERVICE_UUID)
            .addServiceData(SOS_SERVICE_UUID, payload)
            .build()

        stopSOS()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Log.d(TAG, "Beacon SOS activo estilo Smartwatch con ID y GPS: $fullDataString")
                onStatusChanged(true, null)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising = false
                val errorMsg = when (errorCode) {
                    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos demasiado grandes"
                    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados anunciantes activos"
                    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "El anuncio ya está activo"
                    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno del chip Bluetooth"
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada por el hardware"
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
            onStatusChanged(false, e.localizedMessage ?: "Error desconocido al transmitir SOS")
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
        try {
            gattServer?.close()
            gattServer = null
        } catch (e: Exception) {}
        advertiseCallback = null
        isAdvertising = false
    }
}
