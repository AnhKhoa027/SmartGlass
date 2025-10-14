package com.example.smartglass.TTSandSTT

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log
import java.io.File
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
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordFile)   // File .ppn đã nằm trong internal storage
                .setSensitivity(sensitivity)
                .build(context, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        val now = System.currentTimeMillis()
                        if (now - lastDetectedTime < debounceMs) {
                            Log.d("WakeWord", "Bỏ qua wake word do vừa kích hoạt gần đây.")
                            return
                        }
                        lastDetectedTime = now
                        Log.d("WakeWord", "🎤 Wake word phát hiện (index=$keywordIndex)")
                        onWakeWordDetected()
                    }
                })
            porcupineManager?.start()
            Log.d("WakeWord", "Porcupine bắt đầu lắng nghe từ khóa.")
        } catch (e: IOException) {
            Log.e("WakeWord", "Lỗi khi khởi tạo Porcupine: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopListening() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null
        Log.d("WakeWord", "Dừng lắng nghe wake word.")
    }

    companion object {
        /**
         * Hàm tiện ích: copy file .ppn từ assets (nếu cần),
         * rồi tạo & start luôn WakeWordManager
         */
        fun createAndInit(
            context: Context,
            accessKey: String,
            keywordAssetName: String = "Hey-bro_en_android_v3_0_0.ppn",
            sensitivity: Float = 0.6f,
            onWakeWordDetected: () -> Unit
        ): WakeWordManager? {
            return try {
                // Copy file ppn từ assets sang internal storage nếu chưa có
                val keywordFile = File(context.filesDir, keywordAssetName)
                if (!keywordFile.exists()) {
                    context.assets.open(keywordAssetName).use { input ->
                        keywordFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("WakeWord", "Copied keyword file to ${keywordFile.absolutePath}")
                }

                WakeWordManager(
                    context = context,
                    accessKey = accessKey,
                    keywordFile = keywordFile.absolutePath,
                    sensitivity = sensitivity,
                    onWakeWordDetected = onWakeWordDetected
                ).apply {
                    startListening()
                }
            } catch (e: Exception) {
                Log.e("WakeWord", "Lỗi khi tạo WakeWordManager: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
}
