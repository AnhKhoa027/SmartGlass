package com.example.smartglass.TTSandSTT
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.example.smartglass.DetectResponse.GeminiChat
import com.example.smartglass.Navigation.NavigationCallback
import com.example.smartglass.SettingAction.SettingsManager
import com.example.smartglass.gps.LocationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import okhttp3.*
import android.util.Log
import com.google.gson.JsonParser
import java.io.IOException
import java.util.*

class VoiceCommandProcessor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val bottomNav: BottomNavigationView,
    private val onConnect: (callback: (Boolean) -> Unit) -> Unit,
    private val onDisconnect: (callback: (Boolean) -> Unit) -> Unit,
    private val voiceResponder: VoiceResponder,
    private val geminiChat: GeminiChat,
    private val navigationCallback: NavigationCallback
) {

    private val settings = SettingsManager.getInstance(activity.applicationContext)
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient()

    init {
        scope.launch { settings.volumeFlow.collect {} }
        scope.launch { settings.speedFlow.collect {} }
        scope.launch { settings.keepScreenOnFlow.collect {} }
    }

    // =========================
    // SPEAK HELPERS (QUAN TRỌNG)
    // =========================
    private fun speak(text: String) {
        voiceResponder.speak(text)
    }

    private fun speakNav(text: String) {
        voiceResponder.speakNavigation(text)
    }

    private fun speakGemini(text: String) {
        voiceResponder.speakGemini(text)
    }

    fun handleCommand(command: String): Boolean {
        val cmdLower = command.lowercase(Locale.getDefault())

        // 1. Lệnh nội bộ
        if (handleLocalCommand(cmdLower)) return true

        // 2. Khẩn cấp
        if (cmdLower.contains("khẩn cấp") ||
            cmdLower.contains("cứu tôi") ||
            cmdLower.contains("cầu cứu") ||
            cmdLower.contains("help")
        ) {
            try {
                com.example.smartglass.EmergencyCall.EmergencyManager(context)
                    .triggerEmergency()
                speak("Đang kích hoạt khẩn cấp.")
            } catch (e: Exception) {
                speak("Không thể kích hoạt khẩn cấp.")
            }
            return true
        }

        // 3. Điều hướng (Navigation)
        val triggers = listOf("đi đến", "đi tới", "chỉ đường đến", "tìm đường đến", "tới", "đến")
        for (trigger in triggers) {
            if (cmdLower.contains(trigger)) {
                val destination = cmdLower.substringAfter(trigger).trim()
                if (destination.isNotBlank()) {
                    speakNav("Đã nhận lệnh. Đang dẫn đường đến $destination.")
                    navigationCallback.startNavigationTo(destination)
                } else {
                    speak("Bạn muốn đi đến đâu?")
                }
                return true
            }
        }

        // 4. Ngày / giờ
        val realtimeKeywords = listOf("mấy giờ", "giờ hiện tại", "hôm nay", "ngày mấy")
        if (realtimeKeywords.any { cmdLower.contains(it) }) {
            val text = if (cmdLower.contains("giờ"))
                getRealTimeDateTime()
            else
                getRealTimeDate()
            speak("Hôm nay là $text")
            geminiChat.handledByRealtime = true
            return true
        }

        // 5. Thời tiết
        if (cmdLower.contains("thời tiết")) {
            fetchWeatherAtCurrentLocation()
            return true
        }

        // 6. Tin tức
        if (cmdLower.contains("tin tức") || cmdLower.contains("news")) {
            fetchNewsAtCurrentLocation()
            return true
        }

        // 7. Fallback → Gemini
        Log.e("VCP", "🎤 User command=$command")

        geminiChat.analyzeIntent(command) { actions, responseText ->
            Log.e("VCP", "🤖 Gemini callback")
            Log.e("VCP", "🤖 responseText=$responseText")
            Log.e("VCP", "🤖 actions=$actions")
            if (!responseText.isNullOrEmpty()) {
                Log.e("VCP", "🔥 Call speakGemini")
                speakGemini(responseText)
            }

            actions?.forEach { action ->
                val intent = action["intent"]?.lowercase() ?: ""
                val target = action["target"]?.lowercase() ?: ""
                val value = action["value"]?.lowercase() ?: ""

                scope.launch {
                    delay(120)
                    interpretIntentSilently(intent, target, value)
                }
            }
        }

        return false
    }

    // =========================
    // LOCAL COMMANDS
    // =========================
    private fun handleLocalCommand(command: String): Boolean {
        var handled = false

        // UI
        if (command.contains("cài đặt")) {
            bottomNav.selectedItemId = R.id.setting
            speak("Đang mở cài đặt.")
            handled = true
        }

        if (command.contains("trang chủ")) {
            bottomNav.selectedItemId = R.id.home
            speak("Đang về trang chủ.")
            handled = true
        }

        // Connection
        if (command.contains("ngắt kết nối") || command.contains("hủy kết nối")) {
            if (isConnected) {
                onDisconnect {
                    isConnected = !it
                    speak(if (it) "Đã hủy kết nối." else "Không thể hủy kết nối.")
                }
            }
            handled = true
        }

        if (command.contains("kết nối")) {
            if (!isConnected) {
                onConnect {
                    isConnected = it
                    speak(if (it) "Đã kết nối thành công." else "Kết nối thất bại.")
                }
            } else {
                speak("Thiết bị đã được kết nối.")
            }
            handled = true
        }

        // Volume / Speed / Screen
        if (command.contains("âm lượng")) {
            adjustVolume(command)
            handled = true
        }

        if (command.contains("tốc độ")) {
            adjustSpeed(command)
            handled = true
        }

        if (command.contains("màn hình")) {
            adjustScreen(command)
            handled = true
        }

        // Location
        if (command.contains("vị trí") || command.contains("ở đâu")) {
            handleUserAskLocation()
            handled = true
        }

        return handled
    }

    // =========================
    // INTENT FROM GEMINI
    // =========================
    private suspend fun interpretIntentSilently(intent: String, target: String, value: String) {
        withContext(Dispatchers.Main) {
            when (intent) {
                "navigate" -> {
                    if (target.contains("cài đặt")) bottomNav.selectedItemId = R.id.setting
                    if (target.contains("trang chủ")) bottomNav.selectedItemId = R.id.home
                }

                "adjust" -> {
                    when {
                        target.contains("âm lượng") -> adjustVolume(value)
                        target.contains("tốc độ") -> adjustSpeed(value)
                        target.contains("màn hình") -> adjustScreen(value)
                        target.contains("vị trí") -> handleUserAskLocation()
                    }
                }

                "connect" -> onConnect {
                    isConnected = it
                    speak(if (it) "Đã kết nối thành công." else "Kết nối thất bại.")
                }

                "disconnect" -> onDisconnect {
                    isConnected = !it
                    speak(if (it) "Đã hủy kết nối." else "Không thể hủy kết nối.")
                }
            }
        }
    }

    // =========================
    // LOCATION
    // =========================
    @SuppressLint("MissingPermission")
    private fun handleUserAskLocation() {
        if (!hasLocationPermission()) {
            speak("Ứng dụng chưa được cấp quyền vị trí.")
            return
        }

        LocationHelper(context).getCurrentLocation { loc ->
            if (loc == null) {
                speak("Không xác định được vị trí.")
                return@getCurrentLocation
            }

            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = try {
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            } catch (e: Exception) {
                null
            }

            handleAddressResult(addresses)
        }
    }

    private fun handleAddressResult(addressList: List<Address>?) {
        if (!addressList.isNullOrEmpty()) {
            speak("Bạn đang ở ${addressList[0].getAddressLine(0)}")
        } else {
            speak("Không xác định được địa chỉ.")
        }
    }

    // =========================
    // WEATHER
    // =========================
    @SuppressLint("MissingPermission")
    private fun fetchWeatherAtCurrentLocation() {
        if (!hasLocationPermission()) {
            speak("Ứng dụng chưa được cấp quyền vị trí.")
            return
        }

        LocationHelper(context).getCurrentLocation { loc ->
            if (loc == null) {
                speak("Không xác định được vị trí.")
                return@getCurrentLocation
            }

            val url =
                "https://api.openweathermap.org/data/2.5/weather?lat=${loc.latitude}&lon=${loc.longitude}&units=metric&lang=vi&appid=YOUR_API_KEY"

            client.newCall(Request.Builder().url(url).build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        speak("Không lấy được thời tiết.")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val json = JsonParser.parseString(response.body!!.string()).asJsonObject
                            val temp = json["main"].asJsonObject["temp"].asDouble
                            val desc =
                                json["weather"].asJsonArray[0].asJsonObject["description"].asString
                            speak("Thời tiết hiện tại: $desc, $temp độ C")
                        } catch (e: Exception) {
                            speak("Không thể đọc dữ liệu thời tiết.")
                        }
                    }
                })
        }
    }

    // =========================
    // NEWS
    // =========================
    @SuppressLint("MissingPermission")
    private fun fetchNewsAtCurrentLocation() {
        speak("Chức năng tin tức đang được phát triển.")
    }

    // =========================
    // ADJUST
    // =========================
    private fun adjustVolume(value: String) {
        val current = settings.getVolume()
        val newVol = when {
            value.contains("tăng") -> (current + 10).coerceAtMost(100)
            value.contains("giảm") -> (current - 10).coerceAtLeast(0)
            value.matches(Regex("\\d+")) -> value.toInt().coerceIn(0, 100)
            else -> current
        }
        settings.setVolume(newVol)
        speak("Âm lượng $newVol phần trăm")
    }

    private fun adjustSpeed(value: String) {
        val speeds = listOf("very_slow", "slow", "normal", "fast", "very_fast")
        val idx = speeds.indexOf(settings.getSpeed()).coerceAtLeast(0)

        val newIdx = when {
            value.contains("tăng") -> (idx + 1).coerceAtMost(4)
            value.contains("giảm") -> (idx - 1).coerceAtLeast(0)
            value.contains("rất chậm") -> 0
            value.contains("chậm") -> 1
            value.contains("bình thường") -> 2
            value.contains("nhanh") -> 3
            value.contains("rất nhanh") -> 4
            else -> idx
        }

        settings.setSpeed(speeds[newIdx])
        speak("Tốc độ ${speedText(speeds[newIdx])}")
    }

    private fun adjustScreen(value: String) {
        when {
            value.contains("bật") || value.contains("sáng") -> {
                settings.setKeepScreenOn(true)
                speak("Màn hình sẽ luôn sáng.")
            }

            value.contains("tắt") || value.contains("khóa") -> {
                settings.setKeepScreenOn(false)
                speak("Màn hình có thể tự tắt.")
            }
        }
    }

    private fun speedText(speed: String) = when (speed) {
        "very_slow" -> "rất chậm"
        "slow" -> "chậm"
        "normal" -> "bình thường"
        "fast" -> "nhanh"
        "very_fast" -> "rất nhanh"
        else -> "bình thường"
    }

    // =========================
    // TIME
    // =========================
    private fun getRealTimeDate(): String {
        val format = java.text.SimpleDateFormat("dd 'tháng' MM 'năm' yyyy", Locale("vi"))
        return format.format(Date())
    }

    private fun getRealTimeDateTime(): String {
        val format = java.text.SimpleDateFormat("dd 'tháng' MM 'năm' yyyy, HH:mm", Locale("vi"))
        return format.format(Date())
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
