//package com.example.smartglass.EmergencyCall
//
//import android.util.Log
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.RequestBody.Companion.toRequestBody
//import java.io.IOException
//
//object EsmsCallHelper {
//
//    private const val API_KEY = "C277B4DEEB3132CC29AEDE2876B517"
//    private const val SECRET_KEY = "9FEF714ECE822D242B291C7222922B"
//    private const val URL = "https://rest.esms.vn/MainService.svc/json/MakeCall"
//
//    private val client = OkHttpClient()
//
//    /**
//     * Gọi nhiều số cùng lúc qua eSMS
//     * @param phones danh sách số đã verify trong eSMS trial
//     * @param content nội dung thông báo
//     */
//    fun callMultiplePhones(phones: List<String>, content: String) {
//        val validPhones = phones.filter { it.isNotBlank() }
//        if (validPhones.isEmpty()) {
//            Log.w("EsmsCallHelper", "Không có số hợp lệ để gọi")
//            return
//        }
//
//        val phoneString = validPhones.joinToString(",")
//
//        val json = """
//            {
//              "ApiKey": "$API_KEY",
//              "SecretKey": "$SECRET_KEY",
//              "Phone": "$phoneString",
//              "Content": "$content"
//            }
//        """.trimIndent()
//
//        val body = json.toRequestBody("application/json".toMediaType())
//        val request = Request.Builder()
//            .url(URL)
//            .post(body)
//            .build()
//
//        client.newCall(request).enqueue(object : okhttp3.Callback {
//            override fun onFailure(call: okhttp3.Call, e: IOException) {
//                Log.e("EsmsCallHelper", "Gọi eSMS thất bại: ${e.message}")
//            }
//
//            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
//                response.use {
//                    if (!it.isSuccessful) {
//                        Log.e("EsmsCallHelper", "Gọi eSMS thất bại, code: ${it.code}")
//                        return
//                    }
//                    val respBody = it.body?.string()
//                    Log.d("EsmsCallHelper", "Gọi eSMS thành công: $respBody")
//                }
//            }
//        })
//    }
//}
