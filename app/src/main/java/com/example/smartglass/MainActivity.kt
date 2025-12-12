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
import com.google.android.gms.location.*
import okhttp3.OkHttpClient
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.TTSandSTT.VoiceRecognitionManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.smartglass.gps.GoongRetrofitClient
import com.example.smartglass.gps.GoongGeocodingResponse
import com.example.smartglass.gps.GoongDirectionResponse
import com.example.smartglass.gps.GoongStepsParser
import com.example.smartglass.gps.GoongStep

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
    private var destination: String? = null
    private var destLon: Double? = null
    private var destLat: Double? = null

    private var currentStepIndex = 0// Chỉ số bước hiện tại không thể âm
    private var isNavigating = false // Cờ hiệu đang trong quá trình điều hướng
    private var waitingForInitialLocation = false
    private var lastSpokenStepIndex = -1
    private var routeSteps: List<GoongStep>? = null
    private val GOONG_API_KEY = "1hDHs4M7KJHX3mCe1cTzxVxtFvs3hHVyuP6wdEDh"
    private var lastDistanceToManeuver: Float? = null
    private var wrongDirCount = 0
    private var isRecalculating = false


    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "location_channel_id"
        const val ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST =
            "com.example.smartglass.action.FOREGROUND_ONLY_LOCATION_BROADCAST"
        const val EXTRA_LOCATION = "com.example.smartglass.extra.LOCATION"
        //Cho phép cập nhật vị trí đến
        private var serviceRunningInForeground = true
        private const val WRONG_DIRECTION_TOLERANCE_METERS = 10f // Cho phép sai số khi so sánh khoảng cách tăng/giảm

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
            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
                currentLocation = latestLocation
                if (waitingForInitialLocation) {
                    currentLocation = latestLocation // Gán vị trí đã lấy được
                    waitingForInitialLocation = false // Tắt cờ

                    // Chạy logic điều hướng chính với vị trí đã có
                    // Cần đảm bảo rằng `destination` đã được lưu từ lúc trước
                    destination?.let { dest ->
                        changeLocationToGeoCoding()
                    }
                }

                Log.d(
                    "GPS_DEBUG",
                    "Longtitude: ${latestLocation.longitude}, Latitude: ${latestLocation.latitude}"
                )

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
                    val steps: List<GoongStep> = routeSteps!!

                    // Chỉ xử lý nếu chưa đến bước cuối cùng
                    val finalStep = steps.last()
                    val destinationLat = finalStep.endLocation?.lat ?: 0.0
                    val destinationLon = finalStep.endLocation?.lng ?: 0.0

                    val resultsToDestination = FloatArray(1)
                    Location.distanceBetween(
                        latestLocation.latitude, latestLocation.longitude,
                        destinationLat, destinationLon,
                        resultsToDestination
                    )
                    val distanceToDestination =
                        resultsToDestination[0] // Khoảng cách đến đích cuối cùng (meters)
                    if (currentStepIndex >= 0 && currentStepIndex < steps.size) {
                        val step = steps[currentStepIndex]
                        val turnLat = step.endLocation?.lat ?: 0.0
                        val turnLon = step.endLocation?.lng ?: 0.0

                        val results = FloatArray(1)
                        Location.distanceBetween(
                            latestLocation.latitude, latestLocation.longitude,
                            turnLat, turnLon,
                            results
                        )
                        val distanceToManeuver = results[0] // Khoảng cách đến điểm ngoặt (meters)

                        Log.d("NAV_STEP", "Bước $currentStepIndex: Cách ${distanceToManeuver}m")

                        // Hàm này sẽ kiểm tra và nếu thấy sai đường sẽ gọi recalculateRoute()
                        // và thoát khỏi phần xử lý bước rẽ (vì route cũ đã bị hủy).
                        if (checkOffRouteAndReroute(latestLocation, steps)) {
                            // Nếu hàm trả về true (đã kích hoạt re-route), thì ta dừng xử lý chuyển bước
                            return
                        }
                        // Kiểm tra: Nếu gần đến điểm ngoặt (ví dụ dưới 20 mét)
                        if (distanceToManeuver < 10) {
                            // Đã hoàn thành bước hiện tại

                            if (currentStepIndex == steps.size - 1) {
                                // Đã hoàn thành bước cuối cùng, báo đã đến đích.
                                voiceResponder.speak("Bạn đã đến ${destination}. Kết thúc chỉ đường.")
                                isNavigating = false
                                stopLocationUpdates()

                            } else if (currentStepIndex < steps.size - 1) { // Nếu vẫn còn ít nhất 2 bước nữa (bước hiện tại và bước tiếp theo)
                                // Nếu KHÔNG phải là bước cuối cùng, chuyển sang đọc bước tiếp theo.
                                currentStepIndex++ // Tăng index lên bước tiếp theo (N+1)
                                readCurrentStepInstruction() // Đọc hướng dẫn của bước N+1
                                lastSpokenStepIndex = -1 // Reset cờ nhắc nhở cho bước mới
                                Log.i(
                                    "NAV_DECISION",
                                    "ĐÃ ĐẠT NGƯỠNG RẼ (< 5m) -> Chuyển sang đọc bước ${currentStepIndex}!"
                                )
                            }

                            // Lưu ý: Trường hợp currentStepIndex >= steps.size đã được loại trừ ở bên ngoài.

                            // Thoát khỏi hàm để không chạy logic nhắc nhở dưới đây cho bước vừa hoàn thành
                            return
                        } else if (distanceToManeuver < 20)//&& lastSpokenStepIndex < currentStepIndex)
                        {
                            // 1. KIỂM TRA: NẾU ĐANG Ở BƯỚC ÁP CHÓT (chỉ còn 1 bước nữa là hết)
                            if (distanceToDestination < 30) {
                                // Nếu đây là bước áp chót, chỉ cần nhắc nhở rằng đích đến sắp tới.
                                if (currentStepIndex != lastSpokenStepIndex) {
                                    voiceResponder.speak(
                                        "Đích đến của bạn chỉ còn cách khoảng ${
                                            formatDistance(
                                                distanceToDestination.toDouble()
                                            )
                                        }."
                                    )
                                    lastSpokenStepIndex = currentStepIndex
                                }
                            }
                            // 2. NGƯỢC LẠI: LÀ BƯỚC GIỮA ĐƯỜNG, ĐỌC HƯỚNG DẪN RẼ
                            else {
                                if (currentStepIndex != lastSpokenStepIndex) {
                                    Log.w(
                                        "NAV_DECISION",
                                        "Đang nhắc nhở bước $currentStepIndex, khoảng cách còn lại: ${distanceToManeuver}m"
                                    )

                                    val instruction = step.htmlInstructions
                                    val remainingDistance = distanceToManeuver.toDouble()

                                    // Sử dụng remainingDistance để thông báo chính xác
                                    voiceResponder.speak(
                                        "Sắp đến điểm rẽ. ${instruction} trong khoảng ${
                                            formatDistance(
                                                remainingDistance
                                            )
                                        } "
                                    )

                                    // Cập nhật cờ: Đánh dấu bước này đã được nhắc nhở
                                    lastSpokenStepIndex = currentStepIndex
                                }
                            }
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
            lastSpokenStepIndex = -1
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

    private fun checkOffRouteAndReroute(currentLocation: Location, steps: List<GoongStep>): Boolean {

        // 1. Kiểm tra an toàn: Đã đến đích chưa
        if (currentStepIndex >= steps.size) return false

        // 2. TÍNH KHOẢNG CÁCH ĐẾN ĐIỂM RẼ TIẾP THEO
        val targetLat = steps[currentStepIndex].endLocation?.lat ?: 0.0 // Lấy từ endLocation
        val targetLon = steps[currentStepIndex].endLocation?.lng ?: 0.0 // Lấy từ endLocation

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            targetLat, targetLon,
            results
        )
        val currentDistance = results[0] // <-- BIẾN currentDistance ĐÃ ĐƯỢC TÍNH TẠI ĐÂY

        // 3. KIỂM TRA KHỞI TẠO (Lần đầu nhận vị trí sau khi tính đường)
        if (lastDistanceToManeuver == null) {
            lastDistanceToManeuver = currentDistance // Gán giá trị currentDistance đã tính
            return false // Thoát, chưa cần kiểm tra sai đường
        }

        // 4. KIỂM TRA SAI LỆCH VÀ RE-ROUTE
        if (isRecalculating) return true
        // Chỉ kiểm tra khi không quá gần điểm rẽ (ví dụ > 10m), vì ở gần rẽ GPS có thể nhiễu
        if (currentDistance > 10) {

            // Nếu khoảng cách MỚI lớn hơn khoảng cách CŨ + một ngưỡng an toàn
            if (currentDistance > lastDistanceToManeuver!! + WRONG_DIRECTION_TOLERANCE_METERS) {
                wrongDirCount++
                Log.w("NAV_DEVIATION", "Khoảng cách TĂNG: ${lastDistanceToManeuver} -> $currentDistance (count=$wrongDirCount)")

                if (wrongDirCount >= 3) { // 3 lần liên tiếp (khoảng 15s)
                    Log.e("NAV_DECISION", "ĐI LỆCH ĐƯỜNG -> Kích hoạt TÍNH LẠI ROUTE")
                    voiceResponder.speak("Bạn đã đi sai hướng, tôi đang tính lại tuyến đường.") {
                        recalculateRoute()
                    }
                    wrongDirCount = 0
                    lastDistanceToManeuver = null // Reset để khởi động lại quá trình kiểm tra cho route mới
                    return true // Đã kích hoạt Re-route
                }
            } else if (currentDistance < lastDistanceToManeuver!! - 1) {
                // Nếu khoảng cách GIẢM ổn định (đi đúng)
                wrongDirCount = 0
            }
        }

        // 5. CẬP NHẬT TRẠNG THÁI CUỐI
        lastDistanceToManeuver = currentDistance
        return false
    }

    private fun recalculateRoute() {
        // 1. Dọn dẹp trạng thái điều hướng hiện tại
        if (isRecalculating) {
            Log.d("NAV_DECISION", "Đã có lệnh tính lại đường, bỏ qua lệnh này.")
            return
        }
        isRecalculating = true // BẬT CỜ
        isNavigating = false
        routeSteps = null
        currentStepIndex = 0
        lastDistanceToManeuver = null
        wrongDirCount = 0

        // 2. Tái sử dụng logic Geocoding và Directions
        if (currentLocation != null && destination != null) {
            // Gọi hàm tìm tọa độ (Geocoding) và sau đó gọi Directions (sendLocationToServer)
            changeLocationToGeoCoding()
        } else {
            voiceResponder.speak("Không thể tính lại tuyến đường vì thiếu thông tin vị trí hoặc điểm đến.")
            isRecalculating = false // TẮT CỜ nếu lỗi ngay lập tức
        }
    }

    private fun changeLocationToGeoCoding() {
        if (currentLocation == null) {
            voiceResponder.speak("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
            return
        }
        val place = destination ?: return

        val apiKey = GOONG_API_KEY // <-- SỬ DỤNG KEY GOONG

        // KIỂM TRA API KEY (BẠN ĐÃ CÓ KEY TỪNG LÀM VIETMAP, NẾU KHÔNG PHẢI GOONG THẬT SẼ BỊ LỖI)
        if (apiKey.isEmpty() || apiKey == "YOUR_GOONG_API_KEY") {
            Log.e("GOONG_API", "LỖI: API Key Goong chưa được thiết lập!")
            voiceResponder.speak("Lỗi thiết lập hệ thống, Key truy cập chỉ đường chưa hợp lệ.")
            return
        }

        // Tạo chuỗi tọa độ ưu tiên theo format Goong: lat,lng
        val proximityLocation = "${currentLocation!!.latitude},${currentLocation!!.longitude}"

        // GỌI GOONG GEOCoding (ĐÃ SỬA TÊN CLIENT)
        GoongRetrofitClient.goongApi.getGeocoding(
            address = place,
            apiKey = apiKey,
            location = proximityLocation // Dùng vị trí hiện tại để ưu tiên kết quả gần nhất
        )
            .enqueue(object : Callback<GoongGeocodingResponse> { // <-- DÙNG GOONG RESPONSE
                override fun onResponse(
                    call: Call<GoongGeocodingResponse>,
                    response: Response<GoongGeocodingResponse>
                ) {
                    if (response.isSuccessful) {
                        // Lấy tọa độ từ phản hồi Goong: results[0].geometry.location
                        val location = response.body()?.results?.firstOrNull()?.geometry?.location

                        if (location != null) {
                            // Goong trả về {lat, lng}
                            destLon = location.lng
                            destLat = location.lat
                            Log.d("GOONG", "Đã lấy tọa độ thành công Lon:${destLon},Lat:${destLat}")
                            sendLocationToServer()
                        } else {
                            Log.d("GOONG", "Lấy tọa độ thất bại")
                            voiceResponder.speak("Xin lỗi, không tìm thấy tọa độ cho địa điểm đã nhập. Vui lòng thử lại sau.")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("GOONG", "Geocoding API Lỗi HTTP ${response.code()}: $errorBody")
                        voiceResponder.speak("Xin lỗi, hệ thống tìm kiếm địa điểm đang gặp sự cố. Vui lòng thử lại sau.")
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<GoongGeocodingResponse>,
                    t: Throwable
                ) {
                    Log.e("GOONG", "Lỗi kết nối Geocoding: ${t.message}")
                    voiceResponder.speak("Xin lỗi, lỗi kết nối mạng. Vui lòng kiểm tra internet và thử lại.")
                }

            }
            )
    }

    // ĐÃ CHUYỂN SANG GOONG API
    private fun sendLocationToServer() {
        val origin = currentLocation ?: return

        // Directions API Goong dùng format LAT,LON
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destLat},${destLon}"

        val apiKey =GOONG_API_KEY

        // GỌI GOONG DIRECTIONS (ĐÃ SỬA TÊN CLIENT)
        GoongRetrofitClient.goongApi.getDirections(
            origin = originStr,
            destination = destinationStr,
            vehicle = "bike",
            apiKey = apiKey
        )
            .enqueue(object : Callback<GoongDirectionResponse> { // <-- DÙNG GOONG RESPONSE
                override fun onResponse(call: Call<GoongDirectionResponse>, response: Response<GoongDirectionResponse>) {
                    Log.d("GOONG_RAW", response.body().toString())
                    if (response.isSuccessful) {

                        val route = response.body()?.routes?.firstOrNull()
                        val leg = route?.legs?.firstOrNull() // Lấy Leg đầu tiên

                        if (route == null || route.legs.isNullOrEmpty()) {
                            voiceResponder.speak("Xin lỗi, không tìm thấy đường đi khả dụng đến ${destination}.")
                            Toast.makeText(this@MainActivity, "Không tìm thấy chỉ dẫn", Toast.LENGTH_SHORT).show()
                            return
                        }

                        // SỬ DỤNG PARSER CỦA GOONG
                        routeSteps = GoongStepsParser.parseSteps(response.body()) // <-- DÙNG GOONG PARSER

                        // Kiểm tra an toàn lần nữa
                        if (routeSteps.isNullOrEmpty()) {
                            voiceResponder.speak("Xin lỗi, không tìm thấy chỉ dẫn chi tiết.")
                            return
                        }
                        // ==========================================================
                        // 🚀 LOGIC IN RA LOGCAT ĐỂ KIỂM TRA DEBUGGING
                        // ==========================================================
                        Log.i("GOONG_NAV", "========================================")
                        Log.i("GOONG_NAV", "Tuyến đường có ${routeSteps!!.size} bước:")

                        routeSteps!!.forEachIndexed { index, step ->
                            // Lấy giá trị số (Int) từ GoongValue, chuyển sang Double
                            val dist = step.distance?.value?.toDouble() ?: 0.0
                            val dur = step.duration?.value?.toDouble() ?: 0.0

                            // Lấy tọa độ cuối của bước đi (điểm rẽ/mục tiêu)
                            val endLat = step.endLocation?.lat
                            val endLon = step.endLocation?.lng

                            // Lấy hướng dẫn (instruction)
                            val instruction = step.htmlInstructions ?: "Không có hướng dẫn HTML"

                            Log.i("GOONG_NAV",
                                "STEP $index: " +
                                        "| Hướng dẫn: $instruction | " +
                                        "| Khoảng cách: ${formatDistance(dist)} | " +
                                        "| Thời gian: ${formatDuration(dur)} | " +
                                        "| Tọa độ đích (Lat/Lon): (${endLat}, ${endLon})"
                            )
                        }
                        Log.i("GOONG_NAV", "========================================")
                        // ==========================================================

                        isNavigating = true
                        currentStepIndex = 0

                        // LẤY TỔNG KHOẢNG CÁCH VÀ THỜI GIAN TỪ LEG (SỬ DỤNG .value)
                        val distance = leg?.distance?.value?.toDouble() ?: 0.0
                        val duration = leg?.duration?.value?.toDouble() ?: 0.0

                        isRecalculating = false
                        val firstStep = routeSteps?.firstOrNull() ?: return
                        val endLat = firstStep.endLocation?.lat ?: return
                        val endLon = firstStep.endLocation?.lng ?: return
                        val initialDirection = calculateInitialDirection(currentLocation!!, endLat, endLon)

                        voiceResponder.speak(
                            text = "Đã tìm thấy đường đi dài ${formatDistance(distance)}, mất ${formatDuration(duration)}. " +
                                    "Bạn hãy bắt đầu đi về hướng ${initialDirection}.", // <-- THÊM THÔNG BÁO HƯỚNG
                            onDone = {
                                readCurrentStepInstruction()
                            }
                        )

                    } else {
                        // Trường hợp lỗi HTTP (4xx, 5xx)
                        isRecalculating = false
                        val errorBody = response.errorBody()?.string()
                        Log.e("GOONG", "Directions API Lỗi ${response.code()}: $errorBody")
                        voiceResponder.speak("Xin lỗi, không thể tính toán tuyến đường. Vui lòng kiểm tra địa điểm đã nhập.")
                    }
                }
                override fun onFailure(call: Call<GoongDirectionResponse>, t: Throwable) {
                    isRecalculating = false
                    Log.e(TAG, "Directions Lỗi: ${t.message}")
                    Toast.makeText(this@MainActivity, "Lỗi kết nối server", Toast.LENGTH_SHORT).show()
                }
            })
    }
    private fun readCurrentStepInstruction() {
        routeSteps?.let { steps ->
            if (currentStepIndex >= 0 && currentStepIndex < steps.size) {
                val step = steps[currentStepIndex]

                // Lấy instruction và distance
                val instruction = step.htmlInstructions
                val distance = step.distance?.value?.toDouble() ?: 0.0

                val formattedDistance = formatDistance(distance)
                // 1. Nếu là hướng dẫn rẽ thật sự (không phải "Bắt đầu đi")
                if (instruction!!.contains("rẽ", ignoreCase = true) ||
                    instruction.contains("đi thẳng", ignoreCase = true)) {
                    voiceResponder.speak("${instruction},sau đó đi thêm${formattedDistance}")

                }
                // 2. Xử lý trường hợp "Bắt đầu đi" (thường là bước 0)
                else if (instruction.contains("Bắt đầu đi", ignoreCase = true) || currentStepIndex == 0) {

                    val startMessage = if (currentStepIndex == 0) {
                        // Đối với bước 0, chỉ cần xác nhận và nói khoảng cách:
                        "Hãy bắt đầu di chuyển. Bạn cần đi ${formattedDistance}."
                    } else {
                        // Nếu là bước giữa chừng có hướng dẫn là "Bắt đầu đi", giữ nguyên
                        "${instruction},sau đó đi thêm${formattedDistance}"
                    }
                    voiceResponder.speak(startMessage)
                }


            }
            else if (currentStepIndex == steps.size) {
                voiceResponder.speak("Bạn đã đến gần điểm cuối.")
            }
        }
    }
    private fun calculateInitialDirection(start: Location, endLat: Double, endLon: Double): String {
        // 1. Tính Bearing (Góc la bàn) giữa hai điểm
        val results = FloatArray(3)
        // Android's Location.distanceBetween cũng tính Bearing (initialBearing)
        Location.distanceBetween(
            start.latitude, start.longitude,
            endLat, endLon,
            results
        )
        // Bearing (góc từ Bắc) nằm ở results[1]
        val initialBearing = results[1].toDouble()

        // 2. Chuẩn hóa góc về [0, 360)
        var angle = (initialBearing + 360) % 360

        // 3. Chuyển Bearing sang Hướng chính (8 Hướng)
        return when {
            angle >= 337.5 || angle < 22.5 -> "Bắc"
            angle >= 22.5 && angle < 67.5  -> "Đông Bắc"
            angle >= 67.5 && angle < 112.5 -> "Đông"
            angle >= 112.5 && angle < 157.5 -> "Đông Nam"
            angle >= 157.5 && angle < 202.5 -> "Nam"
            angle >= 202.5 && angle < 247.5 -> "Tây Nam"
            angle >= 247.5 && angle < 292.5 -> "Tây"
            else -> "Tây Bắc" // 292.5 - 337.5
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
            val keywordFile = File(filesDir, "Hey-Nana_en_android_v4_0_0.ppn")
            if (!keywordFile.exists()) {
                assets.open("Hey-Nana_en_android_v4_0_0.ppn").use { input ->
                    keywordFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("WakeWord", "Copied keyword file: ${keywordFile.absolutePath}")
            }

            wakeWordManager = WakeWordManager(
                context = this,
                accessKey = "W8WX0LISM+lvDmBoZmZZFgzot+XezDl3EP4quWB4KCVNQ3klMjhOhw==",
                //Cua Khoa
                //accessKey = "LBKWPv6jiRpVsjkJp9wmYWhiv/H1dTxzzu6eQpOd++WZNm7kHMPUbw==",
                //Cua Hiep Hey Nana
                //accessKey = "0deYDn+Gu4ANgLreLQbh19vppkEoOIzjxy2o2PQpFQRpHYsJM5vn5Q==",
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



