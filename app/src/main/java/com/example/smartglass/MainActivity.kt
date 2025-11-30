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
import java.text.SimpleDateFormat
import java.util.*

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

        val homeFragment = getOrCreateHomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, homeFragment, "HOME_FRAGMENT")
            .commit()

        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment? = when (item.itemId) {
                R.id.home -> getOrCreateHomeFragment()
                R.id.setting -> getOrCreateFragment("SETTING_FRAGMENT") ?: SettingFragment()
                else -> null
            }
            selectedFragment?.let {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.frame_layout, it, getFragmentTag(item.itemId))
                    .commit()
            }
            true
        }

        fabMic = findViewById(R.id.fabMic)
        checkAndRequestPermissions()
    }

    private fun getOrCreateHomeFragment(): HomeFragment {
        return (supportFragmentManager.findFragmentByTag("HOME_FRAGMENT") as? HomeFragment)
            ?: HomeFragment().apply { setVoiceResponder(voiceResponder) }
    }

    private fun getOrCreateFragment(tag: String): Fragment? {
        return supportFragmentManager.findFragmentByTag(tag)
    }

    private fun getFragmentTag(itemId: Int): String {
        return when(itemId) {
            R.id.home -> "HOME_FRAGMENT"
            R.id.setting -> "SETTING_FRAGMENT"
            else -> "HOME_FRAGMENT"
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        val perms = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        perms.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(it)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_CODE_ALL)
        } else initVoiceFeatures()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_ALL) {
            var micGranted = false; var camGranted = false; var locGranted = false
            var callGranted = false; var smsGranted = false; var contactsGranted = false

            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> micGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.CAMERA -> camGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.ACCESS_FINE_LOCATION -> locGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.CALL_PHONE -> callGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.SEND_SMS -> smsGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS -> contactsGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                }
            }

            if (micGranted && camGranted && locGranted && callGranted && smsGranted && contactsGranted) {
                voiceResponder.speak("Đã cấp tất cả quyền cần thiết. Tôi sẵn sàng.")
                initVoiceFeatures()
            } else {
                if (!micGranted) voiceResponder.speak("Bạn cần cấp quyền micro để dùng giọng nói.")
                if (!camGranted) voiceResponder.speak("Bạn cần cấp quyền camera để sử dụng camera.")
                if (!locGranted) voiceResponder.speak("Bạn cần cấp quyền vị trí để định vị.")
                if (!callGranted) voiceResponder.speak("Bạn cần cấp quyền gọi điện để thực hiện cuộc gọi.")
                if (!smsGranted) voiceResponder.speak("Bạn cần cấp quyền gửi tin nhắn để nhắn tin.")
                if (!contactsGranted) voiceResponder.speak("Bạn cần cấp quyền danh bạ để truy cập danh bạ.")
            }
        }
    }

    private fun initVoiceFeatures() {
        voiceRecognitionManager = VoiceRecognitionManager(this, voiceRecognitionLauncher)

        voiceCommandProcessor = VoiceCommandProcessor(
            context = this,
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { callback -> sendCommandToHomeFragment(connect = true) },
            onDisconnect = { callback -> sendCommandToHomeFragment(connect = false) },
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
            context = this,
            onHoldScreen = {
                voiceResponder.speak("Bắt đầu nghe...")
                voiceRecognitionManager.startListening()
            }
        ).init()
    }

    fun getRealTimeDate(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val format = SimpleDateFormat("dd 'tháng' MM 'năm' yyyy", Locale("vi"))
        return format.format(calendar.time)
    }

    fun getRealTimeDateTime(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val format = SimpleDateFormat("dd 'tháng' MM 'năm' yyyy, HH:mm", Locale("vi"))
        return format.format(calendar.time)
    }

    private fun handleTranscribedText(transcribed: String) {
        voiceCommandProcessor.handleCommand(transcribed)
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
                //accessKey = "W8WX0LISM+lvDmBoZmZZFgzot+XezDl3EP4quWB4KCVNQ3klMjhOhw==",
                //Cua Khoa
                accessKey = "LBKWPv6jiRpVsjkJp9wmYWhiv/H1dTxzzu6eQpOd++WZNm7kHMPUbw==",
                keywordFile = keywordFile.absolutePath,
                sensitivity = 0.8f
            ) {
                runOnUiThread {
                    voiceResponder.speak("Tôi đang nghe...") {
                        voiceRecognitionManager.startListening()
                    }
                }
            }

            wakeWordManager?.let { manager ->
                mainScope.launch(Dispatchers.Default) {
                    try { manager.startListening() } catch (e: Exception) {
                        Log.e("MainActivity", "WakeWord start failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            voiceResponder.speak("Không thể khởi tạo wake word, kiểm tra file ppn")
        }
    }

    private fun sendCommandToHomeFragment(connect: Boolean) {
        val homeFragment = getOrCreateHomeFragment()
        if (connect) homeFragment.connectToUsbCam() else homeFragment.disconnectFromUsbCam()
    }

    override fun onPause() {
        super.onPause()
        wakeWordManager?.let { manager ->
            mainScope.launch(Dispatchers.Default) {
                try { manager.stopListening() } catch (_: Exception) {}
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

