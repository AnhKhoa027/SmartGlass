package com.example.smartglass.EmergencyCall

import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import com.example.smartglass.gps.LocationHelper

class EmergencyManager(private val context: Context) {

    private var emergencyContacts: List<String> = emptyList()
    private val callTimeoutMs: Long = 25000 // 25 giây
    private val handler = Handler(Looper.getMainLooper())
    private var currentCallIndex = 0

    // Lấy tên chủ máy
    private fun getOwnerName(): String {
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.Profile.CONTENT_URI,
                arrayOf(ContactsContract.Profile.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.Profile.DISPLAY_NAME))
                } else "Chủ thiết bị"
            } ?: "Chủ thiết bị"
        } catch (_: Exception) {
            "Chủ thiết bị"
        }
    }

    // Lấy vị trí GPS
    private fun getLocationText(callback: (String) -> Unit) {
        LocationHelper(context).getCurrentLocation { loc ->
            if (loc != null) {
                callback("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            } else callback("Không xác định")
        }
    }

    // Load danh bạ khẩn cấp hoặc 5 số đầu tiên trong máy
    private fun loadContacts() {
        val saved = EmergencyContactsManager(context).getContacts()

        emergencyContacts =
            if (saved.isNotEmpty()) {
                saved.take(5)
            } else {
                getFirstFiveContacts()
            }

        Log.d("Emergency", "Danh sách khẩn cấp: $emergencyContacts")
    }

    private fun getFirstFiveContacts(): List<String> {
        val list = mutableListOf<String>()
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext() && list.size < 5) {
                val number = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                )
                val cleaned = number.filter { c -> c.isDigit() || c == '+' }
                if (cleaned.isNotBlank()) list.add(cleaned)
            }
        }
        return list
    }

    // Gửi SMS
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

    // Gọi tuần tự các số với timeout 25s, KHÔNG dùng PhoneStateListener
    private fun callNextContact() {
        if (currentCallIndex >= emergencyContacts.size) {
            Log.d("EmergencyCall", "Đã gọi xong toàn bộ danh sách")
            return
        }

        val phone = emergencyContacts[currentCallIndex]
        Log.d("EmergencyCall", "Gọi số: $phone")
        PhoneHelper.callPhone(context, phone)

        // Sau 25s gọi số tiếp theo
        handler.postDelayed({
            currentCallIndex++
            callNextContact()
        }, callTimeoutMs)
    }

    // TRIGGER khẩn cấp (SMS + Gọi)
    fun triggerEmergency() {
        loadContacts()
        currentCallIndex = 0
        val ownerName = getOwnerName()

        getLocationText { location ->
            val message = """KHẨN CẤP! Tôi tên là $ownerName, là người khiếm thị và đang gặp nguy hiểm.
            Vị trí hiện tại: $location
            Hãy gọi lại ngay!
            """.trimIndent()

            // Gửi SMS tới tất cả số
            sendMessageToAllContacts(message)

            // Bắt đầu gọi tuần tự
            callNextContact()
        }
    }

    // STOP emergency
    fun endEmergency() {
        handler.removeCallbacksAndMessages(null)
        currentCallIndex = 0
        Log.d("Emergency", "Đã dừng chế độ khẩn cấp")
    }
}
