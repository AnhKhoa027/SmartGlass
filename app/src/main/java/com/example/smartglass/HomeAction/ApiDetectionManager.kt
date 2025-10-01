package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ApiDetectionManager(
    private val context: Context,
    private val apiUrl: String = "http://192.168.1.8:8000/predict" // server local
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

    /** Gọi API detect object (multipart/form-data) */
    suspend fun detectFrame(bitmap: Bitmap): List<BoundingBoxAPI> = withContext(Dispatchers.IO) {
        val result = mutableListOf<BoundingBoxAPI>()
        try {
            val jpegBytes = bitmapToJpegBytes(bitmap)

            val boundary = UUID.randomUUID().toString()
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                doOutput = true
                doInput = true
            }

            val outputStream = DataOutputStream(conn.outputStream)

            // --- Gửi file ---
            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes(
                "Content-Disposition: form-data; name=\"file\"; filename=\"frame.jpg\"$lineEnd"
            )
            outputStream.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")
            outputStream.write(jpegBytes)
            outputStream.writeBytes(lineEnd)

            // --- Kết thúc multipart ---
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            outputStream.flush()
            outputStream.close()

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            // Parse JSON theo format: score, label, location
            val arr = JSONArray(responseText)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val location = obj.getJSONArray("location")

                val x = location.getDouble(0).toFloat()
                val y = location.getDouble(1).toFloat()
                val w = location.getDouble(2).toFloat()
                val h = location.getDouble(3).toFloat()

                result.add(
                    BoundingBoxAPI(
                        label = obj.getString("label"),
                        score = obj.getDouble("score").toFloat(),
                        x = x,
                        y = y,
                        w = w,
                        h = h
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result
    }
}
