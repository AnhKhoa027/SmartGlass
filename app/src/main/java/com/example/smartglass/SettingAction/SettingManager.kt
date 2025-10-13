package com.example.smartglass.SettingAction

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ----------------------------
    // STATEFLOW
    // ----------------------------
    private val _volumeFlow = MutableStateFlow(getVolumeFromPrefs())
    val volumeFlow: StateFlow<Int> = _volumeFlow.asStateFlow()

    private val _speedFlow = MutableStateFlow(getSpeedFromPrefs())
    val speedFlow: StateFlow<String> = _speedFlow.asStateFlow()

    private val _keepScreenOnFlow = MutableStateFlow(isKeepScreenOnFromPrefs())
    val keepScreenOnFlow: StateFlow<Boolean> = _keepScreenOnFlow.asStateFlow()

    fun getVolume(): Int = _volumeFlow.value
    fun getSpeed(): String = _speedFlow.value
    fun isKeepScreenOn(): Boolean = _keepScreenOnFlow.value

    fun setVolume(volume: Int) {
        val vol = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
        prefs.edit().putInt(KEY_VOLUME, vol).apply()
        _volumeFlow.value = vol
    }

    fun setSpeed(speed: String) {
        if (speed in validSpeeds) {
            prefs.edit().putString(KEY_SPEED, speed).apply()
            _speedFlow.value = speed
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _keepScreenOnFlow.value = enabled
    }

    fun getSpeedMultiplier(): Float = when (getSpeed()) {
        "very_slow" -> 0.5f
        "slow" -> 0.75f
        "fast" -> 1.75f
        "very_fast" -> 2.5f
        else -> 1.0f
    }

    fun getVolumeFloat(): Float = getVolume() / 100f

    // ----------------------------
    // HELPER
    // ----------------------------
    private fun getVolumeFromPrefs() = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(MIN_VOLUME, MAX_VOLUME)
    private fun getSpeedFromPrefs() = prefs.getString(KEY_SPEED, DEFAULT_SPEED) ?: DEFAULT_SPEED
    private fun isKeepScreenOnFromPrefs() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_VOLUME = "volume"
        private const val KEY_SPEED = "speed"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val DEFAULT_VOLUME = 100
        private const val DEFAULT_SPEED = "normal"

        private val validSpeeds = setOf("very_slow", "slow", "normal", "fast", "very_fast")

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
