package com.example.smartglass

import android.content.*
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.example.smartglass.DetectResponse.DetectionSpeaker
import com.example.smartglass.ObjectDetection.OverlayView
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.HomeAction.*
import kotlinx.coroutines.*

class HomeFragment : Fragment() {

    private lateinit var btnConnectXiaoCam: Button
    private lateinit var textureViewCam: TextureView
    private lateinit var glassIcon: ImageView
    private lateinit var overlayView: OverlayView

    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    private lateinit var usbCameraViewManager: UsbCameraViewManager
    private lateinit var requestQueue: RequestQueue

    private var detectionManager: DetectionManager? = null
    private var detectionSpeaker: DetectionSpeaker? = null
    private var voiceResponder: VoiceResponder? = null

    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pausedForWakeWord = false

    // ✅ Lưu reference BroadcastReceiver để unregister
    private var usbReceiver: BroadcastReceiver? = null

    fun setVoiceResponder(vr: VoiceResponder) {
        voiceResponder = vr
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        btnConnectXiaoCam = view.findViewById(R.id.btnConnect)
        textureViewCam = view.findViewById(R.id.camera_view)
        glassIcon = view.findViewById(R.id.glass_icon)
        overlayView = view.findViewById(R.id.overlay)
        statusDot = view.findViewById(R.id.camera_status_dot)
        statusText = view.findViewById(R.id.camera_status_text)

        textureViewCam.visibility = View.GONE
        glassIcon.visibility = View.VISIBLE

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestQueue = Volley.newRequestQueue(requireContext())

        usbCameraViewManager = UsbCameraViewManager(
            requireContext(),
            textureViewCam,
            overlayView,
            detectionManager,
            glassIcon
        )
        usbCameraViewManager.initTextureView()
        usbCameraViewManager.initUsbMonitor()

        btnConnectXiaoCam.setOnClickListener {
            if (isConnected) disconnectFromUsbCam()
            else connectToUsbCam()
        }

        updateButtonState(R.string.connect, "#2F58C3", true)

        // ✅ Kiểm tra kết nối USB ngay khi vào fragment
        checkUsbCameraConnection(requireContext())

        // ✅ Lắng nghe cắm/rút USB camera
        registerUsbReceiver()
    }

    private fun updateButtonState(textRes: Int, bgColor: String, enabled: Boolean) {
        btnConnectXiaoCam.apply {
            text = getString(textRes)
            setBackgroundColor(bgColor.toColorInt())
            isEnabled = enabled
        }
    }

    private fun updateCameraStatus(isConnected: Boolean) {
        requireActivity().runOnUiThread {
            if (isConnected) {
                statusDot.setBackgroundResource(R.drawable.status_dot_green)
                statusText.text = "Đã nhận tín hiệu camera"
            } else {
                statusDot.setBackgroundResource(R.drawable.status_dot_gray)
                statusText.text = "Chưa nhận tín hiệu từ Camera"
            }
        }
    }

    private fun checkUsbCameraConnection(context: Context) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val hasUsbCamera = manager.deviceList.values.any { device ->
            device.deviceClass == android.hardware.usb.UsbConstants.USB_CLASS_VIDEO
        }
        updateCameraStatus(hasUsbCamera)
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> updateCameraStatus(true)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> updateCameraStatus(false)
                }
            }
        }

        requireContext().registerReceiver(usbReceiver, filter)
    }

    private fun ensureManagers() {
        if (voiceResponder == null) return
        if (detectionSpeaker == null)
            detectionSpeaker = DetectionSpeaker(requireContext(), voiceResponder!!)
        if (detectionManager == null) {
            val apiManager = ApiDetectionManager(requireContext())
            detectionManager = DetectionManager(
                requireContext(),
                usbCameraViewManager,
                detectionSpeaker!!,
                apiManager,
                scope
            )
        } else detectionManager?.lastFrame = null
        usbCameraViewManager.detectionManager = detectionManager
    }

    fun connectToUsbCam() {
        updateButtonState(R.string.connecting, "#808080", false)
        voiceResponder?.speak("Đang kết nối với kính")

        try {
            ensureManagers()

            usbCameraViewManager.setOnCameraStateListener(object :
                UsbCameraViewManager.CameraStateListener {
                override fun onCameraConnected() {
                    requireActivity().runOnUiThread {
                        isConnected = true
                        updateButtonState(R.string.connected, "#4CAF50", true)
                        updateCameraStatus(true)
                        voiceResponder?.speak("Kết nối thành công")
                    }
                }

                override fun onCameraDisconnected() {
                    requireActivity().runOnUiThread {
                        isConnected = false
                        updateButtonState(R.string.connect, "#2F58C3", true)
                        updateCameraStatus(false)
                        voiceResponder?.speak("Camera đã ngắt kết nối")
                    }
                }

                override fun onCameraError(error: String) {
                    requireActivity().runOnUiThread {
                        isConnected = false
                        updateButtonState(R.string.connect, "#2F58C3", true)
                        usbCameraViewManager.showGlassIcon()
                        updateCameraStatus(false)
                        voiceResponder?.speak("Lỗi camera: $error")
                        Toast.makeText(context, "Lỗi camera: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            usbCameraViewManager.startCamera()

        } catch (e: Exception) {
            e.printStackTrace()
            requireActivity().runOnUiThread {
                isConnected = false
                updateButtonState(R.string.connect, "#2F58C3", true)
                updateCameraStatus(false)
                voiceResponder?.speak("Kết nối thất bại")
            }
        }
    }

    fun disconnectFromUsbCam() {
        usbCameraViewManager.isUserRequestedConnect = false
        try { usbCameraViewManager.showGlassIcon(); usbCameraViewManager.release() } catch (_: Exception) {}
        detectionSpeaker?.stop()
        isConnected = false
        updateButtonState(R.string.connect, "#2F58C3", true)
        updateCameraStatus(false)
        voiceResponder?.speak("Đã ngắt kết nối")
    }

    fun pauseDetectionAndSpeech() {
        pausedForWakeWord = true
        detectionManager?.cancelAllTasks()
        detectionSpeaker?.stop()
    }

    fun resumeDetectionAndSpeech() {
        if (!pausedForWakeWord) return
        pausedForWakeWord = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { usbCameraViewManager.release() } catch (_: Exception) {}
        requestQueue.cancelAll { true }
        scope.cancel()
        detectionManager?.release()
        detectionSpeaker?.stop()

        usbReceiver?.let {
            requireContext().unregisterReceiver(it)
            usbReceiver = null
        }
    }
}
