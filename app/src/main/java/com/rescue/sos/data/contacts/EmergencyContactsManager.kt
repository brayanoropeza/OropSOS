package com.rescue.sos.data.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.rescue.sos.data.location.LocationHelper

data class EmergencyContact(
    val name: String,
    val phone: String
)

class EmergencyContactsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("OropSOS_Contacts", Context.MODE_PRIVATE)
    private val locationHelper = LocationHelper(context)

    companion object {
        private const val TAG = "EmergencyContacts"
        private const val KEY_CONTACT_COUNT = "contact_count"
    }

    fun getContacts(): List<EmergencyContact> {
        val count = prefs.getInt(KEY_CONTACT_COUNT, 0)
        val list = mutableListOf<EmergencyContact>()
        for (i in 0 until count) {
            val name = prefs.getString("contact_name_$i", "") ?: ""
            val phone = prefs.getString("contact_phone_$i", "") ?: ""
            if (name.isNotBlank() && phone.isNotBlank()) {
                list.add(EmergencyContact(name, phone))
            }
        }
        return list
    }

    fun saveContacts(contacts: List<EmergencyContact>) {
        val editor = prefs.edit()
        editor.putInt(KEY_CONTACT_COUNT, contacts.size)
        contacts.forEachIndexed { index, contact ->
            editor.putString("contact_name_$index", contact.name)
            editor.putString("contact_phone_$index", contact.phone)
        }
        editor.apply()
    }

    fun addContact(name: String, phone: String): Boolean {
        val current = getContacts().toMutableList()
        if (current.size >= 5) return false

        // Limpiar y formatear número telefónico
        var formattedPhone = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (!formattedPhone.startsWith("+") && formattedPhone.length == 10) {
            // Si tiene 10 dígitos (México), anteponer +52 para WhatsApp/SMS internacionales
            formattedPhone = "+52$formattedPhone"
        }

        current.add(EmergencyContact(name.trim(), formattedPhone))
        saveContacts(current)
        return true
    }

    fun removeContact(index: Int) {
        val current = getContacts().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveContacts(current)
        }
    }

    fun dispatchEmergencyAlerts(victimId: String) {
        val contacts = getContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No hay contactos de emergencia guardados para despachar SMS/WhatsApp.")
            return
        }

        val gpsCoords = locationHelper.getLastKnownLocation()
        val mapsUrl = if (gpsCoords != "SIN_GPS" && gpsCoords.contains(",")) {
            val parts = gpsCoords.split(",")
            "https://maps.google.com/?q=${parts[0].trim()},${parts[1].trim()}"
        } else {
            "Ubicación GPS no disponible"
        }

        val messageText = "🚨 ALERTA SOS SÍSMICO OROPSOS: La persona ($victimId) ha activado auxilio tras sismo. Ubicación GPS en vivo: $mapsUrl"

        // 1. Envío de SMS Automático en segundo plano para Android 12/14/15+
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                contacts.forEach { contact ->
                    try {
                        val parts = smsManager.divideMessage(messageText)
                        smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null)
                        Log.d(TAG, "SMS de emergencia enviado con éxito a ${contact.name} (${contact.phone})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enviando SMS individual a ${contact.phone}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error crítico en gestor de SMS", e)
            }
        } else {
            Log.e(TAG, "Permiso SEND_SMS no concedido por el usuario en Android.")
        }

        // 2. Despacho de WhatsApp con formato internacional
        val firstContact = contacts.firstOrNull()
        if (firstContact != null && firstContact.phone.isNotBlank()) {
            try {
                var cleanPhone = firstContact.phone.replace("+", "").replace(" ", "").replace("-", "")
                if (cleanPhone.length == 10) {
                    cleanPhone = "52$cleanPhone"
                }

                val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(messageText)}")
                val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage("com.whatsapp")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(waIntent)
                } catch (e: Exception) {
                    val genericWaIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(genericWaIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error lanzando intent de WhatsApp", e)
            }
        }
    }
}
