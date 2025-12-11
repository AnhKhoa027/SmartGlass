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

            Log.d("VoiceResponder", "=== TTS INIT OK ===")

            // Kiểm tra engine hiện tại
            val engine = tts.defaultEngine
            Log.d("VoiceResponder", "Engine đang dùng: $engine")

            // Set audio attributes
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            // Ngôn ngữ tiếng Việt
            val locale = Locale.forLanguageTag("vi-VN")
            val result = tts.setLanguage(locale)
            Log.d("VoiceResponder", "Kết quả setLanguage = $result")

            when (result) {
                TextToSpeech.LANG_AVAILABLE ->
                    Log.d("VoiceResponder", "Hỗ trợ tiếng Việt (LANG_AVAILABLE)")

                TextToSpeech.LANG_COUNTRY_AVAILABLE ->
                    Log.d("VoiceResponder", "Hỗ trợ tiếng Việt + country")

                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE ->
                    Log.d("VoiceResponder", "Hỗ trợ tiếng Việt đầy đủ")

                TextToSpeech.LANG_MISSING_DATA ->
                    Log.e("VoiceResponder", "Thiếu dữ liệu giọng Việt! Phải tải voice data.")

                TextToSpeech.LANG_NOT_SUPPORTED ->
                    Log.e("VoiceResponder", "Engine KHÔNG hỗ trợ tiếng Việt!")

                else ->
                    Log.e("VoiceResponder", "Lỗi setLanguage không xác định: $result")
            }

            // Log toàn bộ voices trong máy
            try {
                val voices = tts.voices
                for (v in voices) {
                    Log.d("VoiceResponder",
                        "Voice: ${v.name} | locale=${v.locale} | quality=${v.quality} | latency=${v.latency}")
                }
            } catch (e: Exception) {
                Log.e("VoiceResponder", "Không lấy được danh sách voices: ${e.message}")
            }

            isReady = true
            Log.d("VoiceResponder", "TextToSpeech đã sẵn sàng")

            // Nếu có văn bản bị pending → đọc ngay
            pendingText?.let { speak(it, pendingCallback) }
            pendingText = null
            pendingCallback = null

        } else {
            Log.e("VoiceResponder", "TTS INIT ERROR: $status")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            pendingText = text
            pendingCallback = onDone
            Log.w("VoiceResponder", "TTS chưa sẵn sàng → đã lưu pending")
            return
        }

        val utteranceId = "utt_${System.currentTimeMillis()}"

        // Listener hoàn thành
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d("VoiceResponder", "Bắt đầu đọc: $text")
            }

            override fun onDone(id: String?) {
                Log.d("VoiceResponder", "Hoàn thành đọc")
                onDone?.let {
                    (context as? MainActivity)?.runOnUiThread { it() }
                }
            }

            override fun onError(id: String?) {
                Log.e("VoiceResponder", "Lỗi khi đọc TTS")
            }
        })

        // Lấy settings từ user
        val volumeFloat = settings.getVolumeFloat()
        val speed = settings.getSpeedMultiplier()

        val params = Bundle().apply {
            putFloat("volume", volumeFloat)
        }

        tts.setSpeechRate(speed)
        tts.setPitch(1.0f)

        Log.d("VoiceResponder",
            "🗣️ Đọc: \"$text\" (speed=$speed, volume=$volumeFloat)")

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        isReady = false
        Log.d("VoiceResponder", "Đã tắt TextToSpeech")
    }
}
