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
import java.io.ByteArrayOutputStream
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

    private var previewWidth = 1920
    private var previewHeight = 1080
    private var surfaceReady = false
    var isUserRequestedConnect = false

    fun initTextureView() {
        textureView.surfaceTextureListener = this
    }

    fun initUsbMonitor() {
        if (usbMonitor != null) return

        usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: android.hardware.usb.UsbDevice) {
                Toast.makeText(context, "USB Camera Phát hiện: ${device.deviceName}", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceOpen(
                device: android.hardware.usb.UsbDevice,
                ctrlBlock: USBMonitor.UsbControlBlock,
                createNew: Boolean
            ) {
                if (isUserRequestedConnect) openCamera(ctrlBlock)
                else try { ctrlBlock.close() } catch (_: Exception) {}
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
                cameraStateListener?.onCameraError("Quyền USB bị từ chối")
            }
        }, mainHandler)

        usbMonitor?.register()
        showGlassIcon()
    }

    fun startCamera() {
        if (usbMonitor == null) initUsbMonitor()
        val devices = usbMonitor?.deviceList
        if (devices.isNullOrEmpty()) {
            Toast.makeText(context, "Không tìm thấy USB Camera", Toast.LENGTH_SHORT).show()
            return
        }

        isUserRequestedConnect = true
        surfaceReady = textureView.isAvailable
        usbMonitor?.requestPermission(devices[0])
    }

    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock?) {
        if (ctrlBlock == null) {
            cameraStateListener?.onCameraError("UsbControlBlock null")
            return
        }

        stopCamera()

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

            uvcCamera?.setFrameCallback(IFrameCallback { buffer: ByteBuffer? ->
                if (buffer == null) return@IFrameCallback

                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                try {
                    val yuv = YuvImage(bytes, ImageFormat.NV21, previewWidth, previewHeight, null)
                    val out = ByteArrayOutputStream()
                    yuv.compressToJpeg(Rect(0, 0, previewWidth, previewHeight), 80, out)
                    val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())

                    bitmap?.let {
                        detectionManager?.detectFrame(it) // <<< Thêm dòng này
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }, UVCCamera.PIXEL_FORMAT_NV21)

        } catch (e: Exception) {
            e.printStackTrace()
            showGlassIcon()
            cameraStateListener?.onCameraError(e.message ?: "Không thể mở Camera")
        }
    }

    fun stopCamera() {
        try {
            uvcCamera?.stopPreview()
            uvcCamera?.destroy()
        } catch (_: Exception) {}
        uvcCamera = null

        detectionManager?.cancelAllTasks()
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
        detectionManager?.cancelAllTasks()
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
                cameraStateListener?.onCameraError("Không Thể mở Stream của Camera")
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
