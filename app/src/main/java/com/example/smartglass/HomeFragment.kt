package com.example.smartglass

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
            Request.Method.GET, buildESP32Url("connect"),
            {
                isESP32Connected = true
                updateButtonState(R.string.connected, "#4CAF50", true)
            },
            {
                Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show()
                updateButtonState(R.string.connect, "#2F58C3", true)
            }
        )
        requestQueue.add(request)
    }

    fun disconnectFromESP32() {
        updateButtonState(R.string.disconnecting, "#808080", false)

        val request = StringRequest(
            Request.Method.GET, buildESP32Url("disconnect"),
            {
                isESP32Connected = false
                updateButtonState(R.string.disconnected, "#A9AFC0", true)
            },
            {
                Toast.makeText(context, R.string.disconnect_failed, Toast.LENGTH_SHORT).show()
                updateButtonState(R.string.connected, "#4CAF50", true)
            }
        )
        requestQueue.add(request)
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_ESP32_IP = "esp32_ip"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val DEFAULT_IP = "192.168.4.1"
    }
}
