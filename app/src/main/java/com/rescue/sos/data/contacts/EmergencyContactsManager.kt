package com.rescue.sos.data.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
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
        current.add(EmergencyContact(name.trim(), phone.trim()))
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
        if (contacts.isEmpty()) return

        val gpsCoords = locationHelper.getLastKnownLocation()
        val mapsUrl = if (gpsCoords != "SIN_GPS" && gpsCoords.contains(",")) {
            val parts = gpsCoords.split(",")
            "https://maps.google.com/?q=${parts[0].trim()},${parts[1].trim()}"
        } else {
            "Ubicación GPS no disponible"
        }

        val messageText = "🚨 ALERTA SOS OROPSOS: $victimId ha activado una señal de auxilio tras evento sísmico. Ubicación en vivo: $mapsUrl"

        // 1. Envío de SMS Automático en segundo plano
        try {
            val smsManager = SmsManager.getDefault()
            contacts.forEach { contact ->
                smsManager.sendTextMessage(contact.phone, null, messageText, null, null)
                Log.d(TAG, "SMS de emergencia enviado a ${contact.name} (${contact.phone})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando SMS automáticos", e)
        }

        // 2. Envío de WhatsApp a primer contacto principal
        val firstPhone = contacts.firstOrNull()?.phone
        if (!firstPhone.isNullOrBlank()) {
            try {
                val cleanPhone = firstPhone.replace(" ", "").replace("-", "").replace("+", "")
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
