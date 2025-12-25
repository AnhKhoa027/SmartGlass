package com.example.smartglass.Navigation

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.gps.*
import com.google.android.gms.location.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class NavigationManager(
    private val context: Context,
    private val voiceResponder: VoiceResponder,
    val listener: NavigationListener,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationRequest: LocationRequest,
    private val goongApiKey: String
) : LocationCallback() {
    private var currentLocation: Location? = null
    private var destination: String? = null
    private var destLon: Double? = null
    private var destLat: Double? = null
    private var routeSteps: List<GoongStep>? = null
    private var waitingForInitialLocation = false
    var isNavigating = false
    var currentStepIndex = 0
    private var isRecalculating = false
    private var lastSpokenStepIndex = -1
    private var lastDistanceToManeuver: Float? = null
    private var wrongDirCount = 0
    private val wrongDirection = 10f
    fun startListeningForLocation() {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            this,
            Looper.getMainLooper()
        )
    }
    fun stopListeningForLocation() {
        fusedLocationClient.removeLocationUpdates(this)
    }

    override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)
        val latestLocation = locationResult.lastLocation ?: return
        currentLocation = latestLocation
        listener.onLocationUpdate(latestLocation)
        if (waitingForInitialLocation) {
            waitingForInitialLocation = false
            destination?.let { changeLocationToGeoCoding() }
        }
        if (isNavigating && !routeSteps.isNullOrEmpty()) {
            handleNavigationStep(latestLocation)
        }
    }

    fun startNavigation(destination: String) {
        if (currentLocation == null) {
            this.destination = destination
            voiceResponder.speakGemini("Hãy di chuyển vài bước để tôi xác định hướng của bạn")
            voiceResponder.speakGemini("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
            waitingForInitialLocation = true
            listener.onStartLocationUpdatesRequested()
            return
        }

        this.destination = destination
        isNavigating = false
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
            resultsToDestination[0]
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
            val distanceToManeuver = results[0]
            if (checkOffRouteAndReroute(latestLocation, steps)) {
                return
            }
            if (distanceToManeuver < 10) {
                if (currentStepIndex == steps.size - 1) {
                    voiceResponder.speakNavigation("Bạn đã đến ${destination}. Kết thúc chỉ đường.")
                    isNavigating = false
                } else if (currentStepIndex < steps.size - 1) {
                    currentStepIndex++
                    readCurrentStepInstruction()
                    lastSpokenStepIndex = -1
                }
                return
            } else if (distanceToManeuver < 20)
            {
                if (distanceToDestination < 30) {
                    if (currentStepIndex != lastSpokenStepIndex) {
                        voiceResponder.speakNavigation(
                            "Đích đến của bạn chỉ còn cách khoảng ${formatDistance(distanceToDestination.toDouble())}."
                        )
                        lastSpokenStepIndex = currentStepIndex
                    }
                }
                else {
                    if (currentStepIndex != lastSpokenStepIndex) {
                        val instruction = step.htmlInstructions
                        val remainingDistance = distanceToManeuver.toDouble()
                        voiceResponder.speakNavigation(
                            "Sắp đến điểm rẽ. ${instruction} trong khoảng ${formatDistance(remainingDistance)} "
                        )
                        lastSpokenStepIndex = currentStepIndex
                    }
                }
            }
        }
    }
    private fun checkOffRouteAndReroute(currentLocation: Location, steps: List<GoongStep>): Boolean {
        if (currentStepIndex >= steps.size) return false
        val targetLat = steps[currentStepIndex].endLocation?.lat ?: 0.0
        val targetLon = steps[currentStepIndex].endLocation?.lng ?: 0.0

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            targetLat, targetLon,
            results
        )
        val currentDistance = results[0]
        if (lastDistanceToManeuver == null) {
            lastDistanceToManeuver = currentDistance
            return false
        }
        if (isRecalculating) return true
        if (currentDistance > 10) {
            if (currentDistance > lastDistanceToManeuver!! + wrongDirection) {
                wrongDirCount++
                if (wrongDirCount >= 3) { // 3 lần liên tiếp (khoảng 15s)
                    voiceResponder.speakNavigation("Bạn đã đi sai hướng, tôi đang tính lại tuyến đường.")
                    recalculateRoute()
                    wrongDirCount = 0
                    lastDistanceToManeuver = null
                    return true
                }
            } else if (currentDistance < lastDistanceToManeuver!! - 1) {
                // Nếu khoảng cách GIẢM ổn định (đi đúng)
                wrongDirCount = 0
            }
        }
        lastDistanceToManeuver = currentDistance
        return false
    }

    private fun recalculateRoute() {
        if (isRecalculating) {
            return
        }
        isRecalculating = true
        isNavigating = false
        routeSteps = null
        currentStepIndex = 0
        lastDistanceToManeuver = null
        wrongDirCount = 0
        if (currentLocation != null && destination != null) {
            changeLocationToGeoCoding()
        } else {
            voiceResponder.speakNavigation("Không thể tính lại tuyến đường vì thiếu thông tin vị trí hoặc điểm đến.")
            isRecalculating = false
        }
    }
    fun readCurrentStepInstruction() {
        routeSteps?.let { steps ->
            if (currentStepIndex >= 0 && currentStepIndex < steps.size) {
                val step = steps[currentStepIndex]
                val instruction = step.htmlInstructions
                val distance = step.distance?.value?.toDouble() ?: 0.0
                val formattedDistance = formatDistance(distance)
                if (instruction!!.contains("rẽ", ignoreCase = true) ||
                    instruction.contains("đi thẳng", ignoreCase = true)) {
                    voiceResponder.speakNavigation("${instruction},sau đó đi thêm${formattedDistance}")
                }
                else if (instruction.contains("Bắt đầu đi", ignoreCase = true) || currentStepIndex == 0) {
                    val startMessage = if (currentStepIndex == 0) {
                        // Đối với bước 0, chỉ cần xác nhận và nói khoảng cách:
                        "Hãy bắt đầu di chuyển. Bạn cần đi ${formattedDistance}."
                    } else {
                        "${instruction},sau đó đi thêm${formattedDistance}"
                    }
                    voiceResponder.speakNavigation(startMessage)
                }
            }
            else if (currentStepIndex == steps.size) {
                voiceResponder.speakNavigation("Bạn đã đến gần điểm cuối.")
            }
        }
    }

    private fun changeLocationToGeoCoding() {
        if (currentLocation == null) {
            voiceResponder.speakNavigation("Vui lòng đợi, tôi đang xác định vị trí hiện tại của bạn.")
            return
        }
        val place = destination ?: return
        val apiKey = goongApiKey
        if (apiKey.isEmpty() || apiKey == "YOUR_GOONG_API_KEY") {
            voiceResponder.speakNavigation("Lỗi thiết lập hệ thống, Key truy cập chỉ đường chưa hợp lệ.")
            return
        }
        val proximityLocation = "${currentLocation!!.latitude},${currentLocation!!.longitude}"
        GoongRetrofitClient.goongApi.getGeocoding(
            address = place,
            apiKey = apiKey,
            location = proximityLocation
        )
            .enqueue(object : Callback<GoongGeocodingResponse> {
                override fun onResponse(
                    call: Call<GoongGeocodingResponse>,
                    response: Response<GoongGeocodingResponse>
                ) {
                    if (response.isSuccessful) {
                        val location = response.body()?.results?.firstOrNull()?.geometry?.location

                        if (location != null) {
                            destLon = location.lng
                            destLat = location.lat
                            sendLocationToServer()
                        } else {
                            voiceResponder.speakNavigation("Xin lỗi, không tìm thấy tọa độ cho địa điểm đã nhập. Vui lòng thử lại sau.")
                        }
                    } else {
                        voiceResponder.speakNavigation("Xin lỗi, hệ thống tìm kiếm địa điểm đang gặp sự cố. Vui lòng thử lại sau.")
                    }
                }

                override fun onFailure(
                    call:Call<GoongGeocodingResponse>,
                    t: Throwable
                ) {
                    voiceResponder.speakNavigation("Xin lỗi, lỗi kết nối mạng. Vui lòng kiểm tra internet và thử lại.")
                }

            })
    }
    private fun sendLocationToServer() {
        val origin = currentLocation ?: return
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destLat},${destLon}"
        val apiKey =goongApiKey
        GoongRetrofitClient.goongApi.getDirections(
            origin = originStr,
            destination = destinationStr,
            vehicle = "bike",
            apiKey = apiKey
        )
            .enqueue(object : Callback<GoongDirectionResponse> {
                override fun onResponse(call: Call<GoongDirectionResponse>, response: Response<GoongDirectionResponse>) {
                    if (response.isSuccessful) {

                        val route = response.body()?.routes?.firstOrNull()
                        val leg = route?.legs?.firstOrNull() // Lấy Leg đầu tiên

                        if (route == null || route.legs.isNullOrEmpty()) {
                            voiceResponder.speakNavigation(text="Xin lỗi, không tìm thấy đường đi khả dụng đến ${destination}.")
                            return
                        }
                        routeSteps = GoongStepsParser.parseSteps(response.body()) // <-- DÙNG GOONG PARSER
                        if (routeSteps.isNullOrEmpty()) {
                            voiceResponder.speakNavigation("Xin lỗi, không tìm thấy chỉ dẫn chi tiết.")
                            return
                        }
                        isNavigating = true
                        currentStepIndex = 0
                        val distance = leg?.distance?.value?.toDouble() ?: 0.0
                        val duration = leg?.duration?.value?.toDouble() ?: 0.0

                        isRecalculating = false
                        val firstStep = routeSteps?.firstOrNull() ?: return
                        val endLat = firstStep.endLocation?.lat
                        val endLon = firstStep.endLocation?.lng
                        val initialDirection = calculateInitialDirection(currentLocation!!, endLat!!, endLon!!)

                        voiceResponder.speakNavigation(
                            "Đã tìm thấy đường đi dài ${formatDistance(distance)}, mất ${formatDuration(duration)}. " +
                                    "Bạn hãy ${initialDirection}."
                        )
                        readCurrentStepInstruction()


                    } else {
                        // Trường hợp lỗi HTTP (4xx, 5xx)
                        isRecalculating = false
                        val errorBody = response.errorBody()?.string()
                        Log.e("GOONG", "Directions API Lỗi ${response.code()}: $errorBody")
                        voiceResponder.speakNavigation("Xin lỗi, không thể tính toán tuyến đường. Vui lòng kiểm tra địa điểm đã nhập.")
                    }
                }
                override fun onFailure(call: Call<GoongDirectionResponse>, t: Throwable) {
                    isRecalculating = false
                }
            })
    }

    // Giữ nguyên các hàm này
    private fun calculateInitialDirection(start: Location, endLat: Double, endLon: Double): String {
        /*
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
         */
        val userDirection=start.bearing.toDouble()
        val results= FloatArray(3)
        Location.distanceBetween(
            start.latitude,start.longitude,
            endLat,endLon,
            results
        )
        val directionTarget=results[0].toDouble()
        val diff=(directionTarget-userDirection+360)%360
        return when {
            diff < 15 || diff > 345 ->
                "Đi thẳng"

            diff in 15.0..45.0 ->
                "Chếch phải một chút"

            diff in 45.0..120.0 ->
                "Rẽ phải"

            diff in 120.0..240.0 ->
                "Quay lại"

            diff in 240.0..315.0 ->
                "Rẽ trái"

            else ->
                "Chếch trái một chút"
        }
    }
    private fun formatDistance(meters: Double): String {
        // ... (Logic format)
        return if (meters >= 1000) {
            String.format(Locale.US,"%.1f km", meters / 1000)
        } else {
            String.format(Locale.US,"%d mét", meters.toInt())
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