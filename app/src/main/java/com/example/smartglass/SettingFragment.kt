package com.example.smartglass

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartglass.SettingAction.SettingsManager
import com.example.smartglass.TTSandSTT.VoiceResponder
import kotlinx.coroutines.launch

class SettingFragment : Fragment() {

    private lateinit var seekBarVolume: SeekBar
    private lateinit var tvTocDoDoc: TextView
    private lateinit var cbUnlockDevice: CheckBox
    private lateinit var settingsManager: SettingsManager
    private lateinit var voiceResponder: VoiceResponder
    private var wakeLock: PowerManager.WakeLock? = null

    private var ignoreCheckChange = false
    private var isUiInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_setting, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settingsManager = SettingsManager.getInstance(requireContext())
        voiceResponder = VoiceResponder(requireContext())

        seekBarVolume = view.findViewById(R.id.seekbar_tts_volume)
        tvTocDoDoc = view.findViewById(R.id.tv_toc_do_doc)
        cbUnlockDevice = view.findViewById(R.id.cb_unlock_device)

        val tvDevTeam = view.findViewById<TextView>(R.id.tv_dev_team)
        tvDevTeam.setOnClickListener { showDevTeamDialog() }

        setupListeners()
        observeSettings()
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsManager.volumeFlow.collect { vol ->
                if (seekBarVolume.progress != vol) seekBarVolume.progress = vol
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsManager.speedFlow.collect { speed ->
                tvTocDoDoc.text = when (speed) {
                    "very_slow" -> "Tốc độ đọc: Rất chậm"
                    "slow" -> "Tốc độ đọc: Chậm"
                    "fast" -> "Tốc độ đọc: Nhanh"
                    "very_fast" -> "Tốc độ đọc: Rất nhanh"
                    else -> "Tốc độ đọc: Bình thường"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsManager.keepScreenOnFlow.collect { enabled ->
                ignoreCheckChange = true
                cbUnlockDevice.isChecked = enabled
                ignoreCheckChange = false
                applyKeepScreenOn(enabled)
                isUiInitialized = true
            }
        }
    }

    private fun setupListeners() {
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setVolume(progress)
                    voiceResponder.speak("Âm lượng hiện tại là $progress phần trăm.")
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        tvTocDoDoc.setOnClickListener { showSpeedSelectionDialog() }

        cbUnlockDevice.setOnCheckedChangeListener { _, isChecked ->
            if (!isUiInitialized || ignoreCheckChange) return@setOnCheckedChangeListener

            settingsManager.setKeepScreenOn(isChecked)
            val msg = if (isChecked)
                "Thiết bị sẽ luôn sáng màn hình."
            else
                "Thiết bị sẽ tắt màn hình."
            voiceResponder.speak(msg)
        }
    }

    private fun showSpeedSelectionDialog() {
        val speeds = arrayOf("Rất chậm", "Chậm", "Bình thường", "Nhanh", "Rất nhanh")
        val currentIndex = when (settingsManager.getSpeed()) {
            "very_slow" -> 0
            "slow" -> 1
            "normal" -> 2
            "fast" -> 3
            "very_fast" -> 4
            else -> 2
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Chọn tốc độ đọc")
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                val selectedSpeed = when (which) {
                    0 -> "very_slow"
                    1 -> "slow"
                    2 -> "normal"
                    3 -> "fast"
                    4 -> "very_fast"
                    else -> "normal"
                }
                settingsManager.setSpeed(selectedSpeed)
                voiceResponder.speak("Đã đặt tốc độ đọc: ${speeds[which]}")
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (enabled) {
            if (wakeLock == null || !wakeLock!!.isHeld) {
                wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SmartGlass::KeepScreenOn"
                )
                wakeLock?.acquire()
            }
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroyView() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        voiceResponder.shutdown()
        super.onDestroyView()
    }

    data class DevMember(
        val name: String,
        val studentId: String,
        val role: String,
        val avatarRes: Int
    )

    private fun showDevTeamDialog() {
        val members = listOf(
            DevMember("Lê Hòa Hiệp", "28219005065", "Leader - AI Machine Development", R.drawable.hiep),
            DevMember("Nguyễn Văn Hướng", "28211152883", "Embedded Software Development", R.drawable.huong),
            DevMember("Phạm Anh Khoa", "28211152934", "Frontend & Backend Android Development", R.drawable.khoa),
            DevMember("Trương Công Thành", "28219043538", "Backend Android Development - Collect Data", R.drawable.thanh),
            DevMember("Nguyễn Tấn Việt", "28211152290", "Backend Android Development - GPS Tracker", R.drawable.viet),
        )

        val dialogView = layoutInflater.inflate(R.layout.dialog_dev_team_card, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.member_container)

        members.forEach { member ->
            val memberView = layoutInflater.inflate(R.layout.item_member, container, false)
            memberView.findViewById<ImageView>(R.id.img_avatar).setImageResource(member.avatarRes)
            memberView.findViewById<TextView>(R.id.tv_name).text = member.name
            memberView.findViewById<TextView>(R.id.tv_info).text =
                "${member.studentId} - ${member.role}"
            container.addView(memberView)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Đóng", null)
            .create()

        dialog.show()
    }
}
