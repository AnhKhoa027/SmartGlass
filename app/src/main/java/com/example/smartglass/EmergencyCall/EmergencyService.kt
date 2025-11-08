package com.example.smartglass.EmergencyCall

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class EmergencyService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "📞 Đang xử lý tình huống khẩn cấp...", Toast.LENGTH_LONG).show()
        Log.d("EmergencyService", "Đang thực hiện hành động khẩn cấp...")
        // 👉 Ví dụ: gọi điện, gửi SMS, gửi tọa độ GPS, ...
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}