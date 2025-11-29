// Zoom
//package com.example.smartglass.EmergencyCall
//
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.database.Cursor
//import android.net.Uri
//import android.provider.ContactsContract
//import android.telephony.SmsManager
//import android.util.Log
//import android.widget.Toast
//import com.example.smartglass.gps.LocationHelper
//import java.util.concurrent.TimeUnit
//import android.os.Handler
//import android.os.Looper
//import com.android.volley.toolbox.JsonObjectRequest
//import com.android.volley.toolbox.Volley
//import com.android.volley.Request
//class EmergencyManager(private val context: Context) {
//
//    private var currentIndex = 0
//    private var callListener: PhoneCallListener? = null
//    private var maxCalls = 0
//    private val fixedZoomLink = "https://us05web.zoom.us/j/6705249145?pwd=RZK8qSxnDY89ZabzcV6KMFODVDQUJq.1&omn=84058839368?uname=Anonymous"
//
//    var emergencyContacts: List<String> = emptyList()
//    private val callTimeoutMs: Long = TimeUnit.SECONDS.toMillis(15)
//
////    fun triggerEmergency(timesPressed: Int = 1) {
////        Log.d("Emergency", "triggerEmergency times=$timesPressed")
////        loadContacts()  // Lấy danh bạ khẩn cấp hoặc danh bạ máy
//////        getLocationText { locationText ->
//////            val message = "KHẨN CẤP: Tôi tên là:.... Tôi là người Khiếm Thị. Hiện tại tôi đang gặp nguy hiểm. Hãy giúp tôi. Vị trí: $locationText"
//////            sendMessageToAllContacts(message)
////            startCallSequence(timesPressed.coerceAtLeast(1))
//////        }
////    }
//
// Lấy tên chủ điện thoại từ Profile
//private fun getOwnerName(): String {
//    return try {
//        val cursor: Cursor? = context.contentResolver.query(
//            ContactsContract.Profile.CONTENT_URI,
//            arrayOf(ContactsContract.Profile.DISPLAY_NAME),
//            null, null, null
//        )
//        cursor?.use {
//            if (it.moveToFirst()) {
//                it.getString(it.getColumnIndexOrThrow(ContactsContract.Profile.DISPLAY_NAME))
//            } else {
//                "Chủ thiết bị"
//            }
//        } ?: "Chủ thiết bị"
//    } catch (e: Exception) {
//        "Chủ thiết bị"
//    }
//}
//
//    fun triggerEmergency() {
//        Log.d("Emergency", "BẮT ĐẦU CUỘC GỌI KHẨN CẤP qua Zoom")
//
//        loadContacts() // load danh bạ khẩn cấp
//        val ownerName = getOwnerName()
//
//        // Gửi SMS tới tất cả số khẩn cấp
//        getLocationText { locationText ->
//            val message = "KHẨN CẤP: Tôi là $ownerName là người khiếm thị. Hiện tại đang gặp nguy hiểm.\n" +
//                    "Vị trí: $locationText\n" +
//                    "Tham gia Zoom: $fixedZoomLink"
//            sendMessageToAllContacts(message)
//        }
//
//        openMeet(fixedZoomLink)
//        Log.d("Emergency", "Zoom link mở: $fixedZoomLink")
//    }
//
//    private fun openMeet(link: String) {
//        try {
//            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//            context.startActivity(intent)
//            Log.d("Emergency", "Mở Zoom: $link")
//        } catch (e: Exception) {
//            Log.e("Emergency", "Mở Zoom thất bại: ${e.message}")
//        }
//    }
//
////    fun triggerEmergency() {
////        Log.d("Emergency", "BẮT ĐẦU GỌI KHẨN CẤP QUA ZALO")
////
////        loadContacts()
////
////        // Gửi SMS như cũ
////        getLocationText { locationText ->
////            val message = "KHẨN CẤP: Tôi là người khiếm thị." +
////                    "Vị trí: $locationText" +
////                    "Tôi đã gọi nhóm Zalo khẩn cấp."
////            sendMessageToAllContacts(message)
////        }
////
////        // MỞ CUỘC GỌI NHÓM ZALO
////        val zaloGroupId = "https://zalo.me/g/xbvbaa156"   // ⚠ thay ID nhóm thật vào đây
////        startZaloGroupCall()
////    }
//
//    private fun showToast(msg: String) {
//        Handler(Looper.getMainLooper()).post {
//            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
//        }
//    }
//
//    fun endEmergency() {
//        // Dừng mọi cuộc gọi đang diễn ra
//        // Huỷ listener nếu có
//        callListener?.unregister()
//        // Reset trạng thái
//        currentIndex = 0
//    }
////    fun openZaloGroup(context: Context, groupId: String) {
////        try {
////            val url = "https://zalo.me/g/$groupId"   // LINK CÓ THẬT, ZALO HỖ TRỢ
////            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
////            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
////            context.startActivity(intent)
////        } catch (e: Exception) {
////            Log.e("Emergency", "Không mở được Zalo: ${e.message}")
////            Toast.makeText(context, "Không mở được Zalo", Toast.LENGTH_SHORT).show()
////        }
////    }
//
////    private fun startZaloGroupCall() {
////        val intent = context.packageManager.getLaunchIntentForPackage("com.zing.zalo")
////        if (intent != null) {
////            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
////            context.startActivity(intent)
////        } else {
////            Log.e("ZALO_CALL", "Không tìm thấy ứng dụng Zalo trên thiết bị")
////        }
////    }
//
//
//
//    private fun loadContacts() {
//        val contactsManager = EmergencyContactsManager(context)
//        val emergency = contactsManager.getContacts()
//
//        emergencyContacts = if (emergency.isNotEmpty()) {
//            Log.d("Emergency", "Sử dụng danh bạ khẩn cấp đã lưu: $emergency")
//            emergency
//        } else {
//            Log.d("Emergency", "Không có danh bạ khẩn cấp → dùng danh bạ máy")
//            getPhoneContacts()
//        }
//    }
//    private fun getPhoneContacts(): List<String> {
//        val phones = mutableListOf<String>()
//        try {
//            val cursor: Cursor? = context.contentResolver.query(
//                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
//                null, null, null
//            )
//            cursor?.use {
//                while (it.moveToNext()) {
//                    val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
//                    phones.add(number)
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("Emergency", "Error fetching contacts: ${e.message}")
//        }
//        return phones
//    }
//
//    private fun getLocationText(callback: (String) -> Unit) {
//        val locationHelper = LocationHelper(context)
//        locationHelper.getCurrentLocation { loc ->
//            if (loc != null) {
//                callback("https://maps.google.com/?q=${loc.latitude},${loc.longitude} (${loc.latitude},${loc.longitude})")
//            } else {
//                callback("Vị trí không xác định")
//            }
//        }
//    }
//
//    private fun sendMessageToAllContacts(message: String) {
//        for (phone in emergencyContacts) {
//            sendSms(phone, message)
//        }
//    }
//
//    private fun sendSms(phone: String, message: String) {
//        try {
//            val smsManager = SmsManager.getDefault()
//            val parts = smsManager.divideMessage(message)
//            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
//            Log.d("Emergency", "SMS sent to $phone")
//        } catch (e: Exception) {
//            Log.e("Emergency", "SMS send failed: ${e.message}")
//        }
//    }
//
//    private fun startCallSequence(limit: Int) {
//        val handler = Handler(Looper.getMainLooper())
//        var currentIndex = 0
//
//        fun callNext() {
//            if (currentIndex >= limit) return
//
//            val phone = emergencyContacts[currentIndex]
//            PhoneHelper.callPhone(context, phone)
//
//            handler.postDelayed({
//                currentIndex++
//                callNext()
//            }, callTimeoutMs)
//        }
//        callNext()
//    }
//}
//
//
//


//Call Tuan TU
package com.example.smartglass.EmergencyCall

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.smartglass.gps.LocationHelper

class EmergencyManager(private val context: Context) {

    private var emergencyContacts: List<String> = emptyList()
    private var callListener: PhoneCallListener? = null
    private var telephonyManager: TelephonyManager? = null

    init {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }


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

    // Load danh bạ khẩn cấp → hoặc lấy 5 số đầu tiên trong máy
    private fun loadContacts() {
        val saved = EmergencyContactsManager(context).getContacts()

        emergencyContacts =
            if (saved.isNotEmpty()) {
                saved.take(4)
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
            while (it.moveToNext() && list.size < 4) {
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
    // GỌI TUẦN TỰ
    private fun callAllContactsSequentially() {
        if (emergencyContacts.isEmpty()) {
            Log.e("EmergencyCall", "Không có số để gọi")
            return
        }

        val iterator = emergencyContacts.iterator()

        fun callNext() {
            if (!iterator.hasNext()) {
                Log.d("EmergencyCall", "Đã gọi xong toàn bộ danh sách")
                stopCallListener()
                return
            }

            val phone = iterator.next()
            Log.d("EmergencyCall", "Gọi số: $phone")
            callListener?.startTimeout()
            PhoneHelper.callPhone(context, phone)
        }

        callListener = PhoneCallListener(context) {
            Log.d("EmergencyCall", "Cuộc gọi kết thúc → gọi số tiếp theo")
            callNext()
        }

        callListener?.register()

        callNext()
    }

    private fun stopCallListener() {
        callListener?.unregister()
        callListener = null
    }

    // TRIGGER KHẨN CẤP (SMS + GỌI)
    fun triggerEmergency() {
        loadContacts()

        val ownerName = getOwnerName()

        getLocationText { location ->

            val message = """  KHẨN CẤP! Tôi tên là $ownerName là người khiếm thị và tôi đang gặp nguy hiểm.
            Vị trí hiện tại của tôi: $location
            Hãy gọi lại ngay!
            """.trimIndent()

            sendMessageToAllContacts(message)

            callAllContactsSequentially()
        }
    }

    // STOP
    fun endEmergency() {
        stopCallListener()
        Log.d("Emergency", "Đã dừng chế độ khẩn cấp")
    }
}



