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
        private const val THRESHOLD_MS = 400L  // 400ms giữa 2 lần nhấn
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_SCREEN_ON) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPressTime <= THRESHOLD_MS) pressCount++ else pressCount = 1
            lastPressTime = currentTime

            if (pressCount == 2) {
                pressCount = 0
                Toast.makeText(context, "Kích hoạt khẩn cấp!", Toast.LENGTH_LONG).show()
                Log.d("PowerButtonReceiver", "Tính năng khẩn cấp được kích hoạt!")

                val i = Intent(context, EmergencyService::class.java)
                context.startService(i)

                val emergencyManager = EmergencyManager(context)
                emergencyManager.triggerEmergency(1) // 1 là số lần gọi theo thứ tự
            }
        }
    }
}
