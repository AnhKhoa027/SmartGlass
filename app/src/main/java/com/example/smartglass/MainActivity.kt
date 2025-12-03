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
import com.example.smartglass.Navigation.NavigationCallback
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import MapboxResponse
import MapboxStep
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.smartglass.R
import com.google.android.gms.location.*
import okhttp3.OkHttpClient
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.example.smartglass.MainActivity
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.TTSandSTT.VoiceRecognitionManager
import com.example.smartglass.gps.GeocodingResponse
import com.example.smartglass.gps.MapBoxStepsParser
import com.example.smartglass.gps.RetrofitClient
import com.example.smartglass.gps.location_gps
import com.example.smartglass.gps.location_gps.Companion.CHANNEL_ID
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.compareTo
import kotlin.inc
import kotlin.text.compareTo
import kotlin.text.get

class MainActivity : AppCompatActivity(), NavigationCallback {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var voiceResponder: VoiceResponder
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private var wakeWordManager: WakeWordManager? = null

    private var greeted = false
    private val REQUEST_CODE_ALL = 1001
    private val REQ_LOCATION = 1002
    private val geminiApiKey = "AIzaSyCdB2dFJiYjBSL3X4-VKy3mz3jYxQ0kcIc"
    private lateinit var geminiChat: GeminiChat
    private val mainScope = MainScope()
    //Sử dụng fusedLocationClient để lấy chính xác vị trí,chính xác hơn so với LocationManager thuần
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //private lateinit var tvLatitude: TextView
    //private lateinit var tvLongitude: TextView
    private lateinit var btnStartListen: Button
    private var currentLocation: Location? = null

    //Nhận cập nhật vị trí
    private lateinit var locationCallback: LocationCallback
    private val client = OkHttpClient()
    private val TAG = "LocationService"

    //Điều chỉnh thông báo hiển thị trên foreground
    private lateinit var notificationManager: NotificationManager

    private lateinit var locationRequest: LocationRequest


    private var voiceManager: VoiceRecognitionManager? = null
    private var destination: String? = null
    private var destLon: Double? = null
    private var destLat: Double? = null
    private var routeSteps: List<MapboxStep>? = null // Danh sách các bước chỉ đường
    private var currentStepIndex = 0// Chỉ số bước hiện tại không thể âm
    private var isNavigating = false // Cờ hiệu đang trong quá trình điều hướng
    private var waitingForInitialLocation = false


    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "location_channel_id"
        const val ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST =
            "com.example.smartglass.action.FOREGROUND_ONLY_LOCATION_BROADCAST"
        const val EXTRA_LOCATION = "com.example.smartglass.extra.LOCATION"
        //Cho phép cập nhật vị trí đến
        private var serviceRunningInForeground = true
    }
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
        //Khởi tạo gps
        // Tạo notification channel cho Android 8+
        /*Trên Android 8+ phải tạo channel để show notification;
        channel có importance quyết định độ ưu tiên (LOW thường không gây âm thanh).
        Nếu không tạo, notification có thể không hiển thị đúng.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            //Dùng GPS+providers ,mức độ chính xác nhất
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        )
            /*
            Cho phép cập nhật ngay cả khi không di chuyển
            Trả về vị trí càng nhanh càng tốt thì false,true thì phải đợi hệ thống lấy vị trí chính xác hơn
            Số lần cập nhật không giới hạn để liên tục lấy vị trí
             */
            .setMinUpdateDistanceMeters(3f)
            .setWaitForAccurateLocation(true)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()

        //Xử lí locationResults
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (locationResult.locations.isEmpty()) {
                    Log.d(TAG, "Không có kết quả định vị.")
                    return
                }
                //Google trả về nhiều điểm trong 1 lần nên xử lí bằng vòng lặp
                // Lấy vị trí mới nhất (latestLocation)
                val latestLocation = locationResult.lastLocation

                // Kiểm tra an toàn
                if (latestLocation == null) {
                    Log.d(TAG, "Không có kết quả định vị mới.")
                    return
                }
                if (waitingForInitialLocation) {
                    currentLocation = latestLocation // Gán vị trí đã lấy được
                    waitingForInitialLocation = false // Tắt cờ

                    // Chạy logic điều hướng chính với vị trí đã có
                    // Cần đảm bảo rằng `destination` đã được lưu từ lúc trước
                    destination?.let { dest ->
                            changeLocationToGeoCoding()
                    }
                }

                Log.d("GPS_DEBUG", "Latitude: ${latestLocation.latitude}, Longitude: ${latestLocation.longitude}")

                // Gửi Broadcast (một lần)
                val intent =
                    Intent(ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
                intent.putExtra(EXTRA_LOCATION, latestLocation)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

                // Cập nhật Notification (một lần)
                if (serviceRunningInForeground) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        generateNotification(latestLocation)
                    )
                }

                if (isNavigating && !routeSteps.isNullOrEmpty() && currentLocation != null) {
                    val steps = routeSteps!!

                    // Chỉ xử lý nếu chưa đến bước cuối cùng
                    if (currentStepIndex >= 0 && currentStepIndex < steps.size) {
                        val step = steps[currentStepIndex]

                        // Mapbox trả về [lon, lat] cho Maneuver point
                        val maneuverLon = step.maneuver.location[0]
                        val maneuverLat = step.maneuver.location[1]

                        val results = FloatArray(1)
                        Location.distanceBetween(
                            currentLocation!!.latitude, currentLocation!!.longitude,
                            maneuverLat, maneuverLon,
                            results
                        )
                        val distanceToManeuver = results[0] // Khoảng cách đến điểm ngoặt (meters)

                        Log.d("NAV_STEP", "Bước $currentStepIndex: Cách ${distanceToManeuver}m")

                        // Kiểm tra: Nếu gần đến điểm ngoặt (ví dụ dưới 20 mét)
                        if (distanceToManeuver < 20) {
                            // Đã hoàn thành bước hiện tại
                            currentStepIndex++
                            if (currentStepIndex < steps.size) {
                                readCurrentStepInstruction() // Đọc bước mới
                            } else {
                                // Đã đến đích
                                voiceResponder?.speak("Bạn đã đến đích, ${destination}. Kết thúc chỉ đường.")
                                isNavigating = false
                            }
                        } else if (distanceToManeuver < 100) {
                            // Có thể thêm logic nhắc nhở nếu còn 100m nhưng chưa đọc
                            val instruction = steps[currentStepIndex].maneuver.instruction
                            val distance = steps[currentStepIndex].distance
                            // Chỉ đọc khi bước hiện tại chưa được đọc nhắc nhở (lastSpokenStepIndex < currentStepIndex)
                            voiceResponder?.speak("Trong ${formatDistance(distance)}, sắp đến ${instruction}")
                        }
                    }
                }
            }
        }
        checkAndRequestPermissions()
    }


    override fun startNavigationTo(destination: String) {
        // Logic của hàm này:
        // 1. Lưu địa điểm đến (this.destination = destination)
        // 2. Thông báo bằng giọng nói (voiceResponder.speak)
        // 3. Bắt đầu quá trình tìm tọa độ (changeLocationToGeoCoding())

        if (currentLocation == null) {
            this.destination = destination
            voiceResponder.speak(text = "Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn."
            , onDone = {
                    // Đảm bảo GPS đang chạy để lấy vị trí
                    startLocationUpdates(locationRequest)
                })
            waitingForInitialLocation = true
            return
        }

        this.destination = destination
        isNavigating = false // Tắt cờ cũ trước khi bắt đầu mới
        voiceResponder.speak("Đã nhận lệnh: Đi đến $destination. Đang tìm đường.", onDone = {
            changeLocationToGeoCoding() // Gọi hàm tìm tọa độ và chỉ đường
        })
    }

    override fun stopNavigation() {
        // Logic của hàm này:
        // 1. Đặt lại cờ trạng thái (isNavigating = false)
        // 2. Dọn dẹp biến (destination = null, routeSteps = null)
        // 3. Thông báo bằng giọng nói

        if (isNavigating) {
            isNavigating = false
            destination = null
            routeSteps = null
            currentStepIndex = 0
            voiceResponder.speak("Đã dừng chỉ đường.")
            stopLocationUpdates()
        } else {
            voiceResponder.speak("Hiện không có chỉ đường nào đang hoạt động.")
        }

    }
    private fun startLocationUpdates(locationRequest: LocationRequest) {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Vui lòng bật GPS", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
            return
        }
        // Đăng ký nhận vị trí , truyền các tham số để callback chạy trên UI thread chính
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun requestPermissions() {
        /*
        Từ Android 10 trở lên, ACCESS_BACKGROUND_LOCATION phải được xin sau khi user đã cấp FINE/COARSE,
        nếu không hệ thống sẽ tự động bỏ qua.

         */
        val permissionsToRequest = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQ_LOCATION
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    private fun generateNotification(location: Location?): Notification {
        val mainNotificationText = if (location != null) {
            "Vị trí hiện tại: ${location.latitude}, ${location.longitude}"
        } else {
            "Đang lấy vị trí..."
        }

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

    private fun changeLocationToGeoCoding() {
        if (currentLocation == null) {
            voiceResponder?.speak("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
            return
        }
        val place = destination ?: return
        val token = "pk.eyJ1Ijoia2hvYXplcm8yNyIsImEiOiJjbWlqM2h1M3gwYWhhM2VzNHRxYTdybmpkIn0.nLGN6nehHZ-0k7Sbfnq74A"
        RetrofitClient.api.getGeocoding(place, token)
            .enqueue(object : Callback<GeocodingResponse> {
                override fun onResponse(
                    call: Call<GeocodingResponse>,
                    response: Response<GeocodingResponse>
                ) {
                    if (response.isSuccessful) {
                        val feature = response.body()?.features?.firstOrNull()
                        if (feature != null) {
                            destLon = feature.center[0]
                            destLat = feature.center[1]
                            Log.d("MAPBOX", "Đã lấy tọa độ thành công ${destLon},${destLat}")
                            voiceResponder?.speak(text="Đã lấy tọa độ thành công ${destLon},${destLat} ",
                                onDone = {
                                    sendLocationToServer()
                                })


                        } else {
                            Log.d("MAPBOX", "Lấy tọa độ thất bại")
                            voiceResponder?.speak("Xin lỗi, hệ thống tìm kiếm địa điểm đang gặp sự cố. Vui lòng thử lại sau.")
                        }
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<GeocodingResponse>,
                    t: Throwable
                ) {
                    Log.d("MAPBOX", "Lỗi kết nối")
                    voiceResponder?.speak("Xin lỗi, lỗi kết nối mạng. Vui lòng kiểm tra internet và thử lại.")
                }

            }
            )
    }

    private fun sendLocationToServer() {
        val origin = currentLocation ?: return
        val destinationStr = "${destLon},${destLat}"
        val coordinates = "${origin.longitude},${origin.latitude};$destinationStr"
        val token = "pk.eyJ1Ijoia2hvYXplcm8yNyIsImEiOiJjbWlqM2h1M3gwYWhhM2VzNHRxYTdybmpkIn0.nLGN6nehHZ-0k7Sbfnq74A"
        //val proximityString="${origin.longitude},${origin.latitude}"

        RetrofitClient.api.getDirections(coordinates, steps = true, geometries = "geojson", token = token)
            .enqueue(object : Callback<MapboxResponse> {
                override fun onResponse(call: Call<MapboxResponse>, response: Response<MapboxResponse>) {
                    Log.d("MAPBOX_RAW", response.body().toString())
                    if (response.isSuccessful) {

                        val route = response.body()?.routes?.firstOrNull()
                        val leg = route?.legs?.firstOrNull()
                        val steps = leg?.steps ?: emptyList()

                        if (steps.isEmpty()) {
                            voiceResponder?.speak("Xin lỗi, không tìm thấy đường đi khả dụng đến ${destination}.")
                            Toast.makeText(this@MainActivity, "Không tìm thấy chỉ dẫn", Toast.LENGTH_SHORT).show()
                            return
                        }

                        routeSteps = MapBoxStepsParser.parseSteps(response.body())
                        isNavigating = true
                        currentStepIndex = 0

                        val distance = route?.distance ?: 0.0
                        val duration = route?.duration ?: 0.0
                        voiceResponder?.speak(text="Đã tìm thấy đường đi dài ${formatDistance(distance)}, mất ${formatDuration(duration)}. Bắt đầu chỉ đường.",
                            onDone = {
                                readCurrentStepInstruction()
                                //currentStepIndex++
                            })

                    } else {
                        // Trường hợp lỗi HTTP (4xx, 5xx)
                        val errorBody = response.errorBody()?.string()
                        Log.e("MAPBOX", "Directions API Lỗi ${response.code()}: $errorBody")
                        voiceResponder?.speak("Xin lỗi, không thể tính toán tuyến đường. Vui lòng kiểm tra địa điểm đã nhập.")
                    }
                }
                override fun onFailure(call: Call<MapboxResponse>, t: Throwable) {
                    Log.e(TAG, "Directions Lỗi: ${t.message}")
                    Toast.makeText(this@MainActivity, "Lỗi kết nối server", Toast.LENGTH_SHORT).show()
                }
            })
    }
    private fun readCurrentStepInstruction() {
        routeSteps?.let { steps ->
            if (currentStepIndex >= 0 && currentStepIndex < steps.size) {
                val instruction = steps[currentStepIndex].maneuver.instruction
                val distance = steps[currentStepIndex].distance
                voiceResponder?.speak("Trong ${formatDistance(distance)}, ${instruction}")
            }
        }
    }
    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.1f km", meters / 1000)
        } else {
            String.format("%d mét", meters.toInt())
        }
    }

    private fun formatDuration(seconds: Double): String {
        val minutes = (seconds / 60).toInt()
        val remainingSeconds = (seconds % 60).toInt()
        return if (minutes > 0) {
            "$minutes phút $remainingSeconds giây"
        } else {
            "$remainingSeconds giây"
        }
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
        if (requestCode == REQ_LOCATION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Đã cấp quyền định vị", Toast.LENGTH_SHORT).show()
                startLocationUpdates(locationRequest)
            } else {
                Toast.makeText(this, "Thiếu quyền định vị", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val voiceResponderOnDone = { text: String, onDoneAction: () -> Unit ->
        // Sử dụng TTS object (voiceResponder) để gọi hàm speak với callback
        // Đây là cách bạn ánh xạ cấu trúc (String, () -> Unit) -> Unit của VoiceCommandProcessor
        // sang cấu trúc speak(text, onDone = { ... }) của voiceResponder
        voiceResponder.speak(text = text, onDone = onDoneAction)
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
            voiceResponderOnDone = voiceResponderOnDone,
            geminiChat = geminiChat,
            NavigationCallback = this,
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



