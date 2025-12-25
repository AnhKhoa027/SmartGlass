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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.example.smartglass.Navigation.*
class MainActivity : AppCompatActivity(), NavigationCallback, NavigationListener {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var voiceResponder: VoiceResponder
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private var wakeWordManager: WakeWordManager? = null

    private var greeted = false
    private val REQUEST_CODE_ALL = 1001
    private val REQ_LOCATION = 1002

    private val geminiApiKey = ""
    private lateinit var geminiChat: GeminiChat
    var isListeningSTT = false
    private val mainScope = MainScope()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationRequest: LocationRequest

    private lateinit var navigationManager: NavigationManager // KHAI BÁO MANAGER MỚI

    private val GOONG_API_KEY = "1hDHs4M7KJHX3mCe1cTzxVxtFvs3hHVyuP6wdEDh"


    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "location_channel_id"
    }

    private val voiceRecognitionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isListeningSTT = false
            val homeFragment = supportFragmentManager.findFragmentByTag("HOME_FRAGMENT") as? HomeFragment
            homeFragment?.restartDistanceReader()
            if (result.resultCode == RESULT_OK) {
                val resultText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.get(0)

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

        setupBottomNavigation()

        // 1. Cấu hình GPS Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        )
            .setMinUpdateDistanceMeters(3f)
            .setWaitForAccurateLocation(true)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()

        // 2. Tạo Notification Channel
        setupNotificationChannel()

        // 3. KHỞI TẠO NAVIGATION MANAGER
        navigationManager = NavigationManager(
            context = this,
            voiceResponder = voiceResponder,
            listener = this, // Activity này là Listener
            fusedLocationClient = fusedLocationClient,
            locationRequest = locationRequest,
            goongApiKey = GOONG_API_KEY
        )

        // 4. Kiểm tra và xin quyền
        checkAndRequestPermissions()
    }

    private fun setupBottomNavigation() {
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
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Channel",
                NotificationManager.IMPORTANCE_LOW // Dùng LOW để tránh làm phiền
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onGpsSettingsRequired() {
        // Yêu cầu bật GPS
        Toast.makeText(this, "Vui lòng bật GPS", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    override fun onLocationPermissionRequired() {
        // Yêu cầu xin quyền Location
        requestLocationPermissions() // Hàm xin quyền đã được tối ưu
    }

    override fun onShowToast(message: String) {
        // Hiển thị Toast
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onLocationUpdate(location: Location) {
        // Cập nhật Notification (khi có vị trí mới)
        notificationManager.notify(
            NOTIFICATION_ID,
            generateNotification(location)
        )
    }


    override fun onRouteFound(distance: String, duration: String, initialDirection: String) {

        Log.i("NAV_LISTENER", "Đã tìm thấy tuyến đường: $distance, $duration, hướng $initialDirection")
    }

    override fun onRouteError(message: String) {
        voiceResponder.speak(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSpeakInstruction(instruction: String) {
        voiceResponder.speakNavigation(instruction)
    }

    override fun onDestinationReached(destination: String) {
        voiceResponder.speakGemini("Bạn đã đến ${destination}. Kết thúc chỉ đường.")
    }

    override fun onStartLocationUpdatesRequested() {
        startListeningForLocation(locationRequest)
    }

    private fun startListeningForLocation(locationRequest: LocationRequest) {
        // 1. Kiểm tra GPS
        if (!isLocationEnabled()) {
            // Yêu cầu Manager báo lỗi GPS để gọi onGpsSettingsRequired()
            navigationManager.listener.onGpsSettingsRequired()
            return
        }

        // 2. Kiểm tra Quyền
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Yêu cầu Manager báo lỗi Quyền để gọi onLocationPermissionRequired()
            navigationManager.listener.onLocationPermissionRequired()
            return
        }
        // Gọi Manager bắt đầu lắng nghe, Manager sẽ tự gọi fusedLocationClient
        navigationManager.startListeningForLocation()
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun startNavigationTo(destination: String) {
        navigationManager.startNavigation(destination)
    }

    override fun stopNavigation() {
        navigationManager.stopNavigation()
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQ_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_ALL -> {
                handleAllPermissionsResult(permissions, grantResults)
            }
            REQ_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Đã cấp quyền định vị", Toast.LENGTH_SHORT).show()
                    // Sau khi cấp quyền, yêu cầu Manager bắt đầu lại quy trình
                    navigationManager.startListeningForLocation()
                } else {
                    Toast.makeText(this, "Thiếu quyền định vị", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun handleAllPermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
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


    private fun generateNotification(location: Location): Notification {
        val mainNotificationText = "Vị trí hiện tại: ${location.latitude}, ${location.longitude}"

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Theo dõi vị trí GPS")
            .setContentText(mainNotificationText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
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
    private fun initVoiceFeatures() {

        voiceRecognitionManager =
            VoiceRecognitionManager(this, voiceRecognitionLauncher)

        voiceCommandProcessor = VoiceCommandProcessor(
            context = this,
            activity = this,
            bottomNav = bottomNavigationView,
            onConnect = { callback ->
                sendCommandToHomeFragment(connect = true)
                callback(true)
            },
            onDisconnect = { callback ->
                sendCommandToHomeFragment(connect = false)
                callback(true)
            },
            voiceResponder = voiceResponder,
            geminiChat = geminiChat,
            navigationCallback = this
        )

        fabMic.setOnClickListener {
            startSTT()
        }

        if (!greeted) {
            voiceResponder.speak(getString(R.string.voice_greeting))
            greeted = true
        }

        setupWakeWord()

        GestureActionManager(
            rootView = findViewById(R.id.main),
            context = this,
            onHoldScreen = {
                voiceResponder.speakGemini("Bắt đầu nghe")
                startSTT()
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
            val keywordFile = File(filesDir, "Hey-Nana_en_android_v4_0_0.ppn")
            if (!keywordFile.exists()) {
                assets.open("Hey-Nana_en_android_v4_0_0.ppn").use { input ->
                    keywordFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("WakeWord", "Copied keyword file: ${keywordFile.absolutePath}")
            }

            wakeWordManager = WakeWordManager(
                context = this,
//                accessKey = "W8WX0LISM+lvDmBoZmZZFgzot+XezDl3EP4quWB4KCVNQ3klMjhOhw==",
                accessKey = "0Ikb221liO0oHxw/sYkxGrBxF98p9TwK581yZX+0lhYYootqTgkhGw==",

                        keywordFile = keywordFile.absolutePath,
                sensitivity = 0.8f
            ) {
                runOnUiThread {
                    voiceResponder.speakDone("Tôi đang nghe..."){
                        startSTT()
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
        navigationManager.stopListeningForLocation()
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
        navigationManager.stopListeningForLocation() // Đảm bảo dừng khi Activity bị hủy
    }

    private fun startSTT() {
        isListeningSTT = true
        voiceResponder.stopAll()
        voiceRecognitionManager.startListening()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("greeted", greeted)
    }
}