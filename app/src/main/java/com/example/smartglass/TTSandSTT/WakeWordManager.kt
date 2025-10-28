package com.example.smartglass.TTSandSTT

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import java.io.IOException
import android.util.Log

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
                .setKeywordPath(keywordFile)
                .setSensitivity(sensitivity)
                .build(context, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        onWakeWordDetected()
                    }
                })
            porcupineManager?.start()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("WakeWordManager", "Error starting Porcupine: ${e.message}")
        }
    }


    fun stopListening() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null
    }
}