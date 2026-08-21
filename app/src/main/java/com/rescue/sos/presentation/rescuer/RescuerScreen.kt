package com.rescue.sos.presentation.rescuer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rescue.sos.data.ble.BleScanner
import com.rescue.sos.domain.model.DistanceCategory
import com.rescue.sos.domain.model.VictimSignal

@Composable
fun RescuerScreen(
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { BleScanner(context) }
    val detectedVictims by scanner.detectedVictims.collectAsState()

    var isScanning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            scanner.stopScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabecera Rescatista
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MODO RESCATISTA (ESCANER BLE)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Escanea señales de socorro emitidas por teléfonos de víctimas bajo escombros.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(
                    onClick = {
                        scanner.clearVictims()
                        onStatusMessage("Lista de víctimas limpiada.")
                    }
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Limpiar")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controles de Escaneo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isScanning) {
                        scanner.stopScan()
                        isScanning = false
                        onStatusMessage("Escaneo de rescatista detenido.")
                    } else {
                        scanner.startScan { success, error ->
                            if (success) {
                                isScanning = true
                                onStatusMessage("Escanear víctimas activado...")
                            } else {
                                isScanning = false
                                onStatusMessage(error ?: "Error al escanear")
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.CellTower else Icons.Default.PersonSearch,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "DETENER ESCANEO" else "INICIAR BÚSQUEDA SOS")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contador y Estado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Víctimas Detectadas: ${detectedVictims.size}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            if (isScanning) {
                Text(
                    text = "Busqueda Activa...",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lista de Víctimas Detectadas
        if (detectedVictims.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Buscando señales SOS en las cercanías..." else "Pulse 'INICIAR BÚSQUEDA SOS' para escanear los alrededores.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(detectedVictims, key = { it.victimId }) { victim ->
                    VictimSignalCard(victim)
                }
            }
        }
    }
}

@Composable
fun VictimSignalCard(victim: VictimSignal) {
    val (statusColor, categoryText) = when (victim.distanceCategory) {
        DistanceCategory.VERY_CLOSE -> Color(0xFFD32F2F) to "¡MUY CERCA (1 - 3m)!"
        DistanceCategory.CLOSE -> Color(0xFFF57C00) to "CERCA (3 - 10m)"
        DistanceCategory.FAR -> Color(0xFF1976D2) to "LEJOS (> 10m)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de Proximidad
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(statusColor, shape = MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = victim.victimId,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = categoryText,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = victim.distanceCategory.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${victim.rssi} dBm",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = statusColor
                )
                Text(
                    text = "Intensidad",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
