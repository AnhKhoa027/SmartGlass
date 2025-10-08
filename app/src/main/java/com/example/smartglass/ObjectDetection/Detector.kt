package com.example.smartglass.ObjectDetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

/**
 * Detector (Optimized)
 * ---------------------
 * Thực hiện YOLOv8 inference (TensorFlow Lite)
 */
class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String?,
    private val detectorListener: DetectorListener,
    private val message: (String) -> Unit
) {
    private var interpreter: Interpreter
    private val labels = mutableListOf<String>()

    private var tensorWidth = 640
    private var tensorHeight = 640
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STD))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val compat = CompatibilityList()
        val options = Interpreter.Options().apply {
            if (compat.isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(compat.bestOptionsForThisDevice))
            } else {
                setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        if (inputShape[1] == 3) {
            tensorWidth = inputShape[2]
            tensorHeight = inputShape[3]
        }

        numChannel = outputShape[1]
        numElements = outputShape[2]

        // Labels từ metadata hoặc file
        labels.addAll(MetaData.extractNamesFromMetadata(model))
        if (labels.isEmpty()) {
            labelPath?.let {
                labels.addAll(MetaData.extractNamesFromLabelFile(context, it))
            } ?: run {
                message("⚠️ Model không có metadata, dùng nhãn tạm.")
                labels.addAll(MetaData.TEMP_CLASSES)
            }
        }

        message("🎯 Model loaded: $modelPath (${tensorWidth}x${tensorHeight})")
    }

    fun detect(frame: Bitmap) {
        if (tensorWidth == 0 || tensorHeight == 0) {
            message("⚠️ Model chưa sẵn sàng.")
            return
        }

        val start = SystemClock.uptimeMillis()

        // Scale ảnh ở đây (chỉ 1 lần duy nhất)
        val resized = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resized)
        val processed = imageProcessor.process(tensorImage)

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter.run(processed.buffer, output.buffer)

        val boxes = extractBoxes(output.floatArray)
        val duration = SystemClock.uptimeMillis() - start

        if (boxes.isEmpty()) detectorListener.onEmptyDetect()
        else detectorListener.onDetect(boxes, duration)
    }

    private fun extractBoxes(array: FloatArray): List<BoundingBox> {
        val boxes = mutableListOf<BoundingBox>()

        for (c in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var clsIdx = -1
            var offset = 4
            var arrIdx = c + numElements * offset
            while (offset < numChannel) {
                if (array[arrIdx] > maxConf) {
                    maxConf = array[arrIdx]
                    clsIdx = offset - 4
                }
                offset++
                arrIdx += numElements
            }

            if (clsIdx != -1 && maxConf > CONFIDENCE_THRESHOLD) {
                val cx = array[c]
                val cy = array[c + numElements]
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]
                val x1 = cx - w / 2f
                val y1 = cy - h / 2f
                val x2 = cx + w / 2f
                val y2 = cy + h / 2f

                if (x1 in 0f..1f && y1 in 0f..1f && x2 in 0f..1f && y2 in 0f..1f) {
                    boxes.add(
                        BoundingBox(
                            x1, y1, x2, y2, cx, cy, w, h,
                            cnf = maxConf,
                            cls = clsIdx,
                            clsName = labels.getOrElse(clsIdx) { "Unknown" }
                        )
                    )
                }
            }
        }

        return applyNMS(boxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sorted = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selected = mutableListOf<BoundingBox>()

        while (sorted.isNotEmpty()) {
            val first = sorted.removeAt(0)
            selected.add(first)

            val it = sorted.iterator()
            while (it.hasNext()) {
                val next = it.next()
                val iou = calculateIoU(first, next)
                if (iou >= IOU_THRESHOLD) it.remove()
            }
        }
        return selected
    }

    private fun calculateIoU(b1: BoundingBox, b2: BoundingBox): Float {
        val x1 = maxOf(b1.x1, b2.x1)
        val y1 = maxOf(b1.y1, b2.y1)
        val x2 = minOf(b1.x2, b2.x2)
        val y2 = minOf(b1.y2, b2.y2)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = b1.w * b1.h + b2.w * b2.h - inter
        return inter / union
    }

    fun close() = interpreter.close()

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STD = 255f
        private val INPUT_IMAGE_TYPE = DataType.UINT8
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val IOU_THRESHOLD = 0.5f
    }
}
