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
import com.example.smartglass.TTSandSTT.*
import com.example.smartglass.HomeAction.GestureActionManager
import com.example.smartglass.DetectResponse.GeminiChat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import android.util.Log
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var voiceResponder: VoiceResponder
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private var wakeWordManager: WakeWordManager? = null

    private var greeted = false
    private val REQUEST_CODE_ALL = 1001

    private val geminiApiKey = "AIzaSyCdB2dFJiYjBSL3X4-VKy3mz3jYxQ0kcIc"
    private lateinit var geminiChat: GeminiChat

    private val mainScope = MainScope()

    private val voiceRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val resultText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!resultText.isNullOrBlank()) {
                handleTranscribedText(resultText)
            } else {
                voiceResponder.speak(getString(R.string.voice_not_understood))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        greeted = savedInstanceState?.getBoolean("greeted") ?: false

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        voiceResponder = VoiceResponder(this)
        geminiChat = GeminiChat(geminiApiKey)

        val homeFragment = HomeFragment()
        homeFragment.setVoiceResponder(voiceResponder)
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, homeFragment)
            .commit()

        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.home -> HomeFragment().apply { setVoiceResponder(voiceResponder) }
                R.id.setting -> SettingFragment()
                else -> HomeFragment().apply { setVoiceResponder(voiceResponder) }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, selectedFragment)
                .commit()
            true
        }

        fabMic = findViewById(R.id.fabMic)

        checkAndRequestPermissions()
    }


    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                REQUEST_CODE_ALL
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

        if (requestCode == REQUEST_CODE_ALL) {
            var micGranted = false
            var camGranted = false
            var locGranted = false

            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> micGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.CAMERA -> camGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.ACCESS_FINE_LOCATION -> locGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                }
            }

            if (micGranted && camGranted && locGranted) {
                voiceResponder.speak("Đã cấp quyền micro, camera và vị trí. Tôi sẵn sàng.")
                initVoiceFeatures()
            } else {
                if (!micGranted) voiceResponder.speak("Bạn cần cấp quyền micro để dùng giọng nói.")
                if (!camGranted) voiceResponder.speak("Bạn cần cấp quyền camera để sử dụng camera.")
                if (!locGranted) voiceResponder.speak("Bạn cần cấp quyền vị trí để định vị.")
            }
        }
    }

    private fun initVoiceFeatures() {
        voiceRecognitionManager = VoiceRecognitionManager(this, voiceRecognitionLauncher)

        voiceCommandProcessor = VoiceCommandProcessor(
            context = this,
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { callback -> sendCommandToHomeFragment(connect = true, callback) },
            onDisconnect = { callback -> sendCommandToHomeFragment(connect = false, callback) },
            voiceResponder = { voiceResponder.speak(it) },
            geminiChat = geminiChat
        )

        fabMic.setOnClickListener { voiceRecognitionManager.startListening() }

        if (!greeted) {
            voiceResponder.speak(getString(R.string.voice_greeting))
            greeted = true
        }

        setupWakeWord()

        GestureActionManager(
            rootView = findViewById(R.id.main),
            onHoldScreen = {
                voiceResponder.speak("Bắt đầu nghe...")
                voiceRecognitionManager.startListening()
            }
        ).init()
    }

    private fun handleTranscribedText(transcribed: String) {
        val handled = voiceCommandProcessor.handleCommand(transcribed)
        if (!handled) {
            voiceResponder.speak("Tôi hiểu.")
            geminiChat.sendMessageAsync(transcribed) { responseText ->
                runOnUiThread {
                    voiceResponder.speak(responseText ?: "Mình không nhận được phản hồi từ Gemini.")
                }
            }
        }
    }

    private fun setupWakeWord() {
        try {
            val keywordFile = File(filesDir, "Hey-Bro_en_android_v3_0_0.ppn")
            if (!keywordFile.exists()) {
                assets.open("Hey-Bro_en_android_v3_0_0.ppn").use { input ->
                    keywordFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("WakeWord", "Copied keyword file: ${keywordFile.absolutePath}")
            }

            wakeWordManager = WakeWordManager(
                context = this,
                accessKey = "LBKWPv6jiRpVsjkJp9wmYWhiv/H1dTxzzu6eQpOd++WZNm7kHMPUbw==",
                keywordFile = keywordFile.absolutePath,
                sensitivity = 0.6f
            ) {
                runOnUiThread {
                    voiceResponder.speak("Tôi đang nghe...")
                    voiceRecognitionManager.startListening()

                }
            }

            wakeWordManager?.let { manager ->
                mainScope.launch(Dispatchers.Default) {
                    try {
                        manager.startListening()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "WakeWord start failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            voiceResponder.speak("Không thể khởi tạo wake word, kiểm tra file ppn")
        }
    }

    private fun sendCommandToHomeFragment(connect: Boolean, callback: (Boolean) -> Unit) {
        (supportFragmentManager.findFragmentById(R.id.frame_layout) as? HomeFragment)?.let {
            if (connect) it.connectToUsbCam() else it.disconnectFromUsbCam()
        }
    }

    override fun onPause() {
        super.onPause()
        wakeWordManager?.let { manager ->
            mainScope.launch(Dispatchers.Default) {
                try {
                    manager.stopListening()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wakeWordManager?.startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceResponder.shutdown()
        mainScope.cancel()
        wakeWordManager?.stopListening()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("greeted", greeted)
    }
}
