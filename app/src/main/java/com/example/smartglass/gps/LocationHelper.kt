package com.example.smartglass.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.widget.Toast
import com.google.android.gms.location.*

class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(callback: (Location?) -> Unit) {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        callback(location)
                    } else {
                        // Nếu chưa có thì yêu cầu cập nhật mới
                        val locationRequest = LocationRequest.Builder(
                            Priority.PRIORITY_HIGH_ACCURACY, 2000L
                        ).setMaxUpdates(1).build()

                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            object : LocationCallback() {
                                override fun onLocationResult(result: LocationResult) {
                                    fusedLocationClient.removeLocationUpdates(this)
                                    callback(result.lastLocation)
                                }
                            },
                            context.mainLooper
                        )
                    }
                }
                .addOnFailureListener {
                    callback(null)
                }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Chưa cấp quyền GPS", Toast.LENGTH_SHORT).show()
            callback(null)
        }
    }
}
