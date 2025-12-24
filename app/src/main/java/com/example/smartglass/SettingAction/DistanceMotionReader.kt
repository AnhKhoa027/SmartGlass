package com.example.smartglass.SettingAction

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DistanceMotionReader(
    private val context: Context,
    private val sensorBuffer: SensorBuffer
) {

    companion object {
        private const val TAG = "USB-DIST"
        private const val ACTION_USB_PERMISSION =
            "com.example.smartglass.USB_PERMISSION"
    }

    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var serialPort: UsbSerialPort? = null
    private var executor: ExecutorService? = null
    private var keepReading = false

    private var lastTimestamp = -1L
    private var isRegistered = false

    /** CUSTOM PROBER */
    private val customProber: UsbSerialProber by lazy {
        val table = UsbSerialProber.getDefaultProbeTable().apply {
            addProduct(0x1A86, 0x7523, CdcAcmSerialDriver::class.java)
            addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)
            addProduct(0x10C4, 0xEA60, Cp21xxSerialDriver::class.java)
        }
        UsbSerialProber(table)
    }

    /** USB Permission Receiver */
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return

            val device =
                intent.getParcelableExtra<UsbDevice>(
                    UsbManager.EXTRA_DEVICE
                ) ?: return

            if (intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false
                )
            ) {
                openPort(device)
            } else {
                Log.e(TAG, "USB permission denied")
            }
        }
    }

    private val attachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.i(TAG, "USB ATTACHED")
                    start()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.w(TAG, "USB DETACHED")
                    stop()
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return

        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(attachReceiver, filter)

        isRegistered = true
    }

    fun start() {
        register()

        val drivers = customProber.findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            Log.w(TAG, "No USB serial device")
            return
        }

        val device = drivers[0].device

        if (!usbManager.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, pi)
        } else {
            openPort(device)
        }
    }

    private fun openPort(device: UsbDevice) {
        stop()

        val driver = customProber.probeDevice(device) ?: return
        val connection = usbManager.openDevice(device) ?: return

        serialPort = driver.ports[0]

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

            lastTimestamp = -1L
            startReading()
            Log.i(TAG, "Serial connected")

        } catch (e: Exception) {
            Log.e(TAG, "Open error: ${e.message}")
            stop()
        }
    }

    private fun startReading() {
        keepReading = true
        executor = Executors.newSingleThreadExecutor()

        executor?.submit {
            val buffer = ByteArray(128)
            var dataBuffer = ""

            while (keepReading) {
                try {
                    val len = serialPort?.read(buffer, 200) ?: 0
                    if (len > 0) {
                        val chunk =
                            String(buffer, 0, len, StandardCharsets.UTF_8)
                        dataBuffer += chunk

                        val lines = dataBuffer.split("\n")
                        for (i in 0 until lines.size - 1) {
                            processLine(lines[i].trim())
                        }
                        dataBuffer = lines.last()
                    }
                } catch (e: Exception) {
                    keepReading = false
                }
            }
        }
    }

    private fun processLine(line: String) {
        val parts = line.split(",")
        if (parts.size != 6) return

        val timestamp = parts[0].toLongOrNull() ?: return
        val distance = parts[1].toIntOrNull() ?: return
        val status = parts[2]
        val dirX = parts[3]
        val dirY = parts[4]
        val checksum = parts[5].toIntOrNull() ?: return

        if (status != "OK") return
        if (lastTimestamp != -1L && timestamp <= lastTimestamp) return

        val calc =
            distance + dirX.length + dirY.length + (timestamp % 1000).toInt()
        if (checksum != calc) return

        lastTimestamp = timestamp

        sensorBuffer.add(
            SensorSample(
                distanceMm = distance,
                dirX = dirX,
                dirY = dirY,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun stop() {
        keepReading = false
        executor?.shutdownNow()
        executor = null
        serialPort?.close()
        serialPort = null
        lastTimestamp = -1L
    }

    fun release() {
        stop()
        if (isRegistered) {
            context.unregisterReceiver(permissionReceiver)
            context.unregisterReceiver(attachReceiver)
            isRegistered = false
        }
    }
}
