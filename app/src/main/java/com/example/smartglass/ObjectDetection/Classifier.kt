package com.example.smartglass.ObjectDetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

/**
 * Classifier (Fallback)
 * ---------------------
 * Model phân loại vật thể đơn giản dùng sau YOLO và API.
 */
class Classifier(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String? = null
) {
    private val interpreter: Interpreter
    private val labels = mutableListOf<String>()

    init {
        interpreter = Interpreter(loadModelFile(context, modelPath))

        // Đọc nhãn từ file nếu có
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
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Phân loại ảnh → Trả về Pair(label, confidence)
     */
    fun classify(bitmap: Bitmap): Pair<String, Float> {
        val inputSize = 128 // chỉnh theo model
        val inputImage = TensorImage(DataType.FLOAT32)
        inputImage.load(Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true))

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)

        interpreter.run(inputImage.buffer, outputBuffer.buffer.rewind())

        val outputArray = outputBuffer.floatArray
        val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
        val confidence = outputArray[maxIndex]
        val label = labels.getOrElse(maxIndex) { "Unknown" }

        return label to confidence
    }

    fun close() = interpreter.close()
}
