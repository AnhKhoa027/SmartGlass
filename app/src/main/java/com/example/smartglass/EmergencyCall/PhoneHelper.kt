package com.example.smartglass.EmergencyCall

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

object PhoneHelper {
    fun callPhone(context: Context, phone: String) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("PhoneHelper", "CALL_PHONE permission not granted")
                Toast.makeText(context, "Chưa cấp quyền gọi điện", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phone")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PhoneHelper", "callPhone error: ${e.message}")
        }
    }
}
