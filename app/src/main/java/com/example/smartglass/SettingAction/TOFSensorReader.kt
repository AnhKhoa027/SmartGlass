package com.example.smartglass.SettingAction

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialDriver
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class TOFSensorReader(
    private val context: Context,
    private val onDistanceUpdate: (distanceMm: Int) -> Unit
) {

    companion object {
        private const val TAG = "TOFSensorReader"
        private const val ACTION_USB_PERMISSION = "com.example.smartglass.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var keepReading = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { openPort(it) }
                    } else {
                        Log.e(TAG, "USB permission denied for device $device")
                    }
                }
            }
        }
    }

    fun start() {
        // Đăng ký receiver USB permission
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Tìm driver USB
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            Log.e(TAG, "No USB serial drivers found")
            return
        }
        val driver: UsbSerialDriver = drivers[0]
        val device = driver.device

        // Request permission
        if (!usbManager.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE)
            usbManager.requestPermission(device, pi)
        } else {
            openPort(device)
        }
    }

    private fun openPort(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            Log.e(TAG, "No driver for device $device")
            return
        }

        serialPort = driver.ports[0]
        val connection = usbManager.openDevice(driver.device) ?: run {
            Log.e(TAG, "Cannot open device")
            return
        }

        try {
            serialPort?.apply {
                open(connection)
                setParameters(
                    115200,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
            }
            startReading()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening serial port: ${e.message}")
        }
    }

    private fun startReading() {
        keepReading = true
        executor.submit {
            val buffer = ByteArray(64)
            var dataBuffer = ""

            while (keepReading) {
                try {
                    val len = serialPort?.read(buffer, 100) ?: 0
                    if (len > 0) {
                        val chunk = String(buffer, 0, len, StandardCharsets.UTF_8)
                        dataBuffer += chunk

                        // Mỗi dòng kết thúc bằng \n là một giá trị distance
                        val lines = dataBuffer.split("\n")
                        for (i in 0 until lines.size - 1) {
                            val line = lines[i].trim()
                            val distance = line.toIntOrNull() ?: 0
                            if (distance > 0) {
                                onDistanceUpdate(distance)
                            }
                        }
                        dataBuffer = lines.last() // giữ phần còn lại cho lần đọc sau
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading USB serial: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        keepReading = false
        serialPort?.close()
        serialPort = null
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
