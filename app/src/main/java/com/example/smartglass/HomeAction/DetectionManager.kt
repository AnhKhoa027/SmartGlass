package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import kotlinx.coroutines.*

class DetectionManager(
    context: Context,
    private val cameraViewManager: UsbCameraViewManager,
    private val detectionSpeaker: DetectionSpeaker,
    private val apiDetectionManager: ApiDetectionManager,
    private val scope: CoroutineScope
) {
    // --- CẤU HÌNH NGƯỠNG (THRESHOLDS) ---
    private val THRESH_YOLO_HIGH = 0.80f
    private val THRESH_CUSTOM_HIGH = 0.90f
    private val THRESH_API_HIGH = 0.70f
    private val THRESH_CONSENSUS = 0.50f

    // Thời gian chờ giữa các lần Deep Check (ms) - Giữ 1s để mượt
    private val DEEP_CHECK_COOLDOWN = 1000L

    private val tracker = ObjectTracker(maxObjects = 10, iouThreshold = 0.5f)
    var lastFrame: Bitmap? = null
    private var isDetecting = false
    var isPaused = false

    private lateinit var detector: Detector
    private val classifier = Classifier(context, ModelPaths.CLASSIFY_MODEL, ModelPaths.CLASSIFY_LABEL)

    init {
        detector = Detector(
            context = context,
            modelPath = ModelPaths.YOLO_MODEL,
            labelPath = ModelPaths.YOLO_LABEL,
            detectorListener = object : Detector.DetectorListener {

                override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                    scope.launch(Dispatchers.IO) {
                        // 1. Cập nhật Tracker
                        val allTracked = tracker.update(boundingBoxes)

                        // 2. LỌC & SẮP XẾP (Đơn giản hóa: Chỉ lấy Top 3 To Nhất)
                        val priorityTargets = allTracked
                            // Sắp xếp diện tích (W * H) từ Lớn xuống Bé
                            .sortedByDescending { it.smoothBox.w * it.smoothBox.h }
                            // Lấy 3 vật to nhất
                            .take(3)

                        // 3. Xử lý nhận diện sâu (Deep Check) cho Top 3
                        priorityTargets.forEach { trackedObj ->
                            processObjectDeepCheck(trackedObj)
                        }

                        // 4. Cập nhật Giao diện & Loa
                        withContext(Dispatchers.Main) {
                            if (priorityTargets.isNotEmpty()) {
                                // Chỉ hiển thị Top 3
                                val displayBoxes = priorityTargets.map { it.smoothBox }
                                cameraViewManager.setOverlayResults(displayBoxes)

                                // Đọc loa Top 3
                                detectionSpeaker.speakDetections(
                                    priorityTargets,
                                    cameraViewManager.getOverlayWidth(),
                                    cameraViewManager.getOverlayHeight()
                                )
                            } else {
                                cameraViewManager.setOverlayResults(emptyList())
                            }
                        }
                    }
                }

                override fun onEmptyDetect() {
                    cameraViewManager.setOverlayResults(emptyList())
                }
            },
            message = { println("Detector: $it") }
        )
    }

    private suspend fun processObjectDeepCheck(trackedObj: TrackedObject) {
        val now = SystemClock.uptimeMillis()

        // Throttling: Nếu mới check xong trong vòng 1s thì bỏ qua
        if (now - trackedObj.lastDeepCheckTime < DEEP_CHECK_COOLDOWN) {
            return
        }

        // Nếu đã xác định chắc chắn (Màu Xanh Dương/Trắng & Tin cậy cao) -> Bỏ qua để tiết kiệm pin
        if ((trackedObj.smoothBox.boxColor == Color.BLUE || trackedObj.smoothBox.boxColor == Color.WHITE)
            && trackedObj.smoothBox.cnf > 0.95f
            && (now - trackedObj.lastDeepCheckTime < 2000)) {
            return
        }

        val originalBox = trackedObj.smoothBox
        val frameCopy = lastFrame?.takeIf { !it.isRecycled }?.copy(Bitmap.Config.ARGB_8888, false) ?: return

        try {
            val crop = cropBoundingBox(frameCopy, originalBox)
            if (crop.width <= 1) return // Bỏ qua nếu crop lỗi

            trackedObj.lastDeepCheckTime = now // Đánh dấu thời gian

            // A. Lấy dữ liệu
            val yoloSync = detector.detectSynchronous(crop)
            val bestYolo = yoloSync?.maxByOrNull { it.cnf }
            val yoloConf = bestYolo?.cnf ?: 0f
            val yoloLabel = bestYolo?.clsName ?: ""
            val (customLabel, customConf) = classifier.classify(crop)

            var finalLabel = originalBox.clsName
            var finalConf = originalBox.cnf
            var finalColor = originalBox.boxColor // Giữ màu hiện tại làm mặc định
            var isUpdated = false

            // B. Logic Ưu Tiên
            // 1. YOLO (Xanh Lá)
            if (yoloConf >= THRESH_YOLO_HIGH) {
                finalLabel = yoloLabel; finalConf = yoloConf; finalColor = Color.GREEN; isUpdated = true
            }
            // 2. Custom (Xanh Dương)
            else if (customConf >= THRESH_CUSTOM_HIGH) {
                finalLabel = customLabel; finalConf = customConf; finalColor = Color.BLUE; isUpdated = true
            }
            // 3. Đồng thuận (Xanh Dương)
            else if (yoloConf >= THRESH_CONSENSUS && customConf >= THRESH_CONSENSUS && areLabelsSimilar(yoloLabel, customLabel)) {
                finalLabel = customLabel; finalConf = (yoloConf + customConf) / 2; finalColor = Color.BLUE; isUpdated = true
            }

            // 4. API Fallback (Trắng)
            if (!isUpdated) {
                val apiResults = apiDetectionManager.detectFrame(crop)
                if (apiResults.isNotEmpty()) {
                    val bestApi = apiResults.maxByOrNull { it.score }!!
                    val apiConf = bestApi.score
                    val apiLabel = bestApi.label

                    if (apiConf >= THRESH_API_HIGH) {
                        finalLabel = apiLabel; finalConf = apiConf; finalColor = Color.WHITE; isUpdated = true
                    } else if (apiConf >= THRESH_CONSENSUS) {
                        if (areLabelsSimilar(apiLabel, yoloLabel) || areLabelsSimilar(apiLabel, customLabel)) {
                            finalLabel = apiLabel; finalConf = apiConf; finalColor = Color.WHITE; isUpdated = true
                        }
                    }
                }
            }

            // C. Cập nhật
            if (isUpdated) {
                trackedObj.smoothBox = originalBox.copy(clsName = finalLabel, cnf = finalConf, boxColor = finalColor)
            }
            // Nếu không update: Giữ nguyên thông tin cũ (không gán Unknown bừa bãi)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // HÀM CROP AN TOÀN (CHỐNG CRASH)
    private fun cropBoundingBox(frame: Bitmap, box: BoundingBox): Bitmap {
        if (frame.isRecycled) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        // Check NaN
        if (box.x1.isNaN() || box.y1.isNaN() || box.x2.isNaN() || box.y2.isNaN()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val safeW = frame.width
        val safeH = frame.height

        // Ép tọa độ
        val left = (box.x1 * safeW).toInt().coerceIn(0, safeW - 1)
        val top = (box.y1 * safeH).toInt().coerceIn(0, safeH - 1)
        val right = (box.x2 * safeW).toInt().coerceIn(left + 1, safeW)
        val bottom = (box.y2 * safeH).toInt().coerceIn(top + 1, safeH)

        val width = right - left
        val height = bottom - top

        // Check kích thước âm hoặc bằng 0
        if (width <= 0 || height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        return try {
            Bitmap.createBitmap(frame, left, top, width, height)
        } catch (e: Exception) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun areLabelsSimilar(l1: String, l2: String): Boolean {
        if (l1.isEmpty() || l2.isEmpty()) return false
        return l1.lowercase().contains(l2.lowercase()) || l2.lowercase().contains(l1.lowercase())
    }

    fun detectFrame(bitmap: Bitmap) {
        if (isPaused || isDetecting || !::detector.isInitialized) return
        isDetecting = true
        lastFrame = bitmap
        scope.launch(Dispatchers.Default) {
            try {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
                detector.detect(scaledBitmap)
            } catch (e: Exception) { e.printStackTrace() }
            finally { isDetecting = false }
        }
    }

    fun cancelAllTasks() { scope.coroutineContext.cancelChildren(); isDetecting = false }
    fun release() { if (::detector.isInitialized) detector.close(); classifier.close(); detectionSpeaker.stop() }
}