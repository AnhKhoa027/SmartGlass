package com.example.smartglass.ai

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonParser
import java.io.IOException
import android.util.Log

class GeminiChat(private val apiKey: String) {

    private val client = OkHttpClient()
    private val apiUrl =
//        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"

    /**
     * Gọi API đồng bộ (blocking)
     */
    fun sendMessageSync(prompt: String): String? {
        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "$prompt" }
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
            if (!response.isSuccessful) {
                Log.e("GeminiChat", "Request failed: ${response.code}")
                Log.e("GeminiChat", response.body?.string() ?: "No body")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            Log.d("GeminiRaw", "Gemini raw response: $responseBody")

            val textResponse = extractTextResponse(responseBody)
            Log.d("GeminiChat", "Gemini trả lời: $textResponse")

            return textResponse
        }
    }

    /**
     * Gọi API bất đồng bộ (dành cho Android UI)
     */
    fun sendMessageAsync(prompt: String, callback: (String?) -> Unit) {
        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "$prompt" }
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
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: ""
                    Log.d("GeminiRaw", "Gemini raw response: $responseBody")

                    if (!response.isSuccessful) {
                        Log.e("GeminiChat", "Request failed: ${response.code}")
                        callback(null)
                        return
                    }

                    try {
                        val textResponse = extractTextResponse(responseBody)
                        Log.d("GeminiChat", "Gemini trả lời: $textResponse")

                        val cleaned = cleanResponse(textResponse)
                        callback(cleaned)
                    } catch (e: Exception) {
                        Log.e("GeminiChat", "Lỗi khi parse JSON: ${e.message}")
                        callback(null)
                    }
                }
            }
        })
    }

    /**
     * Trích xuất nội dung text từ JSON phản hồi
     */
    private fun extractTextResponse(responseBody: String): String? {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            json["candidates"]
                ?.asJsonArray?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject?.get("text")?.asString
        } catch (e: Exception) {
            Log.e("GeminiChat", "Lỗi extractTextResponse: ${e.message}")
            null
        }
    }

    /**
     * Loại bỏ ký tự Markdown, khoảng trắng thừa
     */
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
}