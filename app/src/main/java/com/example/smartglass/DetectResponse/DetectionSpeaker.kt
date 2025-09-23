package com.example.smartglass.DetectResponse

import android.content.Context
import com.example.smartglass.ObjectDetection.TrackedObject
import com.example.smartglass.TTSandSTT.VoiceResponder

class DetectionSpeaker(
    private val context: Context,
    private val voiceResponder: VoiceResponder
) {
    private var lastSpeakTime = 0L
    private val speakInterval = 2000L // 2 giây

    fun speakDetections(trackedObjects: List<TrackedObject>, frameW: Int, frameH: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < speakInterval || trackedObjects.isEmpty()) return

        val messages = trackedObjects.map {
            val bboxArr = floatArrayOf(it.smoothBox.x1, it.smoothBox.y1, it.smoothBox.x2, it.smoothBox.y2)
            val direction = it.direction ?: "không rõ"
            val boxW = (it.smoothBox.x2 - it.smoothBox.x1) * frameW
            val boxH = (it.smoothBox.y2 - it.smoothBox.y1) * frameH
            val area = boxW * boxH
            val distance = when {
                area > frameW * frameH * 0.2 -> "rất gần"
                area > frameW * frameH * 0.05 -> "gần"
                else -> "xa"
            }
            "Ở $direction có ${it.box.clsName} $distance, đang ${it.status}"
        }

        voiceResponder.speak(messages.joinToString(". "))
        lastSpeakTime = now
    }

    fun stop() {
        lastSpeakTime = 0L
    }
}