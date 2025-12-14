package com.example.smartglass.TTSandSTT
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.example.smartglass.DetectResponse.GeminiChat
import com.example.smartglass.Navigation.NavigationCallback
import com.example.smartglass.Navigation.NavigationListener
import com.example.smartglass.SettingAction.SettingsManager
import com.example.smartglass.gps.LocationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import okhttp3.*
import com.google.gson.JsonParser
import java.io.IOException
import java.util.*

class VoiceCommandProcessor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val bottomNav: BottomNavigationView,
    private val onConnect: (callback: (Boolean) -> Unit) -> Unit,
    private val onDisconnect: (callback: (Boolean) -> Unit) -> Unit,
    private val voiceResponder: (String) -> Unit,
    private val voiceResponderOnDone: (String, () -> Unit) -> Unit,
    private val geminiChat: GeminiChat,
    private val NavigationCallback: NavigationCallback
) {

    private val settings = SettingsManager.getInstance(activity.applicationContext)
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private val client = OkHttpClient()

    init {
        // Lắng nghe các flow setting
        scope.launch { settings.volumeFlow.collect {} }
        scope.launch { settings.speedFlow.collect {} }
        scope.launch { settings.keepScreenOnFlow.collect {} }
    }

    fun handleCommand(command: String): Boolean {
        val cmdLower = command.lowercase()

        // Kiểm tra lệnh nội bộ
        val handledLocally = handleLocalCommand(cmdLower)
        if (handledLocally) return true
        // Kích hoạt khẩn cấp
        if (cmdLower.contains("khẩn cấp") ||
            cmdLower.contains("cứu") ||
            cmdLower.contains("cầu cứu") ||
            cmdLower.contains("cứu tôi") ||
            cmdLower.contains("help")) {

            voiceResponder("Đang kích hoạt khẩn cấp")

            // Gọi EmergencyManager
            try {
                val emergencyManager = com.example.smartglass.EmergencyCall.EmergencyManager(context)
                emergencyManager.triggerEmergency()
            } catch (e: Exception) {
                e.printStackTrace()
                voiceResponder("Không thể kích hoạt khẩn cấp.")
            }

            return true
        }
        //Xử lí đầu vào nếu nhận được các từ khóa chỉ đường
        val navigateTriggerWords = listOf("đi đến", "đi tới", "chỉ đường đến", "tìm đường đến", "tới", "đến")
        for (trigger in navigateTriggerWords) {
            if (cmdLower.contains(trigger)) {
                // Trích xuất điểm đến từ câu lệnh
                val destination = cmdLower.substringAfter(trigger).trim()

                if (destination.isNotBlank()) {
                    val responseText = "Đã nhận lệnh: Đi đến $destination. Đang bắt đầu tìm đường."

                    // Hành động chuyển hướng được đặt làm callback onDone
                    voiceResponderOnDone(responseText) {
                        // Hành động này chỉ được gọi khi TTS đã phát xong responseText
                        NavigationCallback.startNavigationTo(destination)
                    }
                } else {
                    voiceResponder("Xin lỗi, bạn muốn đi đến đâu?")
                }
                return true
            }
        }

        // Kiểm tra realtime ngày/giờ
        val realtimeKeywords = listOf("hôm nay", "ngày mấy", "ngày hôm nay", "mấy giờ", "thời gian", "giờ hiện tại")
        if (realtimeKeywords.any { cmdLower.contains(it) }) {
            val responseText = if (cmdLower.contains("giờ") || cmdLower.contains("thời gian")) getRealTimeDateTime() else getRealTimeDate()
            voiceResponder("Hôm nay là $responseText")
            geminiChat.handledByRealtime = true
            return true
        }

        // Kiểm tra thời tiết/tin tức
        if (cmdLower.contains("thời tiết")) {
            fetchWeatherAtCurrentLocation()
            return true
        }

        if (cmdLower.contains("tin tức") || cmdLower.contains("news")) {
            fetchNewsAtCurrentLocation()
            return true
        }

        // 4. Nếu không, gửi cho Gemini phân tích intent
        geminiChat.analyzeIntent(command) { actions, responseText ->
            if (!responseText.isNullOrEmpty()) voiceResponder(responseText)
            actions?.forEach { action ->
                val intent = action["intent"]?.lowercase() ?: ""
                val target = action["target"]?.lowercase() ?: ""
                val value = action["value"]?.lowercase() ?: ""

                scope.launch {
                    delay(100)
                    interpretIntentSilently(intent, target, value)
                }
            }
        }

        return false
    }

    // XỬ LÝ LỆNH NỘI BỘ
    private fun handleLocalCommand(command: String): Boolean {
        var handled = false

        // Điều hướng
        if (command.contains("cài đặt")) { bottomNav.selectedItemId = R.id.setting; voiceResponder("Đang chuyển đến cài đặt."); handled = true }
        if (command.contains("trang chủ")) { bottomNav.selectedItemId = R.id.home; voiceResponder("Đang chuyển đến trang chủ."); handled = true }

        // Kết nối
        if (command.contains("hủy kết nối") || command.contains("ngắt kết nối")) {
            if (isConnected) { onDisconnect { success -> isConnected = !success; voiceResponder(if (success) "Đã hủy kết nối." else "Không thể hủy kết nối.") }; handled = true }
            else { voiceResponder("Thiết bị chưa được kết nối."); handled = true }
        }
        if (command.contains("kết nối")) {
            if (!isConnected) { onConnect { success -> isConnected = success; voiceResponder(if (success) "Đã kết nối thành công." else "Kết nối thất bại.") }; handled = true }
            else { voiceResponder("Thiết bị đã được kết nối."); handled = true }
        }

        // Âm lượng
        if (command.contains("âm lượng")) { adjustVolume(command); handled = true }

        // Tốc độ
        if (command.contains("tốc độ")) { adjustSpeed(command); handled = true }

        // Màn hình
        if (command.contains("bật màn hình") || command.contains("sáng màn hình") || command.contains("màn hình luôn sáng")) { settings.setKeepScreenOn(true); voiceResponder("Thiết bị sẽ luôn sáng màn hình."); handled = true }
        if (command.contains("tắt màn hình") || command.contains("không sáng màn hình") || command.contains("dừng sáng màn hình")) { settings.setKeepScreenOn(false); voiceResponder("Thiết bị sẽ tắt màn hình."); handled = true }

        // Vị trí
        if (command.contains("vị trí") || command.contains("ở đâu")) { handleUserAskLocation(); handled = true }

        return handled
    }
    // Hàm Helper mới để trích xuất điểm đến
    /*
    private fun extractDestination(target: String, value: String): String? {
        // 1. Kết hợp target và value thành một chuỗi duy nhất,
        //    vì đôi khi target chứa từ khóa (đi đến) và value chứa địa điểm (Hà Nội)
        val combinedText = "$target $value".trim().toLowerCase(Locale("vi", "VN"))

        // 2. Định nghĩa các cụm từ khóa dẫn đầu (trigger phrases)
        val triggerPhrases = listOf(
            "đi đến",
            "chỉ đường tới",
            "tìm đường đến",
            "tới",
            "đến"
        )

        // 3. Lặp qua các cụm từ khóa để tìm điểm bắt đầu của điểm đến thực sự
        for (phrase in triggerPhrases) {
            val index = combinedText.indexOf(phrase)
            if (index != -1) {
                // Lấy phần còn lại của chuỗi sau cụm từ khóa + khoảng trắng
                val destination = combinedText.substring(index + phrase.length).trim()
                if (destination.isNotBlank()) {
                    return destination
                }
            }
        }

        // 4. Nếu không tìm thấy cụm từ khóa, chỉ lấy target/value đầu tiên
        val fallback = if (target.isNotBlank()) target else if (value.isNotBlank()) value else null
        return fallback?.trim()
    }

     */

    private suspend fun interpretIntentSilently(intent: String, target: String, value: String) {
        withContext(Dispatchers.Main) {
            when (intent) {
                "navigate" -> {
                    if (target.contains("cài đặt")) {
                        bottomNav.selectedItemId = R.id.setting
                    } else if (target.contains("trang chủ")) {
                        bottomNav.selectedItemId = R.id.home
                    } else {
                        // 2. Kích hoạt Điều hướng GPS/Mapbox
                        /*
                        val destination = extractDestination(target, value)

                        if (destination != null && destination.isNotBlank()) {
                            // 2. Kích hoạt Điều hướng GPS/Mapbox
                            voiceResponder("Đã nhận lệnh: Đi đến $destination. Đang bắt đầu tìm đường.")
                            // Gọi hàm startNavigationTo trong MainActivity thông qua callback
                            NavigationCallback.startNavigationTo(destination)
                        } else {
                            voiceResponder("Xin lỗi, tôi không hiểu rõ điểm đến bạn muốn là gì.")
                        }

                         */

                    }
                }
                "adjust" -> {
                    when {
                        target.contains("âm lượng") -> adjustVolume(value)
                        target.contains("tốc độ") -> adjustSpeed(value)
                        target.contains("màn hình") -> adjustScreen(value)
                        target.contains("vị trí") -> handleUserAskLocation()
                    }
                }
                "connect" -> onConnect { success -> isConnected = success; voiceResponder(if (success) "Đã kết nối thành công." else "Kết nối thất bại.") }
                "disconnect" -> onDisconnect { success -> isConnected = !success; voiceResponder(if (success) "Đã hủy kết nối." else "Không thể hủy kết nối.") }
                "check_location", "getinfo" -> if (target.contains("vị trí")) handleUserAskLocation()
            }
        }
    }

    // VỊ TRÍ
    @SuppressLint("MissingPermission")
    private fun handleUserAskLocation() {
        val locationHelper = LocationHelper(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            voiceResponder("Ứng dụng chưa được cấp quyền truy cập vị trí.")
            return
        }
        locationHelper.getCurrentLocation { loc ->
            if (loc != null) {
                val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
                val addressList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1) { handleAddressResult(it) }
                    null
                } else {
                    try { geocoder.getFromLocation(loc.latitude, loc.longitude, 1) } catch (e: Exception) { e.printStackTrace(); null }
                }
                handleAddressResult(addressList)
            } else voiceResponder("Không thể xác định vị trí hiện tại.")
        }
    }

    private fun handleAddressResult(addressList: List<Address>?) {
        if (!addressList.isNullOrEmpty()) {
            val fullAddress = addressList[0].getAddressLine(0)
            voiceResponder("Bạn đang ở tại $fullAddress")
        } else voiceResponder("Không thể xác định địa chỉ cụ thể.")
    }

    // WEATHER
    @SuppressLint("MissingPermission")
    private fun fetchWeatherAtCurrentLocation() {
        val locationHelper = LocationHelper(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            voiceResponder("Ứng dụng chưa được cấp quyền truy cập vị trí.")
            return
        }
        locationHelper.getCurrentLocation { loc ->
            if (loc != null) {
                val url = "https://api.openweathermap.org/data/2.5/weather?lat=${loc.latitude}&lon=${loc.longitude}&appid=772d61d67d139b5973e3c923a171d1bc&units=metric&lang=vi"
                val request = Request.Builder().url(url).build()
                client.newCall(request).enqueue(object: Callback {
                    override fun onFailure(call: Call, e: IOException) { voiceResponder("Không lấy được thời tiết.") }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val body = response.body?.string()
                            val json = JsonParser.parseString(body).asJsonObject
                            val temp = json["main"].asJsonObject["temp"].asDouble
                            val desc = json["weather"].asJsonArray[0].asJsonObject["description"].asString
                            voiceResponder("Thời tiết hiện tại: $desc, $temp°C")
                        } catch (e: Exception) { voiceResponder("Không thể lấy dữ liệu thời tiết.") }
                    }
                })
            } else voiceResponder("Không xác định được vị trí hiện tại.")
        }
    }

    // NEWS
    @SuppressLint("MissingPermission")
    private fun fetchNewsAtCurrentLocation() {
        val locationHelper = LocationHelper(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            voiceResponder("Ứng dụng chưa được cấp quyền truy cập vị trí.")
            return
        }

        locationHelper.getCurrentLocation { loc ->
            if (loc == null) {
                voiceResponder("Không xác định được vị trí hiện tại.")
                return@getCurrentLocation
            }

            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                val cityName = addresses?.firstOrNull()?.locality
                    ?: addresses?.firstOrNull()?.subAdminArea
                    ?: addresses?.firstOrNull()?.adminArea
                    ?: "Việt Nam" // fallback nếu không lấy được city

                val url = "https://newsapi.org/v2/top-headlines?q=$cityName&apiKey=38f0dc0cc99c9fcdc7d887c8627cc6f0"

                val request = Request.Builder().url(url).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        voiceResponder("Không lấy được tin tức.")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val body = response.body?.string()
                            if (body.isNullOrEmpty()) {
                                voiceResponder("Không lấy được tin tức.")
                                return
                            }

                            val json = JsonParser.parseString(body).asJsonObject
                            val articles = json["articles"]?.asJsonArray
                            if (articles == null || articles.size() == 0) {
                                voiceResponder("Không có tin tức mới tại $cityName.")
                                return
                            }

                            val first = articles[0].asJsonObject
                            val title = first["title"]?.asString ?: "Không có tiêu đề"
                            voiceResponder("Tin nổi bật tại $cityName: $title")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            voiceResponder("Không lấy được tin tức.")
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
                voiceResponder("Không thể xác định thành phố từ vị trí hiện tại.")
            }
        }
    }

    // ÂM LƯỢNG / TỐC ĐỘ / MÀN HÌNH
    private fun adjustVolume(value: String) {
        val current = settings.getVolume()
        val newVol = when {
            value.contains("tăng") -> (current + 10).coerceAtMost(100)
            value.contains("giảm") -> (current - 10).coerceAtLeast(0)
            value.matches(Regex("\\d+")) -> value.toInt().coerceIn(0, 100)
            else -> current
        }
        settings.setVolume(newVol)
        voiceResponder("Âm lượng hiện tại: $newVol%")
    }

    private fun adjustSpeed(value: String) {
        val speeds = listOf("very_slow", "slow", "normal", "fast", "very_fast")
        val idx = speeds.indexOf(settings.getSpeed())
        val newIdx = when {
            value.contains("tăng") && idx < speeds.size - 1 -> idx + 1
            value.contains("giảm") && idx > 0 -> idx - 1
            value.contains("rất chậm") -> 0
            value.contains("chậm") -> 1
            value.contains("bình thường") -> 2
            value.contains("nhanh") -> 3
            value.contains("rất nhanh") -> 4
            else -> idx
        }
        settings.setSpeed(speeds[newIdx])
        voiceResponder("Tốc độ đọc: ${speedText(speeds[newIdx])}")
    }

    private fun adjustScreen(value: String) {
        when {
            value.contains("bật") || value.contains("sáng") -> { settings.setKeepScreenOn(true); voiceResponder("Màn hình sẽ luôn sáng.") }
            value.contains("tắt") || value.contains("khóa") -> { settings.setKeepScreenOn(false); voiceResponder("Thiết bị có thể tự khóa màn hình.") }
        }
    }

    private fun speedText(speed: String) = when(speed) {
        "very_slow" -> "rất chậm"
        "slow" -> "chậm"
        "normal" -> "bình thường"
        "fast" -> "nhanh"
        "very_fast" -> "rất nhanh"
        else -> "bình thường"
    }

    // REaltime
    private fun getRealTimeDate(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val format = java.text.SimpleDateFormat("dd 'tháng' MM 'năm' yyyy", Locale("vi"))
        return format.format(calendar.time)
    }

    private fun getRealTimeDateTime(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val format = java.text.SimpleDateFormat("dd 'tháng' MM 'năm' yyyy, HH:mm", Locale("vi"))
        return format.format(calendar.time)
    }
}



