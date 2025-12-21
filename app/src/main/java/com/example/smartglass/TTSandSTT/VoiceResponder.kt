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

    // ===============================
    // STATE
    // ===============================
    private var currentMode: SpeakMode? = null
    private var isNavigationPaused = false

    // ===============================
    // QUEUE
    // ===============================
    private val navigationQueue: ArrayDeque<String> = ArrayDeque()

    // pending khi TTS chưa init
    private var pendingText: String? = null
    private var pendingMode: SpeakMode? = null
    private var pendingCallback: (() -> Unit)? = null

    private val settings = SettingsManager.getInstance(context)

    init {
        tts = TextToSpeech(context, this)
    }

    // =====================================================
    // INIT
    // =====================================================
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            tts.setLanguage(Locale.forLanguageTag("vi-VN"))
            isReady = true

            pendingText?.let {
                internalSpeak(it, pendingMode ?: SpeakMode.DETECTION, pendingCallback)
            }

            pendingText = null
            pendingMode = null
            pendingCallback = null
        }
    }

    // =====================================================
    // PUBLIC API
    // =====================================================

    /** 👁️ Detection – thấp nhất */
    fun speak(text: String) {
        val activity = context as? MainActivity

        // STT đang nghe thì bỏ
        if (activity?.isListeningSTT == true) return

        // Có mode khác đang nói thì bỏ
        if (currentMode != null) return

        internalSpeak(text, SpeakMode.DETECTION)
    }

    /** 🧭 Navigation – không bao giờ mất */
    fun speakNavigation(text: String) {
        navigationQueue.add(text)

        if (currentMode == null || currentMode == SpeakMode.NAVIGATION) {
            playNextNavigation()
        }
    }

    /** 🤖 Gemini – PAUSE Navigation rồi RESUME */
    fun speakGemini(text: String) {

        tts.stop()
        currentMode = null

        if (currentMode == SpeakMode.NAVIGATION) {
            isNavigationPaused = true
        }

        internalSpeak(text, SpeakMode.GEMINI) {
            isNavigationPaused = false
            playNextNavigation()
        }
    }


    // =====================================================
    // NAVIGATION CORE
    // =====================================================
    private fun playNextNavigation() {
        if (isNavigationPaused) return
        if (navigationQueue.isEmpty()) {
            currentMode = null
            return
        }

        currentMode = SpeakMode.NAVIGATION
        val text = navigationQueue.removeFirst()

        internalSpeak(text, SpeakMode.NAVIGATION) {
            playNextNavigation()
        }
    }

    // =====================================================
    // CORE SPEAK (LOW LEVEL)
    // =====================================================
    private fun internalSpeak(
        text: String,
        mode: SpeakMode,
        onDone: (() -> Unit)? = null
    ) {
        if (!isReady) {
            pendingText = text
            pendingMode = mode
            pendingCallback = onDone
            return
        }

        // Gemini được ngắt mọi thứ
        if (mode == SpeakMode.GEMINI) {
            tts.stop()
        }

        currentMode = mode
        val utteranceId = "utt_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (mode != SpeakMode.NAVIGATION) {
                    currentMode = null
                }
                onDone?.invoke()
            }

            override fun onError(id: String?) {
                currentMode = null
            }
        })

        val params = Bundle().apply {
            putFloat("volume", settings.getVolumeFloat())
        }

        tts.setSpeechRate(settings.getSpeedMultiplier())
        tts.setPitch(1.0f)

        Log.d("VoiceResponder", "🔊 [$mode] $text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================
    fun stopAll() {
        if (::tts.isInitialized) {
            tts.stop()
        }
        navigationQueue.clear()
        currentMode = null
        isNavigationPaused = false
    }

    fun shutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        navigationQueue.clear()
        currentMode = null
        isNavigationPaused = false
        isReady = false
    }
}
