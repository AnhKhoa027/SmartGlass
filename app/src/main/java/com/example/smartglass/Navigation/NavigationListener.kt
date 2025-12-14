// File: Navigation/NavigationListener.kt (CẬP NHẬT)
package com.example.smartglass.Navigation

import android.location.Location

interface NavigationListener {
    fun onRouteFound(distance: String, duration: String, initialDirection: String)
    fun onRouteError(message: String)
    fun onSpeakInstruction(instruction: String)
    fun onDestinationReached(destination: String)
    fun onStartLocationUpdatesRequested()
    fun onGpsSettingsRequired()
    fun onLocationPermissionRequired()
    fun onShowToast(message: String)
    fun onLocationUpdate(location: Location)
}