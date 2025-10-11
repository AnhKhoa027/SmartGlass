package com.example.smartglass.TTSandSTT

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.smartglass.MainActivity
import com.example.smartglass.SettingAction.SettingsManager
import java.util.*

class VoiceResponder(private val context: Context) : TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isReady = false
    private var pendingText: String? = null
    private var pendingCallback: (() -> Unit)? = null
    private val settings = SettingsManager.getInstance(context)

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.forLanguageTag("vi-VN")

            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            isReady = true
            Log.d("VoiceResponder", "Google TTS đã sẵn sàng")

            // Nếu có văn bản chờ, đọc ngay sau khi init
            pendingText?.let { speak(it, pendingCallback) }
            pendingText = null
            pendingCallback = null
        } else {
            Log.e("VoiceResponder", "TTS init failed: $status")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            pendingText = text
            pendingCallback = onDone
            Log.w("VoiceResponder", "TTS chưa sẵn sàng, chờ khởi tạo")
            return
        }

        val utteranceId = "utt_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone?.let {
                    (context as? MainActivity)?.runOnUiThread { it() }
                }
            }
        })

        val volumeFloat = settings.getVolumeFloat()
        val speed = settings.getSpeedMultiplier()

        val params = Bundle().apply { putFloat("volume", volumeFloat) }
        tts.setSpeechRate(speed)
        tts.setPitch(1.0f)

        Log.d("VoiceResponder", "🗣️ Nói: $text (speed=$speed, volume=$volumeFloat)")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        isReady = false
    }
}
