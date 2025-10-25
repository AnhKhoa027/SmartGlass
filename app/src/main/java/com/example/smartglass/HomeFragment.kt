package com.example.smartglass

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.example.smartglass.DetectResponse.DetectionSpeaker
import com.example.smartglass.ObjectDetection.OverlayView
import com.example.smartglass.TTSandSTT.VoiceResponder
import com.example.smartglass.HomeAction.DetectionManager
import com.example.smartglass.HomeAction.ApiDetectionManager
import com.example.smartglass.HomeAction.FirebaseHelperOnDemand
import com.example.smartglass.HomeAction.UsbCameraViewManager
import kotlinx.coroutines.*

class HomeFragment : Fragment() {

    private lateinit var btnConnectXiaoCam: Button
    private lateinit var tvCameraIp: TextView
    private lateinit var requestQueue: RequestQueue

    private lateinit var overlayView: OverlayView
    private lateinit var usbCameraViewManager: UsbCameraViewManager
    private lateinit var surfaceViewCam: android.view.SurfaceView
    private lateinit var glassIcon: ImageView

    private var detectionManager: DetectionManager? = null
    private var detectionSpeaker: DetectionSpeaker? = null
    private var voiceResponder: VoiceResponder? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var firebaseHelper: FirebaseHelperOnDemand

    private var isConnected = false

    fun setVoiceResponder(vr: VoiceResponder) {
        voiceResponder = vr
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        btnConnectXiaoCam = view.findViewById(R.id.btnConnect)
        //tvCameraIp = view.findViewById(R.id.tvCameraIp)
        surfaceViewCam = view.findViewById(R.id.camera_view)
        glassIcon = view.findViewById(R.id.glass_icon)
        overlayView = view.findViewById(R.id.overlay)

        firebaseHelper = FirebaseHelperOnDemand()

        //fetchCameraIpFromFirebase()

        return view
    }

    private fun fetchCameraIpFromFirebase() {
        firebaseHelper.fetchCameraIp { ip ->
            activity?.runOnUiThread {
                if (!ip.isNullOrEmpty()) {
                    tvCameraIp.text = "IP Firebase: $ip"
                    requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_XIAOCAM_IP, ip).apply()
                } else {
                    tvCameraIp.text = "IP: --"
                    voiceResponder?.speak("Chưa có IP camera trong Firebase")
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestQueue = Volley.newRequestQueue(requireContext())

        btnConnectXiaoCam.setOnClickListener {
            if (isConnected) {
                disconnectFromUsbCam()
            } else {
                connectToUsbCam()
            }
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
                /* cameraViewManager = */ usbCameraViewManager, // truyền UsbCameraViewManager
                detectionSpeaker!!,
                apiManager,
                scope
            )
        } else {
            detectionManager?.lastFrame = null
        }
    }

     fun connectToUsbCam(callback: ((Boolean) -> Unit)? = null) {
        updateButtonState(R.string.connecting, "#808080", false)
        voiceResponder?.speak("Đang kết nối với kính")

        ensureManagers()

        try {
            usbCameraViewManager = UsbCameraViewManager(
                requireContext(),
                surfaceViewCam,
                overlayView,
                detectionManager
            )

            usbCameraViewManager.startCamera()

            activity?.runOnUiThread {
                isConnected = true
                updateButtonState(R.string.connected, "#4CAF50", true)
                voiceResponder?.speak("Kết nối thành công")
                callback?.invoke(true)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            activity?.runOnUiThread {
                isConnected = false
                updateButtonState(R.string.connect, "#2F58C3", true)
                glassIcon.visibility = View.VISIBLE
                voiceResponder?.speak("Kết nối thất bại")
                callback?.invoke(false)
            }
        }
    }

     fun disconnectFromUsbCam(callback: ((Boolean) -> Unit)? = null) {
        try {
            usbCameraViewManager.release()
        } catch (_: Exception) {}

        detectionSpeaker?.stop()
        isConnected = false

        updateButtonState(R.string.connect, "#2F58C3", true)
        voiceResponder?.speak("Đã ngắt kết nối ")

        callback?.invoke(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            usbCameraViewManager.release()
        } catch (_: Exception) { }
        requestQueue.cancelAll { true }
        scope.cancel()
        detectionManager?.release()
        detectionSpeaker?.stop()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_XIAOCAM_IP = "xiaocam_ip"
    }
}
