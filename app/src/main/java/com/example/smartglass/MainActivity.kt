package com.example.smartglass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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

        // Khởi tạo TTS duy nhất
        voiceResponder = VoiceResponder(this)

        // Load mặc định HomeFragment và truyền VoiceResponder
        val homeFragment = HomeFragment()
        homeFragment.setVoiceResponder(voiceResponder)
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, homeFragment)
            .commit()

        // BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.home -> {
                    val frag = HomeFragment()
                    frag.setVoiceResponder(voiceResponder)
                    frag
                }
                R.id.setting -> SettingFragment()
                else -> {
                    val frag = HomeFragment()
                    frag.setVoiceResponder(voiceResponder)
                    frag
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, selectedFragment)
                .commit()
            true
        }

        // FloatingActionButton mic
        fabMic = findViewById(R.id.fabMic)

        // Xin quyền micro
        checkMicPermission()
    }

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
        voiceRecognitionManager = VoiceRecognitionManager(this, voiceRecognitionLauncher)

        voiceCommandProcessor = VoiceCommandProcessor(
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { callback -> sendCommandToHomeFragment(connect = true, callback) },
            onDisconnect = { callback -> sendCommandToHomeFragment(connect = false, callback) },
            voiceResponder = { voiceResponder.speak(it) }
        )

        fabMic.setOnClickListener { voiceRecognitionManager.startListening() }

        if (!greeted) {
            voiceResponder.speak(getString(R.string.voice_greeting))
            greeted = true
        }

        setupWakeWord()

        val gestureManager = GestureActionManager(
            rootView = findViewById(R.id.main),
            onHoldScreen = {
                voiceResponder.speak("Tôi đang nghe...")
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

    private fun sendCommandToHomeFragment(connect: Boolean, callback: (Boolean) -> Unit) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as? HomeFragment
        currentFragment?.let {
            if (connect) it.connectToXiaoCam(callback) else it.disconnectFromXiaoCam(callback)
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
