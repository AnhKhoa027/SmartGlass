package com.example.smartglass.ObjectDetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class Classifier(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String? = null
) {
    private val interpreter: Interpreter
    private val labels = mutableListOf<String>()
    private val inputSize = 128
    private val imageProcessor: ImageProcessor

    init {
        val options = Interpreter.Options().apply { setNumThreads(2) }
        interpreter = Interpreter(loadModelFile(context, modelPath), options)

        if (labelPath != null) {
            try { labels.addAll(FileUtil.loadLabels(context, labelPath)) }
            catch (e: Exception) { labels.addAll(listOf("Unknown")) }
        } else { labels.addAll(listOf("Class1", "Class2")) }

        // Logic xử lý ảnh: Tương đương 100% code thủ công cũ nhưng nhanh hơn
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(TransformToGrayscaleOp()) // Chuyển sang đen trắng
            .add(NormalizeOp(0f, 255f))    // Chia 255f
            .add(CastOp(DataType.FLOAT32))
            .build()
    }

    fun classify(bitmap: Bitmap): Pair<String, Float> {
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)
        interpreter.run(processedImage.buffer, outputBuffer.buffer)

        val outputs = outputBuffer.floatArray
        val maxIdx = outputs.indices.maxByOrNull { outputs[it] } ?: -1

        return if (maxIdx != -1 && labels.isNotEmpty()) {
            labels.getOrElse(maxIdx) { "Unknown" } to outputs[maxIdx]
        } else "Unknown" to 0f
    }

    fun close() = interpreter.close()

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}