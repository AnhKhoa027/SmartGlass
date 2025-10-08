package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import kotlinx.coroutines.*

/**
 * DetectionManager (Optimized)
 * ----------------------------
 * - Nhận frame từ CameraViewManager
 * - Gọi YOLO detect trong background
 * - Nếu không phát hiện → fallback API
 * - Quản lý phát giọng nói và overlay
 */
class DetectionManager(
    context: Context,
    private val cameraViewManager: CameraViewManager,
    private val detectionSpeaker: DetectionSpeaker,
    private val apiDetectionManager: ApiDetectionManager,
    private val scope: CoroutineScope
) {
    private val tracker = ObjectTracker(maxObjects = 5, iouThreshold = 0.5f)
    var lastFrame: Bitmap? = null
    private var isDetecting = false
    private var lastUnknownSpeakTime = 0L
    private val unknownSpeakInterval = 5000L // 5s

    // YOLO Detector
    private val detector = Detector(
        context = context,
        modelPath = "yolov8n_int8.tflite",
        labelPath = "example_label_file.txt",
        detectorListener = object : Detector.DetectorListener {
            override fun onEmptyDetect() {
                cameraViewManager.setOverlayResults(emptyList())
                fallbackApiLastFrame()
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                val tracked = tracker.update(boundingBoxes)
                cameraViewManager.setOverlayResults(tracked.map { it.smoothBox })

                val labels = tracked.joinToString { it.smoothBox.clsName }
                detectionSpeaker.speakDetections(
                    tracked,
                    cameraViewManager.getOverlayWidth(),
                    cameraViewManager.getOverlayHeight()
                )

                println("✅ YOLO detect done in ${inferenceTime}ms → $labels")
            }
        },
        message = { println("Detector: $it") }
    )

    /** Nhận frame từ camera và chạy detect */
    fun detectFrame(bitmap: Bitmap) {
        if (isDetecting) return
        isDetecting = true

        lastFrame = bitmap

        scope.launch(Dispatchers.IO) {
            try {
                detector.detect(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                speakOnce("Lỗi khi xử lý vật thể.")
            } finally {
                isDetecting = false
            }
        }
    }

    /** Fallback API khi YOLO không phát hiện gì */
    private fun fallbackApiLastFrame() {
        val frame = lastFrame ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val apiResults = apiDetectionManager.detectFrame(frame)

                if (apiResults.isEmpty()) {
                    speakOnce("Không thể xác định vật thể.")
                } else {
                    val boxes = apiResults.map { apiBox ->
                        BoundingBox(
                            x1 = apiBox.x,
                            y1 = apiBox.y,
                            x2 = apiBox.x + apiBox.w,
                            y2 = apiBox.y + apiBox.h,
                            cx = apiBox.x + apiBox.w / 2f,
                            cy = apiBox.y + apiBox.h / 2f,
                            w = apiBox.w,
                            h = apiBox.h,
                            cnf = apiBox.score,
                            cls = -1,
                            clsName = apiBox.label
                        )
                    }

                    withContext(Dispatchers.Main) {
                        cameraViewManager.setOverlayResults(boxes)
                        val labels = boxes.joinToString { it.clsName }
                        detectionSpeaker.speak("Phát hiện: $labels")
                        println("🌐 Fallback API detect → $labels")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                speakOnce("Lỗi khi xác định vật thể qua API.")
            }
        }
    }

    /** Đảm bảo không lặp lại lời nói trong 5s */
    private fun speakOnce(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastUnknownSpeakTime > unknownSpeakInterval) {
            detectionSpeaker.speak(text)
            lastUnknownSpeakTime = now
        }
    }

    fun release() {
        detector.close()
        detectionSpeaker.stop()
    }
}
