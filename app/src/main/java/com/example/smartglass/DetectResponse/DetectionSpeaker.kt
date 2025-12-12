package com.example.smartglass.DetectResponse

    import com.example.smartglass.ObjectDetection.TrackedObject
    import com.example.smartglass.TTSandSTT.VoiceResponder
    import kotlin.math.roundToInt

    class DetectionSpeaker(
        private val voiceResponder: VoiceResponder
    ) {
        private var lastSpeakTime = 0L
        private val speakInterval = 5000L // 5 giây

        private var lastSensorDirX: String = "STAY"
        private var lastSensorDirY: String = "STAY"

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
        // 2) PHÂN TÍCH HÀNH ĐỘNG CỦA VẬT
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
        // 3) QUAN HỆ GIỮA NGƯỜI VÀ VẬT
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

            val nearestObject =
                if (centerObjects.isNotEmpty()) {
                    centerObjects.maxByOrNull { obj ->
                        val boxW = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
                        val boxH = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
                        boxW * boxH
                    }
                } else {
                    trackedObjects.maxByOrNull { obj ->
                        val boxW = (obj.smoothBox.x2 - obj.smoothBox.x1) * frameW
                        val boxH = (obj.smoothBox.y2 - obj.smoothBox.y1) * frameH
                        boxW * boxH
                    }
                } ?: return

            val label = nearestObject.box.clsName ?: "vật"
            val direction = nearestObject.direction ?: "trước mặt"
            val status = nearestObject.status ?: "đứng yên"

            // --------------------------
            // PHÂN TÍCH DI CHUYỂN
            // --------------------------
            val userMove = getUserMovement()
            val objectMove = getObjectMovement(status)
            val relation = getRelativeMovement(userMove, objectMove)

            // --------------------------
            // TOF LUÔN ĐƯỢC ƯU TIÊN (FIX)
            // --------------------------
            val validDistance = sensorDistanceMm in 100..1500
            val useSensor = validDistance

            val finalMessage = if (useSensor) {
                val distanceM = (sensorDistanceMm / 1000.0 * 10).roundToInt() / 10.0
                "Ở $direction có $label cách khoảng $distanceM mét, $relation"
            } else {
                // Camera fallback
                val boxW = (nearestObject.smoothBox.x2 - nearestObject.smoothBox.x1) * frameW
                val boxH = (nearestObject.smoothBox.y2 - nearestObject.smoothBox.y1) * frameH
                val area = boxW * boxH
                val areaRatio = area / (frameW * frameH).toFloat()

                val isVeryClose = areaRatio > 0.20f
                val isCenter = direction.trim().equals("center", ignoreCase = true)

                if (isVeryClose || isCenter) {
                    "Ở $direction có $label rất gần, $relation"
                } else {
                    "Ở $direction có $label, $relation"
                }
            }

            voiceResponder.speak(finalMessage)
            lastSpeakTime = now
        }

        fun stop() {
            lastSpeakTime = 0L
        }
    }
