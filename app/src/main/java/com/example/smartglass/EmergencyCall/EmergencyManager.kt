package com.example.smartglass.EmergencyCall

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.example.smartglass.gps.LocationHelper
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper

class EmergencyManager(private val context: Context) {

    private val callTimeoutMs = 30_000L  // 30 giây mỗi cuộc gọi
    var emergencyContacts: List<String> = emptyList()

    fun triggerEmergency(timesPressed: Int = 1) {
        Log.d("Emergency", "triggerEmergency times=$timesPressed")
        loadContacts()  // Lấy danh bạ khẩn cấp hoặc danh bạ máy

        getLocationText { locationText ->
            val message = "KHẨN CẤP: Tôi tên là:.... Tôi là người Khiếm Thị. Hiện tại tôi đang gặp nguy hiểm. Hãy giúp tôi. Vị trí: $locationText"
            sendMessageToAllContacts(message)
            startCallSequence(timesPressed.coerceAtLeast(1))
        }
    }

    private fun loadContacts() {
        val contactsManager = EmergencyContactsManager(context)
        val emergency = contactsManager.getContacts()
        emergencyContacts = if (emergency.isNotEmpty()) {
            emergency
        } else {
            getPhoneContacts()
        }
    }

    private fun getPhoneContacts(): List<String> {
        val phones = mutableListOf<String>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    phones.add(number)
                }
            }
        } catch (e: Exception) {
            Log.e("Emergency", "Error fetching contacts: ${e.message}")
        }
        return phones
    }

    private fun getLocationText(callback: (String) -> Unit) {
        val locationHelper = LocationHelper(context)
        locationHelper.getCurrentLocation { loc ->
            if (loc != null) {
                callback("https://maps.google.com/?q=${loc.latitude},${loc.longitude} (${loc.latitude},${loc.longitude})")
            } else {
                callback("Vị trí không xác định")
            }
        }
    }

    private fun sendMessageToAllContacts(message: String) {
        for (phone in emergencyContacts) {
            sendSms(phone, message)
        }
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            Log.d("Emergency", "SMS sent to $phone")
        } catch (e: Exception) {
            Log.e("Emergency", "SMS send failed: ${e.message}")
        }
    }

    private fun startCallSequence(limit: Int) {
        val maxCalls = limit.coerceAtMost(emergencyContacts.size)
        val handler = Handler(Looper.getMainLooper())
        var currentIndex = 0

        fun callNext() {
            if (currentIndex >= maxCalls) {
                Log.d("Emergency", "Finish call sequence")
                return
            }
            val phone = emergencyContacts[currentIndex]
            PhoneHelper.callPhone(context, phone)
            Log.d("Emergency", "Calling $phone (index $currentIndex)")

            handler.postDelayed({
                currentIndex++
                callNext()
            }, callTimeoutMs)
        }

        callNext()
    }
}
