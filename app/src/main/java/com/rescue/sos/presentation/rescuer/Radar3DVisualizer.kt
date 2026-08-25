package com.rescue.sos.presentation.rescuer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Exploration
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rescue.sos.data.audio.BuildingMaterial
import com.rescue.sos.domain.model.VictimSignal
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Radar3DVisualizer(
    victimSignal: VictimSignal?,
    selectedMaterial: BuildingMaterial,
    compassAzimuth: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "Radar3D")

    // Animación de pulso concéntrico 3D
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse3D"
    )

    // Animación de barrido de radar 360 grados
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Sweep3D"
    )

    val rssi = victimSignal?.rssi ?: -95
    val clampedRssi = rssi.coerceIn(-95, -45)
    val closenessProgress = (clampedRssi - (-95)).toFloat() / ((-45) - (-95)).toFloat() // 0.0 a 1.0

    val gpsCoords = victimSignal?.locationCoordinates
    val hasValidGps = !gpsCoords.isNullOrBlank() && gpsCoords != "SIN_GPS" && gpsCoords.contains(",")

    // Dirección de los puntos cardinales según los grados de la brújula
    val compassDirection = when (compassAzimuth.toInt()) {
        in 338..360, in 0..22 -> "NORTE (N)"
        in 23..67 -> "NORESTE (NE)"
        in 68..112 -> "ESTE (E)"
        in 113..157 -> "SURESTE (SE)"
        in 158..202 -> "SUR (S)"
        in 203..247 -> "SUROESTE (SO)"
        in 248..292 -> "OESTE (O)"
        in 293..337 -> "NOROESTE (NO)"
        else -> "NORTE (N)"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(270.dp)
            .background(Color(0xFF070B12), shape = MaterialTheme.shapes.medium)
            .border(1.dp, Color(0xFF1E3A5F), shape = MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = (size.width.coerceAtMost(size.height) / 2.2f)

            // Rotar el plano 3D según el ángulo magnético de la brújula del teléfono
            rotate(degrees = -compassAzimuth, pivot = Offset(centerX, centerY)) {
                // Dibujar rejilla isométrica 3D de escombros/edificio (3 niveles de profundidad)
                val layers = listOf(0.4f, 0.7f, 1.0f)
                layers.forEachIndexed { index, scale ->
                    val levelYOffset = (index - 1) * 25f
                    val radiusX = maxRadius * scale
                    val radiusY = (maxRadius * scale) * 0.45f // Elipse isométrica 3D

                    drawOval(
                        color = Color(0xFF1E3A5F).copy(alpha = 0.6f),
                        topLeft = Offset(centerX - radiusX, centerY + levelYOffset - radiusY),
                        size = androidx.compose.ui.geometry.Size(radiusX * 2, radiusY * 2),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Ejes de coordenadas 3D de escombros (N-S, E-O)
                drawLine(
                    color = Color(0xFF1E3A5F).copy(alpha = 0.8f),
                    start = Offset(centerX - maxRadius, centerY),
                    end = Offset(centerX + maxRadius, centerY),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color(0xFF1E3A5F).copy(alpha = 0.8f),
                    start = Offset(centerX, centerY - maxRadius * 0.5f - 25f),
                    end = Offset(centerX, centerY + maxRadius * 0.5f + 25f),
                    strokeWidth = 1f
                )

                // Dibujar línea de barrido de radar en ángulo 3D
                val rad = Math.toRadians(sweepAngle.toDouble())
                val sweepEndX = centerX + (maxRadius * cos(rad)).toFloat()
                val sweepEndY = centerY + ((maxRadius * 0.45f) * sin(rad)).toFloat()

                drawLine(
                    color = Color(0xFF00E676).copy(alpha = 0.7f),
                    start = Offset(centerX, centerY),
                    end = Offset(sweepEndX, sweepEndY),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )

                // Si hay víctima detectada, calcular su posición 3D relativa según RSSI
                if (victimSignal != null) {
                    val distanceOffset = maxRadius * (1f - (closenessProgress * 0.85f))
                    val victimAngleRad = Math.toRadians(45.0) // Ángulo fijo de sonar para visualización
                    val victimX = centerX + (distanceOffset * cos(victimAngleRad)).toFloat()
                    val victimY = centerY + ((distanceOffset * 0.45f) * sin(victimAngleRad)).toFloat()

                    // Pulso expansivo concéntrico 3D en la ubicación de la víctima
                    val pulseRadius = 10f + (pulseAnim * 35f)
                    val pulseAlpha = 1f - pulseAnim
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = Offset(victimX, victimY),
                        style = Stroke(width = 2f)
                    )

                    // Punto rojo brillante de la víctima atrapada
                    drawCircle(
                        color = Color(0xFFFF1744),
                        radius = 8f,
                        center = Offset(victimX, victimY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3f,
                        center = Offset(victimX, victimY)
                    )
                }

                // Punto azul central representando al Rescatista
                drawCircle(
                    color = Color(0xFF2979FF),
                    radius = 7f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Overlay de texto de estado, brújula y botón Google Maps 3D
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Satellite,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RADAR 3D DE ESCOMBROS",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Indicador de Brújula Magnética
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Exploration,
                        contentDescription = "Brújula",
                        tint = Color(0xFF81D4FA),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${compassAzimuth.toInt()}° $compassDirection",
                        color = Color(0xFF81D4FA),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Botón de Google Maps en Modo 3D / Satélite cuando hay GPS
            if (hasValidGps && gpsCoords != null) {
                Surface(
                    color = Color(0xFF1976D2).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable {
                            try {
                                val parts = gpsCoords.split(",")
                                val lat = parts[0].trim()
                                val lng = parts[1].trim()

                                val gmapsUri = Uri.parse("geo:$lat,$lng?z=20&t=k&q=$lat,$lng(Edificio_SOS)")
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
                                // Manejo seguro de errores
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Maps 3D",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VER EDIFICIO EN GOOGLE MAPS 3D",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "🔵 Rescatista (Origen)\n🔴 Víctima (Objetivo SOS)",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (hasValidGps) {
                        Text(
                            text = "GPS: $gpsCoords",
                            color = Color(0xFF81D4FA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (victimSignal != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SEÑAL VITAL",
                            color = Color(0xFFFF1744),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${victimSignal.rssi} dBm",
                            color = Color(0xFFFF1744),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
