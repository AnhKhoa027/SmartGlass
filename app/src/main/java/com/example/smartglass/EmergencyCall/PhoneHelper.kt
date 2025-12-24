package com.example.smartglass.EmergencyCall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object PhoneHelper {

    fun callPhone(context: Context, number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = Uri.parse("tel:$number")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PhoneHelper", "Lỗi gọi điện: ${e.message}")
        }
    }
}
