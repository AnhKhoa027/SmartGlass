package com.example.smartglass

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.core.content.edit

class SettingFragment : Fragment() {

    private lateinit var seekBarVolume: SeekBar

    private lateinit var tvGiongDoc: TextView
    private lateinit var tvTocDoDoc: TextView
    private lateinit var tvAppVersion: TextView

    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun showDevTeamDialog() {
        val devTeamInfo = """
        - Nhóm phát triển:
        • Lê Hòa Hiệp
        • Nguyễn Văn Hướng
        • Phạm Anh Khoa
        • Trương Công Thành
        • Nguyễn Tấn Việt
    """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Thông tin nhóm")
            .setMessage(devTeamInfo)
            .setPositiveButton("Đóng", null)
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_setting, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        seekBarVolume = view.findViewById(R.id.seekbar_tts_volume)

        tvGiongDoc = view.findViewById(R.id.tv_giong_doc)
        tvTocDoDoc = view.findViewById(R.id.tv_toc_do_doc)
        tvAppVersion = view.findViewById(R.id.tv_app_version)

        val tvDevTeam = view.findViewById<TextView>(R.id.tv_dev_team)
        tvDevTeam.setOnClickListener {
            showDevTeamDialog()
        }

        // Hiển thị phiên bản trực tiếp trong TextView thay vì show dialog
        val versionName = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrDefault("1.0")
        tvAppVersion.text = "Phiên bản ứng dụng: $versionName"

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        // Âm lượng
        seekBarVolume.progress = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME)
            .coerceIn(MIN_VOLUME, MAX_VOLUME)

        // Hiển thị lựa chọn hiện tại cho giọng đọc
        val voice = prefs.getString(KEY_VOICE, "male")
        tvGiongDoc.text = if (voice == "female") "Giọng đọc: Nữ" else "Giọng đọc: Nam"

        // Hiển thị lựa chọn hiện tại cho tốc độ
        when (prefs.getString(KEY_SPEED, "normal")) {
            "slow" -> tvTocDoDoc.text = "Tốc độ đọc: Chậm"
            "fast" -> tvTocDoDoc.text = "Tốc độ đọc: Nhanh"
            "very_fast" -> tvTocDoDoc.text = "Tốc độ đọc: Rất nhanh"
            else -> tvTocDoDoc.text = "Tốc độ đọc: Bình thường"
        }
    }

    private fun saveSettings(key: String, value: String) {
        prefs.edit {
            putString(key, value)
        }
        loadSettings()
    }

    private fun setupListeners() {
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit { putInt(KEY_VOLUME, progress) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Khi click vào giọng đọc → show dialog lựa chọn
        tvGiongDoc.setOnClickListener {
            val voices = arrayOf("Nam", "Nữ")
            val checked = if (prefs.getString(KEY_VOICE, "male") == "female") 1 else 0

            AlertDialog.Builder(requireContext())
                .setTitle("Chọn giọng đọc")
                .setSingleChoiceItems(voices, checked) { dialog, which ->
                    val selected = if (which == 1) "female" else "male"
                    saveSettings(KEY_VOICE, selected)
                    dialog.dismiss()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Khi click vào tốc độ đọc → show dialog lựa chọn
        tvTocDoDoc.setOnClickListener {
            val speeds = arrayOf("Chậm", "Bình thường", "Nhanh", "Rất nhanh")
            val current = when (prefs.getString(KEY_SPEED, "normal")) {
                "slow" -> 0
                "normal" -> 1
                "fast" -> 2
                "very_fast" -> 3
                else -> 1
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Chọn tốc độ đọc")
                .setSingleChoiceItems(speeds, current) { dialog, which ->
                    val selected = when (which) {
                        0 -> "slow"
                        2 -> "fast"
                        3 -> "very_fast"
                        else -> "normal"
                    }
                    saveSettings(KEY_SPEED, selected)
                    dialog.dismiss()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_VOLUME = "volume"
        private const val KEY_VOICE = "voice"
        private const val KEY_SPEED = "speed"

        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val DEFAULT_VOLUME = 100
    }
}
