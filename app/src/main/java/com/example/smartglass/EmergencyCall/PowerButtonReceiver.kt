package com.example.smartglass.EmergencyCall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PowerButtonReceiver : BroadcastReceiver() {

    private var lastClick = 0L
    private var count = 0

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_SCREEN_OFF == intent.action || Intent.ACTION_SCREEN_ON == intent.action) {
            val now = System.currentTimeMillis()

            if (now - lastClick < 800) count++
            else count = 1

            lastClick = now

            if (count >= 3) {
                Log.d("PowerButtonReceiver", "Kích hoạt khẩn cấp")
                EmergencyManager(context).triggerEmergency()
            }
        }
    }
}
