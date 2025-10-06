package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import kotlinx.coroutines.*

/**
 * DetectionManager
 * -------------------
 * Quản lý YOLO (Detector), tracking (ObjectTracker) và phát hiện giọng nói (DetectionSpeaker).
 * Tích hợp với CameraViewManager (WebSocket) cho real-time detection.
 *
 * - detectFrame(): nhận frame nền từ CameraViewManager và xử lý YOLO.
 * - Nếu YOLO không phát hiện gì → fallback API hoặc nói “vật không xác định”.
 * - Toàn bộ detect chạy trong coroutine IO (không block main thread).
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

    // Bộ xử lý YOLO
    private val detector: Detector = Detector(
        context = context,
        modelPath = "yolov8n_int8.tflite",
        labelPath = "example_label_file.txt",
        detectorListener = object : Detector.DetectorListener {
            override fun onEmptyDetect() {
                // Không tìm thấy vật
                cameraViewManager.setOverlayResults(emptyList())
                fallbackApiLastFrame()
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                // Tracking + overlay + đọc tên vật
                val tracked = tracker.update(boundingBoxes)
                cameraViewManager.setOverlayResults(tracked.map { it.smoothBox })

                detectionSpeaker.speakDetections(
                    tracked,
                    cameraViewManager.getOverlayWidth(),
                    cameraViewManager.getOverlayHeight()
                )
            }
        },
        message = { println(it) } // debug
    )

    private var isDetecting = false
    private var lastUnknownSpeakTime = 0L
    private val unknownSpeakInterval = 5000L // 5 giây

    /**
     * detectFrame()
     * --------------
     * Nhận bitmap từ CameraViewManager (WebSocket stream),
     * scale và gọi YOLO detect trong coroutine IO.
     *
     * @param bitmap ảnh đầu vào từ ESP32
     */
    fun detectFrame(bitmap: Bitmap) {
        if (isDetecting) return // bỏ qua frame nếu YOLO đang bận
        isDetecting = true

        // Xử lý nền
        scope.launch(Dispatchers.IO) {
            try {
                val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                detector.detect(scaled)
                lastFrame = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isDetecting = false
            }
        }
    }

    /**
     * fallbackApiLastFrame()
     * -----------------------
     * Gửi frame cuối cùng lên API (cloud detect) nếu YOLO không phát hiện gì.
     */
    private fun fallbackApiLastFrame() {
        val frame = lastFrame ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val apiResults = apiDetectionManager.detectFrame(frame)

                if (apiResults.isEmpty()) {
                    val now = System.currentTimeMillis()
                    if (now - lastUnknownSpeakTime > unknownSpeakInterval) {
                        detectionSpeaker.speak("Không thể xác định vật thể.")
                        lastUnknownSpeakTime = now
                    }
                } else {
                    val boxes = apiResults.map { apiBox ->
                        val x1 = apiBox.x
                        val y1 = apiBox.y
                        val x2 = apiBox.x + apiBox.w
                        val y2 = apiBox.y + apiBox.h
                        BoundingBox(
                            x1 = x1,
                            y1 = y1,
                            x2 = x2,
                            y2 = y2,
                            cx = x1 + apiBox.w / 2f,
                            cy = y1 + apiBox.h / 2f,
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
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val now = System.currentTimeMillis()
                if (now - lastUnknownSpeakTime > unknownSpeakInterval) {
                    detectionSpeaker.speak("Lỗi khi xác định vật thể.")
                    lastUnknownSpeakTime = now
                }
            }
        }
    }

    /** Giải phóng tài nguyên */
    fun release() {
        detector.close()
        detectionSpeaker.stop()
    }
}
