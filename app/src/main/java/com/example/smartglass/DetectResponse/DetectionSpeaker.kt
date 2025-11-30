package com.example.smartglass.DetectResponse

import com.example.smartglass.ObjectDetection.TrackedObject
import com.example.smartglass.TTSandSTT.VoiceResponder
import kotlin.math.roundToInt

class DetectionSpeaker(
    private val voiceResponder: VoiceResponder
) {
    private var lastSpeakTime = 0L
    private val speakInterval = 5000L // 5 giây

    fun speakDetections(
        trackedObjects: List<TrackedObject>,
        frameW: Int,
        frameH: Int,
        sensorDistanceMm: Int = 0 // sensor distance
    ) {
        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < speakInterval) return

        // Sensor ưu tiên
        if (sensorDistanceMm >= 10) { // Sensor hợp lệ, ≥ 10 mm
            val label = trackedObjects.firstOrNull()?.box?.clsName ?: "vật"
            val distanceM = (sensorDistanceMm / 1000.0 * 10).roundToInt() / 10.0
            val message = "Trước mặt có $label cách khoảng $distanceM mét"
            voiceResponder.speak(message)
            lastSpeakTime = now
        }

        // Nếu sensor không hợp lệ, dùng camera
        if (trackedObjects.isNotEmpty()) {
            val nearestObject = trackedObjects.maxByOrNull { obj ->
                val boxW = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
                val boxH = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
                boxW * boxH
            } ?: return

            val boxW = (nearestObject.smoothBox.x2 - nearestObject.smoothBox.x1) * frameW
            val boxH = (nearestObject.smoothBox.y2 - nearestObject.smoothBox.y1) * frameH
            val area = boxW * boxH
            val areaRatio = area / (frameW * frameH).toFloat()

            val isVeryClose = areaRatio > 0.20f
            val isCenter = nearestObject.direction?.lowercase() == "center"

            if (isVeryClose || isCenter) {
                val direction = nearestObject.direction ?: "trước mặt"
                val label = nearestObject.box.clsName ?: "vật không rõ"
                val status = nearestObject.status ?: "không rõ trạng thái"
                val message = "Ở $direction có $label rất gần, đang $status"
                voiceResponder.speak(message)
                lastSpeakTime = now
            }
        }
    }

    fun stop() {
        lastSpeakTime = 0L
    }
}
