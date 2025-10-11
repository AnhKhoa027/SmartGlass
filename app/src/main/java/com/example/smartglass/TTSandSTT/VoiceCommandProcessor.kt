package com.example.smartglass.TTSandSTT

import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.example.smartglass.SettingAction.SettingsManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class VoiceCommandProcessor(
    private val activity: FragmentActivity,
    private val bottomNav: BottomNavigationView,
    private val onConnect: (callback: (Boolean) -> Unit) -> Unit,
    private val onDisconnect: (callback: (Boolean) -> Unit) -> Unit,
    private val voiceResponder: (String) -> Unit
) {
    private val settings = SettingsManager.getInstance(activity.applicationContext)
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        // Observe settings changes to optionally auto-announce changes
        scope.launch {
            settings.volumeFlow.collect { vol ->
                // Nếu muốn, có thể đọc âm lượng tự động khi thay đổi
            }
        }
        scope.launch {
            settings.speedFlow.collect { speed ->
                // Tự động phản hồi thay đổi tốc độ đọc nếu cần
            }
        }
        scope.launch {
            settings.keepScreenOnFlow.collect { enabled ->
                // Phản hồi khi trạng thái màn hình thay đổi
            }
        }
    }

    fun handleCommand(command: String) {
        val cmd = command.lowercase()

        when {
            cmd.contains("hủy kết nối") || cmd.contains("ngắt kết nối") -> handleDisconnect()
            cmd.contains("kết nối") -> handleConnect()
            cmd.contains("tắt mic") || cmd.contains("dừng lại") || cmd.contains("ngưng lắng nghe") ->
                voiceResponder("Đã tắt mic và dừng lắng nghe")
            cmd.contains("cài đặt") -> { bottomNav.selectedItemId = R.id.setting; voiceResponder("Chuyển đến cài đặt") }
            cmd.contains("trang chủ") -> { bottomNav.selectedItemId = R.id.home; voiceResponder("Chuyển đến trang chủ") }

            cmd.contains("hiện tại") && (cmd.contains("âm lượng") || cmd.contains("tốc độ đọc") || cmd.contains("màn hình")) -> {
                val parts = mutableListOf<String>()
                if (cmd.contains("âm lượng")) parts.add("âm lượng bây giờ là ${settings.getVolume()}%")
                if (cmd.contains("tốc độ đọc")) parts.add("tốc độ đọc ${speedText(settings.getSpeed())}")
                if (cmd.contains("màn hình") || cmd.contains("khóa màn hình")) {
                    val screen = if (settings.isKeepScreenOn()) "đang bật" else "có thể tự khóa"
                    parts.add("màn hình $screen")
                }
                voiceResponder(parts.joinToString(", ") + ".")
            }

            cmd.contains("hiện tại") && (cmd.contains("cả") || cmd.contains("tất cả")) -> {
                voiceResponder("Âm lượng bây giờ là ${settings.getVolume()}%, " +
                        "tốc độ đọc ${speedText(settings.getSpeed())}, " +
                        "và màn hình ${if (settings.isKeepScreenOn()) "đang bật" else "có thể tự khóa"}.")
            }

            cmd.contains("tăng âm lượng") -> adjustVolume(up = true)
            cmd.contains("giảm âm lượng") -> adjustVolume(up = false)
            cmd.contains("đặt âm lượng") -> extractNumber(cmd)?.let {
                settings.setVolume(it.coerceIn(0, 100))
                voiceResponder("Đã đặt âm lượng thành ${it.coerceIn(0, 100)}%")
            }

            cmd.contains("tăng tốc độ đọc") -> adjustSpeed(up = true)
            cmd.contains("giảm tốc độ đọc") -> adjustSpeed(up = false)
            cmd.contains("đặt tốc độ đọc") -> parseSpeed(cmd)?.let {
                settings.setSpeed(it)
                voiceResponder("Đã đặt tốc độ đọc thành ${speedText(it)}")
            }

            cmd.contains("bật màn hình") || cmd.contains("Không khóa màn hính") || cmd.contains("sáng màn hình") -> {
                settings.setKeepScreenOn(true)
                voiceResponder("Màn hình sẽ luôn sáng")
            }

            cmd.contains("tắt màn hình") || cmd.contains("khóa màn hình") || cmd.contains("không sáng màn hình") || cmd.contains("không khóa màn hình") -> {
                settings.setKeepScreenOn(false)
                voiceResponder("Thiết bị có thể tự khóa màn hình")
            }

            else -> voiceResponder("Tôi không hiểu lệnh: $command")
        }
    }

    // ---------------- HELPERS ----------------
    private fun handleConnect() {
        if (isConnected) { voiceResponder("Thiết bị đã kết nối sẵn"); return }
        onConnect { success ->
            isConnected = success
            voiceResponder(if (success) "Đã kết nối thiết bị" else "Kết nối thất bại")
        }
    }

    private fun handleDisconnect() {
        if (!isConnected) { voiceResponder("Thiết bị chưa kết nối"); return }
        onDisconnect { success ->
            if (success) { isConnected = false; voiceResponder("Đã hủy kết nối") }
            else voiceResponder("Hủy kết nối thất bại")
        }
    }

    private fun adjustVolume(up: Boolean) {
        val current = settings.getVolume()
        val newVol = if (up) (current + 10).coerceAtMost(100) else (current - 10).coerceAtLeast(0)
        settings.setVolume(newVol)
        voiceResponder("Âm lượng hiện tại: $newVol%")
    }

    private fun adjustSpeed(up: Boolean) {
        val speeds = listOf("very_slow","slow","normal","fast","very_fast")
        val idx = speeds.indexOf(settings.getSpeed())
        val newIdx = when {
            up && idx < speeds.size - 1 -> idx + 1
            !up && idx > 0 -> idx - 1
            else -> idx
        }
        settings.setSpeed(speeds[newIdx])
        voiceResponder("Tốc độ đọc: ${speedText(speeds[newIdx])}")
    }

    private fun speedText(speed: String) = when(speed) {
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

    private fun extractNumber(text: String): Int? = """\d+""".toRegex().find(text)?.value?.toIntOrNull()
}
