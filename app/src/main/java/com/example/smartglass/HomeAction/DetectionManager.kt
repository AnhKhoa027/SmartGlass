package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DetectionManager
 * -------------------
 * File này quản lý việc chạy YOLO (Detector), tracking (ObjectTracker) và phát hiện giọng nói (DetectionSpeaker).
 * Được gọi trong HomeFragment.
 *
 * - detectFrame(): nhận bitmap từ Xiaocam, scale và chạy YOLO detect.
 * - Nếu YOLO detect được -> cập nhật tracker, cập nhật overlay và gọi DetectionSpeaker.speakDetections().
 * - Nếu YOLO không detect (onEmptyDetect) -> fallbackApiLastFrame(): gửi frame cuối cùng lên ApiDetectionManager để thử nhận diện trên cloud.
 * - Nếu API cũng không trả kết quả -> gọi detectionSpeaker.speak("Vật không xác định").
 * - release(): đóng detector + dừng speaker.
 *
 * Liên quan:
 * - HomeFragment (chỗ điều phối vòng đời và frame loop)
 * - CameraViewManager (vẽ overlay bounding box)
 * - ApiDetectionManager (fallback gọi HuggingFace hoặc API khác)
 * - DetectionSpeaker (đọc kết quả bằng TTS)
 */
class DetectionManager(
    context: Context,
    private val cameraViewManager: CameraViewManager,
    private val detectionSpeaker: DetectionSpeaker,
    private val apiDetectionManager: ApiDetectionManager,
    private val scope: CoroutineScope
) {
    private val tracker = ObjectTracker(maxObjects = 5, iouThreshold = 0.5f)

    private var lastFrame: Bitmap? = null

    /**
     * Detector YOLO (TFLite) được khởi tạo 1 lần tại đây.
     * - detectorListener.onEmptyDetect(): gọi khi YOLO không detect được bounding box nào.
     * - detectorListener.onDetect(...): gọi khi YOLO phát hiện bounding box.
     */
    private val detector: Detector = Detector(
        context = context,
        modelPath = "yolov8n_int8.tflite",
        labelPath = "example_label_file.txt",
        detectorListener = object : Detector.DetectorListener {
            // Khi YOLO không tìm thấy object nào
            override fun onEmptyDetect() {
                // Xóa overlay trên giao diện
                cameraViewManager.clearOverlay()
                // Thực hiện fallback: gửi frame cuối cùng lên API để thử nhận diện trên cloud
                fallbackApiLastFrame()
            }

            // Khi YOLO tìm thấy bounding boxes
            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                // Cập nhật tracker (ký hiệu, smoothing)
                val tracked = tracker.update(boundingBoxes)
                // Vẽ kết quả lên overlay (dùng smoothBox)
                cameraViewManager.setOverlayResults(tracked.map { it.smoothBox })
                // Gọi speaker để đọc kết quả (hàm này sẽ xử lý hướng/khoảng cách)
                detectionSpeaker.speakDetections(
                    tracked,
                    cameraViewManager.getOverlayWidth(),
                    cameraViewManager.getOverlayHeight()
                )
            }
        },
        message = { println(it) } // debug message callback
    )

    /**
     * Nhận 1 frame từ HomeFragment (frame thực tế lấy từ camera).
     * - Scale về kích thước model (ví dụ 224x224) và gọi detector.detect().
     * - Lưu lastFrame để sử dụng khi cần fallback API.
     *
     * @param bitmap Ảnh nguyên bản lấy từ camera (số pixel gốc)
     */
    fun detectFrame(bitmap: Bitmap) {
        // Scale để phù hợp input model (không đổi bitmap gốc dùng cho overlay/API)
        val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        detector.detect(scaled)
        // Lưu frame gốc để dùng fallback (API cần ảnh đầy đủ)
        lastFrame = bitmap
    }

    /**
     * Fallback: Khi YOLO không phát hiện gì, gửi frame cuối lên ApiDetectionManager.
     *
     * Lưu ý hiện tại:
     * - apiDetectionManager.detectFrame(frame) trả về danh sách BoundingBoxAPI.
     * - Ở đây ta gọi API trong coroutine IO để không block UI.
     * - Nếu API trả rỗng -> báo "Vật không xác định".
     * - Nếu API có kết quả -> tạm thời ở đây gọi detectionSpeaker.speak(...) để đọc nhãn.
     */
    private var lastUnknownSpeakTime: Long = 0
    private val unknownSpeakInterval = 5000L // 5 giây

    private fun fallbackApiLastFrame() {
        val frame = lastFrame ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val apiResults = apiDetectionManager.detectFrame(frame)
                if (apiResults.isEmpty()) {
                    val now = System.currentTimeMillis()
                    if (now - lastUnknownSpeakTime > unknownSpeakInterval) {
                        detectionSpeaker.speak("A Pi AI Không thể xác định được vật")
                        lastUnknownSpeakTime = now
                    }
                } else {
                    // Convert từ BoundingBoxAPI -> BoundingBox của bạn
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
                            cls = -1,                // API không có id class
                            clsName = apiBox.label   // tên object
                        )
                    }

                    // Vẽ overlay bằng boxes
                    cameraViewManager.setOverlayResults(boxes)

                    // Đọc nhãn
                    val labels = boxes.joinToString { it.clsName }
                    detectionSpeaker.speak("Phát hiện: $labels")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val now = System.currentTimeMillis()
                if (now - lastUnknownSpeakTime > unknownSpeakInterval) {
                    detectionSpeaker.speak("Lỗi, Không thể xác định được vật")
                    lastUnknownSpeakTime = now
                }
            }
        }
    }



    fun release() {
        detector.close()
        detectionSpeaker.stop()
    }
}
