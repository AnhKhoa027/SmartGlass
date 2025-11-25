//package com.example.smartglass.EmergencyCall
//
//
//import android.Manifest
//import android.annotation.SuppressLint
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.net.Uri
//import android.telephony.SmsManager
//import android.util.Log
//import androidx.core.app.ActivityCompat
//import com.example.smartglass.gps.LocationHelper
//
//class EmergencyHelper(private val context: Context) {
//
//    @SuppressLint("MissingPermission")
//    fun triggerEmergency(callCount: Int = 1) {
//        val contacts = EmergencyContactsManager(context).getContacts()
//        if (contacts.isEmpty()) {
//            Log.e("EmergencyHelper", "Không có số liên hệ khẩn cấp nào được lưu.")
//            return
//        }
//
//        val locationHelper = LocationHelper(context)
//        locationHelper.getCurrentLocation { loc ->
//            val message = if (loc != null) {
//                " Tôi tên là:.... Tôi là người Khiếm Thị. Hiện tại tôi đang gặp nguy hiểm. Hãy giúp tôi! Vị trí hiện tại: https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
//            } else {
//                " Tôi tên là:.... Tôi là người Khiếm Thị. Hiện tại tôi đang gặp nguy hiểm. Hãy giúp tôi! Không xác định được vị trí hiện tại."
//            }
//
//            Log.d("EmergencyHelper", "Gửi tin nhắn khẩn cấp: $message")
//
//            var count = 0
//            for (phone in contacts) {
//                if (count >= callCount) break
//                sendSMS(phone, message)
//                makeCall(phone)
//                count++
//            }
//        }
//    }
//
//    private fun sendSMS(phone: String, message: String) {
//        try {
//            val smsManager = SmsManager.getDefault()
//            smsManager.sendTextMessage(phone, null, message, null, null)
//            Log.d("EmergencyHelper", "✅ SMS đã gửi đến $phone")
//        } catch (e: Exception) {
//            Log.e("EmergencyHelper", "❌ Lỗi gửi SMS: ${e.message}")
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun makeCall(phone: String) {
//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
//            Log.e("EmergencyHelper", "❌ Chưa có quyền CALL_PHONE.")
//            return
//        }
//        val intent = Intent(Intent.ACTION_CALL)
//        intent.data = Uri.parse("tel:$phone")
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        context.startActivity(intent)
//        Log.d("EmergencyHelper", "📞 Gọi đến $phone")
//    }
//}
