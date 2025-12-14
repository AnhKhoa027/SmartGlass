// File: Navigation/NavigationManager.kt
package com.example.smartglass.Navigation

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.smartglass.MainActivity
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.gps.*
import com.google.android.gms.location.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NavigationManager(
    private val context: Context,
    private val voiceResponder: VoiceResponder,
    val listener: NavigationListener,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationRequest: LocationRequest,
    private val goongApiKey: String
) : LocationCallback() { // Kế thừa LocationCallback để nhận kết quả GPS

    private var currentLocation: Location? = null
    private var destination: String? = null
    private var destLon: Double? = null
    private var destLat: Double? = null
    private var routeSteps: List<GoongStep>? = null
    private var waitingForInitialLocation = false

    // Trạng thái điều hướng
    var isNavigating = false
    var currentStepIndex = 0
    private var isRecalculating = false
    private var lastSpokenStepIndex = -1
    private var lastDistanceToManeuver: Float? = null
    private var wrongDirCount = 0

    private val TAG = "NavManager"
    private val WRONG_DIRECTION_TOLERANCE_METERS = 10f // Ngưỡng sai lệch cho re-route

    // ==========================================================
    // 🟢 HÀM QUẢN LÝ VỊ TRÍ (Được gọi từ MainActivity)
    // ==========================================================

    fun startListeningForLocation() {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Yêu cầu Activity xử lý việc xin quyền
            Log.e(TAG, "Thiếu quyền GPS, yêu cầu Activity xử lý.")
            return
        }
        // Đăng ký nhận cập nhật vị trí, truyền 'this' làm callback
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            this, // NavigationManager tự xử lý callback vị trí
            Looper.getMainLooper()
        )
    }

    fun stopListeningForLocation() {
        fusedLocationClient.removeLocationUpdates(this)
        Log.i(TAG, "Dừng lắng nghe vị trí.")
    }

    override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)
        val latestLocation = locationResult.lastLocation ?: return
        // --- Bắt đầu Logic Ghi Log GPS ---
        val speedMps = latestLocation.speed // Tốc độ tính bằng mét/giây
        val speedKmh = speedMps * 3.6f // Chuyển sang km/h

        if (currentLocation == null) {
            Log.d("GPS_UPDATE", "Vị trí ban đầu đã được nhận.")
        } else {
            val distance = currentLocation!!.distanceTo(latestLocation)
            val timeElapsed = (latestLocation.time - currentLocation!!.time) / 1000.0 // Thời gian trôi qua (giây)

            Log.d(
                "GPS_UPDATE",
                "Long: ${latestLocation.longitude}, Lat: ${latestLocation.latitude} | " +
                        "Di chuyển: ${String.format("%.2f m", distance)} | " +
                        "Tốc độ: ${String.format("%.2f km/h", speedKmh)}"
            )

            // Kiểm tra và ghi log nếu có sự thay đổi lớn (ví dụ: di chuyển hơn 1 mét)
            if (distance > 1.0) {
                Log.i("GPS_CHANGE", "Vị trí đã thay đổi đáng kể (${String.format("%.2f m", distance)})")
            }
        }
        // --- Kết thúc Logic Ghi Log GPS ---
        currentLocation = latestLocation
        listener.onLocationUpdate(latestLocation)
        // 1. Logic chờ vị trí ban đầu
        if (waitingForInitialLocation) {
            waitingForInitialLocation = false
            destination?.let { changeLocationToGeoCoding() }
        }

        // 2. Logic điều hướng
        if (isNavigating && !routeSteps.isNullOrEmpty()) {
            handleNavigationStep(latestLocation)
        }
    }

    fun startNavigation(destination: String) {
        if (currentLocation == null) {
            this.destination = destination
            //listener.onSpeakInstruction("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
            voiceResponder.speak("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.", onDone =
                {
                    waitingForInitialLocation = true
                    // Đảm bảo GPS đang chạy để lấy vị trí
                    listener.onStartLocationUpdatesRequested()
                })
            return
        }

        this.destination = destination
        isNavigating = false
        //listener.onSpeakInstruction("Đã nhận lệnh: Đi đến $destination. Đang tìm đường.")
        changeLocationToGeoCoding()
    }

    fun stopNavigation() {
        if (isNavigating) {
            isNavigating = false
            destination = null
            routeSteps = null
            currentStepIndex = 0
            lastSpokenStepIndex = -1
            lastDistanceToManeuver = null
            wrongDirCount = 0
            listener.onDestinationReached(destination ?: "đích") // Báo Activity dừng hẳn
            stopListeningForLocation()
        } else {
            listener.onSpeakInstruction("Hiện không có chỉ đường nào đang hoạt động.")
        }
    }

    private fun handleNavigationStep(latestLocation: Location) {
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

    // Hàm kiểm tra và gọi tính lại tuyến đường
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

    // Hàm đọc hướng dẫn rẽ (Được gọi từ handleNavigationStep)
    fun readCurrentStepInstruction() {
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

    private fun changeLocationToGeoCoding() {
        if (currentLocation == null) {
            voiceResponder.speak("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
            return
        }
        val place = destination ?: return

        val apiKey = goongApiKey // <-- SỬ DỤNG KEY GOONG

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

    private fun sendLocationToServer() {
        val origin = currentLocation ?: return

        // Directions API Goong dùng format LAT,LON
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destLat},${destLon}"

        val apiKey =goongApiKey

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
                                   }
            })
    }

    // Giữ nguyên các hàm này
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
        // ... (Logic format)
        return if (meters >= 1000) {
            String.format("%.1f km", meters / 1000)
        } else {
            String.format("%d mét", meters.toInt())
        }
    }
    private fun formatDuration(seconds: Double): String {
        // ... (Logic format)
        val minutes = (seconds / 60).toInt()
        val remainingSeconds = (seconds % 60).toInt()
        return if (minutes > 0) {
            "$minutes phút $remainingSeconds giây"
        } else {
            "$remainingSeconds giây"
        }
    }
}