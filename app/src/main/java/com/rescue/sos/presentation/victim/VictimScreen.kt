package com.rescue.sos.presentation.victim

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rescue.sos.data.battery.BatteryOptimizationHelper
import com.rescue.sos.data.ble.BleAdvertiser
import com.rescue.sos.data.contacts.EmergencyContact
import com.rescue.sos.data.contacts.EmergencyContactsManager
import com.rescue.sos.data.location.LocationHelper
import com.rescue.sos.data.network.SasmexAlertClient
import com.rescue.sos.presentation.components.BannerAdView
import com.rescue.sos.service.SosForegroundService
import kotlinx.coroutines.delay

@Composable
fun VictimScreen(
    victimId: String,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val advertiser = remember { BleAdvertiser(context) }
    val sasmexClient = remember { SasmexAlertClient(context) }
    val batteryHelper = remember { BatteryOptimizationHelper(context) }
    val locationHelper = remember { LocationHelper(context) }
    val contactsManager = remember { EmergencyContactsManager(context) }

    var isSosActive by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(advertiser.isBluetoothEnabled()) }
    var isLocationEnabled by remember { mutableStateOf(locationHelper.isLocationEnabled()) }
    var isBatteryExempt by remember { mutableStateOf(batteryHelper.isIgnoringBatteryOptimizations()) }

    // Modo PRO / Donador para desarrollador y pruebas (Remueve anuncios si está activo)
    var isProUser by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var secretTapCount by remember { mutableIntStateOf(0) }

    // Lista de Contactos de Emergencia
    var savedContacts by remember { mutableStateOf(contactsManager.getContacts()) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    // Estado del Conteo Regresivo de 40 Segundos ("Estoy Bien")
    var show40sConfirmationDialog by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(40) }

    val activeSasmexAlert by sasmexClient.currentAlert.collectAsState()

    // Escuchar el ciclo de vida (ON_RESUME) para refrescar estados al volver de Ajustes de Android 15+
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBluetoothEnabled = advertiser.isBluetoothEnabled()
                isLocationEnabled = locationHelper.isLocationEnabled()
                isBatteryExempt = batteryHelper.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = if (isSosActive) Color(0xFFD32F2F) else Color(0xFF2E7D32),
        label = "SosButtonColor"
    )

    // Monitoreo de Alerta Sísmica con ventana de 40s de confirmación
    DisposableEffect(Unit) {
        sasmexClient.startMonitoring { alert ->
            onStatusMessage("¡ALERTA SÍSMICA DETECTADA! INICIANDO CONTEO DE 40s DE SEGURIDAD...")
            countdownSeconds = 40
            show40sConfirmationDialog = true
        }

        onDispose {
            sasmexClient.stopMonitoring()
        }
    }

    // Función auxiliar para iniciar SOS + Despacho de WhatsApp & SMS
    val triggerFullEmergencySOS: () -> Unit = {
        advertiser.enableBluetoothIfDisabled()
        isBluetoothEnabled = true
        isLocationEnabled = locationHelper.isLocationEnabled()
        SosForegroundService.startService(context, victimId)
        isSosActive = true
        // Enviar alertas de WhatsApp y SMS a la red de contactos
        contactsManager.dispatchEmergencyAlerts(victimId)
        onStatusMessage("¡SOS ACTIVO + WHATSAPP Y SMS ENVIADOS CON GPS!")
    }

    // Efecto de temporizador de 40 segundos para confirmar estado de salud
    LaunchedEffect(show40sConfirmationDialog) {
        if (show40sConfirmationDialog) {
            while (countdownSeconds > 0 && show40sConfirmationDialog) {
                delay(1000L)
                countdownSeconds -= 1
            }
            if (show40sConfirmationDialog && countdownSeconds == 0) {
                show40sConfirmationDialog = false
                triggerFullEmergencySOS()
            }
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
                        text = "🚨 ALERTA SÍSMICA DETECTADA (SASMEX / CIRES) 🚨",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Origen: ${alert.epicenter}\nRevisión automática de seguridad activa.",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    // Botón para Donar / Modo Pro Desarrollador
                    IconButton(onClick = { showDonateDialog = true }) {
                        Icon(
                            imageVector = if (isProUser) Icons.Default.Star else Icons.Default.Coffee,
                            contentDescription = "Donar",
                            tint = if (isProUser) Color(0xFFFFD54F) else Color(0xFFFFB74D)
                        )
                    }
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
                .size(190.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable {
                    if (isSosActive) {
                        SosForegroundService.stopService(context)
                        isSosActive = false
                        onStatusMessage("Señal SOS desactivada.")
                    } else {
                        triggerFullEmergencySOS()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = "SOS Icon",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSosActive) "SOS ACTIVO\nTOCAR PARA DETENER" else "TRANSMITIR\nSOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Controles de Hardware, Batería y Contactos
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
                        containerColor = if (isBluetoothEnabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBluetoothEnabled) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBluetoothEnabled) "Bluetooth Activo" else "Bluetooth OFF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // GPS Status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocationEnabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLocationEnabled) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLocationEnabled) "GPS Listo" else "GPS Apagado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Seccion de Contactos de Emergencia (WhatsApp & SMS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContactPhone,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Contactos de Emergencia (WhatsApp / SMS)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        IconButton(
                            onClick = { showAddContactDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir", tint = Color(0xFF25D366))
                        }
                    }

                    if (savedContacts.isEmpty()) {
                        Text(
                            text = "Añade números de familiares para enviarles WhatsApp + SMS con tu GPS al temblar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            savedContacts.forEachIndexed { index, contact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${contact.name} (${contact.phone})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            contactsManager.removeContact(index)
                                            savedContacts = contactsManager.getContacts()
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Exención de Optimización de Batería (SE MUESTRA ÚNICAMENTE SI NO SE HA CONCEDIDO)
            if (!isBatteryExempt) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
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
                            Icon(imageVector = Icons.Default.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Restricción de Batería Detectada",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Otorga el permiso para que el SOS no se apague en segundo plano.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Button(
                            onClick = {
                                batteryHelper.requestIgnoreBatteryOptimizations()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVAR", fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

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
                            text = "🇲🇽 Red Alerta Sísmica CIRES / SASMEX / USGS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Temporizador 40s + Auto-SOS + GPS + WhatsApp",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Button(
                        onClick = {
                            sasmexClient.simulateTestAlert {
                                onStatusMessage("¡SIMULACRO ACTIVADO! VERIFICA EL TIEMPO DE 40s")
                                countdownSeconds = 40
                                show40sConfirmationDialog = true
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

            // Banner de Publicidad Google AdMob (Se oculta si es PRO o durante emergencia)
            BannerAdView(
                isProUser = isProUser,
                isEmergencyActive = isSosActive || activeSasmexAlert != null || show40sConfirmationDialog
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Créditos de Desarrollador con BOTÓN SECRETO (3 Taps para activar/desactivar anuncios)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        secretTapCount += 1
                        if (secretTapCount >= 3) {
                            secretTapCount = 0
                            isProUser = !isProUser
                            onStatusMessage(if (isProUser) "🔑 ¡MODO DESARROLLADOR ACTIVADO! Anuncios removidos." else "🔑 Modo desarrollador desactivado. Anuncios visibles.")
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = if (isProUser) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isProUser) "Desarrollada por Brayan Jesús Oropeza Acuña (MODO DEV PRO)" else "Desarrollada por Brayan Jesús Oropeza Acuña",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isProUser) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
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

    // Modal para Apoyar Proyecto / Donar / Remover Anuncios
    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Coffee, contentDescription = null, tint = Color(0xFFFFB74D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("☕ Apoyar a OropSOS", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "OropSOS es un proyecto de socorro y protección civil sísmica. Apoya el desarrollo donando para un café o activa el Modo PRO para remover anuncios.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isProUser) "Estado: ⭐️ MODO PRO ACTIVO (SIN ANUNCIOS)" else "Estado: Versión Estándar (Con Banners)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isProUser) Color(0xFFFFD54F) else Color.Gray
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProUser = !isProUser
                        showDonateDialog = false
                        onStatusMessage(if (isProUser) "⭐️ MODO PRO ACTIVADO: Anuncios removidos." else "Modo estándar activado.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isProUser) Color.DarkGray else Color(0xFF2E7D32))
                ) {
                    Text(if (isProUser) "DESACTIVAR MODO PRO" else "ACTIVAR MODO PRO (REMOVER ANUNCIOS)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/oropsos")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignorar si no hay navegador
                        }
                    }
                ) {
                    Text("☕ DONAR PARA UN CAFÉ")
                }
            }
        )
    }

    // Modal para Agregar Contacto de Emergencia
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Añadir Contacto de Emergencia", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("Nombre del Familiar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("Número de Teléfono (Con LADA/WhatsApp)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                            contactsManager.addContact(newContactName, newContactPhone)
                            savedContacts = contactsManager.getContacts()
                            newContactName = ""
                            newContactPhone = ""
                            showAddContactDialog = false
                            onStatusMessage("Contacto de emergencia guardado.")
                        }
                    }
                ) {
                    Text("GUARDAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }

    // Modal de Confirmación de Seguridad de 40 Segundos ("Estoy Bien")
    if (show40sConfirmationDialog) {
        Dialog(
            onDismissRequest = { /* Bloquear cierre accidental */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(56.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🚨 ALERTA SÍSMICA SISMÓGRAFO 🚨",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Confirmación de Estado de Salud:\nSe transmitirá SOS + WhatsApp/SMS en:",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "$countdownSeconds seg",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF1744)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            show40sConfirmationDialog = false
                            sasmexClient.dismissAlert()
                            onStatusMessage("Has confirmado que estás bien. Alerta y SOS cancelados.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("¡ESTOY BIEN! CANCELAR SOS", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Si no respondes en $countdownSeconds segundos, OropSOS activará el beacon de rescate y notificará a tus familiares.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
