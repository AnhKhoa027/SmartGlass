package com.example.smartglass.TTSandSTT

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.example.smartglass.R

// Nhận giọng nói từ micro
class VoiceRecognitionManager(
    private val context: Context,
    private val launcher: ActivityResultLauncher<Intent>
) {
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
        }

        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("VoiceRecognition", "Điều Khiển giọng nói không hỗ trợ cho thiết bị này", e)
            Toast.makeText(
                context,
                context.getString(R.string.voice_not_supported),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
