package com.example.smartglass.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log
import java.net.URLEncoder

class WebSearchHelper(
    private val apiKey: String,
    private val searchEngineId: String
) {
    private val client = OkHttpClient()
    fun search(query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val filteredQuery = "$encodedQuery site:gov.vn OR site:org.vn OR site:edu.vn"
        val url =
            "https://www.googleapis.com/customsearch/v1?q=$filteredQuery&key=$apiKey&cx=$searchEngineId&lr=lang_vi"

        Log.d("WebSearchHelper", "URL tìm kiếm: $url")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("WebSearchHelper", "Lỗi HTTP: ${response.code}")
                    return "Không thể kết nối tới dịch vụ tìm kiếm (HTTP ${response.code})."
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    Log.e("WebSearchHelper", "Phản hồi rỗng từ Google Search API.")
                    return "Không có dữ liệu tìm kiếm từ web."
                }

                val json = JSONObject(body)
                val items = json.optJSONArray("items")
                if (items == null || items.length() == 0) {
                    Log.w("WebSearchHelper", "Không có kết quả nào trong phản hồi.")
                    return "Không tìm thấy kết quả phù hợp từ web."
                }

                val results = StringBuilder()
                for (i in 0 until minOf(items.length(), 3)) {
                    val item = items.getJSONObject(i)
                    val title = item.optString("title", "Không tiêu đề")
                    val snippet = item.optString("snippet", "")
                    val link = item.optString("link", "")
                    results.append("• $title\n$snippet\n$link\n\n")
                }

                val finalText = results.toString().trim()
                Log.d("WebSearchHelper", "Kết quả web:\n$finalText")

                return if (finalText.isNotBlank())
                    finalText
                else
                    "Không tìm thấy thông tin từ các nguồn chính thống."
            }
        } catch (e: Exception) {
            Log.e("WebSearchHelper", "Lỗi khi gọi API: ${e.message}")
            return "Không thể lấy dữ liệu web: ${e.message}"
        }
    }
}
