package com.example.smartglass.HomeAction

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Gesture Action Manager
 * -------------------
 * Đây là là nơi xử lý các tác động chạm đối với màn hình
 *
 * Nhiệm vụ:
 * - Giữ màn hình > 4 giây => Mở Google STT
 * - Vuốt 3 ngón từ trên xuống => tự động kết nối với kính thông minh
 */

class GestureActionManager(
    private val rootView: View,
    private val onHoldScreen: (() -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isPressed = false

    fun init() {
        rootView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    handler.postDelayed({
                        if (isPressed) {
                            onHoldScreen?.invoke()
                        }
                    }, 4000) // giữ tay ≥ 4
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    handler.removeCallbacksAndMessages(null)
                    v.performClick() // để tránh warning accessibility
                }
            }
            true
        }
    }
}
