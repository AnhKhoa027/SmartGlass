package com.example.smartglass.EmergencyCall

import android.content.Context

class EmergencyContactsManager(context: Context) {
    private val prefs = context.getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)

    fun getContacts(): List<String> {
        return prefs.getStringSet("contacts", emptySet())?.toList() ?: emptyList()
    }

    fun setContacts(numbers: List<String>) {
        prefs.edit().putStringSet("contacts", numbers.toSet()).apply()
    }

    fun addContact(number: String) {
        val current = getContacts().toMutableSet()
        current.add(number)
        prefs.edit().putStringSet("contacts", current).apply()
    }

    fun clearContacts() {
        prefs.edit().remove("contacts").apply()
    }
}
