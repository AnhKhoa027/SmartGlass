package com.example.smartglass

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.core.content.edit

class SettingFragment : Fragment() {

    private lateinit var seekBarVolume: SeekBar
    private lateinit var switchAutoConnectDevice: SwitchCompat
    private lateinit var buttonAppInfo: Button

    // Khởi tạo SharedPreferences chỉ 1 lần
    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_setting, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        seekBarVolume = view.findViewById(R.id.seekbar_tts_volume)
        switchAutoConnectDevice = view.findViewById(R.id.switch_auto_connect)
        buttonAppInfo = view.findViewById(R.id.btn_app_info)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        seekBarVolume.progress = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME)
            .coerceIn(MIN_VOLUME, MAX_VOLUME)
        switchAutoConnectDevice.isChecked = prefs.getBoolean(KEY_AUTO_CONNECT, false)
    }

    private fun saveSettings() {
        prefs.edit {
            putInt(KEY_VOLUME, seekBarVolume.progress)
            putBoolean(KEY_AUTO_CONNECT, switchAutoConnectDevice.isChecked)
        }
    }

    private fun setupListeners() {
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchAutoConnectDevice.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }

        buttonAppInfo.setOnClickListener {
            showAppInfoDialog()
        }
    }

    private fun showAppInfoDialog() {
        val context = requireContext()
        val appName = context.getString(R.string.app_name)
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")

        val message = getString(
            R.string.app_info_message,
            appName,
            versionName
        )

        AlertDialog.Builder(context)
            .setTitle(R.string.title_app_info)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_VOLUME = "volume"

        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val DEFAULT_VOLUME = 100
    }
}
