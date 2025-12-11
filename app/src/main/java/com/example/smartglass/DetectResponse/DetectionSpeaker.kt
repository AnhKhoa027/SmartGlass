//package com.example.smartglass.DetectResponse
//
//import com.example.smartglass.ObjectDetection.TrackedObject
//import com.example.smartglass.TTSandSTT.VoiceResponder
//import kotlin.math.roundToInt
//
//class DetectionSpeaker(
//    private val voiceResponder: VoiceResponder
//) {
//    private var lastSpeakTime = 0L
//    private val speakInterval = 5000L // 5 giây
//
//    fun speakDetections(
//        trackedObjects: List<TrackedObject>,
//        frameW: Int,
//        frameH: Int,
//        sensorDistanceMm: Int = 0 // sensor distance
//    ) {
//        val now = System.currentTimeMillis()
//        if (now - lastSpeakTime < speakInterval) return
//        if (trackedObjects.isEmpty()) return
//
//        // Tìm vật gần nhất theo camera (diện tích lớn nhất)
//        val nearestObject = trackedObjects.maxByOrNull { obj ->
//            val boxW = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
//            val boxH = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
//            boxW * boxH
//        } ?: return
//
//        val label = nearestObject.box.clsName ?: "vật"
//        val direction = nearestObject.direction ?: "trước mặt"
//        val status = nearestObject.status ?: "không rõ trạng thái"
//
//        when {
//            // Sensor hợp lệ và trong phạm vi 1.5m
//            sensorDistanceMm in 100..1500 -> {
//                val distanceM = (sensorDistanceMm / 1000.0 * 10).roundToInt() / 10.0
//                val message = "Ở $direction có $label cách khoảng $distanceM mét"
//                voiceResponder.speak(message)
//            }
//            // Sensor = 0 hoặc ngoài phạm vi => dùng camera
//            else -> {
//                val boxW = (nearestObject.smoothBox.x2 - nearestObject.smoothBox.x1) * frameW
//                val boxH = (nearestObject.smoothBox.y2 - nearestObject.smoothBox.y1) * frameH
//                val area = boxW * boxH
//                val areaRatio = area / (frameW * frameH).toFloat()
//                val isVeryClose = areaRatio > 0.20f
//                val isCenter = nearestObject.direction?.lowercase() == "center"
//
//                val message = if (sensorDistanceMm == 0) {
//                    "Ở $direction có $label"
//                } else if (isVeryClose || isCenter) {
//                    "Ở $direction có $label rất gần, đang $status"
//                } else {
//                    "Ở $direction có $label"
//                }
//                voiceResponder.speak(message)
//            }
//        }
//
//        lastSpeakTime = now
//    }
//
//
//
//    fun stop() {
//        lastSpeakTime = 0L
//    }
//}
package com.example.smartglass.DetectResponse

import com.example.smartglass.ObjectDetection.TrackedObject
import com.example.smartglass.TTSandSTT.VoiceResponder
import kotlin.math.roundToInt

class DetectionSpeaker(
    private val voiceResponder: VoiceResponder
) {
    private var lastSpeakTime = 0L
    private val speakInterval = 5000L // 5 giây

    // Giữ lại để sau này dùng (chưa xử lý trong logic)
    private var lastSensorDirX: String = "STAY"
    private var lastSensorDirY: String = "STAY"

    fun speakDetections(
        trackedObjects: List<TrackedObject>,
        frameW: Int,
        frameH: Int,
        sensorDistanceMm: Int = 0,
        sensorDirX: String = "STAY",
        sensorDirY: String = "STAY"
    ) {
        // Lưu lại giá trị sensor direction nhưng KHÔNG dùng
        lastSensorDirX = sensorDirX
        lastSensorDirY = sensorDirY

        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < speakInterval) return
        if (trackedObjects.isEmpty()) return

        // Tìm vật gần nhất theo diện tích
        val nearestObject = trackedObjects.maxByOrNull { obj ->
            val boxW = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
            val boxH = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
            boxW * boxH
        } ?: return

        val label = nearestObject.box.clsName ?: "vật"
        val direction = nearestObject.direction ?: "trước mặt"
        val status = nearestObject.status ?: "không rõ trạng thái"

        when {
            // Sensor hợp lệ và trong phạm vi 1.5m
            sensorDistanceMm in 100..1500 -> {
                val distanceM = (sensorDistanceMm / 1000.0 * 10).roundToInt() / 10.0
                val message = "Ở $direction có $label cách khoảng $distanceM mét"
                voiceResponder.speak(message)
            }

            // Sensor không dùng -> dựa vào camera
            else -> {
                val boxW = (nearestObject.smoothBox.x2 - nearestObject.smoothBox.x1) * frameW
                val boxH = (nearestObject.smoothBox.y2 - nearestObject.smoothBox.y1) * frameH
                val area = boxW * boxH
                val areaRatio = area / (frameW * frameH).toFloat()

                val isVeryClose = areaRatio > 0.20f
                val isCenter = nearestObject.direction?.lowercase() == "center"

                val message =
                    if (sensorDistanceMm == 0) {
                        "Ở $direction có $label"
                    } else if (isVeryClose || isCenter) {
                        "Ở $direction có $label rất gần, đang $status"
                    } else {
                        "Ở $direction có $label"
                    }

                voiceResponder.speak(message)
            }
        }

        lastSpeakTime = now
    }

    fun stop() {
        lastSpeakTime = 0L
    }
}
