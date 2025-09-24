package com.example.smartglass

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.example.smartglass.DetectResponse.DetectionSpeaker
import com.example.smartglass.ObjectDetection.OverlayView
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.HomeAction.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * HomeFragment
 * -------------------
 * Đây là **màn hình chính** hiển thị camera stream và xử lý logic kết nối với
 * camera Xiao ESP32S3 + YOLO + API fallback.
 *
 * Nhiệm vụ:
 * - Quản lý nút kết nối/ngắt kết nối (btnConnectXiaoCam).
 * - Dùng XiaoCamManager để kết nối tới camera ESP32.
 * - Dùng CameraViewManager để hiển thị stream qua WebView + overlay bounding box.
 * - Khởi tạo DetectionManager để chạy YOLO detect, tracking, phát hiện giọng nói.
 * - Nếu YOLO không nhận diện được thì gọi ApiDetectionManager (HuggingFace API).
 * - Vòng lặp `startFrameLoop()` liên tục tải frame từ camera -> detect.
 *
 * Liên quan:
 * - XiaoCamManager.kt : xử lý connect/disconnect tới ESP32S3
 * - CameraViewManager.kt : vẽ icon kính / stream / overlay box
 * - DetectionManager.kt : YOLO detect + tracking + TTS speaker
 * - ApiDetectionManager.kt : fallback gọi API HuggingFace khi YOLO không nhận diện
 */
class HomeFragment : Fragment() {

    // === UI components ===
    private lateinit var btnConnectXiaoCam: Button
    private lateinit var requestQueue: RequestQueue      

    private lateinit var cameraViewManager: CameraViewManager
    private lateinit var xiaoCamManager: XiaoCamManager

    // === Detection & Voice ===
    private var detectionManager: DetectionManager? = null
    private var voiceResponder: VoiceResponder? = null
    private var detectionSpeaker: DetectionSpeaker? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val xiaoCamIp: String
        get() = requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_XIAOCAM_IP, DEFAULT_IP) ?: DEFAULT_IP

    // === Lifecycle ===
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        btnConnectXiaoCam = view.findViewById(R.id.btnConnect)
        val glassIcon = view.findViewById<ImageView>(R.id.glass_icon)
        val overlayView = view.findViewById<OverlayView>(R.id.overlay)
        val webView = view.findViewById<android.webkit.WebView>(R.id.webViewCam)

        // Khởi tạo manager quản lý giao diện camera
        cameraViewManager = CameraViewManager(webView, glassIcon, overlayView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestQueue = Volley.newRequestQueue(requireContext())

        xiaoCamManager = XiaoCamManager(requireContext(), requestQueue) { xiaoCamIp }

        // Set sự kiện click cho nút Connect/Disconnect
        btnConnectXiaoCam.setOnClickListener {
            if (xiaoCamManager.isConnected) disconnectFromXiaoCam()
            else connectToXiaoCam()
        }
    }


    private fun updateButtonState(textRes: Int, bgColor: String, enabled: Boolean) {
        btnConnectXiaoCam.apply {
            text = getString(textRes)
            setBackgroundColor(bgColor.toColorInt())
            isEnabled = enabled
        }
    }

    /**
     * Đảm bảo các manager (YOLO, Speaker, API) được khởi tạo 1 lần sau khi connect.
     */
    private fun ensureManagers() {
        if (voiceResponder == null)
            voiceResponder = VoiceResponder(requireContext())

        if (detectionSpeaker == null)
            detectionSpeaker = DetectionSpeaker(requireContext(), voiceResponder!!)

        if (detectionManager == null) {
            // Manager gọi HuggingFace API (fallback khi YOLO không nhận diện)
            val apiManager = ApiDetectionManager(
                requireContext(),
                apiToken = "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"
            )
            // DetectionManager chính (YOLO + tracking + speaker + API fallback)
            detectionManager = DetectionManager(
                requireContext(),
                cameraViewManager,
                detectionSpeaker!!,
                apiManager,
                scope
            )
        }
    }

    /**
     * Thực hiện kết nối tới camera Xiao ESP32S3
     */
    fun connectToXiaoCam() {
        updateButtonState(R.string.connecting, "#808080", false)
        voiceResponder?.speak("Đang kết nối với kính")

        xiaoCamManager.connect(
            onSuccess = {
                ensureManagers()
                updateButtonState(R.string.connected, "#4CAF50", true)
                cameraViewManager.showWebView(xiaoCamIp)   // Hiển thị stream
                startFrameLoop()                          // Bắt đầu vòng lặp nhận frame
                voiceResponder?.speak("Kết nối thành công")
            },
            onFailure = {
                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                updateButtonState(R.string.connect, "#2F58C3", true)
                cameraViewManager.showGlassIcon()
                voiceResponder?.speak("Kết nối thất bại")
            }
        )
    }

    /**
     * Ngắt kết nối với camera ESP32S3
     */
    fun disconnectFromXiaoCam() {
        xiaoCamManager.disconnect()
        updateButtonState(R.string.connect, "#2F58C3", true)

        cameraViewManager.showGlassIcon()
        scope.coroutineContext.cancelChildren() // Dừng frame loop
        detectionSpeaker?.stop()
        voiceResponder?.speak("Đã ngắt kết nối")
    }

    /**
     * Vòng lặp liên tục:
     * - Lấy frame từ ESP32 (qua HTTP capture endpoint).
     * - Decode thành Bitmap.
     * - Gửi qua DetectionManager để YOLO detect.
     *
     * Chạy trong coroutine IO -> tránh block UI.
     */
    private fun startFrameLoop() {
        scope.launch {
            while (xiaoCamManager.isConnected) {
                try {
                    val url = URL(xiaoCamManager.buildUrl("capture"))
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    // Đọc frame từ stream
                    val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()

                    // Gửi frame cho YOLO detect
                    bitmap?.let { detectionManager?.detectFrame(it) }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(500) // Nhận frame mỗi 500ms (giảm tải CPU)
            }
        }
    }

    /**
     * Giải phóng tài nguyên khi fragment bị destroy.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        cameraViewManager.release()
        requestQueue.cancelAll { true }
        scope.cancel()
        detectionManager?.release()
        detectionSpeaker?.stop()
        voiceResponder?.shutdown()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_XIAOCAM_IP = "xiaocam_ip"
        private const val DEFAULT_IP = "192.168.4.1"
    }
}
