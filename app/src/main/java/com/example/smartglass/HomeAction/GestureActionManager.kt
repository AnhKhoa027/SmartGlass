package com.example.smartglass.HomeAction

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

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
                    }, 4000) // giữ tay ≥ 4 giây
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
