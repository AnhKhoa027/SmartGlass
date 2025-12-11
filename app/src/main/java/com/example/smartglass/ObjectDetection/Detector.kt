package com.example.smartglass.ObjectDetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
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

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String?,
    private val detectorListener: DetectorListener,
    private val message: (String) -> Unit
) {
    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()
    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STD))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val compatList = CompatibilityList()
        val options = Interpreter.Options().apply {
            if (compatList.isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            } else {
                setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()


        if (inputShape != null) {
            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]

            // If in case input shape is in format of [1, 3, ..., ...]
            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2]
                tensorHeight = inputShape[3]
            }
        }

        if (outputShape != null) {
            numChannel = outputShape[1]
            numElements = outputShape[2]
        }

        // Load labels
        labels.addAll(MetaData.extractNamesFromMetadata(model))
        if (labels.isEmpty()) {
            if (labelPath == null) {
                labels.addAll(MetaData.TEMP_CLASSES)
            } else {
                labels.addAll(MetaData.extractNamesFromLabelFile(context, labelPath))
            }
        }
    }

    /** Hàm detect bất đồng bộ (thread-safe) */
    fun detect(frame: Bitmap) {
        if (tensorWidth == 0 || tensorHeight == 0) return
        var inferenceTime = SystemClock.uptimeMillis()

        // Resize và convert bitmap
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        val inputBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(inputBitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // Output buffer chuẩn
        val outputShape = interpreter.getOutputTensor(0).shape()
        val output = TensorBuffer.createFixedSize(outputShape, OUTPUT_IMAGE_TYPE)

        // Thread-safe
        synchronized(interpreter) {
            interpreter.run(processedImage.buffer, output.buffer)
        }

        val bestBoxes = bestBox(output.floatArray)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (bestBoxes == null) {
            detectorListener.onEmptyDetect()
        } else {
            detectorListener.onDetect(bestBoxes, inferenceTime)
        }
    }

    /** Detect đồng bộ, trả kết quả trực tiếp */
    fun detectSynchronous(frame: Bitmap): List<BoundingBox>? {
        if (tensorWidth == 0 || tensorHeight == 0) return null

        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        val inputBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(inputBitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val output = TensorBuffer.createFixedSize(outputShape, OUTPUT_IMAGE_TYPE)

        synchronized(interpreter) {
            interpreter.run(processedImage.buffer, output.buffer)
        }

        return bestBox(output.floatArray)
    }

    /** Tìm bounding box tốt nhất từ output model */
    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (c in 0 until numElements) {
            var maxConf = CONF_THRESHOLD
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }
            if (maxConf > CONF_THRESHOLD) {
                val clsName = labels.getOrElse(maxIdx) { "Unknown" }
                val cx = array[c]; val cy = array[c + numElements]
                val w = array[c + numElements * 2]; val h = array[c + numElements * 3]
                val x1 = cx - (w / 2F); val y1 = cy - (h / 2F)
                val x2 = cx + (w / 2F); val y2 = cy + (h / 2F)
                if (x1 < 0F || x1 > 1F || y1 < 0F || y1 > 1F || x2 < 0F || x2 > 1F || y2 < 0F || y2 > 1F) continue
                boundingBoxes.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, clsName))
            }
        }
        return if (boundingBoxes.isEmpty()) null else applyNMS(boundingBoxes)
    }

    /** Non-Max Suppression */
    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()
        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)
            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                if (calculateIoU(first, iterator.next()) >= IOU_THRESHOLD) iterator.remove()
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1); val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2); val y2 = minOf(box1.y2, box2.y2)
        val inter = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val b1 = box1.w * box1.h; val b2 = box2.w * box2.h
        return inter / (b1 + b2 - inter)
    }

    fun close() = interpreter.close()

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STD = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONF_THRESHOLD = 0.5F
        private const val IOU_THRESHOLD = 0.5F
    }
}
