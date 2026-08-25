package com.rescue.sos.presentation.rescuer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    modifier: Modifier = Modifier
) {
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFF0A0E14), shape = MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = (size.width.coerceAtMost(size.height) / 2.2f)

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

            // Ejes de coordenadas 3D de escombros
            drawLine(
                color = Color(0xFF1E3A5F),
                start = Offset(centerX - maxRadius, centerY),
                end = Offset(centerX + maxRadius, centerY),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF1E3A5F),
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
                strokeWidth = 2f
            )

            // Si hay víctima detectada, calcular su posición 3D relativa según RSSI
            if (victimSignal != null) {
                // Distancia representada visualmente (cuanto más cerca, más próximo al centro)
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
                radius = 6f,
                center = Offset(centerX, centerY)
            )
        }

        // Overlay de texto de estado en el radar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RADAR 3D DE ESCOMBROS",
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = selectedMaterial.displayName.split(" ")[0],
                    color = Color(0xFFFFC107),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "🟢 Rescatista (Centro)\n🔴 Víctima (Objetivo)",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
                if (victimSignal != null) {
                    Text(
                        text = "RSSI: ${victimSignal.rssi} dBm",
                        color = Color(0xFFFF1744),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
