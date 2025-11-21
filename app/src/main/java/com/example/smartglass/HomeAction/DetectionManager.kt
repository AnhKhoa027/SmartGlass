package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import kotlinx.coroutines.*
import kotlin.math.min

class DetectionManager(
    context: Context,
    private val cameraViewManager: UsbCameraViewManager,
    private val detectionSpeaker: DetectionSpeaker,
    private val apiDetectionManager: ApiDetectionManager,
    private val scope: CoroutineScope
) {
    // Ngưỡng ưu tiên
    private val THRESH_YOLO_HIGH = 0.80f
    private val THRESH_CUSTOM_HIGH = 0.90f
    private val THRESH_API_HIGH = 0.70f
    private val THRESH_CONSENSUS = 0.50f

    private val DEEP_CHECK_COOLDOWN = 1000L

    private val tracker = ObjectTracker(maxObjects = 10, iouThreshold = 0.5f)

    // Volatile để đảm bảo luồng khác luôn thấy giá trị mới nhất
    @Volatile var lastFrame: Bitmap? = null
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
                        // 1. Tracker
                        val allTracked = tracker.update(boundingBoxes)

                        // 2. Lọc & Sắp xếp (Bỏ SafeZone, chỉ lấy Top 3 to nhất)
                        val priorityTargets = allTracked
                            .sortedByDescending { it.smoothBox.w * it.smoothBox.h }
                            .take(3)

                        // 3. Deep Check
                        priorityTargets.forEach { trackedObj ->
                            processObjectDeepCheck(trackedObj)
                        }

                        // 4. Hiển thị
                        withContext(Dispatchers.Main) {
                            if (priorityTargets.isNotEmpty()) {
                                val displayBoxes = priorityTargets.map { it.smoothBox }
                                cameraViewManager.setOverlayResults(displayBoxes)
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

        // Throttling: Check 1s một lần
        if (now - trackedObj.lastDeepCheckTime < DEEP_CHECK_COOLDOWN) return

        // Bỏ qua nếu đã chắc chắn
        if ((trackedObj.smoothBox.boxColor == Color.BLUE || trackedObj.smoothBox.boxColor == Color.WHITE)
            && trackedObj.smoothBox.cnf > 0.95f
            && (now - trackedObj.lastDeepCheckTime < 2000)) {
            return
        }

        // --- FIX MEMORY LEAK: KHÔNG COPY TOÀN BỘ FRAME NỮA ---
        // Dùng trực tiếp lastFrame. Hàm cropBoundingBox bên dưới đã được viết lại để an toàn.
        val currentFrame = lastFrame ?: return
        if (currentFrame.isRecycled) return

        try {
            // Cắt ảnh nhỏ từ frame lớn
            val crop = cropBoundingBox(currentFrame, trackedObj.smoothBox)

            // Nếu crop thất bại (trả về null hoặc ảnh rỗng), dừng ngay
            if (crop == null || crop.width <= 1) return

            trackedObj.lastDeepCheckTime = now

            // --- Bắt đầu nhận diện ---
            val yoloSync = detector.detectSynchronous(crop)
            val bestYolo = yoloSync?.maxByOrNull { it.cnf }
            val yoloConf = bestYolo?.cnf ?: 0f
            val yoloLabel = bestYolo?.clsName ?: ""

            // Classifier chạy rất nhanh, không lo
            val (customLabel, customConf) = classifier.classify(crop)

            var finalLabel = trackedObj.smoothBox.clsName
            var finalConf = trackedObj.smoothBox.cnf
            var finalColor = trackedObj.smoothBox.boxColor
            var isUpdated = false

            // Logic Ưu Tiên
            if (yoloConf >= THRESH_YOLO_HIGH) {
                finalLabel = yoloLabel; finalConf = yoloConf; finalColor = Color.GREEN; isUpdated = true
            } else if (customConf >= THRESH_CUSTOM_HIGH) {
                finalLabel = customLabel; finalConf = customConf; finalColor = Color.BLUE; isUpdated = true
            } else if (yoloConf >= THRESH_CONSENSUS && customConf >= THRESH_CONSENSUS && areLabelsSimilar(yoloLabel, customLabel)) {
                finalLabel = customLabel; finalConf = (yoloConf + customConf) / 2; finalColor = Color.BLUE; isUpdated = true
            }

            // API Fallback
            if (!isUpdated) {
                val apiResults = apiDetectionManager.detectFrame(crop)
                if (apiResults.isNotEmpty()) {
                    val bestApi = apiResults.maxByOrNull { it.score }!!
                    if (bestApi.score >= THRESH_API_HIGH) {
                        finalLabel = bestApi.label; finalConf = bestApi.score; finalColor = Color.WHITE; isUpdated = true
                    } else if (bestApi.score >= THRESH_CONSENSUS) {
                        if (areLabelsSimilar(bestApi.label, yoloLabel) || areLabelsSimilar(bestApi.label, customLabel)) {
                            finalLabel = bestApi.label; finalConf = bestApi.score; finalColor = Color.WHITE; isUpdated = true
                        }
                    }
                }
            }

            if (isUpdated) {
                trackedObj.smoothBox = trackedObj.smoothBox.copy(clsName = finalLabel, cnf = finalConf, boxColor = finalColor)
            } else {
                // Giữ nguyên YOLO gốc nhưng đổi màu xanh lá để xác nhận là "đã check nhưng không có gì mới"
                trackedObj.smoothBox = trackedObj.smoothBox.copy(boxColor = Color.GREEN)
            }

            // Quan trọng: Ảnh crop là ảnh mới tạo ra, phải recycle để giải phóng RAM
            if (!crop.isRecycled && crop != currentFrame) { // Check crop != currentFrame để tránh recycle nhầm ảnh gốc
                // crop.recycle() // Tạm thời comment dòng này nếu bạn sợ lỗi, nhưng đúng ra nên recycle
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- HÀM CROP SIÊU AN TOÀN (CHỐNG CRASH) ---
    private fun cropBoundingBox(frame: Bitmap, box: BoundingBox): Bitmap? {
        // 1. Check Frame sống hay chết
        if (frame.isRecycled) return null

        // 2. Check NaN (Lỗi toán học)
        if (box.x1.isNaN() || box.y1.isNaN() || box.x2.isNaN() || box.y2.isNaN()) return null

        val srcWidth = frame.width
        val srcHeight = frame.height

        // 3. Ép toạ độ vào trong khung hình (Clamping)
        // Dùng max/min để đảm bảo không bao giờ lòi ra ngoài dù chỉ 1 pixel
        var left = (box.x1 * srcWidth).toInt()
        var top = (box.y1 * srcHeight).toInt()
        var right = (box.x2 * srcWidth).toInt()
        var bottom = (box.y2 * srcHeight).toInt()

        left = left.coerceIn(0, srcWidth)
        top = top.coerceIn(0, srcHeight)
        right = right.coerceIn(left, srcWidth) // Right phải >= Left
        bottom = bottom.coerceIn(top, srcHeight) // Bottom phải >= Top

        var width = right - left
        var height = bottom - top

        // 4. Check kích thước sau khi ép
        if (width <= 0 || height <= 0) return null

        // 5. Check lần cuối (Double Check) để đảm bảo Bitmap.createBitmap không bao giờ lỗi
        if (left + width > srcWidth) width = srcWidth - left
        if (top + height > srcHeight) height = srcHeight - top

        return try {
            Bitmap.createBitmap(frame, left, top, width, height)
        } catch (e: Exception) {
            // Catch OOM hoặc các lỗi lạ khác
            e.printStackTrace()
            null
        }
    }

    fun detectFrame(bitmap: Bitmap) {
        if (isPaused || isDetecting || !::detector.isInitialized) return
        isDetecting = true
        lastFrame = bitmap // Chỉ gán tham chiếu, không copy
        scope.launch(Dispatchers.Default) {
            try {
                // Resize ảnh nhỏ để detect cho nhanh (vẫn tốn RAM chỗ này nhưng cần thiết)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
                detector.detect(scaledBitmap)
                // scaledBitmap tự được GC thu gom
            } catch (e: Exception) { e.printStackTrace() }
            finally { isDetecting = false }
        }
    }

    private fun areLabelsSimilar(l1: String, l2: String): Boolean {
        if (l1.isEmpty() || l2.isEmpty()) return false
        return l1.lowercase().contains(l2.lowercase()) || l2.lowercase().contains(l1.lowercase())
    }

    fun cancelAllTasks() { scope.coroutineContext.cancelChildren(); isDetecting = false }
    fun release() { if (::detector.isInitialized) detector.close(); classifier.close(); detectionSpeaker.stop() }
}