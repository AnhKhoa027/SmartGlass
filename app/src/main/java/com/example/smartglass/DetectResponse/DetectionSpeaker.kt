//package com.example.smartglass.DetectResponse
//
//import android.util.Log
//import com.example.smartglass.ObjectDetection.DetectSource
//import com.example.smartglass.ObjectDetection.TrackedObject
//import com.example.smartglass.SettingAction.SensorBuffer
//import com.example.smartglass.TTSandSTT.VoiceResponder
//import kotlin.math.roundToInt
//import com.example.smartglass.TTSandSTT.SpeechGate
//
//
//
//class DetectionSpeaker(
//    private val voiceResponder: VoiceResponder,
//    private val sensorBuffer: SensorBuffer
//) {
//
//    private var lastSpeakTime = 0L
//    private val speakInterval = 4000L // 4s
//
//    private var lastSensorDirX: String = "STAY"
//    private var lastSensorDirY: String = "STAY"
//
//    private data class SpokenKey(
//        val objectId: Int,
//        val label: String,
//        val source: DetectSource
//    )
//
//    private var lastSpokenKey: SpokenKey? = null
//
//
//    // USER MOVEMENT
//    private fun getUserMovement(): String {
//        return when {
//            lastSensorDirY == "FORWARD" -> "đi tới"
//            lastSensorDirY == "BACK"    -> "đi lùi"
//            lastSensorDirX == "LEFT"    -> "quẹo trái"
//            lastSensorDirX == "RIGHT"   -> "quẹo phải"
//            else -> "đứng yên"
//        }
//    }
//
//
//    // OBJECT MOVEMENT
//    private fun getObjectMovement(status: String): String {
//        val s = status.lowercase()
//        return when {
//            "approach" in s || "towards" in s -> "đang tiến lại gần bạn"
//            "away" in s                       -> "đang ra xa bạn"
//            "moving" in s                     -> "đang di chuyển"
//            else                              -> "đứng yên"
//        }
//    }
//
//    // ======================
//    // RELATION
//    // ======================
//    private fun getRelativeMovement(
//        userMove: String,
//        objectMove: String
//    ): String {
//        if (userMove == "đi tới" && objectMove == "đang tiến lại gần bạn")
//            return "hai bên đang tiến lại gần nhau"
//
//        if (userMove == "đi tới" && objectMove == "đứng yên")
//            return "bạn đang tiến lại gần vật"
//
//        if (userMove == "đứng yên" && objectMove == "đang tiến lại gần bạn")
//            return "vật đang tiến lại gần bạn"
//
//        return objectMove
//    }
//
//
//    // MAIN TTS
//    fun speakDetections(
//        trackedObjects: List<TrackedObject>,
//        frameW: Int,
//        frameH: Int
//    )
//    {
//        val now = System.currentTimeMillis()
//        if (now - lastSpeakTime < speakInterval) return
//        if (trackedObjects.isEmpty()) return
//
//        val centerObjects = trackedObjects.filter {
//            it.direction?.trim()
//                ?.equals("center", ignoreCase = true) == true
//        }
//
//        val nearestObject =
//            (centerObjects.ifEmpty { trackedObjects })
//                .maxByOrNull { obj ->
//                    val w = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
//                    val h = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
//                    w * h
//                } ?: return
//
//        val box = nearestObject.smoothBox
//        val rawDir = nearestObject.direction?.lowercase() ?: "center"
//
//        val normalizedDir = when (rawDir) {
//            "left"  -> "bên trái"
//            "right" -> "bên phải"
//            else    -> "phía trước"
//        }
//
//        //  UNKNOWN OBJECT – chỉ nói 1 lần
//        if (box.clsName == "unknown") {
//
//            val key = SpokenKey(
//                objectId = nearestObject.id,
//                label = "unknown",
//                source = box.source
//            )
//
//            if (lastSpokenKey == key) {
//                Log.d("TTS_SKIP", "Skip repeated UNKNOWN")
//                return
//            }
//            val message = "$normalizedDir có vật thể không xác định"
//
////            val dir = nearestObject.direction ?: "trước mặt"
////            val message =
////                when (dir.lowercase()) {
////                    "left"  -> "Bên trái có vật thể không xác định"
////                    "right" -> "Bên phải có vật thể không xác định"
////                    else    -> "Phía trước có vật thể không xác định"
////                }
////
////            Log.d("TTS_UNKNOWN", message)
////            voiceResponder.speak(message)
////
////            lastSpeakTime = now
////            lastSpokenKey = key
////            return
////        }
//            VisionContext.update(
//                listOf(
//                    VisionContext.SeenObject(
//                        name = "vật thể không xác định",
//                        direction = normalizedDir,
//                        distance = "không xác định",
//                        movement = "đứng yên"
//                    )
//                )
//            )
//
//            Log.d("TTS_UNKNOWN", message)
//            voiceResponder.speak(message)
//
//            lastSpeakTime = now
//            lastSpokenKey = key
//            return
//        }
//
//        // 🟢 NORMAL OBJECT
//        val key = SpokenKey(
//            objectId = nearestObject.id,
//            label = box.clsName,
//            source = box.source
//        )
//
//        if (lastSpokenKey == key) {
//            Log.d("TTS_SKIP", "Skip same key $key")
//            return
//        }
//
//        val status = nearestObject.status ?: "đứng yên"
//        val userMove = getUserMovement()
//        val objectMove = getObjectMovement(status)
//        val relation = getRelativeMovement(userMove, objectMove)
//
//        val dir = nearestObject.direction?.lowercase() ?: "center"
//        val sensor = sensorBuffer.getLatestValid()
//
//        val distanceText =
//            if (rawDir == "center" && sensor != null) {
//                val distanceM =
//                    (sensor.distanceMm / 1000.0 * 10).roundToInt() / 10.0
//                "cách khoảng $distanceM mét"
//            } else {
//                ""
//            }
//
//        val finalMessage =
//            if (distanceText.isNotEmpty())
//                "$normalizedDir có ${box.clsName} $distanceText, $relation"
//            else
//                "$normalizedDir có ${box.clsName}, $relation"
//
//        Log.d("TTS_SPEAK", finalMessage)
//
//        VisionContext.update(
//            listOf(
//                VisionContext.SeenObject(
//                    name = box.clsName,
//                    direction = normalizedDir,
//                    distance = if (distanceText.isNotEmpty()) distanceText else "không xác định",
//                    movement = relation
//                )
//            )
//        )
//        voiceResponder.speak(finalMessage)
//        lastSpeakTime = now
//        lastSpokenKey = key
//    }
//
//    fun stop() {
//        lastSpeakTime = 0L
//        lastSpokenKey = null
//    }
//}


package com.example.smartglass.DetectResponse

import android.util.Log
import com.example.smartglass.ObjectDetection.DetectSource
import com.example.smartglass.ObjectDetection.TrackedObject
import com.example.smartglass.SettingAction.SensorBuffer
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.TTSandSTT.SpeechGate
import kotlin.math.roundToInt

class DetectionSpeaker(
    private val voiceResponder: VoiceResponder,
    private val sensorBuffer: SensorBuffer
) {

    private var lastSpeakTime = 0L
    private val speakInterval = 4000L

    private data class SpokenKey(
        val objectId: Int,
        val label: String,
        val source: DetectSource
    )

    private var lastSpokenKey: SpokenKey? = null

    fun speakDetections(
        trackedObjects: List<TrackedObject>,
        frameW: Int,
        frameH: Int
    ) {
        // 🔒 KHI USER ĐANG HỎI → CHỈ UPDATE CONTEXT, KHÔNG NÓI
        if (SpeechGate.isUserAsking) {
            VisionContext.update(
                trackedObjects.map {
                    VisionContext.SeenObject(
                        name = it.smoothBox.clsName,
                        direction = it.direction ?: "phía trước",
                        distance = "không xác định",
                        movement = it.status ?: "đứng yên"
                    )
                }
            )
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < speakInterval) return
        if (trackedObjects.isEmpty()) return

        val nearestObject =
            trackedObjects.maxByOrNull { obj ->
                val w = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
                val h = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
                w * h
            } ?: return

        val box = nearestObject.smoothBox
        val rawDir = nearestObject.direction?.lowercase() ?: "center"

        val direction = when (rawDir) {
            "left" -> "bên trái"
            "right" -> "bên phải"
            else -> "phía trước"
        }

        val key = SpokenKey(nearestObject.id, box.clsName, box.source)
        if (lastSpokenKey == key) return

        val sensor = sensorBuffer.getLatestValid()
        val distanceText =
            if (rawDir == "center" && sensor != null) {
                val d = (sensor.distanceMm / 1000.0 * 10).roundToInt() / 10.0
                "cách khoảng $d mét"
            } else ""

        val message =
            if (distanceText.isNotEmpty())
                "$direction có ${box.clsName} $distanceText"
            else
                "$direction có ${box.clsName}"

        // ✅ UPDATE CONTEXT
        VisionContext.update(
            listOf(
                VisionContext.SeenObject(
                    name = box.clsName,
                    direction = direction,
                    distance = distanceText.ifEmpty { "không xác định" },
                    movement = nearestObject.status ?: "đứng yên"
                )
            )
        )

        voiceResponder.speak(message)
        lastSpeakTime = now
        lastSpokenKey = key
    }

    fun stop() {
        lastSpeakTime = 0L
        lastSpokenKey = null
    }
}
