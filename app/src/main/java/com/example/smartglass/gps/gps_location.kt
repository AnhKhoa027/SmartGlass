package com.example.smartglass.gps
import MapboxResponse
import MapboxStep
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.smartglass.R
import com.google.android.gms.location.*
import okhttp3.OkHttpClient
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import com.example.smartglass.MainActivity
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.TTSandSTT.VoiceRecognitionManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder


class location_gps : AppCompatActivity() {
    //Sử dụng fusedLocationClient để lấy chính xác vị trí,chính xác hơn so với LocationManager thuần
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnStartListen: Button
    private var currentLocation: Location? = null

    //Nhận cập nhật vị trí
    private lateinit var locationCallback: LocationCallback
    private val client = OkHttpClient()
    private val TAG = "LocationService"

    //Điều chỉnh thông báo hiển thị trên foreground
    private lateinit var notificationManager: NotificationManager

    private lateinit var locationRequest: LocationRequest

    private var voiceResponder: VoiceResponder? = null
    private var voiceManager: VoiceRecognitionManager? = null
    private var destination: String? = null
    private var destLon: Double? = null
    private var destLat: Double? = null
    private var routeSteps: List<MapboxStep>? = null // Danh sách các bước chỉ đường
    private var currentStepIndex = 0// Chỉ số bước hiện tại không thể âm
    private var isNavigating = false // Cờ hiệu đang trong quá trình điều hướng


    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        private const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "location_channel_id"
        const val ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST =
            "com.example.smartglass.action.FOREGROUND_ONLY_LOCATION_BROADCAST"
        const val EXTRA_LOCATION = "com.example.smartglass.extra.LOCATION"
        //Cho phép cập nhật vị trí đến
        private var serviceRunningInForeground = true
    }
    // --- Activity Result ---//
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull() ?: ""

            if (text.isNotBlank()) {
                handleSpeechResult(text)
            }
        } else {
            // Xử lý khi người dùng không nói hoặc lỗi nhận dạng
            voiceResponder?.speakNavigation("Không nghe rõ, vui lòng thử lại.")
        }
    }
    // --- onCreate ---//
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //tvLatitude = findViewById(R.id.tvLatitude)
        //tvLongitude = findViewById(R.id.tvLongitude)
        //btnStartListen = findViewById(R.id.btnStartListen)
        btnStartListen=findViewById(R.id.fabMic)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Tạo notification channel cho Android 8+
        /*Trên Android 8+ phải tạo channel để show notification;
        channel có importance quyết định độ ưu tiên (LOW thường không gây âm thanh).
        Nếu không tạo, notification có thể không hiển thị đúng.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

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

                // Chỉ cập nhật và xử lý logic một lần
                currentLocation = latestLocation
                val latitude = latestLocation.latitude
                val longitude = latestLocation.longitude

                // Cập nhật UI an toàn
                //tvLatitude.text = "Latitude: $latitude"
                //tvLongitude.text = "Longitude: $longitude"
                Log.d("GPS_DEBUG", "Latitude: $latitude, Longitude: $longitude")

                // Gửi Broadcast (một lần)
                val intent = Intent(ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
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
                                voiceResponder?.speakNavigation("Bạn đã đến đích, ${destination}. Kết thúc chỉ đường.")
                                isNavigating = false
                            }
                        } else if (distanceToManeuver < 100) {
                            // Có thể thêm logic nhắc nhở nếu còn 100m nhưng chưa đọc
                        }
                    }
                }
            }
        }
        // Khởi tạo các thành phần giọng nói
        voiceResponder = VoiceResponder(this)
        voiceManager = VoiceRecognitionManager(this, speechLauncher)
        // btnStartListen cần được khởi tạo từ R.id.btnStartListen


        // Setup nhận lời nói từ người dùng
        btnStartListen.setOnClickListener {
            if (isNavigating) {
                voiceResponder?.speakNavigation("Đang trong quá trình chỉ đường. Bạn muốn dừng lại không?")

            } else {
                voiceResponder?.speakNavigation("Mời bạn nói địa điểm muốn đến.")
                voiceManager?.startListening()
            }
        }

        //  Kiểm tra quyền trước khi bắt đầu cập nhật GPS
        // Luôn kiểm tra runtime permission trước khi yêu cầu định vị
        if (checkPermission()) {
            startLocationUpdates(locationRequest)
        } else {
            requestPermissions()
        }
    }
    /*
       Viết một hàm kiểm tra đầu vào xem có nhận được tín hiệu đúng với parse hay không
       Nếu nhận được đúng địa chỉ thì gọi hàm request tọa độ
    */

    private fun handleSpeechResult(text: String) {
        try {
            val patters=listOf("đi đến","tới","đi qua","điểm đến")
            val lowerText=text.lowercase().trim()
            for(patter in patters)
            {
                if(lowerText.contains(patter))
                {
                    destination = lowerText.substringAfter(patter).trim()
                    break
                }
            }

            if (destination.isNullOrBlank()) {
                voiceResponder?.speakNavigation("Không tìm thấy địa điểm.")
                return
            }
            //destination = URLEncoder.encode(destination, "UTF-8")
            voiceResponder?.speakNavigation(
                "Đã nhận lệnh: Đi đến $destination. Đang tìm đường."
            )
            changeLocationToGeoCoding()


        }catch (e: Exception)
        {
            Log.e("VoiceProcessor", "Parse lỗi: ${e.message}")
            voiceResponder?.speakNavigation("Không nhận được địa điểm cần đến,vui lòng thử lại")
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
    private val REQ_LOCATION = 1001

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

    private fun checkPermission(): Boolean {
        // Lấy chính xác tọa độ của người dùng,nếu không sử dụng chỉ lấy được vị trí tương đối mà thôi
        val fine = ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        //Kiểm tra quyền truy cập vị trí tương đối , mức độ chính xác thấp
        val coarse = ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // Quyền định vị nền, cho phép người dùng không mở app


        return fine && coarse
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_LOCATION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Đã cấp quyền định vị", Toast.LENGTH_SHORT).show()
                startLocationUpdates(locationRequest)
            } else {
                Toast.makeText(this, "Thiếu quyền định vị", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
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
            voiceResponder?.speakNavigation("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
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
                            voiceResponder?.speakNavigation(
                                "Đã xác định được vị trí điểm đến. Đang tính toán tuyến đường."
                            )
                            sendLocationToServer()



                        } else {
                            Log.d("MAPBOX", "Lấy tọa độ thất bại")
                            voiceResponder?.speakNavigation("Xin lỗi, hệ thống tìm kiếm địa điểm đang gặp sự cố. Vui lòng thử lại sau.")
                        }
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<GeocodingResponse>,
                    t: Throwable
                ) {
                    Log.d("MAPBOX", "Lỗi kết nối")
                    voiceResponder?.speakNavigation("Xin lỗi, lỗi kết nối mạng. Vui lòng kiểm tra internet và thử lại.")
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
                            voiceResponder?.speakNavigation("Xin lỗi, không tìm thấy đường đi khả dụng đến ${destination}.")
                            Toast.makeText(this@location_gps, "Không tìm thấy chỉ dẫn", Toast.LENGTH_SHORT).show()
                            return
                        }

                        routeSteps = MapBoxStepsParser.parseSteps(response.body())
                        isNavigating = true
                        currentStepIndex = 0

                        val distance = route?.distance ?: 0.0
                        val duration = route?.duration ?: 0.0
                        voiceResponder?.speakNavigation(text="Đã tìm thấy đường đi dài ${formatDistance(distance)}, mất ${formatDuration(duration)}. Bắt đầu chỉ đường.",)
                        readCurrentStepInstruction()
                        currentStepIndex++

                    } else {
                        // Trường hợp lỗi HTTP (4xx, 5xx)
                        val errorBody = response.errorBody()?.string()
                        Log.e("MAPBOX", "Directions API Lỗi ${response.code()}: $errorBody")
                        voiceResponder?.speakNavigation("Xin lỗi, không thể tính toán tuyến đường. Vui lòng kiểm tra địa điểm đã nhập.")
                    }
                }
                override fun onFailure(call: Call<MapboxResponse>, t: Throwable) {
                    Log.e(TAG, "Directions Lỗi: ${t.message}")
                    Toast.makeText(this@location_gps, "Lỗi kết nối server", Toast.LENGTH_SHORT).show()
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
}


