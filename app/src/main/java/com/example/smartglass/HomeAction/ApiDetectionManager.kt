package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * ApiDetectionManager
 * -------------------
 * Dùng HuggingFace API (DETR) để detect object khi YOLO local không ra kết quả.
 */
class ApiDetectionManager(
    private val context: Context,
    private val apiUrl: String = "https://api-inference.huggingface.co/models/facebook/detr-resnet-50",
    private val apiToken: String
) {
    data class BoundingBoxAPI(
        val label: String,
        val score: Float,
        val x: Float, val y: Float,
        val w: Float, val h: Float
    )

    /** Chuyển bitmap sang JPEG bytes */
    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    /** Gọi HuggingFace API detect object */
    suspend fun detectFrame(bitmap: Bitmap): List<BoundingBoxAPI> = withContext(Dispatchers.IO) {
        val result = mutableListOf<BoundingBoxAPI>()
        try {
            val jpegBytes = bitmapToJpegBytes(bitmap)

            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiToken")
                setRequestProperty("Content-Type", "image/jpeg")
                doOutput = true
                outputStream.use { it.write(jpegBytes) }
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val arr = JSONArray(responseText)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val box = obj.getJSONObject("box")
                result.add(
                    BoundingBoxAPI(
                        label = obj.getString("label"),
                        score = obj.getDouble("score").toFloat(),
                        x = box.getDouble("xmin").toFloat(),
                        y = box.getDouble("ymin").toFloat(),
                        w = (box.getDouble("xmax") - box.getDouble("xmin")).toFloat(),
                        h = (box.getDouble("ymax") - box.getDouble("ymin")).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result
    }
}
