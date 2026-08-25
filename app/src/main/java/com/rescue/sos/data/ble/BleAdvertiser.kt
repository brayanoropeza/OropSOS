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
import com.rescue.sos.data.location.LocationHelper
import java.nio.ByteBuffer

class BleAdvertiser(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private val locationHelper = LocationHelper(context)

    private var advertiseCallback: AdvertiseCallback? = null
    var isAdvertising = false
        private set

    companion object {
        private const val TAG = "BleAdvertiser"
        val SOS_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        const val MANUFACTURER_ID = 0x00E0 // ID de fabricante para difusión garantizada
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

    /**
     * Comprime el ID de la víctima y las coordenadas GPS en un buffer binario de 16 bytes.
     */
    private fun createCompactPayload(victimId: String, gpsCoords: String): ByteArray {
        val cleanId = victimId.take(8).padEnd(8, ' ')
        var lat = 0f
        var lng = 0f
        if (gpsCoords.contains(",")) {
            val parts = gpsCoords.split(",")
            lat = parts.getOrNull(0)?.trim()?.toFloatOrNull() ?: 0f
            lng = parts.getOrNull(1)?.trim()?.toFloatOrNull() ?: 0f
        }

        val buffer = ByteBuffer.allocate(16)
        buffer.put(cleanId.toByteArray(Charsets.UTF_8))
        buffer.putFloat(lat)
        buffer.putFloat(lng)
        return buffer.array()
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
            Thread.sleep(800)
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            onStatusChanged(false, "Este dispositivo no soporta BLE Multiple Advertisement.")
            return
        }

        try {
            // Asignar nombre local de transmisión SOS visible a cualquier escáner de radio
            adapter.name = "SOS_VICTIMA"
        } catch (e: Exception) {
            // Manejo seguro
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onStatusChanged(false, "No se pudo obtener el BluetoothLeAdvertiser.")
            return
        }

        // Configuración de emisión en MÁXIMA FRECUENCIA Y MÁXIMA POTENCIA DE RADIO (100mW)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // Capturar la última ubicación GPS conocida en el instante del SOS
        val gpsCoords = locationHelper.getLastKnownLocation()
        val compactPayload = createCompactPayload(victimId, gpsCoords)

        // Paquete principal optimizado con inclusión de nombre de dispositivo
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(SOS_SERVICE_UUID)
            .addManufacturerData(MANUFACTURER_ID, compactPayload)
            .build()

        // Paquete de respuesta al escaneo (Scan Response)
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(SOS_SERVICE_UUID, compactPayload)
            .build()

        stopSOS()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Log.d(TAG, "Beacon SOS iniciado exitosamente con ID: $victimId y GPS: $gpsCoords")
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
            advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
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
