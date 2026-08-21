package com.rescue.sos.presentation.victim

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
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
import com.rescue.sos.data.ble.BleAdvertiser
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

    var isSosActive by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(advertiser.isBluetoothEnabled()) }

    val activeSasmexAlert by sasmexClient.currentAlert.collectAsState()

    val buttonColor by animateColorAsState(
        targetValue = if (isSosActive) Color(0xFFD32F2F) else Color(0xFF388E3C),
        label = "SosButtonColor"
    )

    // Monitoreo de Alerta Sísmica de México SASMEX / CIRES
    DisposableEffect(Unit) {
        sasmexClient.startMonitoring { alert ->
            onStatusMessage("¡ALERTA SÍSMICA CIRES DETECTADA! ACTIVANDO BLUETOOTH Y TRANSMITIENDO SOS")
            advertiser.enableBluetoothIfDisabled()
            isBluetoothEnabled = true
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
                        text = "Epicentro: ${alert.epicenter}\nTiempo estimado de llegada: ~${alert.secondsRemaining} segundos.\nBluetooth Activado Automáticamente.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Cabecera e Instrucciones
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MODO VÍCTIMA (EMISOR SOS)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID Único: $victimId\nEl Bluetooth se activará automáticamente al temblar o al presionar SOS.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Botón Gigante de SOS
        Box(
            modifier = Modifier
                .size(220.dp)
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
                        SosForegroundService.startService(context, victimId)
                        isSosActive = true
                        onStatusMessage("¡BLUETOOTH ACTIVADO Y SEÑAL SOS ENVIÁNDOSE!")
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = "SOS Icon",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isSosActive) "SOS ACTIVO\nTOCAR PARA DETENER" else "TRANSMITIR\nSOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Sección de Alerta Sísmica SASMEX, Control Bluetooth y Créditos
        Column(modifier = Modifier.fillMaxWidth()) {
            // Estado y Control de Bluetooth
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBluetoothEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (isBluetoothEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isBluetoothEnabled) "Bluetooth Activado" else "Bluetooth Apagado",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Se enciende solo si tiembla o activas el SOS",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (!isBluetoothEnabled) {
                        Button(
                            onClick = {
                                advertiser.enableBluetoothIfDisabled()
                                isBluetoothEnabled = advertiser.isBluetoothEnabled()
                                onStatusMessage("Bluetooth encendido manualmente.")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("ENCENDER", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Card de SASMEX CIRES Alerta Sísmica México
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🇲🇽 Red Alerta Sísmica CIRES / SASMEX",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Enciende Bluetooth + SOS automáticamente si tiembla",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            sasmexClient.simulateTestAlert { alert ->
                                onStatusMessage("¡SIMULACRO ALERTA SÍSMICA ACTIVADO!")
                                advertiser.enableBluetoothIfDisabled()
                                isBluetoothEnabled = true
                                SosForegroundService.startService(context, victimId)
                                isSosActive = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PROBAR", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Créditos de Desarrollador
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Desarrollada por Brayan Jesús Oropeza Acuña",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "OropSOS App de Socorro y Rescate Sísmico | Android ${Build.VERSION.RELEASE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
