package com.example.smartglass.SettingAction

data class SensorSample(
    val distanceMm: Int,
    val dirX: String,
    val dirY: String,
    val timestamp: Long
)
