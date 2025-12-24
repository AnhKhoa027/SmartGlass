package com.example.smartglass.EmergencyCall


import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException


object NetworkHelper {
    private val client = OkHttpClient()

    fun fetchZoomLink(url: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, "")) // POST rỗng
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NetworkHelper", "fetchZoomLink failed: ${e.message}")
                postOnMain { callback(null) }
            }


            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("NetworkHelper", "fetchZoomLink bad response")
                        postOnMain { callback(null) }
                        return
                    }


                    try {
                        val json = JSONObject(it.body!!.string())
                        val link = json.optString("join_url", null)
                        postOnMain { callback(link) }
                    } catch (e: Exception) {
                        Log.e("NetworkHelper", "parseZoomLink error: ${e.message}")
                        postOnMain { callback(null) }
                    }
                }
            }
        })
    }


    private fun postOnMain(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post { action() }
    }
}
