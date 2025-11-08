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
    fun handleCommand(command: String): Boolean {
        val cmdLower = command.lowercase()

        // 1️⃣ Kiểm tra xem có phải lệnh nội bộ không
        val handledLocally = handleLocalCommand(cmdLower)
        if (handledLocally) {
            Log.d("VoiceCommandProcessor", " Lệnh '$cmdLower' đã được xử lý nội bộ, bỏ qua Gemini.")
            return true
        }

        // 2️⃣ Nếu chưa xử lý, gửi cho Gemini phân tích
        scope.launch {
            withContext(Dispatchers.IO) {
                geminiChat.analyzeIntent(command) { actions, responseText ->
                    val toSpeak = mutableListOf<String>()

                    // Nếu Gemini có phản hồi text
                    if (!responseText.isNullOrEmpty()) toSpeak.add(responseText)

                    // Nếu Gemini trả action, thực thi theo
                    actions?.forEach { action ->
                        val intent = action["intent"]?.lowercase() ?: ""
                        val target = action["target"]?.lowercase() ?: ""
                        val value = action["value"]?.lowercase() ?: ""

                        scope.launch {
                            delay(100)
                            interpretIntentSilently(intent, target, value)
                        }
                    }

                    // Nói phản hồi của Gemini nếu có
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
            var handled = false

            // --- 1 Điều hướng ---
            if (cmd.contains("cài đặt")) {
                bottomNav.selectedItemId = R.id.setting
                voiceResponder("Đang chuyển đến cài đặt.")
                handled = true
            }
            if (cmd.contains("trang chủ")) {
                bottomNav.selectedItemId = R.id.home
                voiceResponder("Đang chuyển đến trang chủ.")
                handled = true
            }

            // --- 2 Kết nối ---
            if (cmd.contains("hủy kết nối") || cmd.contains("ngắt kết nối")) {
                if (isConnected) {
                    voiceResponder("Đang hủy kết nối thiết bị...")
                    onDisconnect { success ->
                        isConnected = !success
                        voiceResponder(if (success) "Đã hủy kết nối." else "Không thể hủy kết nối.")
                    }
                } else voiceResponder("Thiết bị chưa được kết nối.")
                handled = true
            }

            if (cmd.contains("kết nối")) {
                if (!isConnected) {
                    voiceResponder("Đang kết nối thiết bị...")
                    onConnect { success ->
                        isConnected = success
                        voiceResponder(if (success) "Đã kết nối thành công." else "Kết nối thất bại.")
                    }
                } else voiceResponder("Thiết bị đã được kết nối.")
                handled = true
            }

            // --- 3 Âm lượng ---
            if (cmd.contains("âm lượng")) {
                when {
                    cmd.contains("tăng") -> adjustVolume("tăng")
                    cmd.contains("giảm") -> adjustVolume("giảm")
                    else -> {
                        extractNumber(cmd)?.let {
                            settings.setVolume(it.coerceIn(0, 100))
                            voiceResponder("Đã đặt âm lượng ${it.coerceIn(0, 100)}%.")
                        } ?: voiceResponder("Không nghe rõ mức âm lượng.")
                    }
                }
                handled = true
            }

            // --- 4 Tốc độ đọc ---
            if (cmd.contains("tốc độ")) {
                when {
                    cmd.contains("tăng") -> adjustSpeed("tăng")
                    cmd.contains("giảm") -> adjustSpeed("giảm")
                    else -> {
                        parseSpeed(cmd)?.let {
                            settings.setSpeed(it)
                            voiceResponder("Đã đặt tốc độ đọc ${speedText(it)}.")
                        } ?: voiceResponder("Không rõ tốc độ bạn muốn đặt.")
                    }
                }
                handled = true
            }

            // --- 5️⃣ Màn hình ---
            if (cmd.contains("bật màn hình") || cmd.contains("sáng màn hình") || cmd.contains("màn hình luôn sáng")) {
                settings.setKeepScreenOn(true)
                voiceResponder("Màn hình sẽ luôn sáng.")
                handled = true
            }
            if (cmd.contains("tắt màn hình") || cmd.contains("khóa màn hình")) {
                settings.setKeepScreenOn(false)
                voiceResponder("Thiết bị có thể tự khóa màn hình.")
                handled = true
            }

            // --- 6️⃣ Vị trí ---
            if (cmd.contains("vị trí") || cmd.contains("ở đâu")) {
                handleUserAskLocation()
                handled = true
            }

            return handled
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
