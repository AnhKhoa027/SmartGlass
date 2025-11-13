package com.example.smartglass.HomeAction

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.example.smartglass.EmergencyCall.EmergencyManager

class GestureActionManager(
    private val rootView: View,
    private val context: Context,
    private val onHoldScreen: (() -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isPressed = false

    // Dùng cho nhấn 2 lần
    private var lastPressTime: Long = 0
    private var pressCount = 0
    private val DOUBLE_PRESS_THRESHOLD = 300L // ms

    fun init() {
        rootView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleDoubleTap()
                    handleLongPress()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    handler.removeCallbacksAndMessages(null)
                    v.performClick()
                }
            }
            true
        }
    }

    private fun handleLongPress() {
        isPressed = true
        handler.postDelayed({
            if (isPressed) {
                Log.d("GestureActionManager", " Giữ màn hình >4s")
                onHoldScreen?.invoke()
            }
        }, 4000)
    }

    private fun handleDoubleTap() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPressTime <= DOUBLE_PRESS_THRESHOLD) {
            pressCount++
        } else {
            pressCount = 1
        }
        lastPressTime = currentTime

        if (pressCount == 10) {
            pressCount = 0
            Log.d("GestureActionManager", "Kích hoạt tính năng khẩn cấp")
            Toast.makeText(context, "Kích hoạt khẩn cấp!", Toast.LENGTH_SHORT).show()

            val manager = EmergencyManager(context)
            manager.triggerEmergency()
        }
    }
}

