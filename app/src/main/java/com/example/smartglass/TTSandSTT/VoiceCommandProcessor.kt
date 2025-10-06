package com.example.smartglass.TTSandSTT

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class VoiceCommandProcessor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val bottomNav: BottomNavigationView,
    private val onConnect: (callback: (Boolean) -> Unit) -> Unit,
    private val onDisconnect: (callback: (Boolean) -> Unit) -> Unit,
    private val voiceResponder: (String) -> Unit
) {
    private var isConnected = false

    fun handleCommand(command: String) {
        when {
            command.contains("hủy kết nối", ignoreCase = true) ||
                    command.contains("ngắt kết nối", ignoreCase = true) -> {
                if (isConnected) {
                    onDisconnect { success ->
                        if (success) {
                            isConnected = false
                            voiceResponder("Đã hủy kết nối thiết bị")
                        } else {
                            voiceResponder("Hủy kết nối thất bại, thử lại")
                        }
                    }
                } else {
                    voiceResponder("Thiết bị chưa kết nối")
                }
            }
            command.contains("kết nối", ignoreCase = true) -> {
                if (!isConnected) {
                    onConnect { success ->
                        if (success) {
                            isConnected = true
                            voiceResponder("Đã kết nối thiết bị")
                        } else {
                            isConnected = false
                            voiceResponder("Kết nối thất bại, thử lại")
                        }
                    }
                } else {
                    voiceResponder("Thiết bị đã kết nối sẵn rồi")
                }
            }

            command.contains("tắt mic", ignoreCase = true) ||
                    command.contains("dừng lại", ignoreCase = true) ||
                    command.contains("ngưng lắng nghe", ignoreCase = true) -> {
                voiceResponder("Đã tắt mic và dừng lắng nghe")
            }

            command.contains("cài đặt", ignoreCase = true) -> {
                bottomNav.selectedItemId = R.id.setting
                voiceResponder("Chuyển đến cài đặt")
            }

            command.contains("trang chủ", ignoreCase = true) -> {
                bottomNav.selectedItemId = R.id.home
                voiceResponder("Chuyển đến trang chủ")
            }

            else -> {
                voiceResponder("Tôi không hiểu lệnh: $command")
            }
        }
    }
}
