package com.example.smartglass

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class HomeFragment : Fragment() {

    private lateinit var btnConnectESP32: Button
    private lateinit var requestQueue: RequestQueue
    private var isESP32Connected = false

    private lateinit var webView: WebView
    private lateinit var glassIcon: ImageView

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
        webView = view.findViewById(R.id.webViewCam)
        glassIcon = view.findViewById(R.id.glass_icon)

        // Setup WebView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.webViewClient = WebViewClient()

        // Ban đầu hiển thị icon kính, ẩn WebView
        showGlassIcon()

        btnConnectESP32.setOnClickListener {
            if (isESP32Connected) {
                disconnectFromESP32()
            } else {
                connectToESP32()
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestQueue = Volley.newRequestQueue(requireContext())

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false)

        if (autoConnect) {
            connectToESP32()
        }
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

        val request = StringRequest(
            Request.Method.GET, buildESP32Url("capture"),
            {
                isESP32Connected = true
                updateButtonState(R.string.connected, "#4CAF50", true)

                // Khi connect thành công -> hiển thị WebView
                showWebView()
                webView.loadUrl("http://$esp32Ip:81/stream")
            },
            {
                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                updateButtonState(R.string.connect, "#2F58C3", true)
                showGlassIcon()
            }
        )
        requestQueue.add(request)
    }

    fun disconnectFromESP32() {
        isESP32Connected = false
        updateButtonState(R.string.connect, "#2F58C3", true)

        webView.stopLoading()
        webView.loadUrl("about:blank")
        showGlassIcon()
    }

    private fun showWebView() {
        webView.visibility = View.VISIBLE
        glassIcon.visibility = View.GONE
    }

    private fun showGlassIcon() {
        webView.visibility = View.GONE
        glassIcon.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Hủy WebView để tránh crash khi đổi tab
        webView.apply {
            stopLoading()
            clearHistory()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        requestQueue.cancelAll { true }
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_ESP32_IP = "esp32_ip"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val DEFAULT_IP = "192.168.4.1"
    }
}
