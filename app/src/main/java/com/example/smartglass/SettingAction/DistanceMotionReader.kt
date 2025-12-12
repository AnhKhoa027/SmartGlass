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
import com.hoho.android.usbserial.driver.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class DistanceMotionReader(
    private val context: Context,
    private val onUpdate: (distance: Int, dirX: String, dirY: String) -> Unit
) {

    companion object {
        private const val TAG = "USB-DIST"
        private const val ACTION_USB_PERMISSION = "com.example.smartglass.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var keepReading = false

    private var lastTimestamp = -1L

    /** CUSTOM PROBER – hỗ trợ nhiều chip USB-Serial */
    private val customProber: UsbSerialProber by lazy {
        val table = UsbSerialProber.getDefaultProbeTable().apply {
            addProduct(0x1A86, 0x7523, CdcAcmSerialDriver::class.java)  // CH340
            addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)   // FTDI
            addProduct(0x10C4, 0xEA60, Cp21xxSerialDriver::class.java) // CP2102
        }
        UsbSerialProber(table)
    }

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
        Log.i(TAG, "Starting USB Distance Motion Reader...")

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val drivers = customProber.findAllDrivers(usbManager)

        if (drivers.isEmpty()) {
            Log.e(TAG, "No USB serial drivers found!")
            return
        }

        val driver = drivers[0]
        val device = driver.device

        Log.i(TAG, "USB device found: VID=${device.vendorId} PID=${device.productId}")

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
        val driver = customProber.probeDevice(device) ?: run {
            Log.e(TAG, "No compatible serial driver for device $device")
            return
        }

        serialPort = driver.ports[0]
        val connection = usbManager.openDevice(driver.device) ?: run {
            Log.e(TAG, "Cannot open USB device")
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
            Log.i(TAG, "Serial port opened successfully")
            startReading()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening serial port: ${e.message}")
        }
    }

    private fun startReading() {
        keepReading = true
        Log.i(TAG, "Start reading serial data...")

        executor.submit {
            val buffer = ByteArray(128)
            var dataBuffer = ""

            while (keepReading) {
                try {
                    val len = serialPort?.read(buffer, 100) ?: 0
                    if (len > 0) {
                        val chunk = String(buffer, 0, len, StandardCharsets.UTF_8)
                        dataBuffer += chunk

                        val lines = dataBuffer.split("\n")
                        for (i in 0 until lines.size - 1) {
                            val line = lines[i].trim()
                            processLine(line)
                        }
                        dataBuffer = lines.last()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading USB serial: ${e.message}")
                }
            }
        }
    }

    /** ------------------------
     *  PROCESS LINE WITH SAFETY
     *  ------------------------ */
    private fun processLine(line: String) {
        val parts = line.split(",")

        if (parts.size != 6) {
            Log.e(TAG, "Invalid data format: $line")
            return
        }

        val timestamp = parts[0].toLongOrNull() ?: return
        val distance = parts[1].toIntOrNull() ?: return
        val status = parts[2]
        val dirX = parts[3]
        val dirY = parts[4]
        val checksumRecv = parts[5].toIntOrNull() ?: return

        // --- CHECK STATUS ---
        if (status != "OK") {
            Log.e(TAG, "Sensor error → skip line")
            return
        }

        // --- CHECK TIMESTAMP (optional safe check) ---
        if (lastTimestamp != -1L && timestamp <= lastTimestamp) {
            Log.e(TAG, "Old or duplicate data → skip")
            return
        }
        lastTimestamp = timestamp

        // --- CHECK CHECKSUM ---
        val checksumCalc =
            distance + dirX.length + dirY.length + (timestamp % 1000).toInt()

        if (checksumRecv != checksumCalc) {
            Log.e(TAG, "Checksum mismatch ($checksumRecv != $checksumCalc) → corrupted line")
            return
        }

        Log.d(TAG, "Valid → dist=$distance dirX=$dirX dirY=$dirY")

        onUpdate(distance, dirX, dirY)
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
