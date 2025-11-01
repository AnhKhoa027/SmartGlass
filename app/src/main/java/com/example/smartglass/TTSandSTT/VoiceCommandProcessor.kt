//package com.example.smartglass.TTSandSTT
//
//import android.content.Context
//import android.util.Log
//import androidx.fragment.app.FragmentActivity
//import com.example.smartglass.R
//import com.example.smartglass.DetectResponse.GeminiChat
//import com.example.smartglass.SettingAction.SettingsManager
//import com.google.android.material.bottomnavigation.BottomNavigationView
//import kotlinx.coroutines.*
//
//class VoiceCommandProcessor(
//    private val context: Context,
//    private val activity: FragmentActivity,
//    private val bottomNav: BottomNavigationView,
//    private val onConnect: (callback: (Boolean) -> Unit) -> Unit,
//    private val onDisconnect: (callback: (Boolean) -> Unit) -> Unit,
//    private val voiceResponder: (String) -> Unit,
//    private val geminiChat: GeminiChat
//
//) {
//    private val settings = SettingsManager.getInstance(activity.applicationContext)
//    private var isConnected = false
//    private val scope = CoroutineScope(Dispatchers.Main)
//
//    init {
//        scope.launch {
//            settings.volumeFlow.collect {}
//        }
//        scope.launch {
//            settings.speedFlow.collect {}
//        }
//        scope.launch {
//            settings.keepScreenOnFlow.collect {}
//        }
//    }
//
//
//    fun handleCommand(command: String): Boolean{
//        val handledInternally = handleLocalCommand(command)
//        if (handledInternally) return true
//
//        scope.launch(Dispatchers.IO) {
//            try {
//                val prompt = """
//                    Hãy phân tích câu nói sau: "$command".
//                    Trích ra từ khóa chính biểu thị hành động, ví dụ: "cài đặt", "trang chủ", "âm lượng", "tốc độ", "kết nối", "thông tin nhóm phát triển", ...
//                    Chỉ trả về đúng từ khóa, không thêm câu chữ khác.
//                """.trimIndent()
//
//                geminiChat.sendMessageAsync(prompt) { response ->
//                    if (response != null) {
//                        Log.d("VoiceCommandProcessor", "Gemini hiểu là: $response")
//                        scope.launch { executeGeminiAction(response.lowercase(), command) }
//                    } else {
//                        scope.launch { voiceResponder("Xin lỗi, tôi chưa hiểu rõ lệnh của bạn.") }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("VoiceCommandProcessor", "Gemini xử lý lỗi: ${e.message}")
//                scope.launch { voiceResponder("Đã xảy ra lỗi khi xử lý lệnh.") }
//            }
//        }
//        return false
//    }
//
//    private fun executeGeminiAction(keyword: String, original: String) {
//        when {
//            keyword.contains("cài đặt") -> {
//                bottomNav.selectedItemId = R.id.setting
//                voiceResponder("Đang chuyển đến cài đặt.")
//            }
//            keyword.contains("trang chủ") -> {
//                bottomNav.selectedItemId = R.id.home
//                voiceResponder("Đang chuyển đến trang chủ.")
//            }
////            keyword.contains("thông báo") -> {
////                bottomNav.selectedItemId = R.id.notification
////                voiceResponder("Đang mở phần thông báo.")
////            }
////            keyword.contains("giúp") || keyword.contains("trợ giúp") -> {
////                bottomNav.selectedItemId = R.id.help
////                voiceResponder("Đang mở phần trợ giúp.")
////            }
////            keyword.contains("bản đồ") -> {
////                bottomNav.selectedItemId = R.id.map
////                voiceResponder("Đang mở bản đồ.")
////            }
//            keyword.contains("kết nối") -> handleLocalCommand("kết nối")
//            keyword.contains("hủy kết nối") || keyword.contains("ngắt") -> handleLocalCommand("hủy kết nối")
//            keyword.contains("âm lượng") || keyword.contains("tốc độ") || keyword.contains("màn hình") ->
//                handleLocalCommand(original)
//            else -> voiceResponder("Tôi chưa được huấn luyện cho lệnh '$keyword' này.")
//        }
//    }
//
//    private fun handleLocalCommand(command: String): Boolean {
//        val cmd = command.lowercase()
//
//        return when {
//            cmd.contains("hủy kết nối") || cmd.contains("ngắt kết nối") -> {
//                if (isConnected) {
//                    voiceResponder("Đang hủy kết nối thiết bị...")
//                    onDisconnect { success ->
//                        isConnected =  !success
//                        voiceResponder(if (success) "Đã hủy kết nối." else "Không thể hủy kết nối.")
//                    }
//                } else voiceResponder("Thiết bị chưa được kết nối.")
//                true
//            }
//
//            cmd.contains("kết nối") -> {
//                if (!isConnected) {
//                    voiceResponder("Đang kết nối thiết bị...")
//                    onConnect { success ->
//                        isConnected = success
//                        voiceResponder(if (success) "Đã kết nối thành công." else "Kết nối thất bại.")
//                    }
//                } else voiceResponder("Thiết bị đã được kết nối.")
//                true
//            }
//
//            cmd.contains("âm lượng") -> { adjustVolume(true); true }
//            cmd.contains("âm lượng") -> { adjustVolume(false); true }
//            cmd.contains("đặt âm lượng") -> {
//                extractNumber(cmd)?.let {
//                    settings.setVolume(it.coerceIn(0, 100))
//                    voiceResponder("Đã đặt âm lượng ${it.coerceIn(0, 100)}%.")
//                } ?: voiceResponder("Không nghe rõ mức âm lượng.")
//                true
//            }
//
//            cmd.contains("tốc độ đọc") -> { adjustSpeed(true); true }
//            cmd.contains("tốc độ đọc") -> { adjustSpeed(false); true }
//
//            cmd.contains("đặt tốc độ đọc") -> {
//                parseSpeed(cmd)?.let {
//                    settings.setSpeed(it)
//                    voiceResponder("Đã đặt tốc độ đọc ${speedText(it)}.")
//                } ?: voiceResponder("Không rõ tốc độ bạn muốn đặt.")
//                true
//            }
//
//            cmd.contains("bật màn hình") || cmd.contains("sáng màn hình") -> {
//                settings.setKeepScreenOn(true)
//                voiceResponder("Màn hình sẽ luôn sáng.")
//                true
//            }
//
//            cmd.contains("tắt màn hình") || cmd.contains("khóa màn hình") -> {
//                settings.setKeepScreenOn(false)
//                voiceResponder("Thiết bị có thể tự khóa màn hình.")
//                true
//            }
//
//            cmd.contains("cài đặt") -> {
//                bottomNav.selectedItemId = R.id.setting
//                voiceResponder("Đang chuyển đến cài đặt.")
//                true
//            }
//
//            cmd.contains("trang chủ") -> {
//                bottomNav.selectedItemId = R.id.home
//                voiceResponder("Đang chuyển đến trang chủ.")
//                true
//            }
//            else -> false
//        }
//    }
//
//    // ------------------ HÀM PHỤ ------------------
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
//
//    private fun speedText(speed: String) = when (speed) {
//        "very_slow" -> "rất chậm"
//        "slow" -> "chậm"
//        "normal" -> "bình thường"
//        "fast" -> "nhanh"
//        "very_fast" -> "rất nhanh"
//        else -> "bình thường"
//    }
//
//    private fun parseSpeed(cmd: String) = when {
//        cmd.contains("rất chậm") -> "very_slow"
//        cmd.contains("chậm") -> "slow"
//        cmd.contains("bình thường") -> "normal"
//        cmd.contains("nhanh") -> "fast"
//        cmd.contains("rất nhanh") -> "very_fast"
//        else -> null
//    }
//
//    private fun extractNumber(text: String): Int? =
//        """\d+""".toRegex().find(text)?.value?.toIntOrNull()
//}
package com.example.smartglass.TTSandSTT

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.example.smartglass.DetectResponse.GeminiChat
import com.example.smartglass.SettingAction.SettingsManager
import com.example.smartglass.gps.LocationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.app.ActivityCompat
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

    init {
        scope.launch { settings.volumeFlow.collect {} }
        scope.launch { settings.speedFlow.collect {} }
        scope.launch { settings.keepScreenOnFlow.collect {} }
    }

    fun handleCommand(command: String): Boolean {
        val handledInternally = handleLocalCommand(command)
        if (handledInternally) return true

        scope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    Hãy phân tích câu nói sau: "$command".
                    Trích ra từ khóa chính biểu thị hành động, ví dụ: "cài đặt", "trang chủ", "âm lượng", "tốc độ", "kết nối", "vị trí", ...
                    Chỉ trả về đúng từ khóa, không thêm câu chữ khác.
                """.trimIndent()

                geminiChat.sendMessageAsync(prompt) { response ->
                    if (response != null) {
                        Log.d("VoiceCommandProcessor", "Gemini hiểu là: $response")
                        scope.launch { executeGeminiAction(response.lowercase(), command) }
                    } else {
                        scope.launch { voiceResponder("Xin lỗi, tôi chưa hiểu rõ lệnh của bạn.") }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceCommandProcessor", "Gemini xử lý lỗi: ${e.message}")
                scope.launch { voiceResponder("Đã xảy ra lỗi khi xử lý lệnh.") }
            }
        }
        return false
    }

    // ---------------------------------------------
    // 🔹 XỬ LÝ CÁC LỆNH TỪ GEMINI
    // ---------------------------------------------
    private fun executeGeminiAction(keyword: String, original: String) {
        when {
            keyword.contains("cài đặt") -> {
                bottomNav.selectedItemId = R.id.setting
                voiceResponder("Đang chuyển đến cài đặt.")
            }
            keyword.contains("trang chủ") -> {
                bottomNav.selectedItemId = R.id.home
                voiceResponder("Đang chuyển đến trang chủ.")
            }
            keyword.contains("kết nối") -> handleLocalCommand("kết nối")
            keyword.contains("hủy kết nối") || keyword.contains("ngắt") -> handleLocalCommand("hủy kết nối")

            // Khi người dùng hỏi về vị trí
            keyword.contains("vị trí") || keyword.contains("ở đâu") || keyword.contains("bản đồ") -> {
                handleUserAskLocation()
            }

            keyword.contains("âm lượng") || keyword.contains("tốc độ") || keyword.contains("màn hình") ->
                handleLocalCommand(original)

            else -> voiceResponder("Tôi chưa được huấn luyện cho lệnh '$keyword' này.")
        }
    }

    // 🔹 XỬ LÝ CÁC LỆNH NỘI BỘ
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

            cmd.contains("giảm âm lượng") -> { adjustVolume(false); true }
            cmd.contains("tăng âm lượng") -> { adjustVolume(true); true }
            cmd.contains("đặt âm lượng") -> {
                extractNumber(cmd)?.let {
                    settings.setVolume(it.coerceIn(0, 100))
                    voiceResponder("Đã đặt âm lượng ${it.coerceIn(0, 100)}%.")
                } ?: voiceResponder("Không nghe rõ mức âm lượng.")
                true
            }

            cmd.contains("tốc độ đọc") -> { adjustSpeed(true); true }

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

            // ✅ Người dùng hỏi vị trí mà Gemini chưa phân tích kịp
            cmd.contains("vị trí") || cmd.contains("ở đâu") -> {
                handleUserAskLocation()
                true
            }

            else -> false
        }
    }
    // HÀM GPS: LẤY VỊ TRÍ HIỆN TẠI & GỌI GEMINI
    @SuppressLint("MissingPermission")
    private fun handleUserAskLocation() {
        val locationHelper = LocationHelper(context)

        // Kiểm tra quyền trước khi truy cập vị trí
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            scope.launch { voiceResponder("Ứng dụng chưa được cấp quyền truy cập vị trí.") }
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

            } else {
                scope.launch { voiceResponder("Không thể xác định vị trí hiện tại.") }
            }
        }
    }

    private fun handleAddressResult(addressList: List<Address>?) {
        if (!addressList.isNullOrEmpty()) {
            val address = addressList[0]
            val fullAddress = address.getAddressLine(0)
            val message = "Bạn đang ở tại $fullAddress"
            Log.d("Gemini-GPS", "Địa chỉ (Geocoder): $fullAddress")
            scope.launch { voiceResponder(message) }
        } else {
            scope.launch { voiceResponder("Không thể xác định địa chỉ cụ thể.") }
        }
    }

    // 🔹 CÁC HÀM PHỤ
    private fun adjustVolume(up: Boolean) {
        val current = settings.getVolume()
        val newVol = if (up) (current + 10).coerceAtMost(100) else (current - 10).coerceAtLeast(0)
        settings.setVolume(newVol)
        voiceResponder("Âm lượng hiện tại: $newVol%")
    }

    private fun adjustSpeed(up: Boolean) {
        val speeds = listOf("very_slow", "slow", "normal", "fast", "very_fast")
        val idx = speeds.indexOf(settings.getSpeed())
        val newIdx = when {
            up && idx < speeds.size - 1 -> idx + 1
            !up && idx > 0 -> idx - 1
            else -> idx
        }
        settings.setSpeed(speeds[newIdx])
        voiceResponder("Tốc độ đọc: ${speedText(speeds[newIdx])}")
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
