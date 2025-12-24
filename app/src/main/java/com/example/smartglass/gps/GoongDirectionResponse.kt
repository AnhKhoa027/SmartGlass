package com.example.smartglass.gps

import com.google.gson.annotations.SerializedName
data class GoongDirectionResponse(
    val status: String?,
    val routes: List<GoongRoute>?
)
data class GoongRoute(
    val legs: List<GoongLeg>?
)
data class GoongLeg(
    val distance: GoongValue?,
    val duration: GoongValue?,
    val steps: List<GoongStep>?
)
data class GoongStep(
    val distance: GoongValue?,
    val duration: GoongValue?,
    @SerializedName("html_instructions")
    val htmlInstructions: String?,
    @SerializedName("start_location")
    val startLocation: GoongRouteLocation?,
    @SerializedName("end_location")
    val endLocation: GoongRouteLocation?
)
data class GoongValue(
    val value: Int?,
    val text: String? = null
)
data class GoongRouteLocation(
    val lat: Double,
    val lng: Double
)