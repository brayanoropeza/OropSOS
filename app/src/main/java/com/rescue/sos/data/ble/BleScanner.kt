package com.rescue.sos.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.rescue.sos.domain.model.VictimSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    @SuppressLint("MissingPermission")
    fun startScan(onStatusChanged: (Boolean, String?) -> Unit) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onStatusChanged(false, "El Bluetooth está desactivado.")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onStatusChanged(false, "BluetoothLeScanner no está disponible.")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(BleAdvertiser.SOS_SERVICE_UUID)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Escaneo rápido para localización precisa
            .build()

        stopScan()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val record = result.scanRecord ?: return
                val serviceData = record.getServiceData(BleAdvertiser.SOS_SERVICE_UUID)

                val victimId = if (serviceData != null && serviceData.isNotEmpty()) {
                    String(serviceData, Charsets.UTF_8)
                } else {
                    result.device.address ?: "VICTIMA_DESCONOCIDA"
                }

                val signal = VictimSignal(
                    victimId = victimId,
                    rssi = result.rssi,
                    timestamp = System.currentTimeMillis()
                )

                victimMap[victimId] = signal
                _detectedVictims.value = victimMap.values.sortedByDescending { it.rssi }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                isScanning = false
                Log.e(TAG, "Error en escaneo BLE: $errorCode")
                onStatusChanged(false, "Fallo al escanear: $errorCode")
            }
        }

        try {
            scanner?.startScan(filters, settings, scanCallback)
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
