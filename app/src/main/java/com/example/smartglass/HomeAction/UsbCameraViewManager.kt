package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.*
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.graphics.BitmapFactory

/**
 * UsbCameraViewManager - Quản lý USB camera, nhận khung hình và gửi qua YOLO
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

    private var uvcCamera: UVCCamera? = null
    private var usbMonitor: USBMonitor? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var previewWidth = 1280
    private var previewHeight = 720

    /** Bắt đầu camera USB */
    fun startCamera() {
        try {
            usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
                override fun onAttach(device: UsbDevice) {
                    Toast.makeText(context, "Phát hiện USB Camera", Toast.LENGTH_SHORT).show()
                    usbMonitor?.requestPermission(device)
                }

                override fun onDeviceOpen(
                    device: UsbDevice,
                    ctrlBlock: USBMonitor.UsbControlBlock,
                    createNew: Boolean
                ) {
                    Toast.makeText(context, "Camera đã kết nối", Toast.LENGTH_SHORT).show()
                    openCamera(ctrlBlock)
                }

                override fun onDeviceClose(
                    device: UsbDevice,
                    ctrlBlock: USBMonitor.UsbControlBlock
                ) {
                    Toast.makeText(context, "Camera bị ngắt kết nối", Toast.LENGTH_SHORT).show()
                    showGlassIcon()
                    cameraStateListener?.onCameraDisconnected()
                }

                override fun onDetach(device: UsbDevice) {
                    Toast.makeText(context, "Camera bị tháo ra", Toast.LENGTH_SHORT).show()
                    showGlassIcon()
                    cameraStateListener?.onCameraDisconnected()
                }

                override fun onCancel(device: UsbDevice) {
                    Toast.makeText(context, "Hủy quyền truy cập USB", Toast.LENGTH_SHORT).show()
                    showGlassIcon()
                    cameraStateListener?.onCameraError("Quyền USB bị hủy")
                }
            }, mainHandler)

            usbMonitor?.register()
            showGlassIcon()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khởi tạo camera: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            showGlassIcon()
            cameraStateListener?.onCameraError(e.message ?: "Lỗi không xác định")
        }
    }

    /** Mở camera sau khi được cấp quyền USB */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock?) {
        try {
            if (ctrlBlock == null) {
                cameraStateListener?.onCameraError("UsbControlBlock null")
                return
            }

            // Khởi tạo UVCCamera với UVCParam mặc định
            val param = UVCParam()
            uvcCamera = UVCCamera(param).apply {
                open(ctrlBlock)
                setPreviewSize(previewWidth, previewHeight, UVCCamera.FRAME_FORMAT_MJPEG)
                setPreviewDisplay(surfaceView.holder)
                startPreview()

                surfaceView.visibility = View.VISIBLE
                glassIcon.visibility = View.GONE
                cameraStateListener?.onCameraConnected()

                // Nhận khung hình để phát hiện vật thể
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

                        // Gửi frame qua YOLO
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
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi mở camera: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            showGlassIcon()
            cameraStateListener?.onCameraError(e.message ?: "Không thể mở camera")
        }
    }

    /** Dừng camera */
    fun stopCamera() {
        try {
            uvcCamera?.stopPreview()
            uvcCamera?.destroy()
        } catch (_: Exception) { }
        uvcCamera = null
        usbMonitor?.unregister()
        usbMonitor = null
    }

    /** Hiện icon kính khi camera dừng */
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

    /** Giải phóng toàn bộ */
    fun release() {
        stopCamera()
        scope.cancel()
    }

    /** Cập nhật kết quả nhận diện */
    fun setOverlayResults(results: List<BoundingBox>) {
        overlayView.setResults(results)
    }

    fun getOverlayWidth() = overlayView.width
    fun getOverlayHeight() = overlayView.height
}
