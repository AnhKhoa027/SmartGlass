package com.example.smartglass.EmergencyCall

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log

class PhoneCallListener(
    private val context: Context,
    private val onCallEnded: () -> Unit
) : PhoneStateListener() {

    private var telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var isCallActive = false
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private val CALL_TIMEOUT = 25000L

    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
        super.onCallStateChanged(state, phoneNumber)

        when (state) {

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("EmergencyCall", "CALL_STATE_OFFHOOK → Đang gọi/Đã kết nối")
                // Đặt cờ là đã có hoạt động gọi, nhưng KHÔNG hủy Timeout.
                // Nếu sau OFFHOOK mà không có IDLE, Timeout sẽ xử lý.
                isCallActive = true
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isCallActive) {
                    // Khi cuộc gọi kết thúc (Dù là thành công hay ngắt máy), IDLE được gửi
                    Log.d("EmergencyCall", "CALL_STATE_IDLE → Cuộc gọi kết thúc → Gọi tiếp số sau")
                    isCallActive = false
                    cancelTimeout() // Hủy Timeout sau khi cuộc gọi kết thúc và gọi số tiếp theo
                    onCallEnded()
                }
            }

            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("EmergencyCall", "CALL_STATE_RINGING → Chuông đang đổ")
                // Vẫn giữ isCallActive = false ở đây, vì nó chỉ là chuông đổ.
            }
        }
    }

    fun startTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            Log.d("EmergencyCall", "TIMEOUT → Không ai bắt máy hoặc không trả về IDLE sau ${CALL_TIMEOUT / 1000}s → Gọi tiếp")
            isCallActive = false // Đặt lại để chuẩn bị cho lần gọi tiếp theo
            onCallEnded()
        }

        timeoutRunnable?.let { handler.postDelayed(it, CALL_TIMEOUT) }
        Log.d("EmergencyCall", "Timeout ${CALL_TIMEOUT / 1000}s đã được kích hoạt")
    }

    fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
    }

    fun register() {
        telephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE)
        Log.d("EmergencyCall", "PhoneCallListener REGISTERED")
    }

    fun unregister() {
        cancelTimeout()
        telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE)
        Log.d("EmergencyCall", "PhoneCallListener UNREGISTERED")
    }
}
