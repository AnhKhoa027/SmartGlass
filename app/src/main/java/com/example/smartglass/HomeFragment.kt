package com.example.smartglass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.smartglass.ObjectDetection.*
import com.example.smartglass.DetectResponse.DetectionSpeaker
import com.example.smartglass.TTSandSTT.VoiceResponder
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment : Fragment() {

    private lateinit var btnConnectESP32: Button
    private lateinit var requestQueue: RequestQueue
    private var isESP32Connected = false

    private lateinit var webView: WebView
    private lateinit var glassIcon: ImageView
    private lateinit var overlayView: OverlayView

    private var detector: Detector? = null
    private val tracker = ObjectTracker(maxObjects = 5, iouThreshold = 0.5f)

    private lateinit var voiceResponder: VoiceResponder
    private lateinit var detectionSpeaker: DetectionSpeaker

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val esp32Ip: String
        get() = requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ESP32_IP, DEFAULT_IP) ?: DEFAULT_IP

    private fun buildESP32Url(path: String) = "http://$esp32Ip/$path"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        btnConnectESP32 = view.findViewById(R.id.btnConnect)
        glassIcon = view.findViewById(R.id.glass_icon)
        overlayView = view.findViewById(R.id.overlay)
        webView = view.findViewById(R.id.webViewCam)

        showGlassIcon()

        btnConnectESP32.setOnClickListener {
            if (isESP32Connected) disconnectFromESP32() else connectToESP32()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestQueue = Volley.newRequestQueue(requireContext())

        voiceResponder = VoiceResponder(requireContext())
        detectionSpeaker = DetectionSpeaker(requireContext(), voiceResponder)

        // Auto-connect nếu bật trong cài đặt
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false)
        if (autoConnect) connectToESP32()
    }

    private fun updateButtonState(textRes: Int, bgColor: String, enabled: Boolean) {
        btnConnectESP32.apply {
            text = getString(textRes)
            setBackgroundColor(bgColor.toColorInt())
            isEnabled = enabled
        }
    }

    fun connectToESP32() {
        updateButtonState(R.string.connecting, "#808080", false)
        voiceResponder.speak("Đang kết nối với kính")

        val request = StringRequest(
            Request.Method.GET, buildESP32Url("capture"),
            {
                isESP32Connected = true
                updateButtonState(R.string.connected, "#4CAF50", true)

                showWebView()
                webView.loadUrl("http://$esp32Ip:81/stream")

                // Lazy init YOLO
                if (detector == null) {
                    detector = Detector(
                        context = requireContext(),
                        modelPath = "yolov8n_int8.tflite",
                        labelPath = "example_label_file.txt",
                        detectorListener = object : Detector.DetectorListener {
                            override fun onEmptyDetect() {
                                activity?.runOnUiThread { overlayView.clear() }
                            }

                            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                                activity?.runOnUiThread {
                                    val tracked = tracker.update(boundingBoxes)
                                    overlayView.setResults(tracked.map { it.smoothBox })
                                    detectionSpeaker.speakDetections(tracked, overlayView.width, overlayView.height)
                                }
                            }
                        },
                        message = { println(it) }
                    )
                }

                startFrameLoop()
                voiceResponder.speak("Kết nối thành công")
            },
            {
                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                updateButtonState(R.string.connect, "#2F58C3", true)
                showGlassIcon()
                voiceResponder.speak("Kết nối thất bại")
            }
        )
        requestQueue.add(request)
    }

    fun disconnectFromESP32() {
        isESP32Connected = false
        updateButtonState(R.string.connect, "#2F58C3", true)

        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE

        overlayView.clear()
        scope.coroutineContext.cancelChildren()
        detectionSpeaker.stop()
        showGlassIcon()
        voiceResponder.speak("Đã ngắt kết nối")
    }

    private fun showWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.webViewClient = WebViewClient()

        webView.visibility = View.VISIBLE
        glassIcon.visibility = View.GONE
    }

    private fun showGlassIcon() {
        webView.visibility = View.GONE
        glassIcon.visibility = View.VISIBLE
        overlayView.clear()
    }

    private fun startFrameLoop() {
        scope.launch {
            while (isESP32Connected) {
                try {
                    val url = URL(buildESP32Url("capture"))
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()

                    bitmap?.let {
                        val scaled = Bitmap.createScaledBitmap(it, 224, 224, true)
                        detector?.detect(scaled)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(500)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.apply {
            stopLoading()
            clearHistory()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        requestQueue.cancelAll { true }
        scope.cancel()
        detector?.close()
        detectionSpeaker.stop()
        voiceResponder.shutdown()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_ESP32_IP = "esp32_ip"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val DEFAULT_IP = "192.168.4.1"
    }
}
