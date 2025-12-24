package com.example.smartglass.gps

import com.google.gson.annotations.SerializedName

data class GoongGeocodingResponse(
    val results: List<GeocodingResult>?,
    val status: String?
)

data class GeocodingResult(
    @SerializedName("formatted_address")
    val formattedAddress: String?,
    val geometry: GeocodingGeometry
)

data class GeocodingGeometry(
    val location: GoongGeocoingLocation
)

data class GoongGeocoingLocation(
    val lat: Double, // Vĩ độ (Latitude)
    val lng: Double  // Kinh độ (Longitude)
)