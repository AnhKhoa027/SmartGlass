//package com.example.smartglass.EmergencyCall
//
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import okhttp3.*
//import okhttp3.MediaType.Companion.toMediaTypeOrNull
//import org.json.JSONObject
//import java.io.IOException
//
//object NetworkHelper {
//    private val client = OkHttpClient()
//
//    fun fetchToken(serverUrl: String, channelName: String, uid: Int = 0, expireInSeconds: Int = 300, callback: (String?) -> Unit) {
//        val jsonBody = JSONObject().apply {
//            put("channelName", channelName)
//            put("uid", uid)
//            put("expireInSeconds", expireInSeconds)
//        }
//
//        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody.toString())
//        val request = Request.Builder()
//            .url("$serverUrl/getToken")
//            .post(requestBody)
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e("NetworkHelper", "fetchToken failed: ${e.message}")
//                postOnMain { callback(null) }
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.use {
//                    val body = it.body?.string()
//                    if (!it.isSuccessful || body == null) {
//                        Log.e("NetworkHelper", "fetchToken bad response")
//                        postOnMain { callback(null) }
//                        return
//                    }
//
//                    try {
//                        val json = JSONObject(body)
//                        val token = json.optString("token", null)
//                        postOnMain { callback(token) }
//                    } catch (e: Exception) {
//                        Log.e("NetworkHelper", "parse token error: ${e.message}")
//                        postOnMain { callback(null) }
//                    }
//                }
//            }
//        })
//    }
//
//    private fun postOnMain(action: () -> Unit) {
//        Handler(Looper.getMainLooper()).post { action() }
//    }
//    fun fetchZoomLink(url: String, callback: (String?) -> Unit) {
//        val client = OkHttpClient()
//        val request = Request.Builder().url(url).post(RequestBody.create(null, "")).build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                callback(null)
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.use {
//                    if (!it.isSuccessful) { callback(null); return }
//                    val json = JSONObject(it.body!!.string())
//                    val link = json.optString("join_url", null)
//                    callback(link)
//                }
//            }
//        })
//    }
//
//}

//package com.example.smartglass.EmergencyCall
//
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import okhttp3.*
//import org.json.JSONObject
//import java.io.IOException
//
//object NetworkHelper {
//    private val client = OkHttpClient()
//
//    fun fetchZoomLink(url: String, callback: (String?) -> Unit) {
//        val request = Request.Builder()
//            .url(url)
//            .post(RequestBody.create(null, "")) // POST rỗng
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e("NetworkHelper", "fetchZoomLink failed: ${e.message}")
//                postOnMain { callback(null) }
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.use {
//                    if (!it.isSuccessful) {
//                        Log.e("NetworkHelper", "fetchZoomLink bad response")
//                        postOnMain { callback(null) }
//                        return
//                    }
//
//                    try {
//                        val json = JSONObject(it.body!!.string())
//                        val link = json.optString("join_url", null)
//                        postOnMain { callback(link) }
//                    } catch (e: Exception) {
//                        Log.e("NetworkHelper", "parseZoomLink error: ${e.message}")
//                        postOnMain { callback(null) }
//                    }
//                }
//            }
//        })
//    }
//
//    private fun postOnMain(action: () -> Unit) {
//        Handler(Looper.getMainLooper()).post { action() }
//    }
//}

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
