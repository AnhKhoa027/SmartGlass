package com.example.smartglass.EmergencyCall

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import com.example.smartglass.gps.LocationHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class EmergencyManager(private val context: Context) {

    // Thứ tự danh bạ khẩn cấp — bạn nên lưu / quản lý qua SettingsManager hoặc DB
    var emergencyContacts: List<String> = listOf("0763538820")

    // Thời gian chờ (ms) để quyết định "không bắt máy" và chuyển sang số kế tiếp
    private val callTimeoutMs = 15_000L

    private val client = OkHttpClient.Builder()
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Trigger chính (timesPressed: số lần nhấn nút)
    fun triggerEmergency(timesPressed: Int) {
        Log.d("Emergency", "triggerEmergency times=$timesPressed")
        getLocationText { locationText ->
            val message = buildMessage(locationText)
            // gửi SMS / API
            sendMessageToAllContacts(message)
            // thu âm ngắn (tuỳ chọn) - return file path or null
            // val audioFile = recordQuickVoice(8000) // optional
            // gửi audio qua API nếu muốn (mình để hàm mẫu bên dưới)
            // khởi chạy chuỗi gọi: gọi lần lượt `timesPressed` số (hoặc ít hơn nếu danh sách ngắn)
            startCallSequence(timesPressed.coerceAtLeast(1))
        }
    }

    private fun buildMessage(locationText: String): String {
        return "🚨 KHẨN CẤP: Tôi đang gặp nguy hiểm. Vị trí hiện tại: $locationText"
    }

    // Lấy vị trí sử dụng LocationHelper (project của bạn)
    private fun getLocationText(callback: (String) -> Unit) {
        val locationHelper = LocationHelper(context)
        locationHelper.getCurrentLocation { loc ->
            if (loc != null) {
                // Nên re-use geocoder hiện có, nhưng để đơn giản trả về lat,lng
                callback("https://maps.google.com/?q=${loc.latitude},${loc.longitude} (${loc.latitude},${loc.longitude})")
            } else {
                callback("Vị trí không xác định")
            }
        }
    }

    // Gửi SMS truyền thống cho tất cả contacts (song song)
    fun sendMessageToAllContacts(message: String) {
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
        } catch (e: SecurityException) {
            Log.e("Emergency", "Missing SEND_SMS permission: ${e.message}")
        } catch (e: Exception) {
            Log.e("Emergency", "SMS send failed: ${e.message}")
        }
    }

    // --- Nếu bạn muốn gửi qua API (ví dụ server/3rd party) ---
    fun sendToApi(endpoint: String, phone: String, message: String, audioFile: File? = null) {
        val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        formBuilder.addFormDataPart("phone", phone)
        formBuilder.addFormDataPart("message", message)
        if (audioFile != null && audioFile.exists()) {
            formBuilder.addFormDataPart("audio", audioFile.name,
                RequestBody.create("audio/*".toMediaTypeOrNull(), audioFile))
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(formBuilder.build())
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) { Log.e("EmergencyAPI","fail: ${e.message}") }
            override fun onResponse(call: Call, response: Response) { Log.d("EmergencyAPI","resp: ${response.code}") }
        })
    }

    // --- Gọi theo thứ tự với timeout ---
    private fun startCallSequence(limit: Int) {
        val maxCalls = limit.coerceAtMost(emergencyContacts.size)
        Log.d("Emergency", "startCallSequence maxCalls=$maxCalls")
        val handler = Handler(Looper.getMainLooper())

        var currentIndex = 0

        fun callNext() {
            if (currentIndex >= maxCalls) {
                Log.d("Emergency", "Finish call sequence")
                return
            }
            val phone = emergencyContacts[currentIndex]
            // thực hiện cuộc gọi
            PhoneHelper.callPhone(context, phone)
            Log.d("Emergency", "Calling $phone (index $currentIndex)")

            // Sau callTimeoutMs, tiếp tục sang số kế tiếp nếu vẫn chưa kết thúc
            handler.postDelayed({
                currentIndex++
                callNext()
            }, callTimeoutMs)
        }

        callNext()
    }
    // --- (Optional) Ghi âm nhanh rồi trả file ---
    // Implement tuỳ theo yêu cầu; cần REQUEST RECORD_AUDIO + storage permission nếu lưu.
}
