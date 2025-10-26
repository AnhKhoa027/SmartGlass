package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.*
import android.hardware.usb.UsbDevice
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.example.smartglass.ObjectDetection.BoundingBox
import com.example.smartglass.ObjectDetection.OverlayView
import com.jiangdg.usb.USBMonitor
import com.jiangdg.usb.USBMonitor.UsbControlBlock
import com.jiangdg.uvc.UVCCamera
import com.jiangdg.uvc.IFrameCallback
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.graphics.BitmapFactory

/**
 * Quản lý USB Camera dùng thư viện UVC (v3.2.9)
 * - Hiển thị preview lên SurfaceView
 * - Gửi frame đến YOLO để detect
 * - Hiển thị icon kính khi không có camera
 */
class UsbCameraViewManager(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val overlayView: OverlayView,
    private val detectionManager: DetectionManager?,
    private val glassIcon: ImageView
) {
    interface CameraStateListener {
        fun onCameraConnected()
        fun onCameraDisconnected()
        fun onCameraError(error: String)
    }

    private var cameraStateListener: CameraStateListener? = null
    fun setOnCameraStateListener(listener: CameraStateListener) {
        this.cameraStateListener = listener
    }

    private var camera: UVCCamera? = null
    private var usbMonitor: USBMonitor? = null
    private var ctrlBlock: UsbControlBlock? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private var previewWidth = 1280
    private var previewHeight = 720

    /** Bắt đầu theo dõi camera USB */
    fun startCamera() {
        try {
            usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
                override fun onAttach(device: UsbDevice?) {
                    Toast.makeText(context, "Phát hiện USB Camera", Toast.LENGTH_SHORT).show()
                    usbMonitor?.requestPermission(device)
                }

                override fun onDetach(device: UsbDevice?) {
                    Toast.makeText(context, "Camera bị tháo ra", Toast.LENGTH_SHORT).show()
                    showGlassIcon()
                    cameraStateListener?.onCameraDisconnected()
                }

                override fun onConnect(
                    device: UsbDevice?,
                    ctrlBlock: UsbControlBlock?,
                    createNew: Boolean
                ) {
                    this@UsbCameraViewManager.ctrlBlock = ctrlBlock
                    Toast.makeText(context, "Camera đã kết nối", Toast.LENGTH_SHORT).show()
                    openCamera(ctrlBlock)
                }

                override fun onDisconnect(device: UsbDevice?, ctrlBlock: UsbControlBlock?) {
                    Toast.makeText(context, "Camera bị ngắt kết nối", Toast.LENGTH_SHORT).show()
                    showGlassIcon()
                    cameraStateListener?.onCameraDisconnected()
                }

                override fun onCancel(device: UsbDevice?) {
                    Toast.makeText(context, "Hủy quyền truy cập USB", Toast.LENGTH_SHORT).show()
                    showGlassIcon()
                    cameraStateListener?.onCameraError("Quyền USB bị hủy")
                }
            })

            usbMonitor?.register()

            // 🔹 Khi mới khởi động: hiển thị icon kính, ẩn preview
            showGlassIcon()

        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi khởi tạo camera: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            showGlassIcon()
            cameraStateListener?.onCameraError(e.message ?: "Lỗi không xác định")
        }
    }

    /** Mở camera thực tế */
    private fun openCamera(ctrlBlock: UsbControlBlock?) {
        try {
            val uvcCamera = UVCCamera().apply {
                open(ctrlBlock)
                setPreviewSize(previewWidth, previewHeight, UVCCamera.FRAME_FORMAT_MJPEG)
                setPreviewDisplay(surfaceView.holder)
                startPreview()

                // 🔹 Khi kết nối thành công: hiển thị SurfaceView, ẩn icon kính
                surfaceView.visibility = View.VISIBLE
                glassIcon.visibility = View.GONE

                cameraStateListener?.onCameraConnected()

                setFrameCallback(IFrameCallback { buffer: ByteBuffer? ->
                    if (buffer == null) return@IFrameCallback
                    try {
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val yuv = YuvImage(bytes, ImageFormat.NV21, previewWidth, previewHeight, null)
                        val out = ByteArrayOutputStream()
                        yuv.compressToJpeg(Rect(0, 0, previewWidth, previewHeight), 85, out)
                        val jpegBytes = out.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                        scope.launch {
                            detectionManager?.detectFrame(bitmap)
                        }

                        bitmap.recycle()
                        out.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, UVCCamera.PIXEL_FORMAT_NV21)
            }

            camera = uvcCamera
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi mở camera: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            showGlassIcon()
            cameraStateListener?.onCameraError(e.message ?: "Không thể mở camera")
        }
    }

    /** Hiển thị icon kính & tắt camera */
    fun showGlassIcon() {
        try {
            surfaceView.visibility = View.GONE
            glassIcon.visibility = View.VISIBLE
            overlayView.clear()
            stopCamera()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Ngắt camera */
    fun stopCamera() {
        try {
            camera?.stopPreview()
            camera?.destroy()
        } catch (_: Exception) { }
        camera = null
        usbMonitor?.unregister()
        usbMonitor = null
        ctrlBlock = null
    }

    /** Giải phóng tài nguyên */
    fun release() {
        stopCamera()
        job.cancel()
    }

    fun setOverlayResults(results: List<BoundingBox>) {
        overlayView.setResults(results)
    }

    fun getOverlayWidth() = overlayView.width
    fun getOverlayHeight() = overlayView.height
}
