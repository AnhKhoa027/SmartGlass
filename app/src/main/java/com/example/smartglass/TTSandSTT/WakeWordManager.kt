package com.example.smartglass.TTSandSTT

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import java.io.IOException

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val keywordFile: String,
    private val sensitivity: Float = 0.6f,
    private val onWakeWordDetected: () -> Unit
) {

    private var porcupineManager: PorcupineManager? = null

    fun startListening() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordFile)   // Đảm bảo file .ppn nằm trong thư mục assets
                .setSensitivity(sensitivity)
                .build(context, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        // Được gọi khi phát hiện từ khóa
                        onWakeWordDetected()
                    }
                })
            porcupineManager?.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopListening() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null
    }
}
