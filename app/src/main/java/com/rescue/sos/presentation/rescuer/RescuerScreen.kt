package com.rescue.sos.presentation.rescuer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
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
import com.rescue.sos.data.audio.BuildingMaterial
import com.rescue.sos.data.audio.MetalDetectorAudioTracker
import com.rescue.sos.data.ble.BleScanner
import com.rescue.sos.data.sensor.CompassHelper
import com.rescue.sos.domain.model.DistanceCategory
import com.rescue.sos.domain.model.VictimSignal

@Composable
fun RescuerScreen(
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { BleScanner(context) }
    val audioTracker = remember { MetalDetectorAudioTracker() }
    val compassHelper = remember { CompassHelper(context) }
    val compassAzimuth by compassHelper.azimuth.collectAsState()
    val detectedVictims by scanner.detectedVictims.collectAsState()

    var isScanning by remember { mutableStateOf(false) }
    val defaultMaterial = BuildingMaterial.CONCRETE
    var selectedVictimFor3d by remember { mutableStateOf<VictimSignal?>(null) }

    // Iniciar escucha de brújula y rastreador sonoro continuo de fondo en silencio visual
    DisposableEffect(Unit) {
        audioTracker.startTracking()
        compassHelper.startListening()
        onDispose {
            audioTracker.stopTracking()
            compassHelper.stopListening()
            scanner.stopScan()
        }
    }

    // Actualizar víctima seleccionada y posición en el Radar 3D al instante al detectar señales
    LaunchedEffect(detectedVictims) {
        if (detectedVictims.isNotEmpty()) {
            val closest = detectedVictims.first()
            selectedVictimFor3d = closest
            audioTracker.updateRssiAndMaterial(closest.rssi, defaultMaterial)
        } else {
            selectedVictimFor3d = null
            audioTracker.updateRssiAndMaterial(-95, defaultMaterial)
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
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MODO RESCATISTA (RADAR 3D)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Visualización 3D instantánea por brújula y mapas satelitales.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(
                    onClick = {
                        scanner.clearVictims()
                        selectedVictimFor3d = null
                        onStatusMessage("Lista de víctimas limpiada.")
                    }
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Limpiar")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Visualizador Radar 3D con ubicación al instante
        Radar3DVisualizer(
            victimSignal = selectedVictimFor3d ?: detectedVictims.firstOrNull(),
            selectedMaterial = defaultMaterial,
            compassAzimuth = compassAzimuth
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Controles de Escaneo BLE
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
                            onStatusMessage("Escaneo BLE de alta frecuencia activo...")
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
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.CellTower else Icons.Default.PersonSearch,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "DETENER ESCANEO" else "INICIAR BÚSQUEDA SOS", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lista de Víctimas Detectadas
        if (detectedVictims.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Buscando señales SOS en el radar 3D..." else "Pulse 'INICIAR BÚSQUEDA SOS' para rastrear dispositivos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(detectedVictims, key = { it.victimId }) { victim ->
                    VictimSignalCard3D(
                        victim = victim,
                        isSelected = selectedVictimFor3d?.victimId == victim.victimId,
                        onSelect = {
                            selectedVictimFor3d = victim
                            audioTracker.updateRssiAndMaterial(victim.rssi, defaultMaterial)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VictimSignalCard3D(
    victim: VictimSignal,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val (statusColor, categoryText) = when (victim.distanceCategory) {
        DistanceCategory.VERY_CLOSE -> Color(0xFFD32F2F) to "¡MUY CERCA (1 - 3m)!"
        DistanceCategory.CLOSE -> Color(0xFFF57C00) to "CERCA (3 - 10m)"
        DistanceCategory.FAR -> Color(0xFF1976D2) to "LEJOS (> 10m)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(statusColor, shape = MaterialTheme.shapes.small)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = victim.victimId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = categoryText,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${victim.rssi} dBm",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = statusColor
                    )
                    Text(
                        text = "Intensidad",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Coordenadas GPS del momento del SOS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GPS: ${victim.locationCoordinates}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (victim.locationCoordinates != "SIN_GPS" && victim.locationCoordinates.contains(",")) {
                    TextButton(
                        onClick = {
                            try {
                                val parts = victim.locationCoordinates.split(",")
                                val lat = parts[0].trim()
                                val lng = parts[1].trim()
                                val gmapsUri = Uri.parse("geo:$lat,$lng?z=20&t=k&q=$lat,$lng(Victima_SOS)")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
                                    setPackage("com.google.android.apps.maps")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/@$lat,$lng,100m/data=!3m1!1e3")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(webIntent)
                                }
                            } catch (e: Exception) {
                                // Ignorar si no hay app de mapas
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("VER MAPA 3D", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
