//package com.example.smartglass.DetectResponse
//
//import android.util.Log
//import com.google.gson.JsonParser
//import okhttp3.Call
//import okhttp3.Callback
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.RequestBody.Companion.toRequestBody
//import okhttp3.Response
//import java.io.IOException
//
//class GeminiChat(private val apiKey: String) {
//
//    private val client = OkHttpClient()
//    private val apiUrl =
//        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
//
//    fun sendMessageSync(prompt: String): String? {
//        val jsonBody = """
//            {
//              "contents": [
//                {
//                  "parts": [
//                    { "text": "$prompt" }
//                  ]
//                }
//              ]
//            }
//        """.trimIndent()
//
//        val body = jsonBody.toRequestBody("application/json".toMediaType())
//
//        val request = Request.Builder()
//            .url(apiUrl)
//            .addHeader("x-goog-api-key", apiKey)
//            .addHeader("Content-Type", "application/json")
//            .post(body)
//            .build()
//
//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) {
//                Log.e("GeminiChat", "Request failed: ${response.code}")
//                Log.e("GeminiChat", response.body?.string() ?: "No body")
//                return null
//            }
//
//            val responseBody = response.body?.string() ?: return null
//            Log.d("GeminiRaw", "Gemini raw response: $responseBody")
//
//            val textResponse = extractTextResponse(responseBody)
//            Log.d("GeminiChat", "Gemini trả lời: $textResponse")
//
//            return textResponse
//        }
//    }
//
//    fun sendMessageAsync(prompt: String, callback: (String?) -> Unit) {
//        val jsonBody = """
//            {
//              "contents": [
//                {
//                  "parts": [
//                    { "text": "$prompt" }
//                  ]
//                }
//              ]
//            }
//        """.trimIndent()
//
//        val body = jsonBody.toRequestBody("application/json".toMediaType())
//        val request = Request.Builder()
//            .url(apiUrl)
//            .addHeader("x-goog-api-key", apiKey)
//            .addHeader("Content-Type", "application/json")
//            .post(body)
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                e.printStackTrace()
//                callback(null)
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.use {
//                    val responseBody = response.body?.string() ?: ""
//                    Log.d("GeminiRaw", "Gemini raw response: $responseBody")
//
//                    if (!response.isSuccessful) {
//                        Log.e("GeminiChat", "Request failed: ${response.code}")
//                        callback(null)
//                        return
//                    }
//
//                    try {
//                        val textResponse = extractTextResponse(responseBody)
//                        Log.d("GeminiChat", "Gemini trả lời: $textResponse")
//
//                        val cleaned = cleanResponse(textResponse)
//                        callback(cleaned)
//                    } catch (e: Exception) {
//                        Log.e("GeminiChat", "Lỗi khi parse JSON: ${e.message}")
//                        callback(null)
//                    }
//                }
//            }
//        })
//    }
//
//    private fun extractTextResponse(responseBody: String): String? {
//        return try {
//            val json = JsonParser.parseString(responseBody).asJsonObject
//            json["candidates"]
//                ?.asJsonArray?.get(0)?.asJsonObject
//                ?.getAsJsonObject("content")?.getAsJsonArray("parts")
//                ?.get(0)?.asJsonObject?.get("text")?.asString
//        } catch (e: Exception) {
//            Log.e("GeminiChat", "Lỗi extractTextResponse: ${e.message}")
//            null
//        }
//    }
//    private fun cleanResponse(text: String?): String? {
//        if (text == null) return null
//        return text
//            .replace("**", "")
//            .replace("__", "")
//            .replace("*", "")
//            .replace("_", "")
//            .replace(Regex("\\s+"), " ")
//            .trim()
//    }
//    fun analyzeUserCommand(command: String, callback: (String?) -> Unit) {
//        val prompt = """
//        Câu nói: "$command"
//    """.trimIndent()
//
//        sendMessageAsync(prompt) { response ->
//            callback(response)
//        }
//    }
//}


package com.example.smartglass.DetectResponse

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class GeminiChat(private val apiKey: String) {

    private val client = OkHttpClient()
    private val apiUrl =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    // ---------------------- 🔹 GỬI ĐỒNG BỘ ----------------------
    fun sendMessageSync(prompt: String): String? {
        val escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "$escapedPrompt" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            Log.d("GeminiRaw", "Gemini raw (sync): $responseBody")

            if (!response.isSuccessful) {
                Log.e("GeminiChat", "Request failed: ${response.code}")
                return null
            }

            val textResponse = extractTextResponse(responseBody)
            val cleaned = cleanResponse(textResponse)
            Log.d("GeminiChat", "Gemini trả lời: $cleaned")
            return cleaned
        }
    }

    // ---------------------- 🔹 GỬI BẤT ĐỒNG BỘ ----------------------
    fun sendMessageAsync(prompt: String, callback: (String?) -> Unit) {
        val escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "$escapedPrompt" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GeminiChat", "Lỗi mạng hoặc request: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: ""
                    Log.d("GeminiRaw", "Gemini raw (async): $responseBody")

                    if (!response.isSuccessful) {
                        Log.e("GeminiChat", "Request failed: ${response.code}")
                        callback(null)
                        return
                    }

                    if (responseBody.isEmpty()) {
                        Log.e("GeminiChat", "Response body rỗng.")
                        callback(null)
                        return
                    }

                    try {
                        val textResponse = extractTextResponse(responseBody)
                        val cleaned = cleanResponse(textResponse)
                        Log.d("GeminiChat", "Gemini trả lời: $cleaned")
                        callback(cleaned)
                    } catch (e: Exception) {
                        Log.e("GeminiChat", "Lỗi khi parse JSON: ${e.message}")
                        callback(null)
                    }
                }
            }
        })
    }

    // ---------------------- 🔹 HÀM PHÂN TÍCH JSON ----------------------
    private fun extractTextResponse(responseBody: String): String? {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val candidates = json["candidates"]?.asJsonArray
            if (candidates != null && candidates.size() > 0) {
                val content = candidates[0].asJsonObject["content"]?.asJsonObject
                val parts = content?.getAsJsonArray("parts")
                val firstText = parts?.firstOrNull()?.asJsonObject?.get("text")?.asString
                firstText ?: "Không có phản hồi từ Gemini."
            } else {
                Log.e("GeminiChat", "Không tìm thấy trường 'candidates'.")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiChat", "Lỗi extractTextResponse: ${e.message}")
            null
        }
    }

    // ---------------------- 🔹 LÀM SẠCH PHẢN HỒI ----------------------
    private fun cleanResponse(text: String?): String? {
        if (text == null) return null
        return text
            .replace("**", "")
            .replace("__", "")
            .replace("*", "")
            .replace("_", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ---------------------- 🔹 HÀM PHÂN TÍCH LỆNH ----------------------
    fun analyzeUserCommand(command: String, callback: (String?) -> Unit) {
        val prompt = """
        Câu nói: "$command"
        Hãy trích ra từ khóa chính, ví dụ: "âm lượng", "tốc độ", "vị trí", "kết nối", "trang chủ", "cài đặt".
        Chỉ trả về đúng từ khóa, không thêm giải thích.
        """.trimIndent()

        sendMessageAsync(prompt) { response ->
            callback(response)
        }
    }
}
