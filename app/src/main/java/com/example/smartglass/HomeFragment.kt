package com.example.smartglass

import android.content.*
import android.hardware.usb.UsbConstants
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
import com.example.smartglass.SettingAction.DistanceMotionReader
import com.example.smartglass.SettingAction.SensorBuffer
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.HomeAction.*
import kotlinx.coroutines.*

class HomeFragment : Fragment() {

    private lateinit var btnConnectXiaoCam: Button
    private lateinit var textureViewCam: TextureView
    private lateinit var overlayView: OverlayView
    private lateinit var glassIcon: ImageView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    // ================= Core =================
    private lateinit var usbCameraViewManager: UsbCameraViewManager
    private lateinit var requestQueue: RequestQueue
    private lateinit var sensorBuffer: SensorBuffer

    private var detectionManager: DetectionManager? = null
    private var detectionSpeaker: DetectionSpeaker? = null
    private var voiceResponder: VoiceResponder? = null

    private var isConnected = false
    private var isUsbCameraConnected = false
    private var isUserDisconnecting = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var usbReceiver: BroadcastReceiver? = null

    private var distanceReader: DistanceMotionReader? = null

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
        overlayView = view.findViewById(R.id.overlay)
        glassIcon = view.findViewById(R.id.glass_icon)
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

        initDistanceSensor()
        registerUsbReceiver()

        btnConnectXiaoCam.setOnClickListener {
            if (isConnected) disconnectFromUsbCam()
            else connectToUsbCam()
        }

        updateButtonState(R.string.connect, "#2F58C3", true)
        checkUsbCameraConnection(requireContext())
    }

    private fun initDistanceSensor() {
        sensorBuffer = SensorBuffer()

        distanceReader = DistanceMotionReader(
            requireContext(),
            sensorBuffer
        )
    }


    private fun trySpeakDetections() {
        val objects = detectionManager?.currentTrackedObjects ?: return
        if (objects.isEmpty()) return

        detectionSpeaker?.speakDetections(
            trackedObjects = objects,
            frameW = overlayView.width,
            frameH = overlayView.height
        )
    }


    private fun updateButtonState(textRes: Int, bgColor: String, enabled: Boolean) {
        btnConnectXiaoCam.apply {
            text = getString(textRes)
            setBackgroundColor(bgColor.toColorInt())
            isEnabled = enabled
        }
    }

    private fun updateCameraStatus(isConnected: Boolean) {
        if (!isAdded || isRemoving) return

        statusDot.setBackgroundResource(
            if (isConnected)
                R.drawable.status_dot_green
            else
                R.drawable.status_dot_gray
        )
        statusText.text =
            if (isConnected)
                "Đã nhận tín hiệu camera"
            else
                "Chưa nhận tín hiệu từ Camera"
    }

    private fun checkUsbCameraConnection(context: Context) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        isUsbCameraConnected = manager.deviceList.values.any {
            it.deviceClass == UsbConstants.USB_CLASS_VIDEO
        }
        updateCameraStatus(isUsbCameraConnected)
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        isUsbCameraConnected = true
                        updateCameraStatus(true)
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        isUsbCameraConnected = false
                        updateCameraStatus(false)

                        sensorBuffer.clear()
                        distanceReader?.stop()
                    }
                }
            }
        }

        requireContext().registerReceiver(usbReceiver, filter)
    }

    private fun ensureManagers() {
        if (voiceResponder == null) return

        if (detectionSpeaker == null) {
            detectionSpeaker = DetectionSpeaker(
                voiceResponder!!,
                sensorBuffer
            )
        }

        if (detectionManager == null) {
            val apiKey = getString(R.string.vision_api_key)
            val apiManager = ApiDetectionManager(apiKey)

            detectionManager = DetectionManager(
                requireContext(),
                usbCameraViewManager,
                detectionSpeaker!!,
                apiManager,
                scope
            )
        } else {
            detectionManager?.lastFrame = null
        }

        usbCameraViewManager.detectionManager = detectionManager
    }

    fun connectToUsbCam() {
        updateButtonState(R.string.connecting, "#808080", false)

        scope.launch(Dispatchers.Main) {
            delay(5000)
            if (!isConnected && isAdded) {
                updateButtonState(R.string.connect, "#2F58C3", true)
            }
        }

        try {
            ensureManagers()

            usbCameraViewManager.setOnCameraStateListener(
                object : UsbCameraViewManager.CameraStateListener {

                    override fun onCameraConnected() {
                        if (!isAdded) return
                        requireActivity().runOnUiThread {
                            isConnected = true
                            updateButtonState(R.string.connected, "#4CAF50", true)
                            voiceResponder?.speak("Kết nối thành công")

                            distanceReader?.start()
                        }
                    }

                    override fun onCameraDisconnected() {
                        if (!isAdded) return
                        requireActivity().runOnUiThread {
                            isConnected = false
                            updateButtonState(R.string.connect, "#2F58C3", true)
                            usbCameraViewManager.showGlassIcon()

                            distanceReader?.stop()

                            if (!isUserDisconnecting) {
                                voiceResponder?.speak("Đã ngắt kết nối")
                            }
                            isUserDisconnecting = false
                        }
                    }

                    override fun onCameraError(error: String) {
                        if (!isAdded) return
                        requireActivity().runOnUiThread {
                            isConnected = false
                            updateButtonState(R.string.connect, "#2F58C3", true)
                            usbCameraViewManager.showGlassIcon()

                            voiceResponder?.speak("Lỗi camera: $error")
                            distanceReader?.stop()
                            isUserDisconnecting = false
                        }
                    }
                }
            )

            usbCameraViewManager.startCamera()

        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded) {
                requireActivity().runOnUiThread {
                    isConnected = false
                    updateButtonState(R.string.connect, "#2F58C3", true)
                    voiceResponder?.speak("Kết nối thất bại")
                }
            }
        }
    }

    fun disconnectFromUsbCam() {
        isUserDisconnecting = true

        usbCameraViewManager.isUserRequestedConnect = false
        try {
            usbCameraViewManager.showGlassIcon()
            usbCameraViewManager.release()
        } catch (_: Exception) {}

        detectionSpeaker?.stop()
        distanceReader?.stop()

        isConnected = false
        updateButtonState(R.string.connect, "#2F58C3", true)
    }

    fun restartDistanceReader() {
        if (!isConnected) return
        distanceReader?.stop()
        distanceReader?.start()
    }


    override fun onPause() {
        super.onPause()
        distanceReader?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        try { usbCameraViewManager.release() } catch (_: Exception) {}
        requestQueue.cancelAll { true }
        scope.cancel()

        detectionManager?.release()
        detectionSpeaker?.stop()
        distanceReader?.release()

        usbReceiver?.let {
            requireContext().unregisterReceiver(it)
            usbReceiver = null
        }
    }
}
