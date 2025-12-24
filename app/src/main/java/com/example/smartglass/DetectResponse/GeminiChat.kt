package com.example.smartglass.DetectResponse

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class GeminiChat(private val apiKey: String) {

    private val client = OkHttpClient()
    var handledByRealtime: Boolean = false
    private fun getApiUrl(modelName: String): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
    }

    private fun createJsonBody(prompt: String, enableGrounding: Boolean): RequestBody {
        val escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
        val jsonContents = """
            "contents": [
                {
                  "parts": [
                    { "text": "$escapedPrompt" }
                  ]
                }
            ]
        """.trimIndent()

        val jsonConfig = if (enableGrounding) {
            """,
            "config": {
              "tools": [
                { "googleSearch": {} }
              ]
            }
            """.trimIndent()
        } else ""

        val finalJsonBody = """
            {
              $jsonContents
              $jsonConfig
            }
        """.trimIndent()

        return finalJsonBody.toRequestBody("application/json".toMediaType())
    }

    //  GỬI SYNC
    fun sendMessageSync(prompt: String): String? {
        val body = createJsonBody(prompt, enableGrounding = false)
        val request = Request.Builder()
            .url(getApiUrl("gemini-2.5-flash"))
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
            return cleanResponse(textResponse)
        }
    }

    //  GỬI ASYNC
    fun sendMessageAsync(prompt: String, callback: (String?) -> Unit) {
        val body = createJsonBody(prompt, enableGrounding = false)
        val request = Request.Builder()
            .url(getApiUrl("gemini-2.5-flash"))
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

                    if (!response.isSuccessful || responseBody.isEmpty()) {
                        Log.e("GeminiChat", "Request thất bại hoặc body rỗng: ${response.code}")
                        callback(null)
                        return
                    }

                    try {
                        val textResponse = extractTextResponse(responseBody)
                        callback(cleanResponse(textResponse))
                    } catch (e: Exception) {
                        Log.e("GeminiChat", "Lỗi parse JSON: ${e.message}")
                        callback(null)
                    }
                }
            }
        })
    }

    //  GỬI CÓ GROUNDING GeminiPro
    fun sendMessageWithGrounding(prompt: String, callback: (String?) -> Unit) {
        val body = createJsonBody(prompt, enableGrounding = true)
        val request = Request.Builder()
            .url(getApiUrl("gemini-2.5-pro"))
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GeminiChat", "Lỗi Grounding: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful || responseBody.isEmpty()) {
                        callback(null)
                        return
                    }
                    try {
                        val textResponse = extractTextResponse(responseBody)
                        callback(cleanResponse(textResponse))
                    } catch (e: Exception) {
                        Log.e("GeminiChat", "Lỗi parse JSON (Grounding): ${e.message}")
                        callback(null)
                    }
                }
            }
        })
    }

    //  XỬ LÝ  THỜI GIAN, NGÀY, THỜI TIẾT, TIN TỨC
    private fun detectIntent(userMessage: String): String {
        val msg = userMessage.lowercase(Locale.getDefault())
        return when {
            listOf("mấy giờ", "giờ nào", "time").any { msg.contains(it) } -> "time"
            listOf("ngày mấy", "thứ mấy", "date").any { msg.contains(it) } -> "date"
            listOf("thời tiết", "nhiệt độ", "trời").any { msg.contains(it) } -> "weather"
            listOf("tin tức", "news", "có gì mới").any { msg.contains(it) } -> "news"
            else -> "normal"
        }
    }

    fun sendMessage(userMessage: String, onResult: (String) -> Unit) {
        when (detectIntent(userMessage)) {
            "time" -> onResult(getCurrentTime())
            "date" -> onResult(getCurrentDate())
            "weather" -> getWeather("Hanoi", onResult)
            "news" -> getNews(onResult)
            else -> sendMessageAsync(userMessage) { response ->
                onResult(response ?: "Không nhận được phản hồi từ Gemini.")
            }
        }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale("vi"))
        sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        return "Bây giờ là ${sdf.format(Date())}"
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("dd 'tháng' MM 'năm' yyyy", Locale("vi"))
        sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        return "Hôm nay là ${sdf.format(Date())}"
    }

    fun getWeather(city: String, callback: (String) -> Unit) {
        val url =
            "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=772d61d67d139b5973e3c923a171d1bc&units=metric&lang=vi"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Không lấy được thời tiết")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JsonParser.parseString(body).asJsonObject
                    val temp = json["main"].asJsonObject["temp"].asDouble
                    val desc = json["weather"].asJsonArray[0].asJsonObject["description"].asString
                    callback("Thời tiết hiện tại ở $city: $desc, $temp°C")
                } catch (e: Exception) {
                    callback("Không lấy được thông tin thời tiết")
                }
            }
        })
    }

    fun getNews(callback: (String) -> Unit) {
        val url = "https://newsapi.org/v2/top-headlines?country=vn&apiKey=38f0dc0cc99c9fcdc7d887c8627cc6f0"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Không lấy được tin tức")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    val json = JsonParser.parseString(body).asJsonObject
                    val articles = json["articles"].asJsonArray
                    if (articles.size() == 0) {
                        callback("Không có tin tức mới")
                        return
                    }
                    val first = articles[0].asJsonObject
                    val title = first["title"].asString
                    callback("Tin tức ngày nay: $title")
                } catch (e: Exception) {
                    callback("Không lấy được tin tức")
                }
            }
        })
    }

    //  PHÂN TÍCH INTENT
    fun analyzeIntent(command: String, callback: (List<Map<String, String>>?, String?) -> Unit) {
        if (handledByRealtime) {
            handledByRealtime = false
            callback(emptyList(), "")
            return
        }
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
                callback(resultList, responseText)
            } catch (e: Exception) {
                Log.e("GeminiChat", "Lỗi parse JSON intent: ${e.message}")
                callback(null, null)
            }
        }
    }

    private fun extractTextResponse(responseBody: String): String? {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val candidates = json["candidates"]?.asJsonArray
            if (candidates != null && candidates.size() > 0) {
                val content = candidates[0].asJsonObject["content"]?.asJsonObject
                val parts = content?.getAsJsonArray("parts")
                parts?.firstOrNull()?.asJsonObject?.get("text")?.asString
            } else null
        } catch (e: Exception) {
            Log.e("GeminiChat", "Lỗi extractTextResponse: ${e.message}")
            null
        }
    }

    private fun cleanResponse(text: String?): String? {
        if (text == null) return null
        return text.replace("**", "")
            .replace("__", "")
            .replace("*", "")
            .replace("_", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractValidJson(text: String): String {
        val regex = Regex("\\{[\\s\\S]*\\}")
        return regex.find(text)?.value ?: text
    }
}


