//package com.example.smartglass.EmergencyCall
//
//import android.content.Context
//import android.telephony.PhoneStateListener
//import android.telephony.TelephonyManager
//import android.util.Log
//
//class PhoneCallListener(
//    private val context: Context,
//    val onCallEnded: () -> Unit
//) {
//    private var telephonyManager: TelephonyManager? = null
//    private var lastState = TelephonyManager.CALL_STATE_IDLE
//
//    fun register() {
//        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
//    }
//
//    fun unregister() {
//        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
//    }
//
//    private val phoneStateListener = object : PhoneStateListener() {
//        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
//            super.onCallStateChanged(state, phoneNumber)
//
//            if (lastState == TelephonyManager.CALL_STATE_OFFHOOK || lastState == TelephonyManager.CALL_STATE_RINGING) {
//                if (state == TelephonyManager.CALL_STATE_IDLE) {
//                    onCallEnded()
//                }
//            }
//
//            lastState = state
//        }
//    }
//}
//
//
//
//

//package com.example.smartglass.EmergencyCall
//
//
//import android.content.Context
//import android.os.Handler
//import android.os.Looper
//import android.telephony.PhoneStateListener
//import android.telephony.TelephonyManager
//import android.util.Log
//
//
///**
// * Lắng nghe trạng thái cuộc gọi. Gọi onCallEnded() khi cuộc gọi từ trạng thái RINGING/OFFHOOK -> IDLE.
// * Sử dụng register()/unregister() để quản lý.
// */
//class PhoneCallListener(
//    private val context: Context,
//    private val onCallEnded: () -> Unit
//) {
//    private var telephonyManager: TelephonyManager? = null
//    private var lastState = TelephonyManager.CALL_STATE_IDLE
//    private val handler = Handler(Looper.getMainLooper())
//
//
//    fun register() {
//        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
//        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
//    }
//
//
//    fun unregister() {
//        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
//    }
//
//
//    private val phoneStateListener = object : PhoneStateListener() {
//        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
//            super.onCallStateChanged(state, phoneNumber)
//
//
//            try {
//// Nếu trước đó đang RINGING hoặc OFFHOOK, và bây giờ IDLE → cuộc gọi kết thúc
//                if ((lastState == TelephonyManager.CALL_STATE_OFFHOOK || lastState == TelephonyManager.CALL_STATE_RINGING)
//                    && state == TelephonyManager.CALL_STATE_IDLE) {
//                    Log.d("PhoneCallListener", "Call ended detected (last=$lastState -> now=$state)")
//// Đảm bảo gọi onCallEnded ở main thread
//                    handler.post { onCallEnded() }
//                }
//            } catch (e: Exception) {
//                Log.e("PhoneCallListener", "onCallStateChanged error: ${e.message}")
//            }
//
//
//            lastState = state
//        }
//    }
//}

// Cal tuan tu

//package com.example.smartglass.EmergencyCall
//
//import android.content.Context
//import android.os.Handler
//import android.os.Looper
//import android.telephony.PhoneStateListener
//import android.telephony.TelephonyManager
//import android.util.Log
//
//class PhoneCallListener(
//    private val context: Context,
//    private val onCallEnded: () -> Unit
//) : PhoneStateListener() {
//
//    private var telephonyManager: TelephonyManager =
//        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//
//    private var isCallActive = false
//    private val handler = Handler(Looper.getMainLooper())
//    private var timeoutRunnable: Runnable? = null
//
//    private val CALL_TIMEOUT = 25000L // 25 giây
//
//    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
//        super.onCallStateChanged(state, phoneNumber)
//
//        when (state) {
//
//            TelephonyManager.CALL_STATE_OFFHOOK -> {
//                Log.d("EmergencyCall", "CALL_STATE_OFFHOOK → Đang gọi")
//                isCallActive = true
//                cancelTimeout()
//            }
//
//            TelephonyManager.CALL_STATE_IDLE -> {
//                if (isCallActive) {
//                    Log.d("EmergencyCall", "CALL_STATE_IDLE → Cuộc gọi kết thúc → Gọi tiếp số sau")
//                    isCallActive = false
//                    cancelTimeout()
//                    onCallEnded()
//                }
//            }
//
//            TelephonyManager.CALL_STATE_RINGING -> {
//                Log.d("EmergencyCall", "CALL_STATE_RINGING → Chuông đang đổ")
//                // Tình trạng này KHÔNG báo khi kết thúc, nên timeout sẽ xử lý
//            }
//        }
//    }
//
//    /** Timeout để xử lý TH nếu không ai bắt máy */
//    fun startTimeout() {
//        cancelTimeout()
//        timeoutRunnable = Runnable {
//            Log.d("EmergencyCall", "TIMEOUT → Không ai bắt máy hoặc không trả về IDLE → Gọi tiếp")
//            onCallEnded()
//        }
//
//        timeoutRunnable?.let { handler.postDelayed(it, CALL_TIMEOUT) }
//        Log.d("EmergencyCall", "Timeout ${CALL_TIMEOUT / 1000}s đã được kích hoạt")
//    }
//
//    fun cancelTimeout() {
//        timeoutRunnable?.let { handler.removeCallbacks(it) }
//    }
//
//    fun register() {
//        telephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE)
//        Log.d("EmergencyCall", "PhoneCallListener REGISTERED")
//    }
//
//    fun unregister() {
//        cancelTimeout()
//        telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE)
//        Log.d("EmergencyCall", "PhoneCallListener UNREGISTERED")
//    }
//}

// Call tuan tu
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

    private val CALL_TIMEOUT = 25000L // 25 giây

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
