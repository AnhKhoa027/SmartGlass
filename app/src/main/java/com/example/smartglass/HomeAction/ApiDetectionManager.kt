package com.example.smartglass.HomeAction

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ApiDetectionManager(
    private val apiUrl: String = "http://192.168.1.8:8000/predict"
) {

    data class BoundingBoxAPI(
        val label: String,
        val score: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val w: Float,
        val h: Float
    )

    /** Chuyển Bitmap -> JPEG byte array */
    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    /** Gọi API detect object */
    suspend fun detectFrame(bitmap: Bitmap): List<BoundingBoxAPI> =
        withContext(Dispatchers.IO) {

            val result = mutableListOf<BoundingBoxAPI>()

            try {
                val jpegBytes = bitmapToJpegBytes(bitmap)

                val boundary = UUID.randomUUID().toString()
                val line = "\r\n"
                val two = "--"

                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    doInput = true
                    doOutput = true
                }

                val output = DataOutputStream(conn.outputStream)

                // ----- Gửi lên API -----
                output.writeBytes("$two$boundary$line")
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"frame.jpg\"$line"
                )
                output.writeBytes("Content-Type: image/jpeg$line$line")
                output.write(jpegBytes)
                output.writeBytes(line)

                // ----- Kết thúc request -----
                output.writeBytes("$two$boundary$two$line")
                output.flush()
                output.close()

                // ----- Đọc response -----
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(responseText)
                val arr = json.getJSONArray("predictions")

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val box = obj.getJSONArray("box")

                    val x1 = box.getDouble(0).toFloat()
                    val y1 = box.getDouble(1).toFloat()
                    val x2 = box.getDouble(2).toFloat()
                    val y2 = box.getDouble(3).toFloat()

                    val w = x2 - x1
                    val h = y2 - y1

                    result.add(
                        BoundingBoxAPI(
                            label = obj.getString("label"),
                            score = obj.getDouble("score").toFloat(),
                            x1 = x1,
                            y1 = y1,
                            x2 = x2,
                            y2 = y2,
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
