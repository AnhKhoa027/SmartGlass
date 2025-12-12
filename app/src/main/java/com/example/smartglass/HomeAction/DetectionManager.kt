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

    private val THRESH_YOLO_HIGH = 0.85f
    private val THRESH_CUSTOM_HIGH = 0.80f
    private val THRESH_API_HIGH = 0.80f
    private val THRESH_CONSENSUS = 0.50f

    private val DEEP_CHECK_COOLDOWN = 1000L

    private val tracker = ObjectTracker(maxObjects = 10, iouThreshold = 0.5f)

    @Volatile var lastFrame: Bitmap? = null
    private var isDetecting = false
    var isPaused = false

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
                        println("YOLO >> Detected ${boundingBoxes.size} boxes")

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
                    println("YOLO >> No objects")
                    currentTrackedObjects = emptyList()
                    cameraViewManager.setOverlayResults(emptyList())
                }
            },
            message = { println("Detector: $it") }
        )
    }

    private suspend fun processObjectDeepCheck(trackedObj: TrackedObject) {
        val now = SystemClock.uptimeMillis()

        if (now - trackedObj.lastDeepCheckTime < DEEP_CHECK_COOLDOWN) return

        println("DEEP-CHECK >> Target YOLO: ${trackedObj.smoothBox.clsName}")

        val currentFrame = lastFrame
        if (currentFrame == null || currentFrame.isRecycled) {
            println("DEEP-CHECK >> lastFrame NULL or RECYCLED")
            return
        }

        val crop = cropBoundingBox(currentFrame, trackedObj.smoothBox)
        if (crop == null) {
            println("DEEP-CHECK >> Crop NULL")
            return
        }

        println("DEEP-CHECK >> Crop size = ${crop.width}x${crop.height}")

        trackedObj.lastDeepCheckTime = now

        try {
            // ---------------- YOLO & CLASSIFIER ----------------
            val yoloDeferred = scope.async(Dispatchers.Default) {
                detector.detectSynchronous(crop)?.maxByOrNull { it.cnf }
            }

            val classifierDeferred = scope.async(Dispatchers.Default) {
                classifier.classify(crop)
            }

            // ---------------- API detection----------------
            val USE_API = true // <-- true để bật, false để tắt
            if (USE_API) {
                scope.launch(Dispatchers.IO) {
                    try {
                        println("API >> START")
                        println("API >> Sending bitmap ${crop.width}x${crop.height}")

                        val apiResults = apiDetectionManager.detectFrame(crop)

                        println("API >> Returned ${apiResults.size} results")

                        if (apiResults.isNotEmpty()) {
                            val bestApi = apiResults.maxByOrNull { it.score }!!
                            println("API >> Best: ${bestApi.label} | score=${bestApi.score}")

                            val similar = areLabelsSimilar(bestApi.label, trackedObj.smoothBox.clsName)
                            println("API >> Similar with YOLO? $similar")

                            if (bestApi.score >= THRESH_API_HIGH ||
                                (bestApi.score >= THRESH_CONSENSUS && similar)
                            ) {

                                println("API >> APPLY OVERRIDE -> ${bestApi.label}")

                                trackedObj.smoothBox = trackedObj.smoothBox.copy(
                                    clsName = bestApi.label,
                                    cnf = bestApi.score,
                                    boxColor = Color.RED
                                )

                                withContext(Dispatchers.Main) {
                                    cameraViewManager.setOverlayResults(
                                        listOf(trackedObj.smoothBox)
                                    )
                                    detectionSpeaker.speakDetections(
                                        listOf(trackedObj),
                                        cameraViewManager.getOverlayWidth(),
                                        cameraViewManager.getOverlayHeight()
                                    )
                                }

                            } else {
                                println("API >> IGNORE (not strong enough)")
                            }
                        } else {
                            println("API >> EMPTY RESULT")
                        }

                    } catch (e: Exception) {
                        println("API >> ERROR = ${e.message}")
                    }
                }
            } else {
                println("API >> SKIPPED (USE_API=false)")
            }

            // ---------------- WAIT YOLO & CLASSIFIER ----------------
            val bestYolo = yoloDeferred.await()
            val (customLabel, customConf) = classifierDeferred.await()

            if (bestYolo != null)
                println("DEEP-CHECK >> YOLO: ${bestYolo.clsName} | ${bestYolo.cnf}")

            println("DEEP-CHECK >> CLASSIFIER: ${customLabel} | ${customConf}")

            // ---------------- Apply YOLO/Classify logic ----------------
            var updated = false
            var finalBox = trackedObj.smoothBox

            if (bestYolo?.cnf ?: 0f >= THRESH_YOLO_HIGH) {
                println("CONSENSUS >> YOLO HIGH -> ${bestYolo!!.clsName}")
                finalBox = finalBox.copy(
                    clsName = bestYolo!!.clsName,
                    cnf = bestYolo.cnf,
                    boxColor = Color.GREEN
                )
                updated = true
            }

            else if (customConf >= THRESH_CUSTOM_HIGH) {
                println("CONSENSUS >> CLASSIFIER HIGH -> ${customLabel}")
                finalBox = finalBox.copy(
                    clsName = customLabel,
                    cnf = customConf,
                    boxColor = Color.BLUE
                )
                updated = true
            }

            else if (
                bestYolo != null &&
                bestYolo.cnf >= THRESH_CONSENSUS &&
                customConf >= THRESH_CONSENSUS &&
                areLabelsSimilar(bestYolo.clsName, customLabel)
            ) {
                println("CONSENSUS >> YOLO + CLASSIFIER AGREED")
                finalBox = finalBox.copy(
                    clsName = customLabel,
                    cnf = (bestYolo.cnf + customConf) / 2f,
                    boxColor = Color.BLUE
                )
                updated = true
            }

            if (updated) {
                println("DEEP-CHECK >> UPDATED label = ${finalBox.clsName}")
                trackedObj.smoothBox = finalBox
            }

        } catch (e: Exception) {
            println("DEEP-CHECK >> ERROR = ${e.message}")
        }
    }



    private fun cropBoundingBox(frame: Bitmap, box: BoundingBox): Bitmap? {

        println("CROP >> Raw box = (${box.x1},${box.y1}) -> (${box.x2},${box.y2})")

        val srcWidth = frame.width
        val srcHeight = frame.height

        val left = (box.x1 * srcWidth).toInt().coerceIn(0, srcWidth)
        val top = (box.y1 * srcHeight).toInt().coerceIn(0, srcHeight)
        val right = (box.x2 * srcWidth).toInt().coerceIn(left, srcWidth)
        val bottom = (box.y2 * srcHeight).toInt().coerceIn(top, srcHeight)

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        println("CROP >> Final = ${width}x${height}")

        return try {
            Bitmap.createBitmap(frame, left, top, width, height)
        } catch (e: Exception) {
            println("CROP >> ERROR: ${e.message}")
            null
        }
    }


    fun detectFrame(bitmap: Bitmap) {
        if (isPaused || isDetecting || !::detector.isInitialized) return
        isDetecting = true

        lastFrame = bitmap
        println("FRAME >> Received ${bitmap.width}x${bitmap.height}")

        scope.launch(Dispatchers.Default) {
            try {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
                detector.detect(scaledBitmap)
            } catch (e: Exception) {
                println("FRAME >> ERROR = ${e.message}")
            } finally {
                isDetecting = false
            }
        }
    }


    private fun areLabelsSimilar(l1: String, l2: String): Boolean {
        if (l1.isEmpty() || l2.isEmpty()) return false
        return l1.lowercase().contains(l2.lowercase()) ||
                l2.lowercase().contains(l1.lowercase())
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
