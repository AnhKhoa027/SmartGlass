package com.example.smartglass.HomeAction

import android.util.Log
import com.google.firebase.database.*

class FirebaseHelperOnDemand {

    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun fetchCameraIp(onIpFetched: (String?) -> Unit) {
        val ipRef = database.child("esp32cam").child("ip")

        ipRef.get()
            .addOnSuccessListener { snapshot ->
                val ip = snapshot.getValue(String::class.java)
                if (!ip.isNullOrEmpty()) {
                    Log.d("FirebaseOnDemand", "IP lấy từ Firebase: $ip")
                    onIpFetched(ip)
                } else {
                    Log.w("FirebaseOnDemand", "IP chưa có trong Firebase")
                    onIpFetched(null)
                }
            }
            .addOnFailureListener { error ->
                Log.e("FirebaseOnDemand", "Lỗi khi lấy IP: ${error.message}")
                onIpFetched(null)
            }
    }
}
