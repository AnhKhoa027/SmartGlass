package com.example.smartglass.DetectResponse

import android.util.Log
import com.example.smartglass.ObjectDetection.DetectSource
import com.example.smartglass.ObjectDetection.TrackedObject
import com.example.smartglass.TTSandSTT.VoiceResponder
import kotlin.math.roundToInt
import com.example.smartglass.DetectResponse.LabelTranslator

class DetectionSpeaker(
    private val voiceResponder: VoiceResponder
) {

    private var lastSpeakTime = 0L
    private val speakInterval = 4000L // 5 giây

    private var lastSensorDirX: String = "STAY"
    private var lastSensorDirY: String = "STAY"

    private data class SpokenKey(
        val objectId: Int,
        val label: String,
        val source: DetectSource
    )

    private var lastSpokenKey: SpokenKey? = null

    // --------------------------
    // PHÂN TÍCH HÀNH ĐỘNG CỦA BẠN
    // --------------------------
    private fun getUserMovement(): String {
        return when {
            lastSensorDirY == "FORWARD" -> "đi tới"
            lastSensorDirY == "BACK"    -> "đi lùi"
            lastSensorDirX == "LEFT"    -> "quẹo trái"
            lastSensorDirX == "RIGHT"   -> "quẹo phải"
            else -> "đứng yên"
        }
    }

    // --------------------------
    // PHÂN TÍCH HÀNH ĐỘNG CỦA VẬT
    // --------------------------
    private fun getObjectMovement(status: String): String {
        val s = status.lowercase()
        return when {
            "approach" in s || "towards" in s -> "đang tiến lại gần bạn"
            "away" in s                       -> "đang ra xa bạn"
            "moving" in s                     -> "đang di chuyển"
            else                              -> "đứng yên"
        }
    }

    // --------------------------
    // QUAN HỆ NGƯỜI – VẬT
    // --------------------------
    private fun getRelativeMovement(userMove: String, objectMove: String): String {
        if (userMove == "đi tới" && objectMove == "đang tiến lại gần bạn")
            return "hai bên đang tiến lại gần nhau"

        if (userMove == "đi tới" && objectMove == "đứng yên")
            return "bạn đang tiến lại gần vật"

        if (userMove == "đứng yên" && objectMove == "đang tiến lại gần bạn")
            return "vật đang tiến lại gần bạn"

        return objectMove
    }

    fun speakDetections(
        trackedObjects: List<TrackedObject>,
        frameW: Int,
        frameH: Int,
        sensorDistanceMm: Int = 0,
        sensorDirX: String = "STAY",
        sensorDirY: String = "STAY"
    ) {
        lastSensorDirX = sensorDirX
        lastSensorDirY = sensorDirY

        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < speakInterval) return
        if (trackedObjects.isEmpty()) return

        // --------------------------
        // ƯU TIÊN CHỌN VẬT Ở CENTER
        // --------------------------
        val centerObjects = trackedObjects.filter {
            it.direction?.trim()?.equals("center", ignoreCase = true) == true
        }
        Log.d("SENSOR_DEBUG", "Distance Ban đầu = $sensorDistanceMm")

        val nearestObject =
            if (centerObjects.isNotEmpty()) {
                centerObjects.maxByOrNull { obj ->
                    val w = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
                    val h = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
                    w * h
                }
            } else {
                trackedObjects.maxByOrNull { obj ->
                    val w = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
                    val h = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
                    w * h
                }
            } ?: return

        val box = nearestObject.smoothBox

        val key = SpokenKey(
            objectId = nearestObject.id,
            label = LabelTranslator.toVietnamese(box.clsName),
            source = box.source
        )

        if (lastSpokenKey == key) {
            Log.d("TTS_SKIP", "Skip same key $key")
            return
        }

        val direction = nearestObject.direction ?: "trước mặt"
        val status = nearestObject.status ?: "đứng yên"

        val userMove = getUserMovement()
        val objectMove = getObjectMovement(status)
        val relation = getRelativeMovement(userMove, objectMove)

        // --------------------------
        // TOF ƯU TIÊN
        // --------------------------
        val finalMessage = if (sensorDistanceMm in 10..1800) {
            val distanceM = (sensorDistanceMm / 1000.0 * 10).roundToInt() / 10.0
            "Ở $direction có ${box.clsName} cách khoảng $distanceM mét, $relation"
        } else {
            "Ở $direction có ${box.clsName}, $relation"
        }

        Log.d("TTS_SPEAK", "Speak [$key] $finalMessage")

        voiceResponder.speak(finalMessage)
        lastSpeakTime = now
        lastSpokenKey = key
    }

    fun stop() {
        lastSpeakTime = 0L
        lastSpokenKey = null
    }
}
