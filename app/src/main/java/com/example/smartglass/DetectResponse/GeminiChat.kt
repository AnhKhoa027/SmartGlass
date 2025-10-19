package com.example.smartglass.DetectResponse

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class GeminiChat(private val apiKey: String) {

    private val client = OkHttpClient()
    private val apiUrl =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

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
    fun analyzeUserCommand(command: String, callback: (String?) -> Unit) {
        val prompt = """
        Câu nói: "$command"
    """.trimIndent()

        sendMessageAsync(prompt) { response ->
            callback(response)
        }
    }
}

