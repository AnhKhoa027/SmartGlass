package com.example.smartglass

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var voiceResponder: VoiceResponder

    companion object {
        const val REQUEST_MIC_PERMISSION = 200
        const val VOICE_RECOGNITION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, HomeFragment())
            .commit()

        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
        fabMic = findViewById(R.id.fabMic)

        voiceResponder = VoiceResponder(this)
        voiceResponder.speak("Xin chào! Chào mừng bạn đến với ứng dụng Smart Glass. Hãy nói kết nối để bắt đầu.") {
            startVoiceRecognition()
        }

        voiceCommandProcessor = VoiceCommandProcessor(
            context = this,
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { sendCommandToHomeFragment(connect = true) },
            onDisconnect = { sendCommandToHomeFragment(connect = false) },
            voiceResponder = { voiceResponder.speak(it) },
            restartListening = { startVoiceRecognition() }
        )

        fabMic.setOnClickListener {
            startVoiceRecognition()
        }

        checkMicrophonePermission()
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_MIC_PERMISSION
            )
        } else {
            startVoiceRecognition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_PERMISSION && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, "Ứng dụng cần quyền micro để hoạt động", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói lệnh...")

        try {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Thiết bị không hỗ trợ giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE &&
            resultCode == RESULT_OK &&
            data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            result?.get(0)?.let { voiceCommandProcessor.handleCommand(it) }
        }
    }

    private fun sendCommandToHomeFragment(connect: Boolean) {
        val currentFragment =
            supportFragmentManager.findFragmentById(R.id.frame_layout) as? HomeFragment
        currentFragment?.let {
            if (connect) it.connectToESP32()
            else it.disconnectFromESP32()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceResponder.shutdown()
    }
}
