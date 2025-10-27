package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.*
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.example.smartglass.ObjectDetection.BoundingBox
import com.example.smartglass.ObjectDetection.OverlayView
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class UsbCameraViewManager(
    private val context: Context,
    private val textureView: TextureView,
    private val overlayView: OverlayView,
    var detectionManager: DetectionManager?,
    private val glassIcon: ImageView
) : TextureView.SurfaceTextureListener {

    interface CameraStateListener {
        fun onCameraConnected()
        fun onCameraDisconnected()
        fun onCameraError(error: String)
    }

    private var cameraStateListener: CameraStateListener? = null
    fun setOnCameraStateListener(listener: CameraStateListener) {
        cameraStateListener = listener
    }

    private var uvcCamera: UVCCamera? = null
    private var usbMonitor: USBMonitor? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var previewWidth = 1920
    private var previewHeight = 1080
    private var surfaceReady = false

    // Flag để kiểm tra user có nhấn Connect không
    var isUserRequestedConnect = false

    fun initTextureView() {
        textureView.surfaceTextureListener = this
    }

    fun initUsbMonitor() {
        if (usbMonitor != null) return

        usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: android.hardware.usb.UsbDevice) {
                // Chỉ thông báo có camera, KHÔNG tự mở
                Toast.makeText(context, "USB Camera detected: ${device.deviceName}", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceOpen(
                device: android.hardware.usb.UsbDevice,
                ctrlBlock: USBMonitor.UsbControlBlock,
                createNew: Boolean
            ) {
                if (isUserRequestedConnect) {
                    openCamera(ctrlBlock)
                } else {
                    // Nếu chưa nhấn Connect, chỉ đóng ctrlBlock thôi
                    try { ctrlBlock.close() } catch (_: Exception) {}
                }
            }

            override fun onDeviceClose(device: android.hardware.usb.UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
                showGlassIcon()
                cameraStateListener?.onCameraDisconnected()
            }

            override fun onDetach(device: android.hardware.usb.UsbDevice) {
                showGlassIcon()
                cameraStateListener?.onCameraDisconnected()
            }

            override fun onCancel(device: android.hardware.usb.UsbDevice) {
                showGlassIcon()
                cameraStateListener?.onCameraError("USB permission denied")
            }
        }, mainHandler)

        usbMonitor?.register()
        showGlassIcon()
    }

    fun startCamera() {
        if (usbMonitor == null) initUsbMonitor()
        val devices = usbMonitor?.deviceList
        if (devices.isNullOrEmpty()) {
            Toast.makeText(context, "No USB camera found", Toast.LENGTH_SHORT).show()
            return
        }

        // Đánh dấu user đã nhấn Connect
        isUserRequestedConnect = true
        usbMonitor?.requestPermission(devices[0])
    }

    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock?) {
        if (ctrlBlock == null) {
            cameraStateListener?.onCameraError("UsbControlBlock null")
            return
        }

        try {
            val param = UVCParam()
            uvcCamera = UVCCamera(param).apply {
                open(ctrlBlock)

                val supportedSizes = getSupportedSizeList()
                val bestSize = supportedSizes?.filter { it.type == UVCCamera.DEFAULT_PREVIEW_FRAME_FORMAT }
                    ?.minByOrNull { kotlin.math.abs(it.width - 1920) + kotlin.math.abs(it.height - 1080) }
                    ?: supportedSizes?.firstOrNull()

                previewWidth = bestSize?.width ?: 1920
                previewHeight = bestSize?.height ?: 1080
                setPreviewSize(previewWidth, previewHeight, UVCCamera.DEFAULT_PREVIEW_FRAME_FORMAT)

                if (surfaceReady) {
                    setPreviewTexture(textureView.surfaceTexture)
                    startPreview()
                }
            }

            textureView.visibility = View.VISIBLE
            glassIcon.visibility = View.GONE
            cameraStateListener?.onCameraConnected()

            // MJPEG frame callback
            uvcCamera?.setFrameCallback(IFrameCallback { buffer: ByteBuffer? ->
                if (buffer == null) return@IFrameCallback

                try {
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bitmap?.let {
                        val bitmapCopy = it.copy(Bitmap.Config.ARGB_8888, true)
                        scope.launch {
                            detectionManager?.detectFrame(bitmapCopy)
                            bitmapCopy.recycle()
                        }
                        it.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, UVCCamera.DEFAULT_PREVIEW_FRAME_FORMAT)

        } catch (e: Exception) {
            e.printStackTrace()
            showGlassIcon()
            cameraStateListener?.onCameraError(e.message ?: "Cannot open camera")
        }
    }

    fun stopCamera() {
        try {
            uvcCamera?.stopPreview()
            uvcCamera?.destroy()
        } catch (_: Exception) {}
        uvcCamera = null
    }

    fun showGlassIcon() {
        textureView.visibility = View.GONE
        glassIcon.visibility = View.VISIBLE
        overlayView.clear()
        stopCamera()
    }

    fun release() {
        stopCamera()
        usbMonitor?.unregister()
        usbMonitor = null
        scope.cancel()
        isUserRequestedConnect = false
    }

    fun setOverlayResults(results: List<BoundingBox>) {
        overlayView.setResults(results)
    }

    fun getOverlayWidth() = overlayView.width
    fun getOverlayHeight() = overlayView.height

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceReady = true
        uvcCamera?.apply {
            try {
                setPreviewTexture(surface)
                startPreview()
            } catch (e: Exception) {
                e.printStackTrace()
                cameraStateListener?.onCameraError("Cannot start preview")
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceReady = false
        stopCamera()
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
