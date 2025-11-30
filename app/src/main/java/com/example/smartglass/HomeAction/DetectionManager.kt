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
    // Ngưỡng ưu tiên
    private val THRESH_YOLO_HIGH = 0.80f
    private val THRESH_CUSTOM_HIGH = 0.95f
    private val THRESH_API_HIGH = 0.70f
    private val THRESH_CONSENSUS = 0.50f

    private val DEEP_CHECK_COOLDOWN = 1000L // 1s kiểm tra lại

    private val tracker = ObjectTracker(maxObjects = 10, iouThreshold = 0.5f)

    // Volatile để các luồng khác luôn thấy frame mới nhất
    @Volatile var lastFrame: Bitmap? = null
    private var isDetecting = false
    var isPaused = false

    // Danh sách tracked objects hiện tại
    var currentTrackedObjects: List<TrackedObject> = emptyList()
        private set

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
                        val allTracked = tracker.update(boundingBoxes)

                        val priorityTargets = allTracked
                            .sortedByDescending { it.smoothBox.w * it.smoothBox.h }
                            .take(3)

                        currentTrackedObjects = priorityTargets

                        priorityTargets.forEach { trackedObj ->
                            processObjectDeepCheck(trackedObj)
                        }

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
                    currentTrackedObjects = emptyList()
                    cameraViewManager.setOverlayResults(emptyList())
                }
            },
            message = { println("Detector: $it") }
        )
    }

    private suspend fun processObjectDeepCheck(trackedObj: TrackedObject) {
        val now = SystemClock.uptimeMillis()

        // Throttling: 1s check 1 lần
        if (now - trackedObj.lastDeepCheckTime < DEEP_CHECK_COOLDOWN) return

        // Bỏ qua nếu đã chắc chắn
        if ((trackedObj.smoothBox.boxColor == Color.BLUE || trackedObj.smoothBox.boxColor == Color.WHITE)
            && trackedObj.smoothBox.cnf > 0.95f
            && (now - trackedObj.lastDeepCheckTime < 2000)
        ) return

        val currentFrame = lastFrame ?: return
        if (currentFrame.isRecycled) return

        try {
            val crop = cropBoundingBox(currentFrame, trackedObj.smoothBox)
            if (crop == null || crop.width <= 1) return

            trackedObj.lastDeepCheckTime = now

            //YOLO detect
            val yoloSync = detector.detectSynchronous(crop)
            val bestYolo = yoloSync?.maxByOrNull { it.cnf }
            val yoloConf = bestYolo?.cnf ?: 0f
            val yoloLabel = bestYolo?.clsName ?: ""

            // Classifier
            val (customLabel, customConf) = classifier.classify(crop)

            var finalLabel = trackedObj.smoothBox.clsName
            var finalConf = trackedObj.smoothBox.cnf
            var finalColor = trackedObj.smoothBox.boxColor
            var isUpdated = false

            // --- Logic ưu tiên ---
            if (yoloConf >= THRESH_YOLO_HIGH) {
                finalLabel = yoloLabel; finalConf = yoloConf; finalColor = Color.GREEN; isUpdated = true
            } else if (customConf >= THRESH_CUSTOM_HIGH) {
                finalLabel = customLabel; finalConf = customConf; finalColor = Color.BLUE; isUpdated = true
            } else if (yoloConf >= THRESH_CONSENSUS && customConf >= THRESH_CONSENSUS
                && areLabelsSimilar(yoloLabel, customLabel)
            ) {
                finalLabel = customLabel; finalConf = (yoloConf + customConf) / 2; finalColor = Color.BLUE; isUpdated = true
            }

            // --- API Fallback ---
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

            // Cập nhật trackedObj
            trackedObj.smoothBox = if (isUpdated) {
                trackedObj.smoothBox.copy(clsName = finalLabel, cnf = finalConf, boxColor = finalColor)
            } else {
                trackedObj.smoothBox.copy(boxColor = Color.GREEN)
            }

            // Cleanup crop bitmap nếu cần
            if (!crop.isRecycled && crop != currentFrame) {
                // crop.recycle() // comment tạm thời, tránh crash
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Crop bitmap an toàn ---
    private fun cropBoundingBox(frame: Bitmap, box: BoundingBox): Bitmap? {
        if (frame.isRecycled) return null
        if (box.x1.isNaN() || box.y1.isNaN() || box.x2.isNaN() || box.y2.isNaN()) return null

        val srcWidth = frame.width
        val srcHeight = frame.height

        var left = (box.x1 * srcWidth).toInt().coerceIn(0, srcWidth)
        var top = (box.y1 * srcHeight).toInt().coerceIn(0, srcHeight)
        var right = (box.x2 * srcWidth).toInt().coerceIn(left, srcWidth)
        var bottom = (box.y2 * srcHeight).toInt().coerceIn(top, srcHeight)

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        return try {
            Bitmap.createBitmap(frame, left, top, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Detect frame mới ---
    fun detectFrame(bitmap: Bitmap) {
        if (isPaused || isDetecting || !::detector.isInitialized) return
        isDetecting = true
        lastFrame = bitmap // chỉ gán tham chiếu, không copy
        scope.launch(Dispatchers.Default) {
            try {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
                detector.detect(scaledBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isDetecting = false
            }
        }
    }

    private fun areLabelsSimilar(l1: String, l2: String): Boolean {
        if (l1.isEmpty() || l2.isEmpty()) return false
        return l1.lowercase().contains(l2.lowercase()) || l2.lowercase().contains(l1.lowercase())
    }

    fun cancelAllTasks() {
        scope.coroutineContext.cancelChildren()
        isDetecting = false
    }

    fun release() {
        if (::detector.isInitialized) detector.close()
        classifier.close()
        detectionSpeaker.stop()
    }
}
