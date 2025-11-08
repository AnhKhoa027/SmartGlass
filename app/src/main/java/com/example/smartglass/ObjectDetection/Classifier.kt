package com.example.smartglass.ObjectDetection

import android.content.Context
import android.graphics.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.nio.*
import java.nio.channels.FileChannel
import java.io.FileInputStream

/**
 * Classifier (for grayscale model: input [1,128,128,1])
 * ----------------------------------------------------
 * Model phân loại vật thể đơn giản sau YOLO hoặc dùng độc lập.
 * Dành cho model_meta.tflite (float32 grayscale, 3 lớp)
 */
class Classifier(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String? = null
) {
    private val interpreter: Interpreter
    private val labels = mutableListOf<String>()
    private val inputSize = 128
    private val numChannels = 1

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(2)
            setUseNNAPI(true)
        }
        interpreter = Interpreter(loadModelFile(context, modelPath), options)

        // Đọc label file
        if (labelPath != null) {
            try {
                labels.addAll(FileUtil.loadLabels(context, labelPath))
            } catch (e: Exception) {
                e.printStackTrace()
                labels.addAll(listOf("Unknown1", "Unknown2", "Unknown3"))
            }
        } else {
            labels.addAll(listOf("Class1", "Class2", "Class3"))
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    /**
     * Chuyển ảnh sang grayscale ByteBuffer để feed vào model
     */
    private fun convertToGrayscaleBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * numChannels)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            buffer.putFloat(gray)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Phân loại ảnh → Trả về Pair(label, confidence)
     */
    fun classify(bitmap: Bitmap): Pair<String, Float> {
        val inputBuffer = convertToGrayscaleBuffer(bitmap)
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)

        interpreter.run(inputBuffer, outputBuffer.buffer)

        val outputs = outputBuffer.floatArray
        val maxIdx = outputs.indices.maxByOrNull { outputs[it] } ?: 0
        val confidence = outputs[maxIdx]
        val label = labels.getOrElse(maxIdx) { "Unknown" }

        println("Classifier → $label (${String.format("%.2f", confidence)})")
        return label to confidence
    }

    fun close() = interpreter.close()
}
