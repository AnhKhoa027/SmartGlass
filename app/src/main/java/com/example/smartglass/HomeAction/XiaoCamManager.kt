package com.example.smartglass.HomeAction

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest

/**
 * Xiaocam 32S3 Manager
 * -------------------
 * Chịu trách nhiệm quản lý việc KẾT NỐI tới Xiaocam 32S3.
 * Được sử dụng trong HomeFragment.
 *
 * - Gọi từ HomeFragment khi người dùng bấm "Kết nối" hoặc "Ngắt kết nối".
 * - Liên quan tới: HomeFragment (nơi điều phối), Volley (thư viện HTTP).
 */
class XiaoCamManager(
    private val context: Context,
    private val requestQueue: RequestQueue,
    private val ipProvider: () -> String // Lấy IP ESP32 từ SharedPreferences
) {
    var isConnected = false
        private set

    /** Xây dựng URL tới Xiaocam 32S3 */
    fun buildUrl(path: String) = "http://${ipProvider()}/$path"

    /**
     * Kết nối tới Xiaocam 32S3 (thực hiện HTTP GET /capture).
     * - Thành công => gọi onSuccess()
     * - Thất bại => gọi onFailure()
     */
    fun connect(
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val request = StringRequest(
            Request.Method.GET, buildUrl("capture"),
            {
                isConnected = true
                onSuccess()
            },
            {
                isConnected = false
                onFailure()
            }
        )
        requestQueue.add(request)
    }

    /** Ngắt kết nối Xiaocam 32S3 */
    fun disconnect() {
        isConnected = false
    }
}
