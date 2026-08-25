package com.rescue.sos.presentation.victim

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rescue.sos.data.battery.BatteryOptimizationHelper
import com.rescue.sos.data.ble.BleAdvertiser
import com.rescue.sos.data.location.LocationHelper
import com.rescue.sos.data.network.SasmexAlertClient
import com.rescue.sos.service.SosForegroundService

@Composable
fun VictimScreen(
    victimId: String,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val advertiser = remember { BleAdvertiser(context) }
    val sasmexClient = remember { SasmexAlertClient(context) }
    val batteryHelper = remember { BatteryOptimizationHelper(context) }
    val locationHelper = remember { LocationHelper(context) }

    var isSosActive by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(advertiser.isBluetoothEnabled()) }
    var isLocationEnabled by remember { mutableStateOf(locationHelper.isLocationEnabled()) }
    var isBatteryExempt by remember { mutableStateOf(batteryHelper.isIgnoringBatteryOptimizations()) }

    val activeSasmexAlert by sasmexClient.currentAlert.collectAsState()

    val buttonColor by animateColorAsState(
        targetValue = if (isSosActive) Color(0xFFD32F2F) else Color(0xFF388E3C),
        label = "SosButtonColor"
    )

    // Monitoreo de Alerta Sísmica de México SASMEX / CIRES
    DisposableEffect(Unit) {
        sasmexClient.startMonitoring { alert ->
            onStatusMessage("¡ALERTA SÍSMICA CIRES DETECTADA! ACTIVANDO BLUETOOTH, GPS Y SOS")
            advertiser.enableBluetoothIfDisabled()
            isBluetoothEnabled = true
            isLocationEnabled = locationHelper.isLocationEnabled()
            SosForegroundService.startService(context, victimId)
            isSosActive = true
        }

        onDispose {
            sasmexClient.stopMonitoring()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Banner de Alerta Sísmica Activa (si hay alerta)
        activeSasmexAlert?.let { alert ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚨 ALERTA SÍSMICA MÉXICO (CIRES) 🚨",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Epicentro: ${alert.epicenter}\nTiempo estimado de llegada: ~${alert.secondsRemaining}s.\nBluetooth + GPS Transmitiendo Posición.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Cabecera e Instrucciones
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MODO VÍCTIMA (EMISOR SOS + GPS)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: $victimId\nEmisión continua BLE con coordenadas GPS capturadas al temblar.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Botón Gigante de SOS
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable {
                    if (isSosActive) {
                        SosForegroundService.stopService(context)
                        isSosActive = false
                        onStatusMessage("Señal SOS desactivada.")
                    } else {
                        advertiser.enableBluetoothIfDisabled()
                        isBluetoothEnabled = true
                        isLocationEnabled = locationHelper.isLocationEnabled()
                        SosForegroundService.startService(context, victimId)
                        isSosActive = true
                        onStatusMessage("¡BLUETOOTH + GPS CAPTURADO Y SOS ENVIÁNDOSE!")
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = "SOS Icon",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSosActive) "SOS ACTIVO\nTOCAR PARA DETENER" else "TRANSMITIR\nSOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Controles de Hardware, Batería y Créditos
        Column(modifier = Modifier.fillMaxWidth()) {
            // Fila de Estados: Bluetooth y GPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bluetooth Status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBluetoothEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBluetoothEnabled) "Bluetooth ON" else "Bluetooth OFF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // GPS Status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocationEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLocationEnabled) "GPS Listo" else "GPS Apagado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Exención de Optimización de Batería (Ejecución sin límites)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBatteryExempt) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isBatteryExempt) "Segundo Plano Sin Restricciones" else "Optimización de Batería Activa",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (isBatteryExempt) "Android no cerrará el SOS al apagar la pantalla" else "Android podría cerrar la app. Otorga el permiso.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (!isBatteryExempt) {
                        Button(
                            onClick = {
                                batteryHelper.requestIgnoreBatteryOptimizations()
                                isBatteryExempt = batteryHelper.isIgnoringBatteryOptimizations()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("EXIMIR", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Card de SASMEX CIRES Alerta Sísmica México
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🇲🇽 Red Alerta Sísmica CIRES / SASMEX",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Auto-activa Bluetooth + GPS + SOS al temblar",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Button(
                        onClick = {
                            sasmexClient.simulateTestAlert { alert ->
                                onStatusMessage("¡SIMULACRO ALERTA SÍSMICA ACTIVADO!")
                                advertiser.enableBluetoothIfDisabled()
                                isBluetoothEnabled = true
                                isLocationEnabled = locationHelper.isLocationEnabled()
                                SosForegroundService.startService(context, victimId)
                                isSosActive = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PROBAR", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Créditos de Desarrollador
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Desarrollada por Brayan Jesús Oropeza Acuña",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "OropSOS Socorro Sísmico + GPS | Android ${Build.VERSION.RELEASE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
