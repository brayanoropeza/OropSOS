package com.rescue.sos.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.util.Log
import com.rescue.sos.domain.model.VictimSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private val _detectedVictims = MutableStateFlow<List<VictimSignal>>(emptyList())
    val detectedVictims: StateFlow<List<VictimSignal>> = _detectedVictims.asStateFlow()

    private val victimMap = mutableMapOf<String, VictimSignal>()

    var isScanning = false
        private set

    companion object {
        private const val TAG = "BleScanner"
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    /**
     * Decodifica el payload compacto de 16 bytes (ID + Lat Float + Lng Float)
     */
    private fun parseCompactPayload(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 16) {
            try {
                val buffer = ByteBuffer.wrap(bytes)
                val idBytes = ByteArray(8)
                buffer.get(idBytes)
                val rawId = String(idBytes, Charsets.UTF_8).trim()
                val lat = buffer.getFloat()
                val lng = buffer.getFloat()
                val coords = if (lat != 0f && lng != 0f) "$lat,$lng" else "SIN_GPS"
                return Pair(if (rawId.isNotEmpty()) rawId else "VICTIMA_SOS", coords)
            } catch (e: Exception) {
                // Fallback
            }
        }
        val textString = String(bytes, Charsets.UTF_8)
        if (textString.contains("|")) {
            val parts = textString.split("|")
            return Pair(parts.getOrNull(0) ?: "VICTIMA_SOS", parts.getOrNull(1) ?: "SIN_GPS")
        }
        return Pair("VICTIMA_SOS", "SIN_GPS")
    }

    @SuppressLint("MissingPermission")
    fun startScan(onStatusChanged: (Boolean, String?) -> Unit) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onStatusChanged(false, "El Bluetooth está desactivado. Enciéndelo para buscar personas.")
            return
        }

        // En Android, si la ubicación del sistema (GPS) está apagada, el escáner BLE no entrega resultados
        if (!isLocationServiceEnabled()) {
            onStatusChanged(false, "⚠️ Enciende la Ubicación/GPS del teléfono en los ajustes de Android para habilitar el escaneo BLE.")
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onStatusChanged(false, "BluetoothLeScanner no está disponible en este teléfono.")
            return
        }

        // Escaneo de baja latencia y alta frecuencia agresiva
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        stopScan()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val record = result.scanRecord ?: return

                val manuData = record.getManufacturerSpecificData(BleAdvertiser.MANUFACTURER_ID)
                val serviceData = record.getServiceData(BleAdvertiser.SOS_SERVICE_UUID)
                val hasSosUuid = record.serviceUuids?.contains(BleAdvertiser.SOS_SERVICE_UUID) == true

                var victimId: String? = null
                var locationCoords = "SIN_GPS"

                if (manuData != null && manuData.isNotEmpty()) {
                    val parsed = parseCompactPayload(manuData)
                    victimId = parsed.first
                    locationCoords = parsed.second
                } else if (serviceData != null && serviceData.isNotEmpty()) {
                    val parsed = parseCompactPayload(serviceData)
                    victimId = parsed.first
                    locationCoords = parsed.second
                } else if (hasSosUuid) {
                    val devName = record.deviceName
                    victimId = if (!devName.isNullOrBlank()) devName else "VICTIMA_${result.device.address.takeLast(5).replace(":", "")}"
                }

                if (victimId != null) {
                    val signal = VictimSignal(
                        victimId = victimId,
                        rssi = result.rssi,
                        timestamp = System.currentTimeMillis(),
                        locationCoordinates = locationCoords
                    )

                    victimMap[victimId] = signal
                    _detectedVictims.value = victimMap.values.sortedByDescending { it.rssi }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                isScanning = false
                Log.e(TAG, "Error en escaneo BLE: $errorCode")
                onStatusChanged(false, "Fallo al escanear BLE: $errorCode")
            }
        }

        try {
            // Escaneo sin filtro de hardware estricto para evitar bloqueos por chipsets
            scanner?.startScan(null, settings, scanCallback)
            isScanning = true
            onStatusChanged(true, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisos de Bluetooth denegados (BLUETOOTH_SCAN)", e)
            isScanning = false
            onStatusChanged(false, "Permiso BLUETOOTH_SCAN denegado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar escaneo", e)
            isScanning = false
            onStatusChanged(false, e.localizedMessage ?: "Error en escaneo")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (scanner != null && scanCallback != null) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener escaneo BLE", e)
            }
        }
        scanCallback = null
        isScanning = false
    }

    fun clearVictims() {
        victimMap.clear()
        _detectedVictims.value = emptyList()
    }
}
