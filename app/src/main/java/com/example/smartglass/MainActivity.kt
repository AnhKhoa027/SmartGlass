package com.example.smartglass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.smartglass.TTSandSTT.VoiceCommandProcessor
import com.example.smartglass.TTSandSTT.VoiceRecognitionManager
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.TTSandSTT.WakeWordManager
import com.example.smartglass.HomeAction.GestureActionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var voiceResponder: VoiceResponder
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private var wakeWordManager: WakeWordManager? = null

    private var greeted = false
    private val REQUEST_CODE_MIC = 1001

    private val voiceRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val resultText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!resultText.isNullOrBlank()) {
                voiceCommandProcessor.handleCommand(resultText)
            } else {
                voiceResponder.speak(getString(R.string.voice_not_understood))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        greeted = savedInstanceState?.getBoolean("greeted") ?: false

        // Xử lý insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Load mặc định HomeFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, HomeFragment())
            .commit()

        // BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.home -> HomeFragment()
                R.id.setting -> SettingFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, selectedFragment)
                .commit()
            true
        }

        // FloatingActionButton mic
        fabMic = findViewById(R.id.fabMic)

        // TTS
        voiceResponder = VoiceResponder(this)

        // Xin quyền micro
        checkMicPermission()
    }

    /**
     * Kiểm tra & xin quyền Micro
     */
    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_MIC
            )
        } else {
            initVoiceFeatures()
        }
    }

    /**
     * Callback kết quả xin quyền
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voiceResponder.speak("Đã cấp quyền micro, tôi sẵn sàng.")
                initVoiceFeatures()
            } else {
                voiceResponder.speak("Bạn cần cấp quyền micro để dùng giọng nói.")
            }
        }
    }

    private fun initVoiceFeatures() {
        // STT
        voiceRecognitionManager = VoiceRecognitionManager(this, voiceRecognitionLauncher)

        // VoiceCommandProcessor
        voiceCommandProcessor = VoiceCommandProcessor(
            context = this,
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { sendCommandToHomeFragment(connect = true) },
            onDisconnect = { sendCommandToHomeFragment(connect = false) },
            voiceResponder = { voiceResponder.speak(it) }
        )

        // Mic click
        fabMic.setOnClickListener { voiceRecognitionManager.startListening() }

        // Lời chào lần đầu
        if (!greeted) {
            voiceResponder.speak(getString(R.string.voice_greeting))
            greeted = true
        }

        // --- Setup Wake Word ---
        setupWakeWord()

        // --- Setup Gesture giữ tay ≥ 5s ---
        val gestureManager = GestureActionManager(
            rootView = findViewById(R.id.main),
            onHoldScreen = {
                voiceResponder.speak("Bắt đầu nghe...")
                voiceRecognitionManager.startListening()
            }
        )
        gestureManager.init()
    }

    private fun setupWakeWord() {
        wakeWordManager = WakeWordManager.createAndInit(
            context = this,
            accessKey = "LBKWPv6jiRpVsjkJp9wmYWhiv/H1dTxzzu6eQpOd++WZNm7kHMPUbw==",
            onWakeWordDetected = {
                voiceResponder.speak("Tôi đang nghe...")
                voiceRecognitionManager.startListening()
            }
        ) ?: run {
            voiceResponder.speak("Không thể khởi tạo wake word, kiểm tra file ppn")
            null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("greeted", greeted)
    }

    private fun sendCommandToHomeFragment(connect: Boolean) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as? HomeFragment
        currentFragment?.let {
            if (connect) it.connectToXiaoCam() else it.disconnectFromXiaoCam()
        }
    }

    override fun onPause() {
        super.onPause()
        wakeWordManager?.stopListening()
    }

    override fun onResume() {
        super.onResume()
        wakeWordManager?.startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceResponder.shutdown()
        wakeWordManager?.stopListening()
    }
}
