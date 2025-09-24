package com.example.smartglass

import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.smartglass.TTSandSTT.VoiceCommandProcessor
import com.example.smartglass.TTSandSTT.VoiceRecognitionManager
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * MainActivity
 * -------------------
 * Đây là Activity chính của ứng dụng:
 * - Chứa navigation (BottomNavigationView) để chuyển giữa HomeFragment và SettingFragment.
 * - Chứa mic button (FloatingActionButton) để nhận lệnh giọng nói.
 * - Tích hợp VoiceResponder (TTS), VoiceRecognitionManager (STT) và VoiceCommandProcessor (xử lý lệnh).
 *
 * Luồng chính:
 * - Khi người dùng bấm mic -> VoiceRecognitionManager bật RecognizerIntent -> trả về text.
 * - VoiceCommandProcessor phân tích text và gọi lệnh tương ứng (connect, disconnect, mở tab Home/Setting...).
 * - Các lệnh connect/disconnect được gửi sang HomeFragment (gọi connectToXiaoCam() / disconnectFromXiaoCam()).
 *
 * Liên quan:
 * - HomeFragment (gọi connect/disconnect camera).
 * - VoiceCommandProcessor.kt (xử lý logic lệnh).
 * - VoiceRecognitionManager.kt (nhận diện giọng nói).
 * - VoiceResponder.kt (phát phản hồi TTS).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var voiceResponder: VoiceResponder
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager

    private var greeted = false

    /**
     * Launcher cho RecognizerIntent (STT).
     * Khi nhận được kết quả từ Google Speech -> chuyển cho VoiceCommandProcessor xử lý.
     */
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

        // Đọc trạng thái chào mừng lần đầu
        greeted = savedInstanceState?.getBoolean("greeted") ?: false

        // Xử lý insets cho notch, status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Load mặc định HomeFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, HomeFragment())
            .commit()

        // Khởi tạo BottomNavigationView để chuyển giữa Home / Setting
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

        // FloatingActionButton (mic) để nhận lệnh giọng nói
        fabMic = findViewById(R.id.fabMic)

        // Khởi tạo TTS responder
        voiceResponder = VoiceResponder(this)

        // Khởi tạo STT manager
        voiceRecognitionManager = VoiceRecognitionManager(this, voiceRecognitionLauncher)

        // Khởi tạo processor để xử lý lệnh
        voiceCommandProcessor = VoiceCommandProcessor(
            context = this,
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { sendCommandToHomeFragment(connect = true) },   // lệnh "connect"
            onDisconnect = { sendCommandToHomeFragment(connect = false) }, // lệnh "disconnect"
            voiceResponder = { voiceResponder.speak(it) } // callback TTS
        )

        // Khi nhấn mic -> bắt đầu nghe
        fabMic.setOnClickListener {
            voiceRecognitionManager.startListening()
        }

        // Lời chào lần đầu
        if (!greeted) {
            voiceResponder.speak(getString(R.string.voice_greeting))
            greeted = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("greeted", greeted)
    }

    /**
     * Gửi lệnh connect/disconnect xuống HomeFragment.
     * HomeFragment có 2 hàm: connectToXiaoCam() / disconnectFromXiaoCam().
     *
     * -> Khi VoiceCommandProcessor nhận lệnh "kết nối" -> gọi connectToXiaoCam().
     * -> Khi VoiceCommandProcessor nhận lệnh "ngắt kết nối" -> gọi disconnectFromXiaoCam().
     */
    private fun sendCommandToHomeFragment(connect: Boolean) {
        val currentFragment =
            supportFragmentManager.findFragmentById(R.id.frame_layout) as? HomeFragment
        currentFragment?.let {
            if (connect) it.connectToXiaoCam()
            else it.disconnectFromXiaoCam()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Giải phóng TTS khi thoát Activity
        voiceResponder.shutdown()
    }
}
