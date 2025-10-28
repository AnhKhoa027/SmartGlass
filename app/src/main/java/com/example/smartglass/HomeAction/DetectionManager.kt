package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import kotlinx.coroutines.*

/**
 * DetectionManager (Optimized + Per-box fallback, YOLO realtime speak only)
 */
class DetectionManager(
    context: Context,
    private val cameraViewManager: UsbCameraViewManager,
    private val detectionSpeaker: DetectionSpeaker,
    private val apiDetectionManager: ApiDetectionManager,
    private val scope: CoroutineScope
) {
    private val tracker = ObjectTracker(maxObjects = 5, iouThreshold = 0.5f)
    var lastFrame: Bitmap? = null
    private var isDetecting = false

    // YOLO Detector
    private val detector = Detector(
        context = context,
        modelPath = "yolov8n_int8.tflite",
        labelPath = "example_label_file.txt",
        detectorListener = object : Detector.DetectorListener {

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                scope.launch(Dispatchers.IO) {
                    val tracked = tracker.update(boundingBoxes)
                    val updatedBoxes = tracked.map { trackedObj ->
                        val box = trackedObj.smoothBox
                        // Nếu YOLO không chắc → crop và classify fallback
                        if (box.clsName == "Unknown" || box.cnf < 0.5f) {
                            val frameCopy = lastFrame?.takeIf { !it.isRecycled }?.copy(Bitmap.Config.ARGB_8888, false)
                            if (frameCopy != null) {
                                try {
                                    val crop = cropBoundingBox(frameCopy, box)
                                    val (label, conf) = classifier.classify(crop)
                                    box.copy(clsName = label, cnf = conf)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    box
                                }
                            } else box
                        } else box

                    }

                    withContext(Dispatchers.Main) {
                        cameraViewManager.setOverlayResults(updatedBoxes)
                        val labels = tracked.joinToString { it.smoothBox.clsName }
                        detectionSpeaker.speakDetections(
                            tracked,
                            cameraViewManager.getOverlayWidth(),
                            cameraViewManager.getOverlayHeight()
                        )
                        println("YOLO detect done in ${inferenceTime}ms → $labels")
                    }
                }
            }

            override fun onEmptyDetect() {
                cameraViewManager.setOverlayResults(emptyList())
//                fallbackApiLastFrame()
            }
        },
        message = { println("Detector: $it") }
    )

    private val classifier = Classifier(context, "model_meta.tflite", "label_model.txt")

    /** Nhận frame từ camera và chạy detect */
    fun detectFrame(bitmap: Bitmap) {
        if (isDetecting) return
        isDetecting = true
        lastFrame = bitmap

        scope.launch(Dispatchers.Default) {
            try {
                val inputSize = 640  // 224/ 320 /640
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

                detector.detect(scaledBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                detectionSpeaker.speak("Lỗi khi xử lý vật thể.")
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
                    detectionSpeaker.speak("Không thể xác định vật thể.")
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
                detectionSpeaker.speak("Lỗi khi xác định vật thể qua API.")
            }
        }
    }

    private fun cropBoundingBox(frame: Bitmap, box: BoundingBox): Bitmap {
        if (frame.isRecycled) {
            throw IllegalStateException("Frame đã bị recycle, không thể crop.")
        }

        val safeBitmap = frame.copy(Bitmap.Config.ARGB_8888, false)

        val left = (box.x1 * safeBitmap.width).toInt().coerceIn(0, safeBitmap.width - 1)
        val top = (box.y1 * safeBitmap.height).toInt().coerceIn(0, safeBitmap.height - 1)
        val right = (box.x2 * safeBitmap.width).toInt().coerceIn(left + 1, safeBitmap.width)
        val bottom = (box.y2 * safeBitmap.height).toInt().coerceIn(top + 1, safeBitmap.height)

        return Bitmap.createBitmap(safeBitmap, left, top, right - left, bottom - top)
    }

    /** ===== Thêm hàm fix khi stop camera để lần sau connect lại vẫn detect được ===== */
    fun cancelAllTasks() {
        scope.coroutineContext.cancelChildren()
        isDetecting = false
    }

    fun release() {
        detector.close()
        detectionSpeaker.stop()
    }
}
