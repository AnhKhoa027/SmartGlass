package com.example.smartglass.gps

import com.google.gson.annotations.SerializedName

data class GoongGeocodingResponse(
    // Goong Geocoding chứa một mảng kết quả
    val results: List<GeocodingResult>?,
    val status: String?
)

data class GeocodingResult(
    @SerializedName("formatted_address")
    val formattedAddress: String?,
    // Chứa thông tin về tọa độ
    val geometry: GeocodingGeometry
)

data class GeocodingGeometry(
    // Chứa {lat, lng} (vĩ độ, kinh độ)
    val location: GoongGeocoingLocation
)

data class GoongGeocoingLocation(
    val lat: Double, // Vĩ độ (Latitude)
    val lng: Double  // Kinh độ (Longitude)
)