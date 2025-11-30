package com.example.smartglass.gps

import MapboxResponse
import MapboxStep
import android.util.Log

/**
 * Parser các bước chỉ đường từ MapboxResponse thành List<MapboxStep>
 */
object MapBoxStepsParser {

    private val TAG = "MapBoxStepsParser"

    /**
     * Chuyển MapboxResponse thành danh sách MapboxStep
     */
    fun parseSteps(response: MapboxResponse?): List<MapboxStep> {
        if (response == null) return emptyList()
        val steps = mutableListOf<MapboxStep>()
        try {
            response.routes.forEach { route ->
                route.legs.forEach { leg ->
                    steps.addAll(leg.steps)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi parse Mapbox steps: ${e.message}")
        }
        Log.d("STEP_DEBUG", "Tổng số steps: ${steps.size}")

        for (s in steps) {
            Log.d("STEP_INSTRUCTION", s.maneuver.instruction)
        }
        return steps
    }
}
