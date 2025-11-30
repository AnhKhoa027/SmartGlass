package com.example.smartglass.navigation

import MapboxStep
import android.location.Location


/**
 * Navigation engine - xử lý điều hướng theo từng bước
 */
object NavigationEngine {

    private var isActive = false
    private var steps = emptyList<MapboxStep>()
    private var currentIndex = 0
    private var lastAnnouncedThreshold: Int? = null
    private val announceThresholds = listOf(100, 50, 20) // m
    private lateinit var onAnnouncement: (String) -> Unit

    fun startNavigation(newSteps: List<MapboxStep>, announcementCallback: (String) -> Unit) {
        steps = newSteps
        onAnnouncement = announcementCallback
        isActive = steps.isNotEmpty()

        if (isActive) {
            onAnnouncement("Bắt đầu dẫn đường. Tổng ${steps.size} bước.")
            announceCurrentInstructionBrief()
        } else {
            onAnnouncement("Không có bước chỉ đường.")
        }
    }

    fun cancelRoute() {
        isActive = false
        steps = emptyList()
        currentIndex = 0
        lastAnnouncedThreshold = null
        onAnnouncement("Đã hủy điều hướng.")
    }

    fun pause() { isActive = false }

    fun resume() {
        if (steps.isNotEmpty()) isActive = true
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        if (!isActive || currentIndex >= steps.size) return

        val step = steps[currentIndex]
        val maneuverLat = step.maneuver.location[1]
        val maneuverLon = step.maneuver.location[0]
        val dist = distanceInMeters(lat, lon, maneuverLat, maneuverLon)

        when {
            dist <= announceThresholds.last() -> {
                onAnnouncement("Đến điểm rẽ: ${step.maneuver.instruction}")
                moveToNextStep()
            }
            else -> {
                val toAnnounce = announceThresholds
                    .filter { dist <= it }
                    .minOrNull()
                if (toAnnounce != null && toAnnounce != lastAnnouncedThreshold) {
                    val msg = when (toAnnounce) {
                        100 -> "Còn khoảng ${dist.toInt()} mét đến: ${step.maneuver.instruction}"
                        50  -> "Còn ${dist.toInt()} mét nữa tới: ${step.maneuver.instruction}"
                        20  -> "Sắp tới, còn ${dist.toInt()} mét: ${step.maneuver.instruction}"
                        else -> "Còn ${dist.toInt()} mét: ${step.maneuver.instruction}"
                    }
                    onAnnouncement(msg)
                    lastAnnouncedThreshold = toAnnounce
                }
            }
        }
    }

    private fun moveToNextStep() {
        currentIndex++
        lastAnnouncedThreshold = null
        if (currentIndex < steps.size) {
            announceCurrentInstructionBrief()
        } else {
            onAnnouncement("Bạn đã đến đích.")
            isActive = false
        }
    }

    private fun announceCurrentInstructionBrief() {
        val step = steps.getOrNull(currentIndex) ?: return
        onAnnouncement("Bước tiếp theo: ${step.maneuver.instruction}")
    }

    private fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val a = Location("a").apply { latitude = lat1; longitude = lon1 }
        val b = Location("b").apply { latitude = lat2; longitude = lon2 }
        return a.distanceTo(b)
    }
}
