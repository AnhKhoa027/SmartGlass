package com.example.smartglass.TTSandSTT

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.example.smartglass.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class VoiceCommandProcessor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val bottomNav: BottomNavigationView,
    private val onConnect: () -> Unit,
    private val onDisconnect: () -> Unit,
    private val voiceResponder: (String) -> Unit
) {
    //Các câu lệnh xử lý
    fun handleCommand(command: String) {
        when {
            command.contains("kết nối", ignoreCase = true) -> {
                onConnect()
            }

            command.contains("hủy kết nối", ignoreCase = true) ||
                    command.contains("ngắt kết nối", ignoreCase = true) -> {
                voiceResponder("Đã hủy kết nối thiết bị")
                onDisconnect()
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
