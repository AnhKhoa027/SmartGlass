package com.example.smartglass

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener


import java.util.*

class VoiceResponder(private val context: Context) : TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var isReady = false
    private var pendingText: String? = null
    private var pendingCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.forLanguageTag("vi-VN")
            isReady = true
            if (pendingText != null) {
                speak(pendingText!!, pendingCallback)
                pendingText = null
                pendingCallback = null
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            pendingText = text
            pendingCallback = onDone
            return
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone?.let {
                    (context as? MainActivity)?.runOnUiThread { it() }
                }
            }
        })

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
