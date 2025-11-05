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

    // ----------------------  HÀM PHÂN TÍCH JSON ----------------------
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

    // ---------------------- TÁCH PHẦN JSON HỢP LỆ ----------------------
    private fun extractValidJson(text: String): String {
        val regex = Regex("\\{[\\s\\S]*\\}")
        return regex.find(text)?.value ?: text
    }

    // ----------------------  HÀM PHÂN TÍCH LỆNH ----------------------
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

    fun analyzeIntent(command: String, callback: (List<Map<String, String>>?, String?) -> Unit) {

        val prompt = """
            Bạn là hệ thống hiểu ngôn ngữ tự nhiên cho kính thông minh NANA.
            Hãy phân tích câu nói sau và trả về JSON hợp lệ.
            
            Câu nói: "$command"
            
            Cấu trúc JSON:
            {
              "actions": [
                { "intent": "navigate", "target": "cài đặt", "value": "" },
                { "intent": "adjust", "target": "âm lượng", "value": "giảm" }
              ],
              "response": "Tôi hiểu. Đang mở phần cài đặt và giảm âm lượng xuống 80%."
            }
            
            - "actions": danh sách hành động cần thực hiện.
            - "response": câu phản hồi tự nhiên NANA sẽ nói.
            Trả về đúng JSON, không thêm chữ nào khác.
            """.trimIndent()

        sendMessageAsync(prompt) { response ->
            if (response == null) {
                callback(null, null)
                return@sendMessageAsync
            }
            try {
                val cleanJson = extractValidJson(response)
                Log.d("GeminiChat", "Chuỗi JSON trích được: $cleanJson")

                val json = JsonParser.parseString(cleanJson).asJsonObject
                val actions = json["actions"]?.asJsonArray
                val responseText = json["response"]?.asString ?: ""

                val resultList = mutableListOf<Map<String, String>>()
                actions?.forEach { el ->
                    val obj = el.asJsonObject
                    val map = mutableMapOf<String, String>()
                    map["intent"] = obj["intent"]?.asString ?: ""
                    map["target"] = obj["target"]?.asString ?: ""
                    map["value"] = obj["value"]?.asString ?: ""
                    resultList.add(map)
                }

                Log.d("GeminiChat", "Danh sách actions: $resultList")
                callback(resultList, responseText)  //Đổi callback ở đây
            } catch (e: Exception) {
                Log.e("GeminiChat", "Lỗi parse JSON intent: ${e.message}")
                callback(null, null)
            }

        }
    }
}
