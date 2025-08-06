package com.example.smartglass

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import androidx.core.graphics.toColorInt

class HomeFragment : Fragment() {

    private lateinit var connectButton: Button
    private val esp32ConnectUrl = "http://192.168.4.1/connect"
    private val esp32DisconnectUrl = "http://192.168.4.1/disconnect"
    private var isConnected = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        connectButton = view.findViewById(R.id.btnConnect)
        connectButton.setOnClickListener {
            if (isConnected) {
                disconnectFromESP32()
            } else {
                connectToESP32()
            }
        }

        return view
    }

    fun connectToESP32() {
        connectButton.apply {
            isEnabled = false
            text = getString(R.string.connecting)
            setBackgroundColor(Color.GRAY)
        }

        val requestQueue = Volley.newRequestQueue(requireContext())
        val request = StringRequest(Request.Method.GET, esp32ConnectUrl,
            {
                isConnected = true
                connectButton.apply {
                    text = getString(R.string.connected)
                    setBackgroundColor("#4CAF50".toColorInt())
                    isEnabled = true
                }
            },
            {
                Toast.makeText(context, getString(R.string.connect_failed), Toast.LENGTH_SHORT).show()
                connectButton.apply {
                    text = getString(R.string.connect)
                    setBackgroundColor("#2F58C3".toColorInt())
                    isEnabled = true
                }
            })

        requestQueue.add(request)
    }

    fun disconnectFromESP32() {
        connectButton.apply {
            isEnabled = false
            text = getString(R.string.disconnecting)
            setBackgroundColor(Color.GRAY)
        }

        val requestQueue = Volley.newRequestQueue(requireContext())
        val request = StringRequest(Request.Method.GET, esp32DisconnectUrl,
            {
                isConnected = false
                connectButton.apply {
                    text = getString(R.string.disconnected)
                    setBackgroundColor("#A9AFC0".toColorInt())
                    isEnabled = true
                }
            },
            {
                Toast.makeText(context, getString(R.string.disconnect_failed), Toast.LENGTH_SHORT).show()
                connectButton.apply {
                    text = getString(R.string.connected)
                    setBackgroundColor("#4CAF50".toColorInt())
                    isEnabled = true
                }
            })

        requestQueue.add(request)
    }
}
