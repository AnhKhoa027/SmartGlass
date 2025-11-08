package com.example.smartglass.EmergencyCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private var lastPressTime: Long = 0
        private var pressCount = 0
        private const val THRESHOLD_MS = 150L  // Khoảng thời gian tối đa giữa 2 lần nhấn
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SCREEN_ON -> {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastPressTime <= THRESHOLD_MS) {
                    pressCount++
                } else {
                    pressCount = 1
                }
                lastPressTime = currentTime

                Log.d("PowerButtonReceiver", "Số lần nhấn: $pressCount")

                if (pressCount == 2) { // đổi thành 2 nếu muốn nhấn 2 lần
                    pressCount = 0
                    Toast.makeText(context, "🚨 Kích hoạt khẩn cấp!", Toast.LENGTH_LONG).show()
                    Log.d("PowerButtonReceiver", "🚨 Tính năng khẩn cấp được kích hoạt!")

                    // 👉 Gửi Intent mở Activity hoặc Service khẩn cấp ở đây
                    val i = Intent(context, EmergencyService::class.java)
                    context.startService(i)
                }
            }
        }
    }
}