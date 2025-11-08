package com.example.smartglass.EmergencyCall

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import android.widget.Toast

object PhoneHelper {
    fun callPhone(context: Context, phone: String) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Không có quyền gọi — bạn nên yêu cầu quyền ở UI (MainActivity)
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
