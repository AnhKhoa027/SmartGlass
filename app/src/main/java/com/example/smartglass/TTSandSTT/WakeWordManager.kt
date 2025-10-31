package com.example.smartglass.TTSandSTT

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log
import java.io.IOException

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val keywordFile: String,
    private val sensitivity: Float = 0.6f,
    private val onWakeWordDetected: () -> Unit
) {

    private var porcupineManager: PorcupineManager? = null

    private var lastDetectedTime = 0L
    private val debounceMs = 2000L

    fun startListening() {
        try {
            if (porcupineManager == null) {
                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath(keywordFile)
                    .setSensitivity(sensitivity)
                    .build(context, object : PorcupineManagerCallback {
                        override fun invoke(keywordIndex: Int) {
                            val now = System.currentTimeMillis()
                            if (now - lastDetectedTime < debounceMs) {
                                Log.d("WakeWord", "Bỏ qua wake word do vừa kích hoạt gần đây (${now - lastDetectedTime}ms).")
                                return
                            }
                            lastDetectedTime = now
                            Log.d("WakeWord", "Wake word phát hiện (index=$keywordIndex)")
                            onWakeWordDetected()
                        }
                    })
            }
            porcupineManager?.start()
            Log.d("WakeWord", "Porcupine bắt đầu lắng nghe từ khóa.")
        } catch (e: IOException) {
            Log.e("WakeWordManager", "Lỗi khi khởi tạo Porcupine: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e("WakeWordManager", "Lỗi khi start Porcupine: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopListening() {
        try {
            porcupineManager?.stop()
            Log.d("WakeWord", "Dừng lắng nghe wake word.")
        } catch (e: Exception) {
            Log.e("WakeWordManager", "Lỗi khi dừng Porcupine: ${e.message}")
        }
    }

    fun destroy() {
        try {
            porcupineManager?.delete()
            porcupineManager = null
            Log.d("WakeWord", "🗑️ WakeWordManager đã hủy tài nguyên.")
        } catch (e: Exception) {
            Log.e("WakeWordManager", "Lỗi khi delete Porcupine: ${e.message}")
        }
    }
}
