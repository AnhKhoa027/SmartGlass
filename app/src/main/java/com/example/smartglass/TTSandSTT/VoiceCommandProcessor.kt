package com.example.smartglass.TTSandSTT

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.example.smartglass.DetectResponse.GeminiChat
import com.example.smartglass.SettingAction.SettingsManager
import com.example.smartglass.gps.LocationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.util.Locale

class VoiceCommandProcessor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val bottomNav: BottomNavigationView,
    private val onConnect: (callback: (Boolean) -> Unit) -> Unit,
    private val onDisconnect: (callback: (Boolean) -> Unit) -> Unit,
    private val voiceResponder: (String) -> Unit,
    private val geminiChat: GeminiChat
) {
    private val settings = SettingsManager.getInstance(activity.applicationContext)
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var locationHandled = false

    init {
        scope.launch { settings.volumeFlow.collect {} }
        scope.launch { settings.speedFlow.collect {} }
        scope.launch { settings.keepScreenOnFlow.collect {} }
    }

//    fun handleCommand(command: String): Boolean {
//        scope.launch {
//            withContext(Dispatchers.IO) {
//                geminiChat.analyzeIntent(command) { actions, responseText ->
//                    val toSpeak = mutableListOf<String>()
//
//                    val hasGetInfoLocation = actions?.any {
//                        it["intent"]?.lowercase() == "getinfo" &&
//                                it["target"]?.lowercase()?.contains("vị trí") == true
//                    } == true
//                    if (!responseText.isNullOrEmpty() && !hasGetInfoLocation) {
//                        toSpeak.add(responseText)
//                    } else if (hasGetInfoLocation) {
//                        Log.d("VoiceCommandProcessor", "Bỏ qua responseText của Gemini vì đang lấy vị trí GPS")
//                    }
//
//                    if (actions == null || actions.isEmpty()) {
//                        val handled = handleLocalCommand(command)
//                        if (!handled && !hasGetInfoLocation) {
//                            voiceResponder("Xin lỗi, tôi chưa hiểu rõ ý bạn nói.")
//                        }
//                        toSpeak.forEach { voiceResponder(it) }
//                        return@analyzeIntent
//                    }
//                    Log.d("VoiceCommandProcessor", "Gemini hiểu: $actions")
//
//                    // Gom phản hồi tuần tự
//                    actions.forEachIndexed { _,action ->
//                        val intent = action["intent"]?.lowercase() ?: ""
//                        val target = action["target"]?.lowercase() ?: ""
//                        val value = action["value"]?.lowercase() ?: ""
//                        scope.launch {
//                            delay(100) // tránh chen giọng
//                            interpretIntentSilently(intent, target, value)
//                        }
//                    }
//                    //Nói tuần tự qua VoiceManager
//                    toSpeak.forEach { voiceResponder(it) }
//                }
//            }
//        }
//        return false
//    }

//    fun handleCommand(command: String): Boolean {
//        val cmdLower = command.lowercase()
//
//        // Kiểm tra các lệnh rõ ràng trước
//        val handledDirectly = handleLocalCommand(cmdLower)
//        if (handledDirectly) return true
//
//        // Nếu chưa xử lý, gửi cho Gemini phân tích
//        scope.launch {
//            withContext(Dispatchers.IO) {
//                geminiChat.analyzeIntent(command) { actions, responseText ->
//                    val toSpeak = mutableListOf<String>()
//
//                    if (!responseText.isNullOrEmpty()) {
//                        toSpeak.add(responseText)
//                    }
//
//                    if (actions != null) {
//                        actions.forEach { action ->
//                            val intent = action["intent"]?.lowercase() ?: ""
//                            val target = action["target"]?.lowercase() ?: ""
//                            val value = action["value"]?.lowercase() ?: ""
//                            scope.launch {
//                                delay(100)
//                                interpretIntentSilently(intent, target, value)
//                            }
//                        }
//                    }
//
//                    toSpeak.forEach { voiceResponder(it) }
//                }
//            }
//        }
//        return false
//    }
//    private suspend fun interpretIntentSilently(intent: String, target: String, value: String) {
//        withContext(Dispatchers.Main) {
//            when (intent.lowercase()) {
//                "navigate" -> {
//                    if (target.contains("cài đặt")) bottomNav.selectedItemId = R.id.setting
//                    else if (target.contains("trang chủ")) bottomNav.selectedItemId = R.id.home
//                }
//
//                "adjust" -> {
//                    when {
//                        target.contains("âm lượng") -> handleLocalCommand("$value âm lượng")
//                        target.contains("tốc độ") -> handleLocalCommand("$value tốc độ đọc")
//                        target.contains("màn hình") -> handleLocalCommand("$value màn hình")
//                        target.contains("vị trí") -> handleLocalCommand("$value vị trí")
//                    }
//                }
//
//                "connect" -> handleLocalCommand("kết nối")
//                "disconnect" -> handleLocalCommand("hủy kết nối")
//                "check_location", "getinfo" -> {
//                    if (target.contains("vị trí")) handleUserAskLocation()
//                }
//            }
//        }
//    }


    fun handleCommand(command: String): Boolean {
        // Không xử lý trực tiếp, luôn gửi cho Gemini
        scope.launch {
            withContext(Dispatchers.IO) {
                geminiChat.analyzeIntent(command) { actions, responseText ->
                    val toSpeak = mutableListOf<String>()

                    // Nếu Gemini trả responseText thì thêm vào danh sách đọc
                    if (!responseText.isNullOrEmpty()) toSpeak.add(responseText)

                    // Thực hiện actions do Gemini trả về
                    actions?.forEach { action ->
                        val intent = action["intent"]?.lowercase() ?: ""
                        val target = action["target"]?.lowercase() ?: ""
                        val value = action["value"]?.lowercase() ?: ""

                        scope.launch {
                            delay(100) // tránh chen giọng
                            interpretIntentSilently(intent, target, value)
                        }
                    }

                    // Đọc tuần tự các responseText
                    toSpeak.forEach { voiceResponder(it) }
                }
            }
        }
        return false
    }

    private suspend fun interpretIntentSilently(intent: String, target: String, value: String) {
        withContext(Dispatchers.Main) {
            when (intent) {
                "navigate" -> {
                    when {
                        target.contains("cài đặt") -> bottomNav.selectedItemId = R.id.setting
                        target.contains("trang chủ") -> bottomNav.selectedItemId = R.id.home
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

                "connect" -> onConnect { success ->
                    isConnected = success
                    voiceResponder(if (success) "Đã kết nối thành công." else "Kết nối thất bại.")
                }

                "disconnect" -> onDisconnect { success ->
                    isConnected = !success
                    voiceResponder(if (success) "Đã hủy kết nối." else "Không thể hủy kết nối.")
                }

                "check_location", "getinfo" -> if (target.contains("vị trí")) handleUserAskLocation()
            }
        }
    }

    private fun handleLocalCommand(command: String): Boolean {
        val cmd = command.lowercase()

        return when {
            cmd.contains("hủy kết nối") || cmd.contains("ngắt kết nối") -> {
                if (isConnected) {
                    voiceResponder("Đang hủy kết nối thiết bị...")
                    onDisconnect { success ->
                        isConnected = !success
                        voiceResponder(if (success) "Đã hủy kết nối." else "Không thể hủy kết nối.")
                    }
                } else voiceResponder("Thiết bị chưa được kết nối.")
                true
            }

            cmd.contains("kết nối") -> {
                if (!isConnected) {
                    voiceResponder("Đang kết nối thiết bị...")
                    onConnect { success ->
                        isConnected = success
                        voiceResponder(if (success) "Đã kết nối thành công." else "Kết nối thất bại.")
                    }
                } else voiceResponder("Thiết bị đã được kết nối.")
                true
            }

//            cmd.contains("giảm âm lượng") -> { adjustVolume(false); true }
//            cmd.contains("tăng âm lượng") -> { adjustVolume(true); true }
            cmd.contains("đặt âm lượng") -> {
                extractNumber(cmd)?.let {
                    settings.setVolume(it.coerceIn(0, 100))
                    voiceResponder("Đã đặt âm lượng ${it.coerceIn(0, 100)}%.")
                } ?: voiceResponder("Không nghe rõ mức âm lượng.")
                true
            }

//            cmd.contains("giảm tốc độ đọc") -> { adjustSpeed(false); true }
//            cmd.contains("tăng tốc độ đọc") -> { adjustSpeed(true); true }
            cmd.contains("đặt tốc độ đọc") -> {
                parseSpeed(cmd)?.let {
                    settings.setSpeed(it)
                    voiceResponder("Đã đặt tốc độ đọc ${speedText(it)}.")
                } ?: voiceResponder("Không rõ tốc độ bạn muốn đặt.")
                true
            }

            cmd.contains("bật màn hình") || cmd.contains("sáng màn hình") -> {
                settings.setKeepScreenOn(true)
                voiceResponder("Màn hình sẽ luôn sáng.")
                true
            }

            cmd.contains("tắt màn hình") || cmd.contains("khóa màn hình") -> {
                settings.setKeepScreenOn(false)
                voiceResponder("Thiết bị có thể tự khóa màn hình.")
                true
            }

            cmd.contains("cài đặt") -> {
                bottomNav.selectedItemId = R.id.setting
                voiceResponder("Đang chuyển đến cài đặt.")
                true
            }

            cmd.contains("trang chủ") -> {
                bottomNav.selectedItemId = R.id.home
                voiceResponder("Đang chuyển đến trang chủ.")
                true
            }

            cmd.contains("vị trí") || cmd.contains("ở đâu") -> {
                handleUserAskLocation()
                true
            }

            else -> false
        }
    }
    @SuppressLint("MissingPermission")
    private fun handleUserAskLocation() {
        val locationHelper = LocationHelper(context)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            voiceResponder("Ứng dụng chưa được cấp quyền truy cập vị trí.")
            return
        }

        locationHelper.getCurrentLocation { loc ->
            if (loc != null) {
                val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1) { addresses ->
                        handleAddressResult(addresses)
                    }
                } else {
                    val addressList = try {
                        geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    handleAddressResult(addressList)
                }

            } else voiceResponder("Không thể xác định vị trí hiện tại.")
        }
    }
//@SuppressLint("MissingPermission")
//private fun handleUserAskLocation() {
//
//    if (locationHandled) return  // đã xử lý, không làm lại
//    locationHandled = true
//
//    val locationHelper = LocationHelper(context)
//
//    // Kiểm tra quyền trước
//    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
//        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//        voiceResponder("Ứng dụng chưa được cấp quyền truy cập vị trí.")
//        return
//    }
//
//    voiceResponder("Được rồi, tôi đang tìm vị trí hiện tại của bạn...")
//
//    locationHelper.getCurrentLocation { loc ->
//        if (loc != null) {
//            Log.d("VoiceCommandProcessor", "Latitude: ${loc.latitude}, Longitude: ${loc.longitude}")
//
//            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
//
//            try {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                    // Android 13+ callback
//                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1) { addresses ->
//                        if (!addresses.isNullOrEmpty()) {
//                            val fullAddress = addresses[0].getAddressLine(0)
//                            Log.d("VoiceCommandProcessor", "Địa chỉ: $fullAddress")
//                            voiceResponder("Bạn đang ở tại $fullAddress")
//                        } else {
//                            voiceResponder("Không thể xác định địa chỉ cụ thể, vui lòng thử lại sau.")
//                        }
//                    }
//                } else {
//                    // Android < 13
//                    val addresses = try {
//                        geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        null
//                    }
//
//                    if (!addresses.isNullOrEmpty()) {
//                        val fullAddress = addresses[0].getAddressLine(0)
//                        Log.d("VoiceCommandProcessor", "Địa chỉ: $fullAddress")
//                        voiceResponder("Bạn đang ở tại $fullAddress")
//                    } else {
//                        voiceResponder("Không thể xác định địa chỉ cụ thể, vui lòng thử lại sau.")
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                voiceResponder("Không thể xác định địa chỉ do lỗi hệ thống.")
//            }
//
//        } else {
//            Log.d("VoiceCommandProcessor", "Không lấy được vị trí GPS")
//            voiceResponder("Không thể xác định vị trí hiện tại, vui lòng thử lại sau.")
//        }
//    }
//}


    private fun handleAddressResult(addressList: List<Address>?) {
        if (!addressList.isNullOrEmpty()) {
            val address = addressList[0]
            val fullAddress = address.getAddressLine(0)
            val message = "Bạn đang ở tại $fullAddress"
            Log.d("Gemini-GPS", "Địa chỉ: $fullAddress")
            voiceResponder(message)
        } else {
            voiceResponder("Không thể xác định địa chỉ cụ thể.")
        }
    }

    // ---------------------------------------------------------------
//    private fun adjustVolume(up: Boolean) {
//        val current = settings.getVolume()
//        val newVol = if (up) (current + 10).coerceAtMost(100) else (current - 10).coerceAtLeast(0)
//        settings.setVolume(newVol)
//        voiceResponder("Âm lượng hiện tại: $newVol%")
//    }
//
//    private fun adjustSpeed(up: Boolean) {
//        val speeds = listOf("very_slow", "slow", "normal", "fast", "very_fast")
//        val idx = speeds.indexOf(settings.getSpeed())
//        val newIdx = when {
//            up && idx < speeds.size - 1 -> idx + 1
//            !up && idx > 0 -> idx - 1
//            else -> idx
//        }
//        settings.setSpeed(speeds[newIdx])
//        voiceResponder("Tốc độ đọc: ${speedText(speeds[newIdx])}")
//    }
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

    // Hàm điều chỉnh tốc độ đọc dựa trên value
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
    // Hàm bật/tắt màn hình dựa trên value
    private fun adjustScreen(value: String) {
        when {
            value.contains("bật") || value.contains("sáng") -> {
                settings.setKeepScreenOn(true)
                voiceResponder("Màn hình sẽ luôn sáng.")
            }
            value.contains("tắt") || value.contains("khóa") -> {
                settings.setKeepScreenOn(false)
                voiceResponder("Thiết bị có thể tự khóa màn hình.")
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

    private fun parseSpeed(cmd: String) = when {
        cmd.contains("rất chậm") -> "very_slow"
        cmd.contains("chậm") -> "slow"
        cmd.contains("bình thường") -> "normal"
        cmd.contains("nhanh") -> "fast"
        cmd.contains("rất nhanh") -> "very_fast"
        else -> null
    }

    private fun extractNumber(text: String): Int? =
        """\d+""".toRegex().find(text)?.value?.toIntOrNull()
}
