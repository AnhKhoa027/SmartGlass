package com.example.smartglass.DetectResponse

import com.example.smartglass.ObjectDetection.TrackedObject
import com.example.smartglass.TTSandSTT.VoiceResponder

class DetectionSpeaker(
    private val voiceResponder: VoiceResponder
) {
    private var lastSpeakTime = 0L
    private val speakInterval = 5000L

    fun speakDetections(trackedObjects: List<TrackedObject>, frameW: Int, frameH: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < speakInterval) return  // Chỉ đọc 1 lần / 5 giây

        if (trackedObjects.isEmpty()) return

        // Lấy vật gần nhất (dựa vào diện tích bounding box)
        val nearestObject = trackedObjects.maxByOrNull { obj ->
            val boxW = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
            val boxH = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
            boxW * boxH
        } ?: return

        // Chỉ đọc nếu vật rất gần hoặc ở trước mặt
        val boxW = (nearestObject.smoothBox.x2 - nearestObject.smoothBox.x1) * frameW
        val boxH = (nearestObject.smoothBox.y2 - nearestObject.smoothBox.y1) * frameH
        val area = boxW * boxH
        val areaRatio = area / (frameW * frameH).toFloat()

        val isVeryClose = areaRatio > 0.20f
        val isCenter = nearestObject.direction?.lowercase() == "center"

        if (!(isVeryClose || isCenter)) return  // Không đọc nếu không đạt điều kiện

        val direction = nearestObject.direction ?: "trước mặt"
        val label = nearestObject.box.clsName ?: "vật không rõ"
        val status = nearestObject.status ?: "không rõ trạng thái"

        val message = "Ở $direction có $label rất gần, đang $status"
        voiceResponder.speak(message)

        lastSpeakTime = now  // Cập nhật thời gian lần đọc cuối
    }

    fun stop() {
        lastSpeakTime = 0L
    }
}
