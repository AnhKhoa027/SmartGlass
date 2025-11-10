package com.example.smartglass

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.example.smartglass.DetectResponse.DetectionSpeaker
import com.example.smartglass.ObjectDetection.OverlayView
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.HomeAction.*
import kotlinx.coroutines.*
import android.view.TextureView
import android.widget.Toast

class HomeFragment : Fragment() {

    private lateinit var btnConnectXiaoCam: Button
    private lateinit var textureViewCam: TextureView
    private lateinit var glassIcon: ImageView
    private lateinit var overlayView: OverlayView

    private lateinit var usbCameraViewManager: UsbCameraViewManager
    private lateinit var requestQueue: RequestQueue

    private var detectionManager: DetectionManager? = null
    private var detectionSpeaker: DetectionSpeaker? = null
    private var voiceResponder: VoiceResponder? = null

    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setVoiceResponder(vr: VoiceResponder) {
        voiceResponder = vr
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        btnConnectXiaoCam = view.findViewById(R.id.btnConnect)
        textureViewCam = view.findViewById(R.id.camera_view)
        glassIcon = view.findViewById(R.id.glass_icon)
        overlayView = view.findViewById(R.id.overlay)

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
        usbCameraViewManager.initTextureView() // init TextureView listener
        usbCameraViewManager.initUsbMonitor()  // chỉ detect, KHÔNG tự mở camera

        btnConnectXiaoCam.setOnClickListener {
            if (isConnected) disconnectFromUsbCam()
            else connectToUsbCam()
        }

        updateButtonState(R.string.connect, "#2F58C3", true)
    }

    private fun updateButtonState(textRes: Int, bgColor: String, enabled: Boolean) {
        btnConnectXiaoCam.apply {
            text = getString(textRes)
            setBackgroundColor(bgColor.toColorInt())
            isEnabled = enabled
        }
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

            usbCameraViewManager.setOnCameraStateListener(object : UsbCameraViewManager.CameraStateListener {
                override fun onCameraConnected() {
                    requireActivity().runOnUiThread {
                        isConnected = true
                        updateButtonState(R.string.connected, "#4CAF50", true)
                        voiceResponder?.speak("Kết nối thành công")
                    }
                }

                override fun onCameraDisconnected() {
                    requireActivity().runOnUiThread {
                        isConnected = false
                        updateButtonState(R.string.connect, "#2F58C3", true)
                        voiceResponder?.speak("Camera đã ngắt kết nối")
                    }
                }

                override fun onCameraError(error: String) {
                    requireActivity().runOnUiThread {
                        isConnected = false
                        updateButtonState(R.string.connect, "#2F58C3", true)
                        usbCameraViewManager.showGlassIcon()
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
        voiceResponder?.speak("Đã ngắt kết nối")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { usbCameraViewManager.release() } catch (_: Exception) {}
        requestQueue.cancelAll { true }
        scope.cancel()
        detectionManager?.release()
        detectionSpeaker?.stop()
    }
}
